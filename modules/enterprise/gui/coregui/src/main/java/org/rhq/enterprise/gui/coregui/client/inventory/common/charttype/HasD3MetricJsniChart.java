/*
 * RHQ Management Platform
 * Copyright (C) 2005-2012 Red Hat, Inc.
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
package org.rhq.enterprise.gui.coregui.client.inventory.common.charttype;

import org.rhq.core.domain.measurement.MeasurementDefinition;

/**
 * Defines GWT JSNI charting capability. Indicator of a class producing
 * d3 (javascript) charts. Allows us to quickly find, in a standard
 * way JSNI charts like d3. Finding implementations of this class
 * will reveal all the d3 charting implementations.
 *
 * @author Mike Thompson
 */
public interface HasD3MetricJsniChart
{
    void drawJsniChart();
    void setEntityId(int entityId) ;
    void setEntityName(String entityName) ;
    void setDefinition(MeasurementDefinition measurementDefinition) ;

}
