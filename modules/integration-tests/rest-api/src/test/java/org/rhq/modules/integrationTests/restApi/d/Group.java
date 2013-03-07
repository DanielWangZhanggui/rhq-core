/*
 * RHQ Management Platform
 * Copyright (C) 2005-2012 Red Hat, Inc.
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
package org.rhq.modules.integrationTests.restApi.d;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * A group for testing
 * @author Heiko W. Rupp
 */
@XmlRootElement(name = "groupRest")
public class Group {

    String name;
    String category = "MIXED";
    Integer resourceTypeId;
    int id;
    boolean recursive;
    int dynaGroupDefinitionId;
    private int explicitCount;
    private int implicitCount;

    List<Link> links;


    public Group() {
    }

    public Group(String name) {
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getResourceTypeId() {
        return resourceTypeId;
    }

    public void setResourceTypeId(Integer resourceTypeId) {
        this.resourceTypeId = resourceTypeId;
    }

    public List<Link> getLinks() {
        return links;
    }

    public void setLinks(List<Link> links) {
        this.links = links;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public int getDynaGroupDefinitionId() {
        return dynaGroupDefinitionId;
    }

    public void setDynaGroupDefinitionId(int dynaGroupDefinitionId) {
        this.dynaGroupDefinitionId = dynaGroupDefinitionId;
    }

    public int getExplicitCount() {
        return explicitCount;
    }

    public void setExplicitCount(int explicitCount) {
        this.explicitCount = explicitCount;
    }

    public int getImplicitCount() {
        return implicitCount;
    }

    public void setImplicitCount(int implicitCount) {
        this.implicitCount = implicitCount;
    }
}
