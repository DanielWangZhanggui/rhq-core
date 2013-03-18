package org.rhq.enterprise.gui.coregui.client.bundle.deploy.selection;

import com.smartgwt.client.data.DSRequest;

import org.rhq.core.domain.criteria.ResourceGroupCriteria;
import org.rhq.core.domain.resource.ResourceCategory;
import org.rhq.enterprise.gui.coregui.client.inventory.resource.selection.ResourceGroupSelector;

public class PlatformResourceGroupSelector extends ResourceGroupSelector {

    public PlatformResourceGroupSelector() {
        super();
    }

    @Override
    protected SelectedPlatformResourceGroupsDataSource getDataSource() {
        return new SelectedPlatformResourceGroupsDataSource();
    }

    protected class SelectedPlatformResourceGroupsDataSource extends SelectedResourceGroupsDataSource {

        @Override
        protected ResourceGroupCriteria getFetchCriteria(final DSRequest request) {
            ResourceGroupCriteria result = super.getFetchCriteria(request);
            result.addFilterExplicitResourceCategory(ResourceCategory.PLATFORM);
            return result;
        }
    }

}
