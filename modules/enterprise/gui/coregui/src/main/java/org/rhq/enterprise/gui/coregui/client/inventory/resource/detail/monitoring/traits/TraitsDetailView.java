/*
 * RHQ Management Platform
 * Copyright (C) 2010 Red Hat, Inc.
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
package org.rhq.enterprise.gui.coregui.client.inventory.resource.detail.monitoring.traits;

import com.smartgwt.client.data.Criteria;

import org.rhq.core.domain.criteria.MeasurementDataTraitCriteria;
import org.rhq.enterprise.gui.coregui.client.inventory.common.AbstractMeasurementDataTraitListDetailView;

/**
 * The detail view for the Resource Monitoring>Traits subtab.
 *
 * @author Ian Springer
 */
public class TraitsDetailView extends AbstractMeasurementDataTraitListDetailView {
    public TraitsDetailView(int resourceId, int definitionId) {
        super(null, new TraitsDataSource(), createCriteria(resourceId, definitionId));
    }

    private static Criteria createCriteria(int resourceId, int definitionId) {
        Criteria criteria = new Criteria();

        criteria.addCriteria(MeasurementDataTraitCriteria.FILTER_FIELD_RESOURCE_ID, resourceId);
        criteria.addCriteria(MeasurementDataTraitCriteria.FILTER_FIELD_DEFINITION_ID, definitionId);

        return criteria;
    }

    @Override
    protected void configureTable() {
        super.configureTable();

    }
}
