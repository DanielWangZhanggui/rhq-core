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
package org.rhq.enterprise.server.cloud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.testng.annotations.Test;

import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.cloud.AffinityGroup;
import org.rhq.core.domain.cloud.PartitionEvent;
import org.rhq.core.domain.cloud.PartitionEventType;
import org.rhq.core.domain.cloud.Server;
import org.rhq.core.domain.cloud.composite.FailoverListComposite;
import org.rhq.core.domain.resource.Agent;
import org.rhq.core.domain.util.PageControl;
import org.rhq.core.domain.util.PageList;
import org.rhq.enterprise.server.core.AgentManagerLocal;
import org.rhq.enterprise.server.test.AbstractEJB3Test;
import org.rhq.enterprise.server.util.LookupUtil;

/**
 * Tests the configuration manager.
 * 
 * NOTE: These tests only work if there are no servers registered in the db.  The tests will clean up themselves for
 * NOTE: multiple runs but will fail (typically the getForSingleAgent tests) if run against a db that has been
 * NOTE: used against an active server.  
 */
@Test
public class FailoverListManagerBeanTest extends AbstractEJB3Test {

    private static final boolean ENABLE_TESTS = true;

    private FailoverListManagerLocal failoverListManager;
    private AgentManagerLocal agentManager;
    private PartitionEventManagerLocal partitionEventManager;
    private TopologyManagerLocal topologyManager;
    private AffinityGroupManagerLocal affinityGroupManager;
    private Subject overlord;

    private List<Server> servers;
    private List<Agent> agents;
    private AffinityGroup ag;
    private PartitionEvent partitionEvent;
    private List<Agent> newAgents;

    @Override
    protected void beforeMethod() throws Exception {
        agentManager = LookupUtil.getAgentManager();
        failoverListManager = LookupUtil.getFailoverListManager();
        partitionEventManager = LookupUtil.getPartitionEventManager();
        topologyManager = LookupUtil.getTopologyManager();
        affinityGroupManager = LookupUtil.getAffinityGroupManager();
        overlord = LookupUtil.getSubjectManager().getOverlord();

        servers = new ArrayList<Server>();
        agents = new ArrayList<Agent>();
        newAgents = new ArrayList<Agent>();

        servers.clear();
        agents.clear();
        newAgents.clear();

        prepareForTestAgents();
    }

    @Override
    protected void afterMethod() throws Exception {
        try {
            getTransactionManager().begin();

            partitionEventManager.deletePartitionEvents(overlord, new Integer[] { partitionEvent.getId() });

            try {
                for (Server server : servers) {
                    // must set to down to allow for a delete
                    topologyManager.updateServerMode(LookupUtil.getSubjectManager().getOverlord(),
                        new Integer[] { server.getId() }, Server.OperationMode.DOWN);
                    topologyManager.deleteServer(LookupUtil.getSubjectManager().getOverlord(), server.getId());
                }

                for (Agent agent : agents) {
                    agentManager.deleteAgent(agent);
                }

                affinityGroupManager.delete(overlord, new Integer[] { ag.getId() });

                if (null != newAgents) {
                    for (Agent agent : newAgents) {
                        agentManager.deleteAgent(agent);
                    }
                }

                // Delete any remaining partition events generated by the test and the test cleanup
                PageList<PartitionEvent> events = partitionEventManager.getPartitionEvents(overlord, null, null, "flm",
                    PageControl.getUnlimitedInstance());
                Integer[] eventIds = new Integer[events.size()];
                for (int i = 0, size = events.size(); (i < size); ++i) {
                    eventIds[i] = events.get(i).getId();
                }
                partitionEventManager.deletePartitionEvents(overlord, eventIds);

            } catch (Exception e) {
                System.out.println(e);
                throw e;
            }

            getTransactionManager().commit();
        } catch (Exception e) {
            try {
                System.out.println(e);
                getTransactionManager().rollback();
            } catch (Exception ignore) {
            }

            throw e;
        } finally {
            this.unprepareForTestAgents();
        }
    }

