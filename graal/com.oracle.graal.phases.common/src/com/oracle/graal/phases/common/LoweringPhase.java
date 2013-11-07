/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common;

import static com.oracle.graal.phases.GraalOptions.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Graph.Mark;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.GuardsStage;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.tiers.*;

/**
 * Processes all {@link Lowerable} nodes to do their lowering.
 */
public class LoweringPhase extends BasePhase<PhaseContext> {

    final class LoweringToolImpl implements LoweringTool {

        private final PhaseContext context;
        private final NodeBitMap activeGuards;
        private GuardingNode guardAnchor;
        private FixedWithNextNode lastFixedNode;
        private ControlFlowGraph cfg;

        public LoweringToolImpl(PhaseContext context, GuardingNode guardAnchor, NodeBitMap activeGuards, FixedWithNextNode lastFixedNode, ControlFlowGraph cfg) {
            this.context = context;
            this.guardAnchor = guardAnchor;
            this.activeGuards = activeGuards;
            this.lastFixedNode = lastFixedNode;
            this.cfg = cfg;
        }

        @Override
        public CodeCacheProvider getCodeCache() {
            return context.getCodeCache();
        }

        @Override
        public ConstantReflectionProvider getConstantReflection() {
            return context.getConstantReflection();
        }

        @Override
        public MetaAccessProvider getMetaAccess() {
            return context.getMetaAccess();
        }

        @Override
        public ForeignCallsProvider getForeignCalls() {
            return context.getForeignCalls();
        }

        @Override
        public LoweringProvider getLowerer() {
            return context.getLowerer();
        }

        @Override
        public Replacements getReplacements() {
            return context.getReplacements();
        }

        @Override
        public GuardingNode createNullCheckGuard(GuardedNode guardedNode, ValueNode object) {
            if (ObjectStamp.isObjectNonNull(object)) {
                // Short cut creation of null check guard if the object is known to be non-null.
                return null;
            }
            StructuredGraph graph = guardedNode.asNode().graph();
            if (graph.getGuardsStage().ordinal() > GuardsStage.FLOATING_GUARDS.ordinal()) {
                NullCheckNode nullCheck = graph.add(new NullCheckNode(object));
                graph.addBeforeFixed((FixedNode) guardedNode, nullCheck);
                return nullCheck;
            } else {
                GuardingNode guard = createGuard(graph.unique(new IsNullNode(object)), DeoptimizationReason.NullCheckException, DeoptimizationAction.InvalidateReprofile, true);
                assert guardedNode.getGuard() == null;
                guardedNode.setGuard(guard);
                return guard;
            }
        }

        @Override
        public GuardingNode createGuard(LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action) {
            return createGuard(condition, deoptReason, action, false);
        }

        @Override
        public Assumptions assumptions() {
            return context.getAssumptions();
        }

        @Override
        public GuardingNode createGuard(LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, boolean negated) {
            if (condition.graph().getGuardsStage().ordinal() > StructuredGraph.GuardsStage.FLOATING_GUARDS.ordinal()) {
                throw new GraalInternalError("Cannot create guards after guard lowering");
            }
            if (OptEliminateGuards.getValue()) {
                for (Node usage : condition.usages()) {
                    if (!activeGuards.isNew(usage) && activeGuards.isMarked(usage) && ((GuardNode) usage).negated() == negated) {
                        return (GuardNode) usage;
                    }
                }
            }
            GuardNode newGuard = guardAnchor.asNode().graph().unique(new GuardNode(condition, guardAnchor, deoptReason, action, negated));
            if (OptEliminateGuards.getValue()) {
                activeGuards.grow();
                activeGuards.mark(newGuard);
            }
            return newGuard;
        }

        @Override
        public Block getBlockFor(Node node) {
            return cfg.blockFor(node);
        }

        public FixedWithNextNode lastFixedNode() {
            return lastFixedNode;
        }

        private void setLastFixedNode(FixedWithNextNode n) {
            assert n.isAlive() : n;
            lastFixedNode = n;
        }
    }

    private final CanonicalizerPhase canonicalizer;

    public LoweringPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    /**
     * Checks that second lowering of a given graph did not introduce any new nodes.
     * 
     * @param graph a graph that was just {@linkplain #lower lowered}
     * @throws AssertionError if the check fails
     */
    private boolean checkPostLowering(StructuredGraph graph, PhaseContext context) {
        Mark expectedMark = graph.getMark();
        lower(graph, context, 1);
        Mark mark = graph.getMark();
        assert mark.equals(expectedMark) : graph + ": a second round in the current lowering phase introduced these new nodes: " + graph.getNewNodes(mark).snapshot();
        return true;
    }

    @Override
    protected void run(final StructuredGraph graph, PhaseContext context) {
        lower(graph, context, 0);
        assert checkPostLowering(graph, context);
    }

    private void lower(StructuredGraph graph, PhaseContext context, int i) {
        IncrementalCanonicalizerPhase<PhaseContext> incrementalCanonicalizer = new IncrementalCanonicalizerPhase<>(canonicalizer);
        incrementalCanonicalizer.appendPhase(new Round(i, context));
        incrementalCanonicalizer.apply(graph, context);
        assert graph.verify();
    }

