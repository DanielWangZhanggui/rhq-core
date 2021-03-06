/*
 * RHQ Management Platform
 * Copyright (C) 2005-2008 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.enterprise.server.plugin.pc.content;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.content.ContentSource;
import org.rhq.core.domain.content.ContentSourceSyncResults;
import org.rhq.core.domain.content.ContentSourceType;
import org.rhq.core.domain.content.ContentSyncStatus;
import org.rhq.core.domain.content.Repo;
import org.rhq.core.domain.content.RepoSyncResults;
import org.rhq.core.domain.util.PageControl;
import org.rhq.core.util.progresswatch.ProgressWatcher;
import org.rhq.enterprise.server.auth.SubjectManagerLocal;
import org.rhq.enterprise.server.content.ContentSourceManagerLocal;
import org.rhq.enterprise.server.content.RepoManagerLocal;
import org.rhq.enterprise.server.content.metadata.ContentSourceMetadataManagerLocal;
import org.rhq.enterprise.server.plugin.pc.ServerPluginEnvironment;
import org.rhq.enterprise.server.plugin.pc.content.metadata.ContentSourcePluginMetadataManager;
import org.rhq.enterprise.server.plugin.pc.content.sync.AdvisorySourceSynchronizer;
import org.rhq.enterprise.server.plugin.pc.content.sync.DistributionSourceSynchronizer;
import org.rhq.enterprise.server.plugin.pc.content.sync.PackageSourceSynchronizer;
import org.rhq.enterprise.server.plugin.pc.content.sync.RepoSourceSynchronizer;
import org.rhq.enterprise.server.util.LookupUtil;

/**
 * Responsible for managing {@link ContentProvider} implementations. These
 * implementations come from the content plugins themselves.
 * 
 * @author John Mazzitelli
 * @author Jason Dobies
 */
public class ContentProviderManager {
    private static final Log log = LogFactory.getLog(ContentProviderManager.class);

    private ContentServerPluginManager pluginManager;
    private Map<ContentSource, ContentProvider> adapters;

    // This is used as a monitor lock to the synchronizeContentProvider method;
    // it helps us avoid two content sources getting synchronized at the same
    // time.
    private final Object synchronizeContentSourceLock = new Object();

    /**
     * Asks that the adapter responsible for the given content source return a
     * stream to the package bits for the package at the given location.
     * 
     * @param contentSourceId
     *            the adapter for this content source will be used to stream the
     *            bits
     * @param location
     *            where the adapter can find the package bits on the content
     *            source
     * @return the stream to the package bits
     * @throws Exception
     *             if the adapter failed to load the bits
     */
    public InputStream loadPackageBits(int contentSourceId, String location) throws Exception {
        ContentProvider adapter = getIsolatedContentProvider(contentSourceId);

        PackageSource packageSource = (PackageSource) adapter;
        InputStream inputStream = packageSource.getInputStream(location);

        if (inputStream == null) {
            throw new Exception("Adapter for content source [" + contentSourceId
                + "] failed to give us a stream to the package at location [" + location + "]");
        }

        return inputStream;
    }

    /**
     * Asks that the adapter responsible for the given content source return a
     * stream to the DistributionFile bits for the DistributionFile at the given
     * location.
     * 
     * @param contentSourceId
     *            the adapter for this content source will be used to stream the
     *            bits
     * @param location
     *            where the adapter can find the DistributionFile bits on the
     *            source
     * @return the stream to the DistributionFile bits
     * @throws Exception
     *             if the adapter failed to load the bits
     */
    public InputStream loadDistributionFileBits(int contentSourceId, String location) throws Exception {
        ContentProvider adapter = getIsolatedContentProvider(contentSourceId);

        DistributionSource distSource = (DistributionSource) adapter;
        InputStream inputStream = distSource.getInputStream(location);

        if (inputStream == null) {
            throw new Exception("Adapter for content source [" + contentSourceId
                + "] failed to give us a stream to the distribution file at location [" + location + "]");
        }

        return inputStream;
    }

