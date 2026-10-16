package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedViewingException;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.languages.helpers.JumpTargets;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.ReturnStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.conditions.SwitchStatement;
import org.vstu.meaningtree.nodes.statements.conditions.components.CaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.ConditionBranch;
import org.vstu.meaningtree.nodes.statements.exceptions.RaiseExceptionStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.JumpStatement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Убирает break без метки из веток switch: в дереве он выходит из switch, а в Python break
 * внутри match выходит из цикла. Завершающий break ветки парсеры уже выразили типом ветки,
 * остаются break в середине. Код после такого break переносится в те ветви {@code if},
 * которые его выполнили бы: {@code if c: break} и {@code a()} становится {@code if not c: a()}.
 * Если таких ветвей несколько, каждая получает свою копию кода. break внутри try, with или
 * ветки else цикла не поддерживается. Работает с копией дерева и не меняет дерево вызывающего.
 */
final class PythonSwitchBreakLowerer {
    /** break, выходящие из обрабатываемого switch; сравнение по ссылке. */
    private final Set<Node> exits;

    private PythonSwitchBreakLowerer(Set<Node> exits) {
        this.exits = exits;
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
            PythonSwitchBreakLowerer lowerer = new PythonSwitchBreakLowerer(exitsOf(switchStatement));
            if (lowerer.exits.isEmpty()) {
                continue;
            }
            switchStatement.makeCompoundBranches();
            List<CaseBlock> branches = new ArrayList<>(switchStatement.getCases());
            if (switchStatement.hasDefaultCase()) {
                branches.add(switchStatement.getDefaultCase());
            }
            for (CaseBlock branch : branches) {
                CompoundStatement body = (CompoundStatement) branch.getBody();
                setNodes(body, lowerer.withoutSwitchExits(body.getNodeList()));
            }
        }
        return result;
    }

    private static boolean hasSwitchExit(MeaningTree tree) {
        for (NodeInfo info : tree) {
            if (info.node() instanceof BreakStatement && JumpTargets.target(info, true) instanceof SwitchStatement) {
                return true;
            }
        }
        return false;
    }

    private static Set<Node> exitsOf(SwitchStatement switchStatement) {
        Set<Node> exits = Collections.newSetFromMap(new IdentityHashMap<>());
        for (NodeInfo info : switchStatement.iterate(true)) {
            if (info.node() instanceof BreakStatement && JumpTargets.target(info, true) == switchStatement) {
                exits.add(info.node());
            }
        }
        return exits;
    }

    /** Операторы без break, выходящих из switch, с тем же порядком выполнения. */
    private List<Node> withoutSwitchExits(List<Node> statements) {
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
    private List<Node> restructure(Statement statement, List<Node> rest) {
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
    private IfStatement simplifyEmptyThen(IfStatement ifStatement) {
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
    private List<Node> copyOf(List<Node> statements) {
        List<Node> copies = new ArrayList<>();
        for (Node statement : statements) {
            Node copy = statement.freshClone();
            List<NodeInfo> originals = statement.iterate(true);
            List<NodeInfo> copied = copy.iterate(true);
            for (int i = 0; i < originals.size(); i++) {
                copied.get(i).node().remap(originals.get(i).node());
                // Копия break из switch выходит из него так же, как оригинал
                if (exits.contains(originals.get(i).node())) {
                    exits.add(copied.get(i).node());
                }
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

    private boolean isSwitchExit(Node node) {
        return exits.contains(node);
    }

    private boolean containsSwitchExit(Node root) {
        return root.iterate(true).stream().anyMatch(info -> exits.contains(info.node()));
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