    /**
     * Checks that lowering of a given node did not introduce any new {@link Lowerable} nodes that
     * could be lowered in the current {@link LoweringPhase}. Such nodes must be recursively lowered
     * as part of lowering {@code node}.
     * 
     * @param node a node that was just lowered
     * @param preLoweringMark the graph mark before {@code node} was lowered
     * @throws AssertionError if the check fails
     */
    private static boolean checkPostNodeLowering(Node node, LoweringToolImpl loweringTool, Mark preLoweringMark) {
        StructuredGraph graph = (StructuredGraph) node.graph();
        Mark postLoweringMark = graph.getMark();
        NodeIterable<Node> newNodesAfterLowering = graph.getNewNodes(preLoweringMark);
        for (Node n : newNodesAfterLowering) {
            if (n instanceof Lowerable) {
                ((Lowerable) n).lower(loweringTool);
                Mark mark = graph.getMark();
                assert postLoweringMark.equals(mark) : graph + ": lowering of " + node + " produced lowerable " + n + " that should have been recursively lowered as it introduces these new nodes: " +
                                graph.getNewNodes(postLoweringMark).snapshot();
            }
        }
        return true;
    }

    private final class Round extends Phase {

        private final PhaseContext context;
        private final SchedulePhase schedule;

        private Round(int iteration, PhaseContext context) {
            super("LoweringIteration" + iteration);
            this.context = context;
            this.schedule = new SchedulePhase();
        }

        @Override
        public void run(StructuredGraph graph) {
            schedule.apply(graph, false);
            processBlock(schedule.getCFG().getStartBlock(), graph.createNodeBitMap(), null);
        }

        private void processBlock(Block block, NodeBitMap activeGuards, GuardingNode parentAnchor) {

            GuardingNode anchor = parentAnchor;
            if (anchor == null) {
                anchor = block.getBeginNode();
            }
            anchor = process(block, activeGuards, anchor);

            // Process always reached block first.
            Block alwaysReachedBlock = block.getPostdominator();
            if (alwaysReachedBlock != null && alwaysReachedBlock.getDominator() == block) {
                processBlock(alwaysReachedBlock, activeGuards, anchor);
            }

            // Now go for the other dominators.
            for (Block dominated : block.getDominated()) {
                if (dominated != alwaysReachedBlock) {
                    assert dominated.getDominator() == block;
                    processBlock(dominated, activeGuards, null);
                }
            }

            if (parentAnchor == null && OptEliminateGuards.getValue()) {
                for (GuardNode guard : anchor.asNode().usages().filter(GuardNode.class)) {
                    if (activeGuards.contains(guard)) {
                        activeGuards.clear(guard);
                    }
                }
            }
        }

        private GuardingNode process(final Block b, final NodeBitMap activeGuards, final GuardingNode startAnchor) {

            final LoweringToolImpl loweringTool = new LoweringToolImpl(context, startAnchor, activeGuards, b.getBeginNode(), schedule.getCFG());

            // Lower the instructions of this block.
            List<ScheduledNode> nodes = schedule.nodesFor(b);
            for (Node node : nodes) {

                if (node.isDeleted()) {
                    // This case can happen when previous lowerings deleted nodes.
                    continue;
                }

                // Cache the next node to be able to reconstruct the previous of the next node
                // after lowering.
                FixedNode nextNode = null;
                if (node instanceof FixedWithNextNode) {
                    nextNode = ((FixedWithNextNode) node).next();
                } else {
                    nextNode = loweringTool.lastFixedNode().next();
                }

                if (node instanceof Lowerable) {
                    assert checkUsagesAreScheduled(node);
                    Mark preLoweringMark = node.graph().getMark();
                    ((Lowerable) node).lower(loweringTool);
                    if (node == startAnchor && node.isDeleted()) {
                        loweringTool.guardAnchor = BeginNode.prevBegin(nextNode);
                    }
                    assert checkPostNodeLowering(node, loweringTool, preLoweringMark);
                }

                if (!nextNode.isAlive()) {
                    // can happen when the rest of the block is killed by lowering
                    // (e.g. by an unconditional deopt)
                    break;
                } else {
                    Node nextLastFixed = nextNode.predecessor();
                    if (!(nextLastFixed instanceof FixedWithNextNode)) {
                        // insert begin node, to have a valid last fixed for next lowerable node.
                        // This is about lowering a FixedWithNextNode to a control split while this
                        // FixedWithNextNode is followed by some kind of BeginNode.
                        // For example the when a FixedGuard followed by a loop exit is lowered to a
                        // control-split + deopt.
                        BeginNode begin = node.graph().add(new BeginNode());
                        nextLastFixed.replaceFirstSuccessor(nextNode, begin);
                        begin.setNext(nextNode);
                        nextLastFixed = begin;
                    }
                    loweringTool.setLastFixedNode((FixedWithNextNode) nextLastFixed);
                }
            }
            return loweringTool.getCurrentGuardAnchor();
        }

        /**
         * Checks that all usages of a floating, lowerable node are scheduled.
         * <p>
         * Given that the lowering of such nodes may introduce fixed nodes, they must be lowered in
         * the context of a usage that dominates all other usages. The fixed nodes resulting from
         * lowering are attached to the fixed node context of the dominating usage. This ensures the
         * post-lowering graph still has a valid schedule.
         * 
         * @param node a {@link Lowerable} node
         */
        private boolean checkUsagesAreScheduled(Node node) {
            if (node instanceof FloatingNode) {
                for (Node usage : node.usages()) {
                    if (usage instanceof ScheduledNode) {
                        Block usageBlock = schedule.getCFG().blockFor(usage);
                        assert usageBlock != null : node.graph() + ": cannot lower floatable node " + node + " that has non-scheduled usage " + usage;
                    }
                }
            }
            return true;
        }
    }
}