    /**
     * Asks the provider responsible for the given content source to synchronize
     * with its remote repository. This will not attempt to load any package
     * bits - it only synchronizes the repos and package version information.
     * Note that if a synchronization is already currently underway, this method
     * will not do anything and will return <code>false</code> (in effect, will
     * let the currently running synchronization continue; we try not to step on
     * it). If this method does actually do a sync, <code>true</code> will be
     * returned.
     * 
     * @param contentSourceId
     *            identifies the database entry of the provider
     * @return <code>true</code> if the synchronization completed;
     *         <code>false</code> if there was already a synchronization
     *         happening and this method aborted
     * @throws Exception
     */
    public boolean synchronizeContentProvider(int contentSourceId) throws Exception {

        ContentSourceManagerLocal contentSourceManager = LookupUtil.getContentSourceManager();

        ContentProvider provider = getIsolatedContentProvider(contentSourceId);
        ContentSourceSyncResults results = null;
        SubjectManagerLocal subjMgr = LookupUtil.getSubjectManager();
        Subject overlord = subjMgr.getOverlord();

        // append to this as we go along, building a status report
        StringBuilder progress = new StringBuilder();

        try {
            ContentSource contentSource = contentSourceManager.getContentSource(overlord, contentSourceId);
            if (contentSource == null) {
                throw new Exception("Cannot sync a non-existing content source [" + contentSourceId + "]");
            }

            // This should not take very long so it should be OK to block other
            // callers.
            // We are avoiding the problem that would occur if we try to
            // synchronize the same source
            // at the same time. We could do it more cleverly by synchronizing
            // on a per content source
            // basis, but I don't see a need right now to make this more
            // complicated.
            // We can come back and revisit if we need more fine-grained
            // locking.
            synchronized (synchronizeContentSourceLock) {
                progress.append(new Date()).append(": ");
                progress.append("Start synchronization of content source [").append(contentSource.getName()).append(
                    "]\n");
                progress.append(new Date()).append(": ");
                progress.append("Getting currently known list of packages...\n");
                results = new ContentSourceSyncResults(contentSource);
                results.setResults(progress.toString());
                results = contentSourceManager.persistContentSourceSyncResults(results);
            }

            if (results == null) {
                // note that it technically is still possible to have concurrent
                // syncs - if two
                // threads running in two different servers (i.e. different VMs)
                // both try to sync the
                // same content source and both enter the
                // persistContentSourceSyncResults method at
                // the same time, you'll get two inprogress rows - this is so
                // rare as to not care.
                // Even if it does happen, it may still work, or
                // one sync will get an error and rollback its tx and no harm
                // will be done.
                log.info("Content source [" + contentSource.getName()
                    + "] is already being synchronized - this sync request will be ignored.");
                return false;
            }

            RepoSourceSynchronizer repoSourceSynchronizer = new RepoSourceSynchronizer(contentSource, provider);
            repoSourceSynchronizer.synchronizeCandidateRepos(progress);
            results.setStatus(ContentSyncStatus.SUCCESS);
            results.setResults(progress.toString());

        } catch (Throwable t) {
            if (results != null) {
                // try to reload the results in case it was updated by the SLSB
                // before the
                // exception happened
                ContentSourceSyncResults reloadedResults = contentSourceManager.getContentSourceSyncResults(results
                    .getId());
                if (reloadedResults != null) {
                    results = reloadedResults;
                    if (results.getResults() != null) {
                        progress = new StringBuilder(results.getResults());
                    }
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                t.printStackTrace(new PrintStream(baos));
                progress.append(new Date()).append(": ");
                progress.append("SYNCHRONIZATION ERROR - STACK TRACE FOLLOWS:\n");
                progress.append(baos.toString());
                results.setResults(progress.toString());
                results.setStatus(ContentSyncStatus.FAILURE);
                // finally clause will merge this
            }

            throw new Exception("Failed to sync content source [" + contentSourceId + "]", t);
        } finally {
            if (results != null) {
                results.setEndTime(System.currentTimeMillis());
                contentSourceManager.mergeContentSourceSyncResults(results);
            }
        }

        return true;
    }

    /**
     * Asks each content source associated with the given repo to synchronize
     * the following information for the given repo:
     * <ul>
     * <li>Package Metadata</li>
     * <li>Package Bits</li>
     * <li>Distribution Tree Metadata</li>
     * <li>Distribution Tree Bits</li>
     * </ul>
     * 
     * @param repoId
     *            must indicate a valid repo in the database
     * @return <code>true</code> if the synchronize took place;
     *         <code>false</code> if it did not (for instance, if there is
     *         already a sync taking place for this repo)
     * @throws Exception
     *             if the data required to perform the sync is missing or
     *             invalid
     */
    public boolean synchronizeRepo(int repoId) {
        log.debug("synchronizeRepo() :: start");

        RepoManagerLocal repoManager = LookupUtil.getRepoManagerLocal();
        SubjectManagerLocal subjectManager = LookupUtil.getSubjectManager();

        Subject overlord = subjectManager.getOverlord();

        // Load the repo to sync
        Repo repo = repoManager.getRepo(overlord, repoId);
        if (repo == null) {
            throw new IllegalArgumentException("Invalid repository with id [" + repoId + "] specified for sync.");
        }

        // results =
        // contentSourceManager.persistContentSourceSyncResults(results);
        StringBuilder progress = new StringBuilder();
        progress.append(new Date()).append(": ");
        progress.append("Start synchronization of Repository [").append(repo.getName()).append("]\n");
        progress.append(new Date()).append(": ");
        progress.append("Getting currently known list of content source packages...\n");
        SyncTracker tracker = new SyncTracker(new RepoSyncResults(repo), new ProgressWatcher());
        tracker.setResults(progress.toString());
        tracker.setRepoSyncResults(repoManager.persistRepoSyncResults(tracker.getRepoSyncResults()));
        log.debug("synchronizeRepo :: inProgress");
        tracker = updatePercentComplete(tracker, repoManager);

        if (tracker.getRepoSyncResults() == null) {
            log.info("Repository [" + repo.getName()
                + "] is already currently being synchronized - this sync request will be ignored.");
            return false;
        }

        boolean syncCancelled = false;
        try {
            ThreadUtil.checkInterrupted();

            // Sync each of the content sources associated with the repo.
            for (ContentSource source : repo.getContentSources()) {
                try {
                    ContentProvider provider = getIsolatedContentProvider(source.getId());
                    SyncProgressWeight sw = provider.getSyncProgressWeight();
                    PackageSourceSynchronizer packageSourceSynchronizer = new PackageSourceSynchronizer(repo, source,
                        provider);
                    log.debug("synchronizeRepo :: synchronizePackageMetadata");

                    tracker = updateSyncStatus(tracker, ContentSyncStatus.PACKAGEMETADATA);
                    tracker = packageSourceSynchronizer.synchronizePackageMetadata(tracker);
                } catch (Exception e) {
                    processSyncException(e, tracker, repo, source, repoManager);
                }
            }
            ThreadUtil.checkInterrupted();

            // Setup ProgressWatcher
            for (ContentSource source : repo.getContentSources()) {
                ContentProvider provider = getIsolatedContentProvider(source.getId());
                SyncProgressWeight sw = provider.getSyncProgressWeight();
                tracker.getProgressWatcher().addWork(sw.getPackageMetadataWeight());
                tracker.addAdvisoryMetadataWork(provider);
                tracker.getProgressWatcher().addWork(sw.getDistribtutionBitsWeight());
                tracker.getProgressWatcher().addWork(sw.getDistribtutionMetadataWeight());
                tracker.addPackageBitsWork(provider);
                tracker.getProgressWatcher().finishWork(sw.getPackageMetadataWeight());
            }
            tracker = updatePercentComplete(tracker, repoManager);

            // Sync package bits.
            for (ContentSource source : repo.getContentSources()) {
                // Don't let the entire sync fail if a single content source
                // fails
                try {
                    ContentProvider provider = getIsolatedContentProvider(source.getId());
                    if (provider instanceof PackageSource) {
                        PackageSourceSynchronizer packageSourceSyncer = new PackageSourceSynchronizer(repo, source,
                            provider);
                        log.debug("synchronizeRepo :: synchronizePackageBits");
                        tracker = updateSyncStatus(tracker, ContentSyncStatus.PACKAGEBITS);
                        tracker = packageSourceSyncer.synchronizePackageBits(tracker, provider);
                        tracker = updatePercentComplete(tracker, repoManager);
                    }
                } catch (Exception e) {
                    processSyncException(e, tracker, repo, source, repoManager);
                }
                // Check for cancellation after each content source.
                ThreadUtil.checkInterrupted();
            }

            // Sync distro metadata.
            log.debug("synchronizeRepo :: synchronizeDistributionMetadata");
            for (ContentSource source : repo.getContentSources()) {
                try {
                    ContentProvider provider = getIsolatedContentProvider(source.getId());
                    if (provider instanceof DistributionSource) {
                        DistributionSourceSynchronizer distroSourceSyncer = new DistributionSourceSynchronizer(
                            repo, source, provider);
                        tracker = updateSyncStatus(tracker, ContentSyncStatus.DISTROMETADATA);
                        tracker = distroSourceSyncer.synchronizeDistributionMetadata(tracker);
                        tracker = updatePercentComplete(tracker, repoManager);
                    }
                } catch (Exception e) {
                    processSyncException(e, tracker, repo, source, repoManager);
                }
                // Check for cancellation after each content source.
                ThreadUtil.checkInterrupted();
            }

            // Sync distro bits.
            log.debug("synchronizeRepo :: synchronizeDistributionBits");
            for (ContentSource source : repo.getContentSources()) {
                try {
                    ContentProvider provider = getIsolatedContentProvider(source.getId());
                    if (provider instanceof DistributionSource) {
                        DistributionSourceSynchronizer distroSourceSyncer = new DistributionSourceSynchronizer(
                            repo, source, provider);
                        tracker = updateSyncStatus(tracker, ContentSyncStatus.DISTROBITS);
                        tracker = distroSourceSyncer.synchronizeDistributionBits(tracker);
                        tracker = updatePercentComplete(tracker, repoManager);
                    }
                    ThreadUtil.checkInterrupted();
                } catch (Exception e) {
                    processSyncException(e, tracker, repo, source, repoManager);
                }
                // Check for cancellation after each content source.
                ThreadUtil.checkInterrupted();
            }

            // Sync advisory metadata.
            log.debug("synchronizeRepo :: synchronizeAdvisoryMetadata");
            for (ContentSource source : repo.getContentSources()) {
                try {

                    ContentProvider provider = getIsolatedContentProvider(source.getId());
                    if (provider instanceof AdvisorySource) {
                        tracker = updateSyncStatus(tracker, ContentSyncStatus.ADVISORYMETADATA);
                        AdvisorySourceSynchronizer advisorySourceSyncer = new AdvisorySourceSynchronizer(repo, source,
                            provider);
                        tracker = advisorySourceSyncer.synchronizeAdvisoryMetadata(tracker);
                        tracker = updatePercentComplete(tracker, repoManager);
                    }
                } catch (Exception e) {
                    processSyncException(e, tracker, repo, source, repoManager);
                }
                // Check for cancellation after each content source.
                ThreadUtil.checkInterrupted();
            }

            // Update status to finished.
            if (tracker.getRepoSyncResults().getStatus() != ContentSyncStatus.FAILURE) {
                tracker.getRepoSyncResults().setStatus(ContentSyncStatus.SUCCESS);
            }
            tracker = updateSyncStatus(tracker, tracker.getRepoSyncResults().getStatus());

            progress = new StringBuilder();
            progress.append("\n");
            progress.append(tracker.getRepoSyncResults().getResults());
            progress.append("\n");
            progress.append(new Date()).append(": ");
            progress.append("Repository [").append(repo.getName()).append("] ");
            progress.append("completed syncing with ");
            progress.append((tracker.getRepoSyncResults().getStatus() == ContentSyncStatus.FAILURE) ? "one or more" : "no");
            progress.append(" errors.\n");
            tracker.setResults(progress.toString());
            
            log.debug("synchronizeRepo :: " + tracker.getRepoSyncResults().getStatus());
        } catch (InterruptedException e) {
            RepoSyncResults recentResults = repoManager.getMostRecentSyncResults(overlord, repo.getId());
            log.debug("Caught InterruptedException during sync of repo with id [" + repoId + "].");
            progress.append("\n ** Cancelled syncing **");
            tracker.setResults(progress.toString());
            tracker.getProgressWatcher().resetToZero();
            tracker = updatePercentComplete(tracker, repoManager);
            try {
                tracker = updateSyncStatus(tracker, ContentSyncStatus.CANCELLED);
            } catch (InterruptedException e1) {
                throw new RuntimeException("Unexpected InterruptedException", e1);
            }
            syncCancelled = true;
        }

        if (tracker.getRepoSyncResults() != null) {
            // pw.stop();
            tracker.getRepoSyncResults().setEndTime(System.currentTimeMillis());
            // results.setPercentComplete(new
            // Long(pw.getPercentComplete()));
            repoManager.mergeRepoSyncResults(tracker.getRepoSyncResults());
            log.debug("synchronizeRepo :: merging results.");
        }
        
        return !syncCancelled;
    }

    private SyncTracker updatePercentComplete(SyncTracker tracker, RepoManagerLocal repoManager) {
        tracker.getRepoSyncResults().setPercentComplete(new Long(tracker.getProgressWatcher().getPercentComplete()));
        tracker.setRepoSyncResults(repoManager.mergeRepoSyncResults(tracker.getRepoSyncResults()));
        return tracker;
    }

    private SyncTracker processSyncException(Exception e, SyncTracker tracker, Repo repo, ContentSource source,
        RepoManagerLocal repoManager) throws InterruptedException {
        if (e instanceof InterruptedException) {
            InterruptedException ie = (InterruptedException) e;
            throw ie;
        }
        StringBuilder progress = new StringBuilder();
        log.error("Error while synchronizing repo [" + repo + "] with content source [" + source
            + "]. Synchronization of the repo will continue for any other associated content sources.", e);

        // Try to reload the results in case it was updated by the SLSB before the exception happened.
        RepoSyncResults reloadedResults = repoManager.getRepoSyncResults(tracker.getRepoSyncResults().getId());
        if (reloadedResults != null) {
            tracker.setRepoSyncResults(reloadedResults);
            if (tracker.getRepoSyncResults().getResults() != null) {
                progress = new StringBuilder(tracker.getRepoSyncResults().getResults());
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(baos));
        progress.append(new Date()).append(": ");
        progress.append("SYNCHRONIZATION ERROR - STACK TRACE FOLLOWS:\n");
        progress.append(baos.toString());
        tracker.setResults(progress.toString());
        tracker.setStatus(ContentSyncStatus.FAILURE);
        return tracker;

    }

    private SyncTracker updateSyncStatus(SyncTracker tracker, ContentSyncStatus status)
        throws InterruptedException {
        RepoManagerLocal repoManager = LookupUtil.getRepoManagerLocal();
        SubjectManagerLocal subjMgr = LookupUtil.getSubjectManager();
        Subject overlord = subjMgr.getOverlord();
        int repoId = tracker.getRepoId();
        RepoSyncResults cancelCheck = repoManager.getMostRecentSyncResults(overlord, repoId);
        if (cancelCheck.getStatus() == ContentSyncStatus.CANCELLING) {
            throw new InterruptedException();
        }
        RepoSyncResults results = tracker.getRepoSyncResults();
        results.setStatus(status);
        results = repoManager.mergeRepoSyncResults(results);
        tracker.setRepoSyncResults(results);
        return tracker;
    }

    /**
     * Tests the connection to the content source that has the given ID.
     * 
     * @param contentSourceId the id of the content source to be tested
     *
     * @throws Exception if the test connection to the specified content source failed
     */
    public void testConnection(int contentSourceId) throws Exception {
        ContentProvider adapter = getIsolatedContentProvider(contentSourceId);
        adapter.testConnection();
        //noinspection UnnecessaryReturnStatement
        return;
    }

    /**
     * Returns a set of all content sources whose adapters are managed by this
     * object.
     * 
     * @return all content sources
     */
    public Set<ContentSource> getAllContentSources() {
        synchronized (this.adapters) {
            return new HashSet<ContentSource>(this.adapters.keySet());
        }
    }

    /**
     * Call this method when a new content source is added to the system during
     * runtime. This can also be used to restart an adapter that was previously
     * {@link #shutdownAdapter(ContentSource) shutdown}.
     * 
     * <p>
     * If there is already an adapter currently started for the given content
     * source, this returns silently.
     * </p>
     * 
     * @param contentSource
     *            the new content source that was added
     * @throws InitializationException
     *             if the provider throws an error on its startup
     */
    public void startAdapter(ContentSource contentSource) throws InitializationException {
        synchronized (this.adapters) {
            if (this.adapters.containsKey(contentSource)) {
                return; // already exists, which means it was already
                // initialized
            }
        }

        instantiateAdapter(contentSource);

        try {
            log.info("Initializing content source adapter for [" + contentSource + "] of type ["
                + contentSource.getContentSourceType() + "]");

            ContentProvider adapter = getIsolatedContentSourceAdapter(contentSource);
            adapter.initialize(contentSource.getConfiguration());
        } catch (Exception e) {
            log.warn("Failed to initialize adapter for content source [" + contentSource.getName() + "]");
            // The adapter is put in the adapter cache when it is instantiated.
            // If it failed to start, remove it
            // from the cache so we don't immediately return at the start of
            // this method.
            this.adapters.remove(contentSource);

            throw new InitializationException(e);
        }
    }

    /**
     * Call this method when a content source is removed from the system during
     * runtime or if you just want to shutdown the adapter for whatever reason.
     * You can restart the adapter by calling
     * {@link #startAdapter(ContentSource)}.
     * 
     * <p>
     * If there are no adapters currently started for the given content source,
     * this returns silently.
     * </p>
     * 
     * @param contentSource
     *            the content source being deleted
     */
    public void shutdownAdapter(ContentSource contentSource) {
        synchronized (this.adapters) {
            if (!this.adapters.containsKey(contentSource)) {
                return; // doesn't exist, it was already shutdown
            }
        }

        try {
            log.info("Shutting down content source adapter for [" + contentSource + "] of type ["
                + contentSource.getContentSourceType() + "]");

            ContentProvider adapter = getIsolatedContentSourceAdapter(contentSource);
            adapter.shutdown();
        } catch (Throwable t) {
            log.warn("Failed to shutdown adapter for content source [" + contentSource.getName() + "]", t);
        } finally {
            this.adapters.remove(contentSource);
        }
    }

    /**
     * Convienence method that simply {@link #shutdownAdapter(ContentSource)
     * shuts down the adapter} and then {@link #startAdapter(ContentSource)
     * restarts it}. Call this when, for example, a content source's
     * {@link ContentSource#getConfiguration() configuration} has changed.
     * 
     * @param contentSource
     *            the content source whose adapter is to be restarted
     * @throws Exception
     *             if there is an error asking the provider to shutdown or start
     */
    public void restartAdapter(ContentSource contentSource) throws Exception {
        shutdownAdapter(contentSource);
        startAdapter(contentSource);
    }

    /**
     * Given a ID to a content source, this returns the adapter that is
     * responsible for communicating with that content source where that adapter
     * object will ensure invocations on it are isolated to its plugin
     * classloader.
     * 
     * @param contentProviderId
     *            an ID to a {@link ContentSource}
     * @return the adapter that is communicating with the content source,
     *         isolated to its classloader
     * @throws RuntimeException
     *             if there is no content source with the given ID
     */
    public ContentProvider getIsolatedContentProvider(int contentProviderId) throws RuntimeException {
        synchronized (this.adapters) {
            for (ContentSource contentSource : this.adapters.keySet()) {
                if (contentSource.getId() == contentProviderId) {
                    return getIsolatedContentSourceAdapter(contentSource);
                }
            }
        }

        throw new RuntimeException("Content source ID [" + contentProviderId + "] doesn't exist; can't get adapter");
    }

    /**
     * Tells this manager to initialize itself which will initialize all the
     * adapters.
     * 
     * <p>
     * This is protected so only the plugin container and subclasses can use it.
     * </p>
     * 
     * @param pluginManager
     *            the plugin manager this object can use to obtain information
     *            from (like classloaders)
     */
    protected void initialize(ContentServerPluginManager pluginManager) {
        this.pluginManager = pluginManager;

        ContentSourceMetadataManagerLocal metadataManager = LookupUtil.getContentSourceMetadataManager();
        ContentSourceManagerLocal contentSourceManager = LookupUtil.getContentSourceManager();

        // Our plugin manager should have parsed all descriptors and have our
        // types for us.
        // Let's register the types to make sure they are merged with the old
        // existing types.
        ContentSourcePluginMetadataManager pluginMetadataManager = this.pluginManager.getMetadataManager();
        Set<ContentSourceType> allTypes = pluginMetadataManager.getAllContentSourceTypes();
        metadataManager.registerTypes(allTypes);

        // now let's instantiate all adapters for all known content sources
        createInitialAdaptersMap();

        PageControl pc = PageControl.getUnlimitedInstance();
        Subject overlord = LookupUtil.getSubjectManager().getOverlord();
        List<ContentSource> contentSources = contentSourceManager.getAllContentSources(overlord, pc);

        // let's initalize all adapters for all content sources
        if (contentSources != null) {
            for (ContentSource contentSource : contentSources) {
                try {
                    startAdapter(contentSource);
                } catch (Exception e) {
                    log.warn("Failed to start adapator for content source [" + contentSource + "]");
                }
            }
        }
    }

    /**
     * Tells this manager to shutdown. This will effectively shutdown all of its
     * adapters.
     * 
     * <p>
     * This is protected so only the plugin container and subclasses can use it.
     * </p>
     */
    protected void shutdown() {
        HashMap<ContentSource, ContentProvider> adaptersCopy;

        // shutdown all adapters to give them a chance to clean up after
        // themselves
        synchronized (this.adapters) {
            adaptersCopy = new HashMap<ContentSource, ContentProvider>(this.adapters);
        }

        for (ContentSource contentSource : adaptersCopy.keySet()) {
            shutdownAdapter(contentSource);
        }

        synchronized (this.adapters) {
            this.adapters.clear();
        }
    }

    /**
     * This is protected so only the plugin container and subclasses can use it.
     */
    protected void createInitialAdaptersMap() {
        this.adapters = new HashMap<ContentSource, ContentProvider>();
    }

    /**
     * This returns the adapter that is responsible for communicating with the
     * given content source where that adaptor object will ensure invocations on
     * it are isolated to its plugin classloader.
     * 
     * @param contentSource
     *            the returned adapter communicates with this content source
     * @return the adapter that is communicating with the content source,
     *         isolated to its classloader
     * @throws RuntimeException
     *             if there is no content source adapter available
     */
    protected ContentProvider getIsolatedContentSourceAdapter(ContentSource contentSource) throws RuntimeException {
        ContentProvider adapter;

        synchronized (this.adapters) {
            adapter = this.adapters.get(contentSource);
        }

        if (adapter == null) {
            throw new RuntimeException("There is no adapter for content source [" + adapter + "]");
        }

        ServerPluginEnvironment env = this.pluginManager.getPluginEnvironment(contentSource.getContentSourceType());
        if (env == null) {
            throw new RuntimeException("There is no plugin env. for content source [" + contentSource + "]");
        }

        ClassLoader classLoader = env.getPluginClassLoader();
        IsolatedInvocationHandler handler = new IsolatedInvocationHandler(adapter, classLoader);

        List<Class<?>> ifacesList = new ArrayList<Class<?>>(1);
        ifacesList.add(ContentProvider.class);

        if (adapter instanceof RepoSource) {
            ifacesList.add(RepoSource.class);
        }
        if (adapter instanceof PackageSource) {
            ifacesList.add(PackageSource.class);
        }
        if (adapter instanceof DistributionSource) {
            ifacesList.add(DistributionSource.class);
        }
        if (adapter instanceof AdvisorySource) {
            ifacesList.add(AdvisorySource.class);
        }

        Class<?>[] ifaces = ifacesList.toArray(new Class<?>[ifacesList.size()]);

        return (ContentProvider) Proxy.newProxyInstance(classLoader, ifaces, handler);
    }

    /**
     * Creates a provider instance that will service the give
     * {@link ContentSource}.
     * 
     * @param contentSource
     *            the source that the adapter will connect to
     * @return an adapter instance; will be <code>null</code> if failed to
     *         create adapter
     */
    protected ContentProvider instantiateAdapter(ContentSource contentSource) {
        ContentProvider adapter = null;
        String apiClassName = "?";
        String pluginName = "?";

        try {
            ContentSourceType type = contentSource.getContentSourceType();
            apiClassName = type.getContentSourceApiClass();
            pluginName = this.pluginManager.getMetadataManager().getPluginNameFromContentSourceType(type);

            ServerPluginEnvironment pluginEnv = this.pluginManager.getPluginEnvironment(pluginName);
            ClassLoader pluginClassloader = pluginEnv.getPluginClassLoader();

            ClassLoader startingClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(pluginClassloader);

                Class<?> apiClass = Class.forName(apiClassName, true, pluginClassloader);

                if (ContentProvider.class.isAssignableFrom(apiClass)) {
                    adapter = (ContentProvider) apiClass.newInstance();
                } else {
                    log.warn("The API class [" + apiClassName + "] does not implement ["
                        + ContentProvider.class.getName() + "] in plugin [" + pluginName + "]");
                }
            } finally {
                Thread.currentThread().setContextClassLoader(startingClassLoader);
            }
        } catch (Throwable t) {
            log.warn("Failed to create the API class [" + apiClassName + "] for plugin [" + pluginName + "]", t);
        }

        if (adapter != null) {
            synchronized (this.adapters) {
                this.adapters.put(contentSource, adapter);
            }
        }

        return adapter;
    }

    /**
     * This will handle invocations to an object ensuring that the call is
     * isolated to within the appropriate classloader.
     */
    private class IsolatedInvocationHandler implements InvocationHandler {
        private final Object instance;
        private final ClassLoader classLoader;

        public IsolatedInvocationHandler(Object obj, ClassLoader cl) {
            instance = obj;
            classLoader = cl;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            ClassLoader startingClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(classLoader);
                return method.invoke(instance, args);
            } finally {                Thread.currentThread().setContextClassLoader(startingClassLoader);
            }
        }
    }
}
