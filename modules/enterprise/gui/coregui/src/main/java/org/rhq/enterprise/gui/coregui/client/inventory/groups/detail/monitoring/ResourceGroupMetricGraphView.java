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
package org.rhq.enterprise.gui.coregui.client.inventory.groups.detail.monitoring;

import java.util.EnumSet;
import java.util.List;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.smartgwt.client.widgets.HTMLFlow;

import org.rhq.core.domain.criteria.ResourceGroupCriteria;
import org.rhq.core.domain.measurement.MeasurementDefinition;
import org.rhq.core.domain.measurement.composite.MeasurementDataNumericHighLowComposite;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.core.domain.resource.group.ResourceGroup;
import org.rhq.core.domain.util.PageList;
import org.rhq.enterprise.gui.coregui.client.CoreGUI;
import org.rhq.enterprise.gui.coregui.client.LinkManager;
import org.rhq.enterprise.gui.coregui.client.gwt.GWTServiceLookup;
import org.rhq.enterprise.gui.coregui.client.gwt.ResourceGroupGWTServiceAsync;
import org.rhq.enterprise.gui.coregui.client.inventory.common.AbstractMetricGraphView;
import org.rhq.enterprise.gui.coregui.client.inventory.resource.type.ResourceTypeRepository;
import org.rhq.enterprise.gui.coregui.client.util.Log;
import org.rhq.enterprise.gui.coregui.client.util.MeasurementUtility;

/**
 * @author Greg Hinkle
 * @author Jay Shaughnessy
 * @deprecated see ResourceGroupD3MetricGraphView
 */
@Deprecated
public class ResourceGroupMetricGraphView extends AbstractMetricGraphView {

    private HTMLFlow resourceGroupTitle;

    public ResourceGroupMetricGraphView() {
        super();
    }

    public ResourceGroupMetricGraphView(int groupId, MeasurementDefinition def,
        List<MeasurementDataNumericHighLowComposite> data) {

        super(groupId, def, data);
    }

    @Override
    protected HTMLFlow getEntityTitle() {
        return resourceGroupTitle;
    }

    @Override
    protected void renderGraph() {
        if (null == getDefinition()) {
            ResourceGroupGWTServiceAsync groupService = GWTServiceLookup.getResourceGroupService();

            ResourceGroupCriteria criteria = new ResourceGroupCriteria();
            criteria.addFilterId(getEntityId());
            criteria.fetchResourceType(true);
            groupService.findResourceGroupsByCriteria(criteria, new AsyncCallback<PageList<ResourceGroup>>() {
                @Override
                public void onFailure(Throwable caught) {
                    CoreGUI.getErrorHandler().handleError(MSG.view_resource_monitor_graphs_lookupFailed(), caught);
                }

                @Override
                public void onSuccess(PageList<ResourceGroup> result) {
                    if (result.isEmpty()) {
                        return;
                    }

                    final ResourceGroup group = result.get(0);
                    String url = LinkManager.getResourceGroupLink(group);
                    resourceGroupTitle = new HTMLFlow(LinkManager.getHref(url, group.getName()));

                    ResourceTypeRepository.Cache.getInstance().getResourceTypes(group.getResourceType().getId(),
                        EnumSet.of(ResourceTypeRepository.MetadataType.measurements),
                        new ResourceTypeRepository.TypeLoadedCallback() {
                            public void onTypesLoaded(final ResourceType type) {

                                for (MeasurementDefinition def : type.getMetricDefinitions()) {
                                    if (def.getId() == getDefinitionId()) {
                                        setDefinition(def);

                                        GWTServiceLookup.getMeasurementDataService().findDataForCompatibleGroupForLast(
                                            getEntityId(), new int[] { getDefinitionId() }, 8,
                                            MeasurementUtility.UNIT_HOURS, 60,
                                            new AsyncCallback<List<List<MeasurementDataNumericHighLowComposite>>>() {
                                                @Override
                                                public void onFailure(Throwable caught) {
                                                    CoreGUI.getErrorHandler().handleError(
                                                        MSG.view_resource_monitor_graphs_loadFailed(), caught);
                                                }

                                                @Override
                                                public void onSuccess(
                                                    List<List<MeasurementDataNumericHighLowComposite>> result) {
                                                    setData(result.get(0));

                                                    drawGraph();
                                                }
                                            });
                                    }
                                }
                            }
                        });
                }
            });

        } else {

            drawGraph();
        }
    }

    @Override
    public AbstractMetricGraphView getInstance(int entityId, MeasurementDefinition def,
        List<MeasurementDataNumericHighLowComposite> data) {

        return new ResourceGroupMetricGraphView(entityId, def, data);
    }
}
