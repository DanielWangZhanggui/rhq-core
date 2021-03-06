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
package org.rhq.enterprise.server.measurement;

import javax.ejb.Local;

import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.measurement.MeasurementSchedule;
import org.rhq.core.domain.measurement.composite.MeasurementOOBComposite;
import org.rhq.core.domain.util.PageControl;
import org.rhq.core.domain.util.PageList;

/**
 * Interface for the OOB Manager
 * @see org.rhq.enterprise.server.measurement.MeasurementOOBManagerBean
 *
 * @author Heiko W. Rupp
 */
@Local
public interface MeasurementOOBManagerLocal {

    /**
     * Compute oobs from the values in the 1h measurement table that just got added.
     * For the total result, this is an incremental computation. The idea is that
     * it gets run *directly* after the 1h compression (and the baseline recalculation too).
     * @param subject Subject of the caller
     * @param begin Start time of the 1h entries to look at
     */
    void computeOOBsFromHourBeginingAt(Subject subject, long begin);

    /**
     * Return OOB Composites that contain all information about the OOBs in a given time as aggregates.
     * @param subject The caller
     * @param metricNameFilter
     * @param resourceNameFilter a resource name to filter for
     * @param parentNameFilter a parent resource name to filter for   @return List of schedules with the corresponing oob aggregates
     * @param pc PageControl to do pagination
     */
    PageList<MeasurementOOBComposite> getSchedulesWithOOBs(Subject subject, String metricNameFilter,
        String resourceNameFilter, String parentNameFilter, PageControl pc);

    /**
     * Computes the OOBs for the last hour.
     * This is done by getting the latest timestamp of the 1h table and invoking
     * #computeOOBsFromHourBeginingAt
     * @param subject caller
     */
    void computeOOBsFromLastHour(Subject subject);

    /**
     * Remove OOBs for schedules that had their baselines calculated after
     * a certain cutoff point. This is used to get rid of outdated OOB data for
     * baselines that got recalculated, as the new baselines will be 'big' enough for
     * what have been OOBs before and we don't have any baseline history.
     * @param subject The caller
     * @param cutoffTime The reference time to determine new baselines
     */
    void removeOutdatedOOBs(Subject subject, long cutoffTime);

    /**
     * Remove all OOB data for the passed schedule
     * @param subject Caller
     * @param sched the schedule for which we want to clean out the data
     */
    void removeOOBsForSchedule(Subject subject, MeasurementSchedule sched);

    void removeOOBsForGroupAndDefinition(Subject subject, int resourceGroupId, int measurementDefinitionId);

    /**
     * Returns the highest n OOBs for the passed resource id within the last 72h
     * @param subject caller
     * @param resourceId the resource we are interested in
     * @param n max number of entries wanted
     * @return
     */
    PageList<MeasurementOOBComposite> getHighestNOOBsForResource(Subject subject, int resourceId, int n);

    /**
     * Returns the highest n OOBs for the passed group id within the last 72h
     * @param subject caller
     * @param groupId the resource we are interested in
     * @param n max number of entries wanted
     * @return
     */
    PageList<MeasurementOOBComposite> getHighestNOOBsForGroup(Subject subject, int groupId, int n);
}
