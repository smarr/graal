/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.extended;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Loads an object's hub. The object is not null-checked by this operation.
 */
@NodeInfo
public class LoadHubNode extends FloatingGuardedNode implements Lowerable, Canonicalizable, Virtualizable {

    @Input ValueNode value;

    public ValueNode getValue() {
        return value;
    }

    public static LoadHubNode create(@InjectedNodeParameter StampProvider stampProvider, ValueNode value) {
        return new LoadHubNode(hubStamp(stampProvider, value), value, null);
    }

    public static LoadHubNode create(@InjectedNodeParameter StampProvider stampProvider, ValueNode value, ValueNode guard) {
        return new LoadHubNode(hubStamp(stampProvider, value), value, guard);
    }

    private static Stamp hubStamp(StampProvider stampProvider, ValueNode value) {
        assert value.stamp() instanceof ObjectStamp;
        return stampProvider.createHubStamp(((ObjectStamp) value.stamp()));
    }

    protected LoadHubNode(Stamp stamp, ValueNode value, ValueNode guard) {
        super(stamp, (GuardingNode) guard);
        assert value != guard;
        this.value = value;
    }

    @Override
    public void lower(LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        MetaAccessProvider metaAccess = tool.getMetaAccess();
        if (metaAccess != null && getValue().stamp() instanceof ObjectStamp) {
            ObjectStamp objectStamp = (ObjectStamp) getValue().stamp();

            ResolvedJavaType exactType;
            if (objectStamp.isExactType()) {
                exactType = objectStamp.type();
            } else if (objectStamp.type() != null && tool.assumptions().useOptimisticAssumptions()) {
                exactType = objectStamp.type().findUniqueConcreteSubtype();
                if (exactType != null) {
                    tool.assumptions().recordConcreteSubtype(objectStamp.type(), exactType);
                }
            } else {
                exactType = null;
            }

            if (exactType != null) {
                return ConstantNode.forConstant(stamp(), exactType.getObjectHub(), metaAccess);
            }
        }
        return this;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(value);
        if (state != null) {
            Constant constantHub = state.getVirtualObject().type().getObjectHub();
            tool.replaceWithValue(ConstantNode.forConstant(stamp(), constantHub, tool.getMetaAccessProvider(), graph()));
        }
    }
}
