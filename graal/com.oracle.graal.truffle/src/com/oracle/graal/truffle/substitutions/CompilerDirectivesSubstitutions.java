/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.substitutions;

import java.util.concurrent.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.truffle.nodes.*;
import com.oracle.graal.truffle.nodes.typesystem.*;
import com.oracle.truffle.api.*;

@ClassSubstitution(CompilerDirectives.class)
public class CompilerDirectivesSubstitutions {

    @MethodSubstitution
    public static void transferToInterpreter() {
        DeoptimizeNode.deopt(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.UnreachedCode);
    }

    @MethodSubstitution
    public static boolean inInterpreter() {
        return false;
    }

    @MethodSubstitution
    public static void interpreterOnly(@SuppressWarnings("unused") Runnable runnable) {
    }

    @MethodSubstitution
    public static <T> T interpreterOnly(@SuppressWarnings("unused") Callable<T> callable) throws Exception {
        return null;
    }

    @MethodSubstitution
    public static boolean injectBranchProbability(double probability, boolean condition) {
        return BranchProbabilityNode.probability(probability, condition);
    }

    @MacroSubstitution(macro = BailoutNode.class, isStatic = true)
    public static native void bailout(String reason);

    @MacroSubstitution(macro = TypeCastMacroNode.class, isStatic = true)
    public static native Object unsafeCast(Object value, Class clazz, Object receiver, Object customType);

    @MacroSubstitution(macro = CustomTypeCheckMacroNode.class, isStatic = true)
    public static native boolean customTypeCheck(boolean condition, Object value, Object customType);

    @MacroSubstitution(macro = UnsafeCustomizationMacroNode.class, isStatic = true)
    public static native Object unsafeCustomization(Object receiver, Object customType, Object locationIdentity);
}
