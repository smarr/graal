/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.api.instrument.impl;

import java.io.*;
import java.util.*;

import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.instrument.Probe.ProbeListener;
import com.oracle.truffle.api.source.*;

/**
 * A mapping from {@link LineLocation} (a line number in a specific piece of {@link Source} code) to
 * a collection of {@link Probe}s whose associated {@link SourceSection} starts on that line.
 */
public class LineToProbesMap implements ProbeListener {

    private static final boolean TRACE = false;
    private static final PrintStream OUT = System.out;

    private static void trace(String msg) {
        OUT.println("LineToProbesMap: " + msg);
    }

    /**
     * Map: Source line ==> probes associated with source sections starting on the line.
     */
    private final Map<LineLocation, Collection<Probe>> lineToProbesMap = new HashMap<>();

    public LineToProbesMap() {
    }

    public void startASTProbing(Source source) {
    }

    public void newProbeInserted(Probe probe) {
        final SourceSection sourceSection = probe.getProbedSourceSection();
        if (sourceSection != null && !(sourceSection instanceof NullSourceSection)) {
            final LineLocation lineLocation = sourceSection.getLineLocation();
            if (TRACE) {
                trace("ADD " + lineLocation.getShortDescription() + " ==> " + probe.getShortDescription());
            }
            this.addProbeToLine(lineLocation, probe);
        }
    }

    public void probeTaggedAs(Probe probe, SyntaxTag tag, Object tagValue) {
        // This map ignores tags
    }

    public void endASTProbing(Source source) {
    }

    /**
     * Returns the {@link Probe}, if any, associated with source that starts on a specified line; if
     * there are more than one, return the one with the first starting character location.
     */
    public Probe findLineProbe(LineLocation lineLocation) {
        Probe probe = null;
        final Collection<Probe> probes = getProbesAtLine(lineLocation);
        for (Probe probesOnLine : probes) {
            if (probe == null) {
                probe = probesOnLine;
            } else if (probesOnLine.getProbedSourceSection().getCharIndex() < probe.getProbedSourceSection().getCharIndex()) {
                probe = probesOnLine;
            }
        }
        return probe;
    }

    /**
     * Records creation of a probe whose associated source starts on the given line.
     * <p>
     * If the line already exists in the internal {@link #lineToProbesMap}, this probe will be added
     * to the existing collection. If no line already exists in the internal map, then a new key is
     * added along with a new collection containing the probe.
     * <p>
     * This class requires that each added line/probe pair hasn't been previously added. However,
     * attaching the same probe to a new line location is allowed.
     *
     * @param line The {@link LineLocation} to attach the probe to.
     * @param probe The {@link Probe} to attach for that line location.
     */
    protected void addProbeToLine(LineLocation line, Probe probe) {

        if (!lineToProbesMap.containsKey(line)) {
            // Key does not exist, add new probe list
            final ArrayList<Probe> newProbeList = new ArrayList<>(2);
            newProbeList.add(probe);
            lineToProbesMap.put(line, newProbeList);
        } else {
            // Probe list exists, add to existing
            final Collection<Probe> existingProbeList = lineToProbesMap.get(line);
            assert !existingProbeList.contains(probe);
            existingProbeList.add(probe);
        }
    }

    /**
     *
     * Returns a collection of {@link Probe}s whose associated source begins at the given
     * {@link LineLocation}, an empty list if none.
     *
     * @param line The line to check.
     * @return A collection of probes at the given line.
     */
    public Collection<Probe> getProbesAtLine(LineLocation line) {
        Collection<Probe> probesList = lineToProbesMap.get(line);

        if (probesList == null) {
            return Collections.emptyList();
        }
        return probesList;
    }

    /**
     * Convenience method to get probes according to a int line number. Returns a collection of
     * {@link Probe}s at the given line number, an empty list if none.
     *
     * @param lineNumber The line number to check.
     * @return A collection of probes at the given line.
     */
    public Collection<Probe> getProbesAtLineNumber(int lineNumber) {

        final Set<LineLocation> keySet = lineToProbesMap.keySet();
        if (keySet.size() == 0) {
            return Collections.emptyList();
        }

        ArrayList<Probe> probes = new ArrayList<>();
        for (LineLocation line : keySet) {
            if (line.getLineNumber() == lineNumber) {
                probes.addAll(lineToProbesMap.get(line));
            }
        }

        return probes;
    }

    public void forget(Source source) {
        final Set<LineLocation> mappedLines = lineToProbesMap.keySet();
        if (mappedLines.size() > 0) {
            List<LineLocation> forgetLines = new ArrayList<>();
            for (LineLocation line : mappedLines) {
                if (line.getSource().equals(source)) {
                    forgetLines.add(line);
                }
            }
            for (LineLocation line : forgetLines) {
                lineToProbesMap.remove(line);
            }
        }
    }
}
