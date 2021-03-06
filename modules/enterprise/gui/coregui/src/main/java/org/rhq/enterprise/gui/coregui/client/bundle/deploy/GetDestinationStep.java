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
package org.rhq.enterprise.gui.coregui.client.bundle.deploy;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.smartgwt.client.util.SC;
import com.smartgwt.client.widgets.Canvas;
import com.smartgwt.client.widgets.form.DynamicForm;
import com.smartgwt.client.widgets.form.fields.CanvasItem;
import com.smartgwt.client.widgets.form.fields.FormItemIcon;
import com.smartgwt.client.widgets.form.fields.RadioGroupItem;
import com.smartgwt.client.widgets.form.fields.TextAreaItem;
import com.smartgwt.client.widgets.form.fields.TextItem;
import com.smartgwt.client.widgets.form.fields.events.ChangedEvent;
import com.smartgwt.client.widgets.form.fields.events.ChangedHandler;
import com.smartgwt.client.widgets.form.fields.events.DataArrivedEvent;
import com.smartgwt.client.widgets.form.fields.events.DataArrivedHandler;
import com.smartgwt.client.widgets.form.fields.events.IconClickEvent;
import com.smartgwt.client.widgets.form.fields.events.IconClickHandler;
import com.smartgwt.client.widgets.form.validator.IsIntegerValidator;
import com.smartgwt.client.widgets.form.validator.Validator;
import com.smartgwt.client.widgets.layout.VLayout;

import org.rhq.core.domain.bundle.BundleDestination;
import org.rhq.core.domain.bundle.ResourceTypeBundleConfiguration;
import org.rhq.core.domain.bundle.ResourceTypeBundleConfiguration.BundleDestinationBaseDirectory;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.core.domain.resource.group.ResourceGroup;
import org.rhq.enterprise.gui.coregui.client.CoreGUI;
import org.rhq.enterprise.gui.coregui.client.bundle.deploy.selection.SingleCompatibleResourceGroupSelector;
import org.rhq.enterprise.gui.coregui.client.components.wizard.AbstractWizardStep;
import org.rhq.enterprise.gui.coregui.client.gwt.BundleGWTServiceAsync;
import org.rhq.enterprise.gui.coregui.client.gwt.GWTServiceLookup;
import org.rhq.enterprise.gui.coregui.client.inventory.groups.wizard.AbstractGroupCreateWizard;
import org.rhq.enterprise.gui.coregui.client.inventory.resource.type.ResourceTypeRepository;
import org.rhq.enterprise.gui.coregui.client.inventory.resource.type.ResourceTypeRepository.MetadataType;
import org.rhq.enterprise.gui.coregui.client.inventory.resource.type.ResourceTypeRepository.TypesLoadedCallback;
import org.rhq.enterprise.gui.coregui.client.util.FormUtility;
import org.rhq.enterprise.gui.coregui.client.util.message.Message;
import org.rhq.enterprise.gui.coregui.client.util.message.Message.Severity;
import org.rhq.enterprise.gui.coregui.client.util.enhanced.EnhancedVLayout;

/**
 * @author Jay Shaughnessy
 *
 */
public class GetDestinationStep extends AbstractWizardStep {

    private final BundleGWTServiceAsync bundleServer = GWTServiceLookup.getBundleService();
    private final BundleDeployWizard wizard;
    private VLayout form;
    DynamicForm valForm = new DynamicForm();
    private SingleCompatibleResourceGroupSelector selector;
    private BundleDestination destination = new BundleDestination();
    private boolean createInProgress = false;
    private RadioGroupItem destBaseDirItem;

    public GetDestinationStep(BundleDeployWizard wizard) {
        this.wizard = wizard;
    }

    public String getName() {
        return MSG.view_bundle_deployWizard_getDestStep();
    }

