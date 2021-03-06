/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.jtt.jdk;

import org.junit.*;

import com.oracle.graal.jtt.*;

/*
 */
public class System_currentTimeMillis02 extends JTTTest {

    static void m(long[] times) {
        times[1] = System.currentTimeMillis() - times[0];
    }

    public static boolean test() {
        long[] times = new long[2];  // { start, delta }
        times[0] = System.currentTimeMillis();
        times[1] = 0;
        // force compilation:
        for (int i = 0; i < 5000; i++) {
            m(times);
        }
        times[0] = System.currentTimeMillis();
        times[1] = 0;
        for (int i = 0; times[1] == 0 && i < 5000000; i++) {
            m(times);
            // do nothing.
        }
        // better get at least 100 millisecond resolution.
        return times[1] >= 1 && times[1] < 100;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test");
    }

}
