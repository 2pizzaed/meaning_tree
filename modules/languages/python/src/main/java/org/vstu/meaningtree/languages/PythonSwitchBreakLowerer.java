package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedViewingException;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Definition;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.ReturnStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.conditions.SwitchStatement;
import org.vstu.meaningtree.nodes.statements.conditions.components.CaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.ConditionBranch;
import org.vstu.meaningtree.nodes.statements.exceptions.RaiseExceptionStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.JumpStatement;

import java.util.ArrayList;
import java.util.List;

/**
 * Убирает break без метки из веток switch: в дереве он выходит из switch, а в Python break
 * внутри match выходит из цикла. Завершающий break ветки парсеры уже выразили типом ветки,
 * остаются break в середине. Код после такого break переносится в те ветви {@code if},
 * которые его выполнили бы: {@code if c: break} и {@code a()} становится {@code if not c: a()}.
 * Если таких ветвей несколько, каждая получает свою копию кода. break внутри try, with или
 * ветки else цикла не поддерживается. Работает с копией дерева и не меняет дерево вызывающего.
 */
final class PythonSwitchBreakLowerer {
    private PythonSwitchBreakLowerer() {
    }

    static MeaningTree lower(MeaningTree source) {
        if (!hasSwitchExit(source)) {
            return source;
        }
        MeaningTree result = new MeaningTree(source.getRootNode().clone());
        List<SwitchStatement> switches = new ArrayList<>();
        for (NodeInfo info : result) {
            if (info.node() instanceof SwitchStatement switchStatement) {
                switches.add(switchStatement);
            }
        }
        for (SwitchStatement switchStatement : switches) {
            switchStatement.makeCompoundBranches();
            List<CaseBlock> branches = new ArrayList<>(switchStatement.getCases());
            if (switchStatement.hasDefaultCase()) {
                branches.add(switchStatement.getDefaultCase());
            }
            for (CaseBlock branch : branches) {
                CompoundStatement body = (CompoundStatement) branch.getBody();
                setNodes(body, withoutSwitchExits(body.getNodeList()));
            }
        }
        return result;
    }

