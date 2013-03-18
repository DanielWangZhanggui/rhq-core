/*
 * RHQ Management Platform
 * Copyright 2010, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.rhq.enterprise.gui.coregui.client.admin.templates;

import com.smartgwt.client.widgets.form.DynamicForm;
import com.smartgwt.client.widgets.form.fields.CheckboxItem;
import com.smartgwt.client.widgets.form.fields.events.ChangedEvent;
import com.smartgwt.client.widgets.form.fields.events.ChangedHandler;
import com.smartgwt.client.widgets.grid.ListGrid;
import com.smartgwt.client.widgets.layout.VLayout;

import org.rhq.enterprise.gui.coregui.client.components.table.TableWidget;
import org.rhq.enterprise.gui.coregui.client.util.enhanced.EnhancedHLayout;

/**
 * A table widget that provides a checkbox for selecting whether or not to update existing schedules when updating
 * metric templates.
 *
 * @author Ian Springer
 */
public class UpdateExistingSchedulesWidget extends EnhancedHLayout implements TableWidget {
    private TemplateSchedulesView schedulesView;

    public UpdateExistingSchedulesWidget(TemplateSchedulesView schedulesView) {
        super();
        this.schedulesView = schedulesView;
    }

    @Override
    protected void onDraw() {
        super.onDraw();

        VLayout spacer = new VLayout();
        spacer.setWidth(20);
        addMember(spacer);

        DynamicForm form = new DynamicForm();
        form.setNumCols(3);

        CheckboxItem checkboxItem = new CheckboxItem("updateExistingSchedules",
            MSG.view_admin_measTemplates_updateExisting_title());
        checkboxItem.setDefaultValue(schedulesView.isUpdateExistingSchedules());
        checkboxItem.setTooltip(MSG.view_admin_measTemplates_updateExisting_tooltip());
        checkboxItem.setHoverWidth(200);
        checkboxItem.addChangedHandler(new ChangedHandler() {
            public void onChanged(ChangedEvent changedEvent) {
                boolean newValue = (Boolean) changedEvent.getValue();
                schedulesView.setUpdateExistingSchedules(newValue);
            }
        });

        form.setFields(checkboxItem);
        addMember(form);
    }

    @Override
    public void refresh(ListGrid listGrid) {
        // When new data arrives, we want to keep the state of our checkbox the same, so nothing to do here.
    }
}
