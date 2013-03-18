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

import com.smartgwt.client.widgets.Canvas;
import com.smartgwt.client.widgets.HTMLFlow;
import com.smartgwt.client.widgets.layout.VLayout;

import org.rhq.core.domain.bundle.BundleDeployment;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.ConfigurationUtility;
import org.rhq.core.domain.configuration.definition.ConfigurationDefinition;
import org.rhq.core.domain.configuration.definition.ConfigurationTemplate;
import org.rhq.enterprise.gui.coregui.client.components.configuration.ConfigurationEditor;
import org.rhq.enterprise.gui.coregui.client.components.wizard.AbstractWizardStep;

/**
 * @author Jay Shaughnessy
 *
 */
public class GetDeploymentConfigStep extends AbstractWizardStep {

    private final BundleDeployWizard wizard;
    private VLayout editor;

    public GetDeploymentConfigStep(BundleDeployWizard wizard) {
        this.wizard = wizard;
    }

    public String getName() {
        return MSG.view_bundle_deployWizard_getConfigStep();
    }

    public Canvas getCanvas() {
        if (null == editor) {
            ConfigurationDefinition configDef = wizard.getBundleVersion().getConfigurationDefinition();

            // if there are no prop defs for this config def then we can skip this step entirely. just
            // set an empty config.
            if (configDef.getPropertyDefinitions().isEmpty()) {
                wizard.setNewDeploymentConfig(new Configuration());
                // This has started behaving badly. Instead of moving ahead let's give them a message
                // and a chance to go back to the previous screen. 
                // this.wizard.getView().incrementStep();
                HTMLFlow label = new HTMLFlow(MSG.view_bundle_deployWizard_getConfigSkip());
                label.setWidth100();
                label.setHeight(50);
                label.setStylePrimaryName("HeaderLabel");
                label.setStyleName("HeaderLabel");
                editor = new VLayout();
                editor.addMember(label);
            } else {
                // otherwise, pop up the config editor to get the needed config
                Configuration startingConfig;
                BundleDeployment liveDeployment = wizard.getLiveDeployment();
                boolean useLiveConfig = false;

                if (liveDeployment != null) {
                    // If we have a live deployment but it didn't have configuration before
                    // then make sure we don't start with a completely empty config.
                    // In that case, we need to ask for the config from the default template.
                    // But if our live deployment DID have a previous non-empty config, we'll use it
                    // to allow the user to see the previous config values used in the live deployment.
                    Configuration liveConfig = liveDeployment.getConfiguration();
                    if (liveConfig != null) {
                        useLiveConfig = !liveConfig.getMap().isEmpty();
                    }
                }

                if (useLiveConfig == false) {
                    ConfigurationTemplate defaultTemplate = configDef.getDefaultTemplate();
                    startingConfig = (defaultTemplate != null) ? defaultTemplate.createConfiguration()
                        : new Configuration();
                } else {
                    startingConfig = getNormalizedLiveConfig(configDef);
                }
                editor = new ConfigurationEditor(configDef, startingConfig);
            }
        }

        return editor;
    }

    private Configuration getNormalizedLiveConfig(ConfigurationDefinition configDef) {
        Configuration config = wizard.getLiveDeployment().getConfiguration();
        if (null == config) {
            config = new Configuration();
        } else {
            config = config.deepCopy(false);
            ConfigurationUtility.normalizeConfiguration(config, configDef, true, false);
        }

        return config;
    }

    public boolean nextPage() {
        if (null == wizard.getNewDeploymentConfig()) {
            wizard.setNewDeploymentConfig(((ConfigurationEditor) editor).getConfiguration());
        }
        return true;
    }
}
