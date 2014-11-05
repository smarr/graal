/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.api.meta;

import java.util.*;

/**
 * A {@link LocationIdentity} with a name.
 */
public final class NamedLocationIdentity implements LocationIdentity {

    /**
     * Canonicalizing map for {@link NamedLocationIdentity} instances. This is in a separate class
     * to work around class initialization issues.
     */
    static class DB {
        private static final HashMap<String, NamedLocationIdentity> map = new HashMap<>();

        static synchronized NamedLocationIdentity register(NamedLocationIdentity identity) {
            NamedLocationIdentity oldValue = map.put(identity.name, identity);
            if (oldValue != null) {
                throw new IllegalArgumentException("identity " + identity + " already exists");
            }
            return identity;
        }

        static synchronized NamedLocationIdentity lookup(String name) {
            return map.get(name);
        }
    }

    protected final String name;

    protected final boolean immutable;

    private NamedLocationIdentity(String name, boolean immutable) {
        this.name = name;
        this.immutable = immutable;
    }

    /**
     * Creates a named unique location identity for read and write operations.
     *
     * @param name the name of the new location identity
     */
    public static NamedLocationIdentity create(String name) {
        return create(name, false);
    }

    /**
     * Creates a named unique location identity for read and write operations.
     *
     * @param name the name of the new location identity
     */
    public static NamedLocationIdentity create(String name, boolean immutable) {
        return DB.register(new NamedLocationIdentity(name, immutable));
    }

    /**
     * Gets the unique {@link NamedLocationIdentity} (if any) for a given name.
     */
    public static NamedLocationIdentity lookup(String name) {
        return DB.lookup(name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof NamedLocationIdentity) {
            NamedLocationIdentity that = (NamedLocationIdentity) obj;
            return this.name.equals(that.name);
        }
        return false;
    }

    @Override
    public String toString() {
        return name + (immutable ? ":immutable" : ":mutable");
    }

    public boolean isImmutable() {
        return immutable;
    }

    /**
     * Returns the named location identity for an array of the given element kind. Array accesses of
     * the same kind must have the same location identity unless an alias analysis guarantees that
     * two distinct arrays are accessed.
     */
    public static LocationIdentity getArrayLocation(Kind elementKind) {
        return ARRAY_LOCATIONS.get(elementKind);
    }

    private static final EnumMap<Kind, LocationIdentity> ARRAY_LOCATIONS = initArrayLocations();

    private static EnumMap<Kind, LocationIdentity> initArrayLocations() {
        EnumMap<Kind, LocationIdentity> result = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            result.put(kind, NamedLocationIdentity.create("Array: " + kind.getJavaName()));
        }
        return result;
    }
}
