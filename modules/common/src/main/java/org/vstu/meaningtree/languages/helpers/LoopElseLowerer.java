package org.vstu.meaningtree.languages.helpers;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.BoolLiteral;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.types.builtin.BooleanType;

import java.util.ArrayList;
import java.util.List;

/**
 * Desugars a loop's Python-only else-clause (executed when the loop completes without a break
 * belonging to it) into a construct languages without that syntax (Java, C++) can render:
 * a boolean flag set false before every break belonging to the loop, checked in a trailing `if`
 * after the loop. Rewrites a clone of the input tree; never mutates the caller's tree.
 */
public final class LoopElseLowerer {
    private record Plan(NodeInfo loopInfo, Node container) {
    }

    private LoopElseLowerer() {
    }

    public static MeaningTree lower(MeaningTree source) {
        MeaningTree result = new MeaningTree(source.getRootNode().clone());
        List<Plan> plans = collectPlans(result);

        int ordinal = 1;
        for (Plan plan : plans) {
            Loop loop = (Loop) plan.loopInfo().node();
            lowerLoop(loop, plan.container(), ordinal++);
        }
        return result;
    }

    private static List<Plan> collectPlans(MeaningTree tree) {
        List<Plan> plans = new ArrayList<>();
        for (NodeInfo info : tree) {
            if (!(info.node() instanceof Loop loop) || !loop.hasElseBranch()) {
                continue;
            }
            NodeInfo parent = info.parent();
            if (parent == null || !(parent.node() instanceof CompoundStatement || parent.node() instanceof ProgramEntryPoint)) {
                throw new IllegalStateException(
                        "Loop with an else branch must be a direct statement in a compound body, found parent: "
                                + (parent == null ? "null" : parent.node().getClass())
                );
            }
            plans.add(new Plan(info, parent.node()));
        }
        return plans;
    }

    private static void lowerLoop(Loop loop, Node container, int ordinal) {
        Statement elseBody = loop.getElseBranch();
        loop.setElseBranch(null);

        if (ownBreaks(loop).isEmpty()) {
            insertAfter(container, loop, flattenStatements(elseBody));
            return;
        }

        SimpleIdentifier flag = new SimpleIdentifier("_loop_else_" + ordinal).remap(loop);
        VariableDeclaration flagDeclaration = remapSyntheticTree(
                new VariableDeclaration(new BooleanType(), flag.clone(), new BoolLiteral(true)), loop
        );

        // Присваивание встаёт перед break, поэтому у каждого break родителем должен быть блок
        LabeledJumpLowerer.makeCompoundBodies(loop);
        for (NodeInfo info : ownBreaks(loop)) {
            CompoundStatement block = (CompoundStatement) info.parentNode();
            block.insert(indexOf(block.getNodes(), info.node()),
                    remapSyntheticTree(makeFlagAssignment(flag, loop), loop));
        }

        IfStatement guardedElse = remapSyntheticTree(new IfStatement(flag.clone(), elseBody), loop);

        insertBefore(container, loop, List.of(flagDeclaration));
        insertAfter(container, loop, List.of(guardedElse));
    }

    private static List<Node> flattenStatements(Statement statement) {
        if (statement instanceof CompoundStatement compound) {
            return List.of(compound.getNodes());
        }
        return List.of(statement);
    }

    /** break, которые выходят из этого цикла; ветку else вызывающий уже снял с цикла. */
    private static List<NodeInfo> ownBreaks(Loop loop) {
        List<NodeInfo> breaks = new ArrayList<>();
        for (NodeInfo info : loop.iterate(true)) {
            if (info.node() instanceof BreakStatement && JumpTargets.target(info, true) == loop) {
                breaks.add(info);
            }
        }
        return breaks;
    }

    private static AssignmentStatement makeFlagAssignment(SimpleIdentifier flag, Node origin) {
        return new AssignmentStatement(flag.clone(), new BoolLiteral(false).remap(origin)).remap(origin);
    }

    private static void insertBefore(Node container, Node anchor, List<? extends Node> nodes) {
        if (container instanceof CompoundStatement compound) {
            int index = indexOf(compound.getNodes(), anchor);
            for (Node node : nodes) {
                compound.insert(index++, node);
            }
            return;
        }
        if (container instanceof ProgramEntryPoint entryPoint) {
            int index = indexOf(entryPoint.getBody().toArray(Node[]::new), anchor);
            entryPoint.getBody().addAll(index, nodes);
            return;
        }
        throw new IllegalArgumentException("Unsupported loop-else container: " + container.getClass().getName());
    }

    private static void insertAfter(Node container, Node anchor, List<? extends Node> nodes) {
        if (container instanceof CompoundStatement compound) {
            int index = indexOf(compound.getNodes(), anchor) + 1;
            for (Node node : nodes) {
                compound.insert(index++, node);
            }
            return;
        }
        if (container instanceof ProgramEntryPoint entryPoint) {
            int index = indexOf(entryPoint.getBody().toArray(Node[]::new), anchor) + 1;
            entryPoint.getBody().addAll(index, nodes);
            return;
        }
        throw new IllegalArgumentException("Unsupported loop-else container: " + container.getClass().getName());
    }

    private static int indexOf(Node[] nodes, Node target) {
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] == target) {
                return i;
            }
        }
        throw new IllegalStateException("Loop anchor was not found in its container");
    }

    private static <T extends Node> T remapSyntheticTree(T node, Node origin) {
        node.remap(origin);
        for (NodeInfo info : node) {
            info.node().remap(origin);
        }
        return node;
    }
}
