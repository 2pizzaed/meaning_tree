package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.languages.helpers.JumpTargets;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.BoolLiteral;
import org.vstu.meaningtree.nodes.expressions.logical.NotOp;
import org.vstu.meaningtree.nodes.interfaces.HasBodyStatement;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.ReturnStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.conditions.SwitchStatement;
import org.vstu.meaningtree.nodes.statements.conditions.components.CaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.ConditionBranch;
import org.vstu.meaningtree.nodes.statements.exceptions.ExceptionCatchStatement;
import org.vstu.meaningtree.nodes.statements.exceptions.RaiseExceptionStatement;
import org.vstu.meaningtree.nodes.statements.exceptions.components.CatchClause;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.JumpStatement;
import org.vstu.meaningtree.nodes.types.builtin.BooleanType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Убирает break без метки из веток switch: в дереве он выходит из switch, а в Python break
 * внутри match выходит из цикла. Завершающий break ветки парсеры уже выразили типом ветки,
 * остаются break в середине. Код после оператора с таким break переносится в ветвь {@code if},
 * которая его выполнила бы: {@code if c: break} и {@code a()} становится {@code if not c: a()}.
 * Если таких ветвей несколько или break стоит внутри try или with, ветка получает флаг: break
 * поднимает его, а код после — под {@code if not флаг}. Работает с копией дерева и не меняет
 * дерево вызывающего.
 */
