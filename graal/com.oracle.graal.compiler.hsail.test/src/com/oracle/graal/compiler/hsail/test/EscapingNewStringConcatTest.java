/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.hsail.test;

import static com.oracle.graal.debug.Debug.*;

import org.junit.*;

import com.oracle.graal.debug.*;

/**
 * Tests allocation of a new String based on string concatenation.
 */

public class EscapingNewStringConcatTest extends EscapingNewBase {

    @Result public String[] myOutArray = new String[num];
    public String[] inArray = new String[num];

    @Override
    void setupArrays() {
        super.setupArrays();
        for (int i = 0; i < num; i++) {
            inArray[i] = Integer.toString(i + 100);
        }
    }

    public void run(int gid) {
        outArray[gid] = inArray[gid] + inArray[(gid + num / 2) % num];
        myOutArray[gid] = inArray[(gid + num / 2) % num] + inArray[gid];
    }

    @Ignore("emitDirectCall unimplemented")
    @Test
    public void test() {
        try (DebugConfigScope s = disableIntercept()) {
            testGeneratedHsail();
        }
    }
}