    public Canvas getCanvas() {
        if (this.form == null) {
            this.form = new EnhancedVLayout();
            this.valForm.setWidth100();
            this.valForm.setNumCols(2);
            this.valForm.setColWidths("50%", "*");

            final TextItem nameTextItem = new TextItem("name", MSG.view_bundle_deployWizard_getDest_name());
            nameTextItem.setWidth(300);
            nameTextItem.setRequired(true);
            nameTextItem.addChangedHandler(new ChangedHandler() {
                public void onChanged(ChangedEvent event) {
                    Object value = event.getValue();
                    if (value == null) {
                        value = "";
                    }
                    wizard.setSubtitle(value.toString());
                    destination.setName(value.toString());
                }
            });
            FormUtility.addContextualHelp(nameTextItem, MSG.view_bundle_deployWizard_getDest_name_help());

            final TextAreaItem descriptionTextAreaItem = new TextAreaItem("description",
                MSG.view_bundle_deployWizard_getDest_desc());
            descriptionTextAreaItem.setWidth(300);
            descriptionTextAreaItem.addChangedHandler(new ChangedHandler() {
                public void onChanged(ChangedEvent event) {
                    Object value = event.getValue();
                    if (value == null) {
                        value = "";
                    }
                    destination.setDescription(value.toString());
                }
            });

            final TextItem deployDirTextItem = new TextItem("deployDir",
                MSG.view_bundle_deployWizard_getDest_deployDir());
            deployDirTextItem.setWidth(300);
            deployDirTextItem.setRequired(true);
            deployDirTextItem.addChangedHandler(new ChangedHandler() {
                public void onChanged(ChangedEvent event) {
                    Object value = event.getValue();
                    if (value == null) {
                        value = "";
                    }
                    destination.setDeployDir(value.toString());
                }
            });
            FormUtility.addContextualHelp(deployDirTextItem, MSG.view_bundle_deployWizard_getDest_deployDir_help());

            this.destBaseDirItem = new RadioGroupItem("destBaseDir",
                MSG.view_bundle_deployWizard_getDest_destBaseDirName());
            this.destBaseDirItem.setWidth(300);
            this.destBaseDirItem.setRequired(true);
            this.destBaseDirItem.setDisabled(true);
            this.destBaseDirItem.addChangedHandler(new ChangedHandler() {
                public void onChanged(ChangedEvent event) {
                    Object value = event.getValue();
                    if (value != null && value.toString().length() > 0) {
                        destination.setDestinationBaseDirectoryName(value.toString());
                    } else {
                        destination.setDestinationBaseDirectoryName(null);
                    }
                }
            });

            this.selector = new SingleCompatibleResourceGroupSelector("group", MSG.common_title_resource_group());
            this.selector.setWidth(300);
            this.selector.setRequired(true);
            Validator validator = new IsIntegerValidator();
            validator.setErrorMessage(MSG.view_bundle_deployWizard_error_8());
            this.selector.setValidators(validator);
            this.selector.addChangedHandler(new ChangedHandler() {
                @Override
                public void onChanged(ChangedEvent event) {
                    // if the user is typing in the name of the group, and is only partially
                    // done, the event value will be the String of the partial group name.
                    // If the selection is an actual group name, the event value will be
                    // an integer (the group ID) and that is our indication that the selection
                    // of an actual group has been made
                    Integer selectedGroupId = null;
                    if (event.getValue() instanceof Integer) {
                        selectedGroupId = (Integer) event.getValue();
                    }
                    groupSelectionChanged(selectedGroupId);
                }
            });
            final FormItemIcon newGroupIcon = new FormItemIcon();
            newGroupIcon.setSrc("[SKIN]/actions/add.png");
            this.selector.addIconClickHandler(new IconClickHandler() {
                public void onIconClick(IconClickEvent event) {
                    if (event.getIcon().equals(newGroupIcon)) {
                        new QuickGroupCreateWizard(selector).startWizard();
                    }
                }
            });

            FormUtility.addContextualHelp(this.selector, MSG.view_bundle_deployWizard_getDest_group_help(),
                newGroupIcon);

            this.valForm.setItems(nameTextItem, descriptionTextAreaItem, this.selector, this.destBaseDirItem,
                deployDirTextItem);
            CanvasItem ci1 = new CanvasItem();
            ci1.setShowTitle(false);
            ci1.setCanvas(valForm);
            ci1.setDisabled(true);

            this.form.addMember(this.valForm);
        }

        return this.form;
    }

    public boolean nextPage() {

        if (!valForm.validate() || createInProgress) {
            return false;
        }

        // protect against multiple calls to create if the user clicks Next multiple times.
        createInProgress = true;

        // protect against re-execution of this step via the "Previous" button. If we had created
        // a dest previously it must be deleted before we try to create a new one.
        if (wizard.isNewDestination() && (null != wizard.getDestination())) {
            bundleServer.deleteBundleDestination(wizard.getDestination().getId(), //
                new AsyncCallback<Void>() {
                    public void onSuccess(Void voidReturn) {
                        createDestination();
                    }

                    public void onFailure(Throwable caught) {
                        CoreGUI.getErrorHandler().handleError(MSG.view_bundle_deployWizard_error_9(), caught);
                        // try anyway and potentially fail again from there 
                        createDestination();
                    }
                });
        } else {
            createDestination();
        }

        return false;
    }

    // this will advance or decrement the step depending on creation success or failure 
    private void createDestination() {
        int selectedGroup = (Integer) this.valForm.getValue("group");

        bundleServer.createBundleDestination(wizard.getBundleId(), destination.getName(), destination.getDescription(),
            destination.getDestinationBaseDirectoryName(), destination.getDeployDir(), selectedGroup, //
            new AsyncCallback<BundleDestination>() {
                public void onSuccess(BundleDestination result) {
                    wizard.setDestination(result);
                    wizard.setNewDestination(true);
                    CoreGUI.getMessageCenter().notify(
                        new Message(MSG.view_bundle_deployWizard_destinationCreatedDetail_concise(result.getName()),
                            MSG.view_bundle_deployWizard_destinationCreatedDetail(result.getName(),
                                result.getDescription()), Severity.Info));
                    createInProgress = false;
                    wizard.getView().incrementStep();
                }

                public void onFailure(Throwable caught) {
                    String message = MSG.view_bundle_deployWizard_error_10();
                    wizard.getView().showMessage(message);
                    CoreGUI.getErrorHandler().handleError(message, caught);
                    createInProgress = false;
                    wizard.getView().decrementStep();
                }
            });
    }

