/*
 * RHQ Management Platform
 * Copyright (C) 2005-2010 Red Hat, Inc.
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
package org.rhq.enterprise.gui.coregui.server.gwt;

import java.util.List;

import org.rhq.core.domain.criteria.AvailabilityCriteria;
import org.rhq.core.domain.measurement.Availability;
import org.rhq.core.domain.resource.group.composite.ResourceGroupAvailability;
import org.rhq.core.domain.util.PageList;
import org.rhq.enterprise.gui.coregui.client.gwt.AvailabilityGWTService;
import org.rhq.enterprise.gui.coregui.server.util.SerialUtility;
import org.rhq.enterprise.server.measurement.AvailabilityManagerLocal;
import org.rhq.enterprise.server.util.LookupUtil;

/**
 * @author Jay Shaughnessy
 */
public class AvailabilityGWTServiceImpl extends AbstractGWTServiceImpl implements AvailabilityGWTService {

    private static final long serialVersionUID = 1L;

    private AvailabilityManagerLocal availabilityManager = LookupUtil.getAvailabilityManager();

    @Override
    public PageList<Availability> findAvailabilityByCriteria(AvailabilityCriteria criteria) throws RuntimeException {
        try {
            return SerialUtility.prepare(availabilityManager.findAvailabilityByCriteria(getSessionSubject(), criteria),
                "AvailabilityService.findAvailabilityByCriteria");
        } catch (Throwable t) {
            throw getExceptionToThrowToClient(t);
        }
    }

    @Override
    public List<Availability> getAvailabilitiesForResource(int resourceId, long startTime, long endTime)
        throws RuntimeException {
        try {
            return SerialUtility.prepare(
                availabilityManager.getAvailabilitiesForResource(getSessionSubject(), resourceId, startTime, endTime),
                "AvailabilityService.getAvailabilitiesForResource");
        } catch (Throwable t) {
            throw getExceptionToThrowToClient(t);
        }
    }

    @Override
    public List<ResourceGroupAvailability> getAvailabilitiesForResourceGroup(int groupId, long startTime, long endTime)
        throws RuntimeException {
        try {
            return SerialUtility
                .prepare(availabilityManager.getAvailabilitiesForResourceGroup(getSessionSubject(), groupId, startTime,
                    endTime), "AvailabilityService.getAvailabilitiesForResourceGroup");
        } catch (Throwable t) {
            throw getExceptionToThrowToClient(t);
        }
    }

}
