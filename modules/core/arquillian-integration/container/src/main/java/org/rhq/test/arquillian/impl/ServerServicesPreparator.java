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

package org.rhq.test.arquillian.impl;

import org.jboss.arquillian.test.spi.event.suite.TestEvent;

import org.rhq.core.pc.PluginContainer;
import org.rhq.test.arquillian.ServerServicesSetup;
import org.rhq.test.arquillian.spi.PluginContainerPreparator;

/**
 * 
 *
 * @author Lukas Krejci
 */
public class ServerServicesPreparator extends AbstractAnnotatedMethodExecutor<ServerServicesSetup> implements PluginContainerPreparator {

    /**
     * @param annotationClass
     */
    public ServerServicesPreparator() {
        super(ServerServicesSetup.class);
    }

    @Override
    public void prepare(PluginContainer pluginContainer, TestEvent testEvent) {
        process(pluginContainer, testEvent);
    }

    @Override
    protected ApplicableTestMethodsAndOrder
        getApplicableTestMethodsAndOrder(ServerServicesSetup annotation) {
        
        return new ApplicableTestMethodsAndOrder(annotation.testMethods(), annotation.order());
    }

    @Override
    protected boolean isApplicableToTest(TestEvent testEvent) {
        return true;
    }

}