    private void setupTest(int numServers, int numAgents) throws Exception {
        Server server = new Server();
        Agent agent;

        getTransactionManager().begin();

        try {
            for (int i = 0; (i < numServers); ++i) {
                server = new Server();
                server.setName("Server-flm-" + i);
                server.setAddress("" + i);
                server.setPort(i);
                server.setSecurePort(i);
                server.setOperationMode(Server.OperationMode.NORMAL);
                em.persist(server);
                servers.add(server);
            }

            for (int i = 0; (i < numAgents); ++i) {
                agent = new Agent("Agent-flm-" + i, "" + i, 1, "endpoint", "token" + i);
                em.persist(agent);
                agents.add(agent);
            }

            ag = new AffinityGroup("AG-flm-1");
            em.persist(ag);

            partitionEvent = new PartitionEvent("FLM-TEST", PartitionEventType.SYSTEM_INITIATED_PARTITION,
                "Test-flm-detail", PartitionEvent.ExecutionStatus.IMMEDIATE);
            em.persist(partitionEvent);

            getTransactionManager().commit();
        } catch (Exception e) {
            try {
                System.out.println(e);
                getTransactionManager().rollback();
            } catch (Exception ignore) {
            }

            throw e;
        }
    }

    private void setupNewAgents(int numNewAgents) throws Exception {
        getTransactionManager().begin();

        try {
            for (int i = 0; (i < numNewAgents); ++i) {

                Agent agent = new Agent("Agent-flm-NEW" + i, "NEW-" + i, 1, "endpoint", "token-NEW-" + i);
                em.persist(agent);
                newAgents.add(agent);
            }

            getTransactionManager().commit();
        } catch (Exception e) {
            try {
                System.out.println(e);
                getTransactionManager().rollback();
            } catch (Exception ignore) {
            }

            throw e;
        }
    }

    private boolean validateBalance(Map<Agent, FailoverListComposite> result, int numServers, int numAgents) {

        validateBasic(result, numServers, numAgents);

        // validate balance level by level
        for (int level = 0; (level < numServers); ++level) {
            Map<String, Integer> distributionMap = new HashMap<String, Integer>(numServers);
            for (Agent agent : result.keySet()) {
                FailoverListComposite flc = result.get(agent);
                // for debugging
                //if (i == 1) {
                //    System.out.println(agent + " : " + flc);
                //}
                FailoverListComposite.ServerEntry server = flc.get(level);
                Integer count = distributionMap.get(server.address);
                distributionMap.put(server.address, (null == count) ? 1 : ++count);
            }
            for (Integer agentsOnServer : distributionMap.values()) {
                double div = (double) numAgents / (double) numServers;
                int ceil = (int) Math.ceil(div);
                int floor = (int) Math.floor(div);
                // as we get deeper be more lenient. We haven't come up with any sort of genius algorithm, it's
                // just ok, if it balances well for the primary and secondary that's good.  Allow a skew of 1 for
                // level 3-5 and a skew of 2 for levels past that
                if (level > 2) {
                    ++ceil;
                    --floor;
                }
                if (level > 5) {
                    ++ceil;
                    --floor;
                }
                assert agentsOnServer <= ceil : "AgentsOnServer [" + agentsOnServer + "] > ceiling [" + ceil
                    + "] (level " + level + ")";
                assert agentsOnServer >= floor : "AgentsOnServer [" + agentsOnServer + "] < floor [" + floor
                    + "] (level " + level + ")";
            }
        }

        return true;
    }

    private void validateAffinity(Map<Agent, FailoverListComposite> result, int numServers, int numAgents,
        int numAffinityServers, int numAffinityAgents) {

        double div;

        validateBasic(result, numServers, numAgents);

        // validate level by level        
        for (int level = 0; (level < numServers); ++level) {
            Map<Integer, Integer> distributionMap = new HashMap<Integer, Integer>(numServers);
            for (Agent agent : result.keySet()) {
                FailoverListComposite flc = result.get(agent);
                FailoverListComposite.ServerEntry server = flc.get(level);
                Integer agentId = Integer.valueOf(agent.getAddress());
                Integer serverId = Integer.valueOf(server.address);
                if ((level < numAffinityServers) && (agentId < numAffinityAgents))
                    assert (serverId < numAffinityServers) : "serverId [" + serverId + "] >= numAffinityServers ["
                        + numAffinityServers + "]";
                Integer count = distributionMap.get(serverId);
                distributionMap.put(serverId, (null == count) ? 1 : ++count);
            }
            for (Integer serverId : distributionMap.keySet()) {
                Integer agentsOnServer = distributionMap.get(serverId);
                if (level >= numAffinityServers) {
                    if (serverId < numAffinityServers) {
                        div = (numAgents - numAffinityAgents) / (double) numAffinityServers;
                    } else {
                        div = numAffinityAgents / (double) (numServers - numAffinityServers);
                    }
                    int ceil = (int) Math.ceil(div);
                    int floor = (int) Math.floor(div);
                    assert agentsOnServer <= ceil : "AgentsOnServer [" + agentsOnServer + "] > ceiling [" + ceil + "]";
                    assert agentsOnServer >= floor : "AgentsOnServer [" + agentsOnServer + "] < floor [" + floor + "]";
                }
            }
        }
    }

