package org.rhq.enterprise.gui.coregui.client.inventory.resource.detail.operation.schedule;

import org.rhq.core.domain.resource.composite.ResourceComposite;
import org.rhq.enterprise.gui.coregui.client.inventory.common.detail.operation.schedule.AbstractOperationScheduleDetailsView;

/**
 * The details view of the Resource Operations>Schedules subtab.
 *
 * @author Ian Springer
 */
public class ResourceOperationScheduleDetailsView extends AbstractOperationScheduleDetailsView {

    private ResourceComposite resourceComposite;

    public ResourceOperationScheduleDetailsView(ResourceComposite resourceComposite, int scheduleId) {
        super(new ResourceOperationScheduleDataSource(resourceComposite), resourceComposite.getResource()
            .getResourceType(), scheduleId);

        this.resourceComposite = resourceComposite;
    }

    @Override
    protected boolean hasControlPermission() {
        return this.resourceComposite.getResourcePermission().isControl();
    }

    @Override
    protected int getResourceId() {
        return this.resourceComposite.getResource().getId();
    }

}
