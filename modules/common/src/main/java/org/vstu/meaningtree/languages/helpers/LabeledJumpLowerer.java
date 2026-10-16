package org.vstu.meaningtree.languages.helpers;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Definition;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.expressions.identifiers.JumpLabel;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.BoolLiteral;
import org.vstu.meaningtree.nodes.interfaces.HasBodyStatement;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.conditions.SwitchStatement;
import org.vstu.meaningtree.nodes.statements.conditions.components.ConditionBranch;
import org.vstu.meaningtree.nodes.statements.exceptions.ExceptionCatchStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.ContinueStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.GotoStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.JumpStatement;
import org.vstu.meaningtree.nodes.types.builtin.BooleanType;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Убирает {@code break} и {@code continue} с меткой цикла для языков, в которых их нет (C++, Python).
 * Переход, который и без метки попал бы в тот же цикл, теряет метку. Остальные переходы
 * выражаются флагом: флаг объявляется в начале тела цикла-цели, переход заменяется на
 * {@code flag = true; break;}, а после каждой промежуточной конструкции, из которой break
 * выходит, ставится {@code if (flag) break;} (или {@code if (flag) continue;}, когда до цели
 * остались одни switch). Метки циклов, на которые не ведёт ни один goto, после прохода снимаются.
 * Работает с копией дерева и не меняет дерево вызывающего.
 */
public final class LabeledJumpLowerer {
    private enum Kind {
        BREAK("_break_"),
        CONTINUE("_continue_");

        private final String flagPrefix;

        Kind(String flagPrefix) {
            this.flagPrefix = flagPrefix;
        }
    }

    /** Переход с меткой и конструкции между ним и циклом-целью, от внутренней к внешней. */
    private record Jump(JumpStatement statement, CompoundStatement container, Kind kind,
                        List<Statement> intermediates, List<CompoundStatement> intermediateContainers) {
    }

    private final boolean switchCapturesBreak;
    private int ordinal = 1;

    private LabeledJumpLowerer(boolean switchCapturesBreak) {
        this.switchCapturesBreak = switchCapturesBreak;
    }

    /**
     * @param switchCapturesBreak выходит ли break без метки из switch, как в C++, или только
     *                            из цикла, как в Python ({@code match} его не перехватывает)
     */
    public static MeaningTree lower(MeaningTree source, boolean switchCapturesBreak) {
        if (!hasLabeledLoops(source)) {
            return source;
        }
        MeaningTree result = new MeaningTree(source.getRootNode().clone());
        LabeledJumpLowerer lowerer = new LabeledJumpLowerer(switchCapturesBreak);
        for (Loop loop : lowerer.targetLoops(result)) {
            lowerer.lowerLoop(loop);
        }
        dropUnusedLoopLabels(result);
        return result;
    }

