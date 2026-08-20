package org.vstu.meaningtree.languages.helpers;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.expressions.identifiers.JumpLabel;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.BoolLiteral;
import org.vstu.meaningtree.nodes.interfaces.HasBodyStatement;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.conditions.SwitchStatement;
import org.vstu.meaningtree.nodes.statements.conditions.components.CaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.ConditionBranch;
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

        if (!hasOwnBreak(loop.getBody(), loop, 0)) {
            insertAfter(container, loop, flattenStatements(elseBody));
            return;
        }

        SimpleIdentifier flag = new SimpleIdentifier("_loop_else_" + ordinal).remap(loop);
        VariableDeclaration flagDeclaration = remapSyntheticTree(
                new VariableDeclaration(new BooleanType(), flag.clone(), new BoolLiteral(true)), loop
        );

        loop.makeCompoundBody();
        rewriteBreaks((CompoundStatement) loop.getBody(), flag, loop, 0);

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

    private static boolean hasOwnBreak(Node node, Loop targetLoop, int depth) {
        if (node instanceof BreakStatement br) {
            return breakTargetsLoop(br, targetLoop, depth);
        }
        int nextDepth = depth;
        if (node instanceof Loop && node != targetLoop) {
            nextDepth++;
        }
        for (NodeInfo info : node.iterate(false)) {
            if (hasOwnBreak(info.node(), targetLoop, nextDepth)) {
                return true;
            }
        }
        return false;
    }

    private static boolean breakTargetsLoop(BreakStatement br, Loop targetLoop, int depth) {
        JumpLabel destination = br.getJumpDestination();
        if (destination == null) {
            return depth == 0;
        }
        JumpLabel loopLabel = targetLoop.getJumpLabel();
        return loopLabel != null && destination.getName().equals(loopLabel.getName());
    }

    /**
     * Inserts `flag = false;` immediately before every break belonging to targetLoop.
     * Only descends into nested Loop/If/Switch/HasBodyStatement bodies (same shape as
     * PythonSpecialNodeTransformations' continue-injection walker); a nested Loop increases
     * the depth used to decide whether an unlabeled break still belongs to targetLoop.
     */
    private static void rewriteBreaks(CompoundStatement compound, SimpleIdentifier flag, Loop targetLoop, int depth) {
        Node[] nodes = compound.getNodes();
        int offset = 0;
        for (int i = 0; i < nodes.length; i++) {
            Node node = nodes[i];
            if (node instanceof BreakStatement br && breakTargetsLoop(br, targetLoop, depth)) {
                compound.insert(i + offset, remapSyntheticTree(makeFlagAssignment(flag, targetLoop), targetLoop));
                offset++;
            } else {
                descendAndRewriteBreaks(node, flag, targetLoop, depth);
            }
        }
    }

    private static void descendAndRewriteBreaks(Node node, SimpleIdentifier flag, Loop targetLoop, int depth) {
        if (node instanceof Loop nestedLoop && nestedLoop != targetLoop) {
            nestedLoop.makeCompoundBody();
            rewriteBreaks((CompoundStatement) nestedLoop.getBody(), flag, targetLoop, depth + 1);
        } else if (node instanceof IfStatement ifStmt) {
            ifStmt.makeCompoundBranches();
            for (ConditionBranch branch : ifStmt.getBranches()) {
                rewriteBreaks((CompoundStatement) branch.getBody(), flag, targetLoop, depth);
            }
            if (ifStmt.hasElseBranch()) {
                rewriteBreaks((CompoundStatement) ifStmt.getElseBranch(), flag, targetLoop, depth);
            }
        } else if (node instanceof SwitchStatement switchStmt) {
            switchStmt.makeCompoundBranches();
            for (CaseBlock branch : switchStmt.getCases()) {
                rewriteBreaks((CompoundStatement) branch.getBody(), flag, targetLoop, depth);
            }
            if (switchStmt.hasDefaultCase()) {
                rewriteBreaks((CompoundStatement) switchStmt.getDefaultCase().getBody(), flag, targetLoop, depth);
            }
        } else if (node instanceof HasBodyStatement hasBody) {
            hasBody.makeCompoundBody();
            rewriteBreaks((CompoundStatement) hasBody.getBody(), flag, targetLoop, depth);
        }
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