    private void groupSelectionChanged(Integer selectedGroupId) {
        // new group is, or is in the process of being, selected so forget what the base location was before
        destination.setDestinationBaseDirectoryName(null);
        destBaseDirItem.clearValue();
        destBaseDirItem.setValueMap((String[]) null);

        // this will be null if there is no true group actually selected (e.g. user is typing a partial name to search) 
        if (selectedGroupId != null) {
            bundleServer.getResourceTypeBundleConfiguration(selectedGroupId.intValue(),
                new AsyncCallback<ResourceTypeBundleConfiguration>() {
                    public void onSuccess(ResourceTypeBundleConfiguration result) {
                        // populate the base location drop down with all the possible dest base directories
                        LinkedHashMap<String, String> menuItems = null;
                        if (result != null) {
                            Set<BundleDestinationBaseDirectory> baseDirs;
                            baseDirs = result.getBundleDestinationBaseDirectories();
                            if (baseDirs != null && baseDirs.size() > 0) {
                                String defaultSelectedItem = null;
                                menuItems = new LinkedHashMap<String, String>(baseDirs.size());
                                for (BundleDestinationBaseDirectory baseDir : baseDirs) {
                                    if (baseDir.getDescription() != null) {
                                        menuItems.put(baseDir.getName(),
                                            "<b>" + baseDir.getName() + "</b>: " + baseDir.getDescription());
                                    } else {
                                        menuItems.put(baseDir.getName(), baseDir.getName());
                                    }
                                    if (defaultSelectedItem == null) {
                                        defaultSelectedItem = baseDir.getName();
                                    }
                                }
                                destBaseDirItem.setValueMap(menuItems);
                                destBaseDirItem.setValue(defaultSelectedItem);
                                destination.setDestinationBaseDirectoryName(defaultSelectedItem);
                            }
                        }

                        destBaseDirItem.setDisabled(menuItems == null);
                    }

                    public void onFailure(Throwable caught) {
                        destBaseDirItem.setDisabled(true);
                        CoreGUI.getErrorHandler().handleError(MSG.view_bundle_deployWizard_error_noBundleConfig(),
                            caught);
                    }
                });
        } else {
            destBaseDirItem.setDisabled(true);
        }
    }

    private class QuickGroupCreateWizard extends AbstractGroupCreateWizard {
        private SingleCompatibleResourceGroupSelector groupSelector;
        private HandlerRegistration handlerRegistrar;

        public QuickGroupCreateWizard(SingleCompatibleResourceGroupSelector theSelector) {
            super();
            this.groupSelector = theSelector;
        }

        @Override
        public boolean createGroup() {
            Integer[] ids = memberStep.getSelecterResourceTypeIds();
            if (ids == null || ids.length == 0) {
                SC.warn(MSG.view_bundle_deployWizard_createGroup_error_1());
                return false;
            }
            ResourceTypeRepository typeRepository = ResourceTypeRepository.Cache.getInstance();
            typeRepository.getResourceTypes(ids, EnumSet.of(MetadataType.bundleConfiguration),
                new TypesLoadedCallback() {
                    public void onTypesLoaded(Map<Integer, ResourceType> types) {
                        Set<ResourceType> typeSet = new HashSet<ResourceType>(types.values());
                        if (typeSet.size() != 1) {
                            SC.warn(MSG.view_bundle_deployWizard_createGroup_error_2());
                        } else if (typeSet.iterator().next().getResourceTypeBundleConfiguration() == null) {
                            SC.warn(MSG.view_bundle_deployWizard_createGroup_error_3());
                        } else {
                            QuickGroupCreateWizard.super.createGroup();
                        }
                    }
                });
            return true;
        }

        @Override
        public void groupCreateCallback(final ResourceGroup group) {
            // note: "group" is essentially a flyweight - it doesn't have much other than ID
            this.groupSelector.setValue(group.getId());

            this.handlerRegistrar = this.groupSelector.addDataArrivedHandler(new DataArrivedHandler() {
                public void onDataArrived(DataArrivedEvent event) {
                    handlerRegistrar.removeHandler(); // this handler is only needed once, when group wizard is finished with and we created our group
                    if (groupSelector.getSelectedRecord() == null) {
                        // it appears that the user created a group that cannot be a bundle target.
                        groupSelector.clearValue();
                        groupSelectionChanged(null);
                    } else {
                        groupSelectionChanged(group.getId());
                    }
                }
            });

            // order is important - we set the value first above, add dataArrivedHandler, then fetch, which triggers our handler
            this.groupSelector.fetchData();
        }
    }

}