    private static boolean hasSwitchExit(MeaningTree tree) {
        for (NodeInfo info : tree) {
            if (info.node() instanceof SwitchStatement switchStatement && containsSwitchExitInBranches(switchStatement)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSwitchExitInBranches(SwitchStatement switchStatement) {
        List<CaseBlock> branches = new ArrayList<>(switchStatement.getCases());
        if (switchStatement.hasDefaultCase()) {
            branches.add(switchStatement.getDefaultCase());
        }
        return branches.stream().anyMatch(branch -> containsSwitchExit(branch.getBody()));
    }

    /** Операторы без break, выходящих из switch, с тем же порядком выполнения. */
    private static List<Node> withoutSwitchExits(List<Node> statements) {
        List<Node> result = new ArrayList<>();
        for (int i = 0; i < statements.size(); i++) {
            Node statement = statements.get(i);
            if (isSwitchExit(statement)) {
                // Код после break недостижим
                return result;
            }
            if (containsSwitchExit(statement)) {
                result.addAll(restructure((Statement) statement, statements.subList(i + 1, statements.size())));
                return result;
            }
            result.add(statement);
        }
        return result;
    }

    /** Переносит код после оператора с break в те его ветви, после которых этот код выполнялся. */
    private static List<Node> restructure(Statement statement, List<Node> rest) {
        if (statement instanceof CompoundStatement block) {
            List<Node> nodes = new ArrayList<>(block.getNodeList());
            nodes.addAll(rest);
            return withoutSwitchExits(nodes);
        }
        if (!(statement instanceof IfStatement ifStatement)) {
            throw new UnsupportedViewingException(
                    "Python cannot leave match in the middle of " + statement.getClass().getSimpleName());
        }

        ifStatement.makeCompoundBranches();
        List<CompoundStatement> bodies = new ArrayList<>();
        for (ConditionBranch branch : ifStatement.getBranches()) {
            bodies.add((CompoundStatement) branch.getBody());
        }
        if (!ifStatement.hasElseBranch() && !rest.isEmpty()) {
            CompoundStatement elseBranch = new CompoundStatement().remap(ifStatement);
            ifStatement.setElseBranch(elseBranch);
        }
        if (ifStatement.hasElseBranch()) {
            bodies.add((CompoundStatement) ifStatement.getElseBranch());
        }

        boolean restPlaced = false;
        for (CompoundStatement body : bodies) {
            List<Node> nodes = new ArrayList<>(body.getNodeList());
            if (!rest.isEmpty() && canCompleteNormally(nodes)) {
                nodes.addAll(restPlaced ? copyOf(rest) : rest);
                restPlaced = true;
            }
            setNodes(body, withoutSwitchExits(nodes));
        }
        return List.of(simplifyEmptyThen(ifStatement));
    }

    /** {@code if c: pass else: a()} становится {@code if not c: a()}. */
    private static IfStatement simplifyEmptyThen(IfStatement ifStatement) {
        if (ifStatement.getBranches().size() != 1 || !ifStatement.hasElseBranch()) {
            return ifStatement;
        }
        ConditionBranch branch = ifStatement.getBranches().getFirst();
        if (((CompoundStatement) branch.getBody()).getLength() != 0) {
            return ifStatement;
        }
        Expression negated = PythonSpecialNodeTransformations.negateCondition(branch.getCondition());
        ifStatement.getBranches().set(0, new ConditionBranch(negated, ifStatement.getElseBranch()).remap(branch));
        ifStatement.setElseBranch(null);
        return ifStatement;
    }

    /**
     * Копия кода для ещё одной ветви: у её узлов свои идентификаторы, иначе в дереве появились
     * бы одинаковые, а в карте исходников копия указывает на исходные узлы.
     */
    private static List<Node> copyOf(List<Node> statements) {
        List<Node> copies = new ArrayList<>();
        for (Node statement : statements) {
            Node copy = statement.freshClone();
            List<NodeInfo> originals = statement.iterate(true);
            List<NodeInfo> copied = copy.iterate(true);
            for (int i = 0; i < originals.size(); i++) {
                copied.get(i).node().remap(originals.get(i).node());
            }
            copies.add(copy);
        }
        return copies;
    }

    private static boolean canCompleteNormally(List<Node> statements) {
        if (statements.isEmpty()) {
            return true;
        }
        Node last = statements.getLast();
        if (last instanceof JumpStatement || last instanceof ReturnStatement || last instanceof RaiseExceptionStatement) {
            return false;
        }
        if (last instanceof IfStatement ifStatement && ifStatement.hasElseBranch()) {
            for (ConditionBranch branch : ifStatement.getBranches()) {
                if (canCompleteNormally(statementsOf(branch.getBody()))) {
                    return true;
                }
            }
            return canCompleteNormally(statementsOf(ifStatement.getElseBranch()));
        }
        return true;
    }

    private static List<Node> statementsOf(Statement statement) {
        return statement instanceof CompoundStatement compound ? compound.getNodeList() : List.of(statement);
    }

    private static boolean isSwitchExit(Node node) {
        return node instanceof BreakStatement br && br.getJumpDestination() == null;
    }

    private static boolean containsSwitchExit(Node root) {
        for (NodeInfo info : root.iterate(true)) {
            if (isSwitchExit(info.node()) && exitsThrough(info, root)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Выходит ли break за пределы root: его не перехватывает вложенный цикл или switch, и он
     * не лежит в функции или лямбде. Ветка else цикла break не перехватывает.
     */
    private static boolean exitsThrough(NodeInfo breakInfo, Node root) {
        NodeInfo current = breakInfo;
        while (current.node() != root) {
            NodeInfo parent = current.parent();
            Node node = parent.node();
            if (node instanceof Loop loop && !(loop.hasElseBranch() && current.node() == loop.getElseBranch())) {
                return false;
            }
            if (node instanceof SwitchStatement || node instanceof Definition || node instanceof Expression) {
                return false;
            }
            current = parent;
        }
        return true;
    }

    private static void setNodes(CompoundStatement compound, List<Node> nodes) {
        for (Node node : compound.getNodes()) {
            compound.remove(node);
        }
        for (int i = 0; i < nodes.size(); i++) {
            compound.insert(i, nodes.get(i));
        }
    }
}