    /** Без циклов с меткой нет ни переходов к ним, ни меток, которые надо снять. */
    private static boolean hasLabeledLoops(MeaningTree tree) {
        for (NodeInfo info : tree) {
            if (info.node() instanceof Loop loop && loop.getJumpLabel() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Циклы, на которые ведёт хотя бы один переход с меткой. Сравнение по ссылке: равные по
     * значению циклы — разные цели.
     */
    private List<Loop> targetLoops(MeaningTree tree) {
        List<Loop> loops = new ArrayList<>();
        for (NodeInfo info : tree) {
            if (isLabeledLoopJump(info.node())
                    && JumpTargets.target(info, switchCapturesBreak) instanceof Loop target
                    && loops.stream().noneMatch(loop -> loop == target)) {
                loops.add(target);
            }
        }
        return loops;
    }

    private void lowerLoop(Loop loop) {
        makeCompoundBodies(loop);
        CompoundStatement loopBody = loop.makeCompoundBody();

        List<Jump> jumps = new ArrayList<>();
        // Обход от самого цикла: цепочка родителей перехода должна до него дойти
        for (NodeInfo info : loop.iterate(true)) {
            if (isLabeledLoopJump(info.node()) && JumpTargets.target(info, switchCapturesBreak) == loop) {
                jumps.add(describeJump(info, loop));
            }
        }

        Map<Kind, SimpleIdentifier> flags = new LinkedHashMap<>();
        // Проверка после промежуточной конструкции общая для всех переходов, которые через неё идут
        Map<Statement, Set<String>> insertedChecks = new IdentityHashMap<>();
        for (Jump jump : jumps) {
            if (!needsFlag(jump)) {
                jump.statement().setJumpDestination(null);
                continue;
            }
            SimpleIdentifier flag = flags.computeIfAbsent(jump.kind(),
                    kind -> new SimpleIdentifier(kind.flagPrefix + ordinal++).remap(loop));
            replace(jump.container(), jump.statement(), List.of(
                    synthetic(new AssignmentStatement(flag.clone(), new BoolLiteral(true)), jump.statement()),
                    synthetic(new BreakStatement(), jump.statement())
            ));
            insertChecks(jump, flag, insertedChecks);
        }

        int index = 0;
        for (SimpleIdentifier flag : flags.values()) {
            loopBody.insert(index++, synthetic(
                    new VariableDeclaration(new BooleanType(), flag.clone(), new BoolLiteral(false)), loop));
        }
    }

    /**
     * Ставит проверку флага после каждой промежуточной конструкции. Для continue проверка
     * после последней из конструкций, за которой до цели остались одни switch, — это уже
     * continue: он проходит сквозь switch к циклу-цели.
     */
    private void insertChecks(Jump jump, SimpleIdentifier flag, Map<Statement, Set<String>> insertedChecks) {
        List<Statement> intermediates = jump.intermediates();
        for (int i = 0; i < intermediates.size(); i++) {
            Statement intermediate = intermediates.get(i);
            boolean loopRemains = containsLoop(intermediates.subList(i + 1, intermediates.size()));
            boolean continues = jump.kind() == Kind.CONTINUE && !loopRemains;

            if (insertedChecks.computeIfAbsent(intermediate, key -> new LinkedHashSet<>()).add(flag.getName())) {
                JumpStatement exit = continues ? new ContinueStatement() : new BreakStatement();
                IfStatement check = new IfStatement(flag.clone(), new CompoundStatement(exit));
                insertAfter(jump.intermediateContainers().get(i), intermediate, synthetic(check, jump.statement()));
            }
            if (continues) {
                return;
            }
        }
    }

    private boolean needsFlag(Jump jump) {
        return switch (jump.kind()) {
            case BREAK -> !jump.intermediates().isEmpty();
            // continue проходит сквозь switch, ему мешают только вложенные циклы
            case CONTINUE -> containsLoop(jump.intermediates());
        };
    }

    private static boolean containsLoop(List<Statement> statements) {
        return statements.stream().anyMatch(statement -> statement instanceof Loop);
    }

    private Jump describeJump(NodeInfo info, Loop target) {
        JumpStatement statement = (JumpStatement) info.node();
        List<Statement> intermediates = new ArrayList<>();
        List<CompoundStatement> containers = new ArrayList<>();
        for (NodeInfo crossed : JumpTargets.crossed(info, target, switchCapturesBreak)) {
            intermediates.add((Statement) crossed.node());
            containers.add((CompoundStatement) crossed.parentNode());
        }
        Kind kind = statement instanceof BreakStatement ? Kind.BREAK : Kind.CONTINUE;
        return new Jump(statement, (CompoundStatement) info.parentNode(), kind, intermediates, containers);
    }

    private static boolean isLabeledLoopJump(Node node) {
        return (node instanceof BreakStatement || node instanceof ContinueStatement)
                && ((JumpStatement) node).getJumpDestination() != null;
    }

    /**
     * Делает составными все тела внутри цикла: флаги и проверки встают между операторами.
     * Цепочка {@code else if} остаётся цепочкой.
     */
    static void makeCompoundBodies(Loop loop) {
        for (NodeInfo info : loop.iterate(true)) {
            switch (info.node()) {
                case IfStatement ifStatement -> {
                    ifStatement.getBranches().forEach(ConditionBranch::makeCompoundBody);
                    ifStatement.makeCompoundElseBranch();
                }
                case SwitchStatement switchStatement -> switchStatement.makeCompoundBranches();
                case ExceptionCatchStatement tryStatement -> tryStatement.makeCompoundBranches();
                case Loop nested -> {
                    nested.makeCompoundBody();
                    nested.makeCompoundElseBody();
                }
                case Definition ignored -> { }
                case HasBodyStatement hasBody -> hasBody.makeCompoundBody();
                default -> { }
            }
        }
    }

    private static void dropUnusedLoopLabels(MeaningTree tree) {
        Set<String> gotoTargets = new HashSet<>();
        List<Loop> labeledLoops = new ArrayList<>();
        for (NodeInfo info : tree) {
            if (info.node() instanceof GotoStatement gotoStatement && gotoStatement.getJumpDestination() != null) {
                gotoTargets.add(gotoStatement.getJumpDestination().getName());
            }
            if (info.node() instanceof Loop loop && loop.getJumpLabel() != null) {
                labeledLoops.add(loop);
            }
        }
        for (Loop loop : labeledLoops) {
            JumpLabel label = loop.getJumpLabel();
            if (!gotoTargets.contains(label.getName())) {
                loop.setJumpLabel(null);
            }
        }
    }

    private static void replace(CompoundStatement container, Node anchor, List<? extends Node> nodes) {
        int index = indexOf(container, anchor);
        container.remove(anchor);
        for (Node node : nodes) {
            container.insert(index++, node);
        }
    }

    private static void insertAfter(CompoundStatement container, Node anchor, Node node) {
        container.insert(indexOf(container, anchor) + 1, node);
    }

    private static int indexOf(CompoundStatement container, Node target) {
        Node[] nodes = container.getNodes();
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] == target) {
                return i;
            }
        }
        throw new IllegalStateException("Jump anchor was not found in its container");
    }

    private static <T extends Node> T synthetic(T node, Node origin) {
        node.remap(origin);
        for (NodeInfo info : node) {
            info.node().remap(origin);
        }
        return node;
    }
}
