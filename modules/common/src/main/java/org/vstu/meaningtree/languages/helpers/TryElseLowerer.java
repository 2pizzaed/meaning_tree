package org.vstu.meaningtree.languages.helpers;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.BoolLiteral;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.exceptions.ExceptionCatchStatement;
import org.vstu.meaningtree.nodes.types.builtin.BooleanType;

import java.util.ArrayList;
import java.util.List;

/**
 * Убирает python-ветвь {@code else} у {@code try} — она выполняется, только если тело
 * завершилось без исключения, а её собственные исключения этими же обработчиками уже не
 * ловятся. В языках без такого синтаксиса (Java, C++) это выражается флагом: он взводится
 * последним оператором тела {@code try} и проверяется в {@code if} после конструкции.
 * <p>
 * Когда есть ещё и {@code finally}, простой вставки «до» и «после» мало: в Python
 * {@code finally} выполняется после ветви {@code else}, а внешний по отношению к ней
 * {@code finally} той же конструкции выполнился бы раньше. Поэтому вся тройка
 * «объявление флага, try/catch, проверка флага» заворачивается в отдельный
 * {@code try ... finally}, а у исходного узла {@code finally} снимается.
 * <p>
 * Работает на клоне входного дерева и никогда не меняет дерево вызывающего — как
 * {@link LoopElseLowerer}, по образцу которого написан.
 */
public final class TryElseLowerer {
    private record Plan(NodeInfo statementInfo, Node container) {
    }

    private TryElseLowerer() {
    }

    public static MeaningTree lower(MeaningTree source) {
        MeaningTree result = new MeaningTree(source.getRootNode().clone());
        List<Plan> plans = collectPlans(result);

        int ordinal = 1;
        for (Plan plan : plans) {
            lowerStatement((ExceptionCatchStatement) plan.statementInfo().node(), plan.container(), ordinal++);
        }
        return result;
    }

    private static List<Plan> collectPlans(MeaningTree tree) {
        List<Plan> plans = new ArrayList<>();
        for (NodeInfo info : tree) {
            if (!(info.node() instanceof ExceptionCatchStatement stmt) || !stmt.hasElseBranch()) {
                continue;
            }
            NodeInfo parent = info.parent();
            if (parent == null || !(parent.node() instanceof CompoundStatement || parent.node() instanceof ProgramEntryPoint)) {
                throw new IllegalStateException(
                        "Try with an else branch must be a direct statement in a compound body, found parent: "
                                + (parent == null ? "null" : parent.node().getClass())
                );
            }
            plans.add(new Plan(info, parent.node()));
        }
        return plans;
    }

    private static void lowerStatement(ExceptionCatchStatement stmt, Node container, int ordinal) {
        Statement elseBody = stmt.getElseBranch();
        stmt.setElseBranch(null);

        SimpleIdentifier flag = new SimpleIdentifier("_try_else_" + ordinal).remap(stmt);
        VariableDeclaration flagDeclaration =
                new VariableDeclaration(new BooleanType(), flag.clone(), new BoolLiteral(false)).remapTree(stmt);

        CompoundStatement body = stmt.makeCompoundBody();
        body.insert(body.getLength(), new AssignmentStatement(flag.clone(), new BoolLiteral(true)).remapTree(stmt));

        IfStatement guardedElse = new IfStatement(flag.clone(), elseBody).remapTree(stmt);

        if (!stmt.hasFinallyBranch()) {
            insertBefore(container, stmt, List.of(flagDeclaration));
            insertAfter(container, stmt, List.of(guardedElse));
            return;
        }

        Statement finallyBody = stmt.getFinallyBranch();
        stmt.setFinallyBranch(null);
        ExceptionCatchStatement wrapper = new ExceptionCatchStatement(
                new CompoundStatement(flagDeclaration, stmt, guardedElse), List.of(), null, finallyBody
        );
        wrapper.remap(stmt);
        wrapper.getBody().remap(stmt);
        replace(container, stmt, wrapper);
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
        throw new IllegalArgumentException("Unsupported try-else container: " + container.getClass().getName());
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
        throw new IllegalArgumentException("Unsupported try-else container: " + container.getClass().getName());
    }

    private static void replace(Node container, Node anchor, Node replacement) {
        if (container instanceof CompoundStatement compound) {
            compound.substitute(indexOf(compound.getNodes(), anchor), replacement);
            return;
        }
        if (container instanceof ProgramEntryPoint entryPoint) {
            entryPoint.getBody().set(indexOf(entryPoint.getBody().toArray(Node[]::new), anchor), replacement);
            return;
        }
        throw new IllegalArgumentException("Unsupported try-else container: " + container.getClass().getName());
    }

    private static int indexOf(Node[] nodes, Node target) {
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] == target) {
                return i;
            }
        }
        throw new IllegalStateException("Try anchor was not found in its container");
    }
}
