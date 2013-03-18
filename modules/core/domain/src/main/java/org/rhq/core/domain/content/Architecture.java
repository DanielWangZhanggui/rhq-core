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
package org.rhq.core.domain.content;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.hibernate.annotations.NamedQueries;
import org.hibernate.annotations.NamedQuery;

import org.rhq.core.domain.resource.Resource;

/**
 * Describes compatibility between {@link Resource}s and {@link Package}s.
 *
 * @author Jason Dobies
 */
@Entity
@NamedQueries({
    @NamedQuery(name = Architecture.QUERY_FIND_BY_NAME, query = "SELECT arch FROM Architecture arch WHERE arch.name = :name"),
    @NamedQuery(name = Architecture.QUERY_FIND_ALL, query = "SELECT arch FROM Architecture arch"),
    @NamedQuery(name = Architecture.QUERY_DYNAMIC_CONFIG_VALUES, query = "SELECT arch.name, arch.name FROM Architecture arch") })
@SequenceGenerator(allocationSize = org.rhq.core.domain.util.Constants.ALLOCATION_SIZE, name = "RHQ_ARCHITECTURE_ID_SEQ", sequenceName = "RHQ_ARCHITECTURE_ID_SEQ")
@Table(name = "RHQ_ARCHITECTURE")
public class Architecture implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String QUERY_FIND_BY_NAME = "Architecture.findByName";
    public static final String QUERY_FIND_ALL = "Architecture.findAll";

    public static final String QUERY_DYNAMIC_CONFIG_VALUES = "Architecture.dynamicConfigValues";

    @Column(name = "ID", nullable = false)
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "RHQ_ARCHITECTURE_ID_SEQ")
    @Id
    private int id;

    @Column(name = "NAME", nullable = false)
    private String name;

    public Architecture() {
        // for JPA use
    }

    public Architecture(String name) {
        setName(name);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    /**
     * Architecture name, this will also be displayed directly to the user (i.e. there is no display name).
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public int hashCode() {
        return ((name == null) ? 0 : name.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if ((obj == null) || !(obj instanceof Architecture)) {
            return false;
        }

        final Architecture other = (Architecture) obj;
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return "Architecture: name=[" + this.name + "]";
    }
}