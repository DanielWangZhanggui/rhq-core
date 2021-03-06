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
package org.rhq.enterprise.gui.legacy.beans;

import org.rhq.core.domain.measurement.MeasurementUnits;

/**
 * Helper bean for chart rendering.
 */
public final class ChartedMetricBean {
    private String metricName;
    private MeasurementUnits units;
    private int collectionType;

    public ChartedMetricBean(String metricName, MeasurementUnits units, int collectionType) {
        this.metricName = metricName;
        this.units = units;
        this.collectionType = collectionType;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public MeasurementUnits getUnits() {
        return units;
    }

    public void setUnits(MeasurementUnits units) {
        this.units = units;
    }

    public int getCollectionType() {
        return collectionType;
    }

    public void setCollectionType(int collectionType) {
        this.collectionType = collectionType;
    }
}