final class PythonSwitchBreakLowerer {
    /** break, выходящие из обрабатываемого switch; сравнение по ссылке. */
    private Set<Node> exits;
    /** Флаг обрабатываемой ветки; {@code null}, пока он не понадобился. */
    private SimpleIdentifier flag;
    private int ordinal = 1;

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
        PythonSwitchBreakLowerer lowerer = new PythonSwitchBreakLowerer();
        for (SwitchStatement switchStatement : switches) {
            lowerer.lowerSwitch(switchStatement);
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

    private void lowerSwitch(SwitchStatement switchStatement) {
        exits = Collections.newSetFromMap(new IdentityHashMap<>());
        for (NodeInfo info : switchStatement.iterate(true)) {
            if (info.node() instanceof BreakStatement && JumpTargets.target(info, true) == switchStatement) {
                exits.add(info.node());
            }
        }
        if (exits.isEmpty()) {
            return;
        }
        switchStatement.makeCompoundBranches();
        List<CaseBlock> branches = new ArrayList<>(switchStatement.getCases());
        if (switchStatement.hasDefaultCase()) {
            branches.add(switchStatement.getDefaultCase());
        }
        for (CaseBlock branch : branches) {
            CompoundStatement body = (CompoundStatement) branch.getBody();
            flag = null;
            List<Node> statements = withoutSwitchExits(body.getNodeList(), false);
            if (flag != null) {
                statements.addFirst(new VariableDeclaration(new BooleanType(), flag.clone(), new BoolLiteral(false))
                        .remapTree(switchStatement));
            }
            setNodes(body, statements);
        }
    }

    /**
     * Операторы без break, выходящих из switch, с тем же порядком выполнения.
     *
     * @param guarded код после этих операторов стоит под флагом, поэтому break должен его поднять
     */
    private List<Node> withoutSwitchExits(List<Node> statements, boolean guarded) {
        List<Node> result = new ArrayList<>();
        for (int i = 0; i < statements.size(); i++) {
            Node statement = statements.get(i);
            List<Node> rest = statements.subList(i + 1, statements.size());
            if (exits.contains(statement)) {
                if (guarded) {
                    result.add(new AssignmentStatement(flag().clone(), new BoolLiteral(true)).remapTree(statement));
                }
                // Код после break недостижим
                return result;
            }
            if (!containsSwitchExit(statement)) {
                result.add(statement);
                continue;
            }
            if (statement instanceof CompoundStatement block) {
                List<Node> nodes = new ArrayList<>(block.getNodeList());
                nodes.addAll(rest);
                result.addAll(withoutSwitchExits(nodes, guarded));
            } else if (rest.isEmpty()) {
                lowerBodies((Statement) statement, guarded);
                result.add(statement);
            } else if (statement instanceof IfStatement ifStatement && completingBodies(ifStatement) <= 1) {
                result.add(moveRestInto(ifStatement, rest, guarded));
            } else {
                // Код после выполнился бы за несколькими ветвями или после try или with
                lowerBodies((Statement) statement, true);
                result.add(statement);
                CompoundStatement guardedRest = new CompoundStatement(withoutSwitchExits(rest, guarded));
                result.add(new IfStatement(new NotOp(flag().clone()), guardedRest).remapTree(statement));
            }
            return result;
        }
        return result;
    }

    /** Переносит код после {@code if} в его единственную ветвь, после которой этот код выполнялся. */
    private IfStatement moveRestInto(IfStatement ifStatement, List<Node> rest, boolean guarded) {
        if (!ifStatement.hasElseBranch()) {
            ifStatement.setElseBranch(new CompoundStatement().remap(ifStatement));
        }
        for (CompoundStatement body : bodiesOf(ifStatement)) {
            List<Node> nodes = new ArrayList<>(body.getNodeList());
            if (canCompleteNormally(nodes)) {
                nodes.addAll(rest);
            }
            setNodes(body, withoutSwitchExits(nodes, guarded));
        }
        return simplifyEmptyThen(ifStatement);
    }

    private void lowerBodies(Statement statement, boolean guarded) {
        for (CompoundStatement body : bodiesOf(statement)) {
            setNodes(body, withoutSwitchExits(body.getNodeList(), guarded));
        }
    }

    /** Тела, сквозь которые break выходит из switch, приведённые к блокам. */
    private static List<CompoundStatement> bodiesOf(Statement statement) {
        List<CompoundStatement> bodies = new ArrayList<>();
        switch (statement) {
            case IfStatement ifStatement -> {
                ifStatement.makeCompoundBranches();
                for (ConditionBranch branch : ifStatement.getBranches()) {
                    bodies.add((CompoundStatement) branch.getBody());
                }
                if (ifStatement.hasElseBranch()) {
                    bodies.add((CompoundStatement) ifStatement.getElseBranch());
                }
            }
            case ExceptionCatchStatement tryStatement -> {
                tryStatement.makeCompoundBranches();
                bodies.add((CompoundStatement) tryStatement.getBody());
                for (CatchClause clause : tryStatement.getCatchClauses()) {
                    bodies.add((CompoundStatement) clause.getBody());
                }
                if (tryStatement.hasElseBranch()) {
                    bodies.add((CompoundStatement) tryStatement.getElseBranch());
                }
                if (tryStatement.hasFinallyBranch()) {
                    bodies.add((CompoundStatement) tryStatement.getFinallyBranch());
                }
            }
            // Тело цикла перехватывает break, из switch выходит только break из ветки else
            case Loop loop -> bodies.add(loop.makeCompoundElseBody());
            case HasBodyStatement hasBody -> bodies.add(hasBody.makeCompoundBody());
            default -> throw new IllegalStateException(
                    "A break cannot leave a switch through " + statement.getClass().getSimpleName());
        }
        return bodies;
    }

    private int completingBodies(IfStatement ifStatement) {
        int count = ifStatement.hasElseBranch() ? 0 : 1;
        for (ConditionBranch branch : ifStatement.getBranches()) {
            count += canCompleteNormally(statementsOf(branch.getBody())) ? 1 : 0;
        }
        if (ifStatement.hasElseBranch()) {
            count += canCompleteNormally(statementsOf(ifStatement.getElseBranch())) ? 1 : 0;
        }
        return count;
    }

    private SimpleIdentifier flag() {
        if (flag == null) {
            flag = new SimpleIdentifier("_match_exit_" + ordinal++);
        }
        return flag;
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
