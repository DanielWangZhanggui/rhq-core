/*
 * RHQ Management Platform
 * Copyright (C) 2005-2008 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2, as
 * published by the Free Software Foundation, and/or the GNU Lesser
 * General Public License, version 2.1, also as published by the Free
 * Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License and the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License
 * and the GNU Lesser General Public License along with this program;
 * if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.rhq.core.clientapi.server.core;

import java.io.Serializable;

/**
 * The request for an agent to connect to a server.
 *
 * @author John Mazzitelli
 */
public class ConnectAgentRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String agentName;
    private final AgentVersion agentVersion;

    public ConnectAgentRequest(String agentName, AgentVersion agentVersion) {
        this.agentName = agentName;
        this.agentVersion = agentVersion;
    }

    /**
     * The name of the agent that is requesting to be connected.
     * This is not necessarily the agent's hostname or IP address, it is
     * the string assigned to the agent that makes it unique among all other
     * agents in the system.
     *  
     * @return agent name
     */
    public String getAgentName() {
        return this.agentName;
    }

    /**
     * The version information of the agent asking to be connected.
     * 
     * @return agent version
     */
    public AgentVersion getAgentVersion() {
        return agentVersion;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("ConnectAgentRequest: ");
        str.append("agent-name=[");
        str.append(this.agentName);
        str.append("; version-info=[");
        str.append(this.agentVersion);
        str.append("]");
        return str.toString();
    }
}