    private void validateBasic(Map<Agent, FailoverListComposite> result, int numServers, int numAgents) {
        // validate server list length
        for (Agent agent : result.keySet()) {
            assert result.get(agent).size() == numServers;
        }

        // validate no duplicates in server list
        for (Agent agent : result.keySet()) {
            Set<String> names = new HashSet<String>(numServers);
            FailoverListComposite flc = result.get(agent);
            for (int i = 0; (i < numServers); ++i) {
                FailoverListComposite.ServerEntry server = flc.get(i);
                assert names.add(server.address);
            }
        }
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents1_0() throws Exception {
        setupTest(1, 0);
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;
        assert validateBalance(result, 1, 0);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents1_1() throws Exception {
        setupTest(1, 1);
        long start = System.currentTimeMillis();
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        System.out.println("Elapsed 1/0 = " + (System.currentTimeMillis() - start) + "ms");
        assert null != result;
        assert validateBalance(result, 1, 1);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents1_2() throws Exception {
        setupTest(1, 2);
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;
        assert validateBalance(result, 1, 2);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents1_5() throws Exception {
        setupTest(1, 5);
        long start = System.currentTimeMillis();
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        System.out.println("Elapsed 1/5 = " + (System.currentTimeMillis() - start) + "ms");
        assert null != result;
        assert validateBalance(result, 1, 5);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents2_0() throws Exception {
        setupTest(2, 0);
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;
        assert validateBalance(result, 2, 0);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents2_1() throws Exception {
        setupTest(2, 1);
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;
        assert validateBalance(result, 2, 1);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents2_2() throws Exception {
        setupTest(2, 2);
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;
        assert validateBalance(result, 2, 2);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents2_3() throws Exception {
        setupTest(2, 3);
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;
        assert validateBalance(result, 2, 3);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents2_9() throws Exception {
        setupTest(2, 9);
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;
        assert validateBalance(result, 2, 9);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents2_10() throws Exception {
        setupTest(2, 10);
        long start = System.currentTimeMillis();
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        System.out.println("Elapsed 2/10 = " + (System.currentTimeMillis() - start) + "ms");
        assert null != result;
        assert validateBalance(result, 2, 10);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents2_15() throws Exception {
        setupTest(2, 15);
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;
        assert validateBalance(result, 2, 15);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents3_2() throws Exception {
        setupTest(3, 2);
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;
        assert validateBalance(result, 3, 2);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents3_3() throws Exception {
        setupTest(3, 3);
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;
        assert validateBalance(result, 3, 3);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents3_4() throws Exception {
        setupTest(3, 4);
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;
        assert validateBalance(result, 3, 4);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents5_10() throws Exception {
        setupTest(5, 10);
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;
        assert validateBalance(result, 5, 10);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents5_25() throws Exception {
        setupTest(5, 25);
        long start = System.currentTimeMillis();
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        System.out.println("Elapsed 2/10 = " + (System.currentTimeMillis() - start) + "ms");
        assert null != result;
        assert validateBalance(result, 5, 25);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents5_42() throws Exception {
        setupTest(5, 42);
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;
        assert validateBalance(result, 5, 42);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents20_1000() throws Exception {
        setupTest(20, 1000);
        long start = System.currentTimeMillis();
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        System.out.println("Elapsed 20/1000 = " + (System.currentTimeMillis() - start) + "ms");
        assert null != result;
        assert validateBalance(result, 20, 1000);

        start = System.currentTimeMillis();
        setupNewAgents(1);
        failoverListManager.getForSingleAgent(partitionEvent, newAgents.get(0).getName());
        System.out.println("Elapsed 1 NEW 20/1000 = " + (System.currentTimeMillis() - start) + "ms");
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents2_10_affinity_1_10() throws Exception {
        setupTest(2, 10);
        // set up affinity such that all agents go to a server-1
        servers.get(0).setAffinityGroup(ag);
        for (Agent agent : agents) {
            agent.setAffinityGroup(ag);
        }

        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;
        validateAffinity(result, 2, 10, 1, 10);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents4_20_affinity_2_10() throws Exception {
        setupTest(4, 20);
        // set up affinity such that all agents go to a server-1
        servers.get(0).setAffinityGroup(ag);
        servers.get(1).setAffinityGroup(ag);
        for (int i = 0; (i < 10); ++i) {
            agents.get(i).setAffinityGroup(ag);
        }

        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;
        validateAffinity(result, 4, 20, 2, 10);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents3_20_affinity_2_10() throws Exception {
        setupTest(3, 20);
        // set up affinity such that all agents go to a server-1
        servers.get(0).setAffinityGroup(ag);
        servers.get(1).setAffinityGroup(ag);
        for (int i = 0; (i < 10); ++i) {
            agents.get(i).setAffinityGroup(ag);
        }

        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;
        validateAffinity(result, 3, 20, 2, 10);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForAllAgents3_20_preferred_20() throws Exception {
        setupTest(3, 20);
        for (int i = 0; (i < 10); ++i) {
            agents.get(i).setServer(servers.get(0));
        }
        for (int i = 10; (i < 20); ++i) {
            agents.get(i).setServer(servers.get(1));
        }

        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;
        validateBalance(result, 3, 20);
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForSingleAgent_existing() throws Exception {
        setupTest(2, 4);
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;
        for (Agent agent : result.keySet()) {
            FailoverListComposite flc1 = result.get(agent);
            FailoverListComposite flc2 = failoverListManager.getForSingleAgent(partitionEvent, agent.getName());
            assert flc1.size() == flc2.size();
            for (int i = 0, size = flc1.size(); (i < size); ++i) {
                assert flc1.get(i).equals(flc2.get(i));
            }
        }
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForSingleAgent_new_1() throws Exception {
        setupTest(2, 4);
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;

        setupNewAgents(3);
        List<FailoverListComposite> serverLists = new ArrayList<FailoverListComposite>(3);
        for (int i = 0; i < 3; ++i) {
            serverLists.add(failoverListManager.getForSingleAgent(partitionEvent, newAgents.get(i).getName()));
            assert null != serverLists.get(i);
            assert serverLists.get(i).size() == servers.size();
        }
        assert !serverLists.get(0).equals(serverLists.get(1));
        assert serverLists.get(0).equals(serverLists.get(2));
        assert !serverLists.get(1).equals(serverLists.get(2));
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForSingleAgent_new_2() throws Exception {
        setupTest(3, 6);
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;

        setupNewAgents(3);
        List<FailoverListComposite> serverLists = new ArrayList<FailoverListComposite>(3);
        for (int i = 0; i < 3; ++i) {
            serverLists.add(failoverListManager.getForSingleAgent(partitionEvent, newAgents.get(i).getName()));
            assert null != serverLists.get(i);
            assert serverLists.get(i).size() == servers.size();
        }
        assert !serverLists.get(0).equals(serverLists.get(1));
        assert !serverLists.get(0).equals(serverLists.get(2)) : serverLists.toString();
        assert !serverLists.get(1).equals(serverLists.get(2));
    }

    @Test(enabled = ENABLE_TESTS)
    public void testGetForSingleAgent_new_3() throws Exception {
        setupTest(1, 0);
        Map<Agent, FailoverListComposite> result = failoverListManager.refresh(partitionEvent, servers, agents);
        assert null != result;

        setupNewAgents(3);
        List<FailoverListComposite> serverLists = new ArrayList<FailoverListComposite>(3);
        for (int i = 0; i < 3; ++i) {
            serverLists.add(failoverListManager.getForSingleAgent(partitionEvent, newAgents.get(i).getName()));
            assert null != serverLists.get(i);
            assert serverLists.get(i).size() == servers.size();
        }
        assert serverLists.get(0).equals(serverLists.get(1));
        assert serverLists.get(0).equals(serverLists.get(2));
        assert serverLists.get(1).equals(serverLists.get(2));
    }
}