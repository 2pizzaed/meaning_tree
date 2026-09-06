package org.vstu.meaningtree.languages.helpers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Definition;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.definitions.GeneratorDefinition;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.ReturnStatement;
import org.vstu.meaningtree.nodes.statements.YieldStatement;
import org.vstu.meaningtree.nodes.statements.loops.ForEachLoop;
import org.vstu.meaningtree.nodes.statements.loops.GeneralForLoop;
import org.vstu.meaningtree.nodes.statements.loops.InfiniteLoop;
import org.vstu.meaningtree.nodes.statements.loops.RangeForLoop;
import org.vstu.meaningtree.nodes.statements.loops.WhileLoop;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.ContinueStatement;

/**
 * Разбор формы генератора: переводится ли его тело в класс-итератор и, если нет, что помешало.
 * <p>
 * От целевого языка разбор не зависит — он говорит только о том, выражается ли поток выдач
 * состоянием объекта. Поэтому он лежит здесь, а не рядом с понижением: им пользуется и
 * само понижение в языке без генераторов
 * ({@code org.vstu.meaningtree.languages.helpers.GeneratorLowerer}), и правило поддержки
 * {@link org.vstu.meaningtree.languages.support.features.UnloweredGeneratorFeature},
 * чтобы отказ называл ровно то, что помешало.
 * <p>
 * Распознаются две формы: цикл с единственной выдачей в своём теле, стоящий последним
 * оператором, и плоская последовательность выдач в хвосте тела.
 */
public final class GeneratorForm {
    private GeneratorForm() {
    }

    /**
     * Причина, по которой генератор не переводится в итератор, или {@code null}, если
     * переводится. Делегирующие выдачи должны быть развёрнуты до разбора.
     */
    @Nullable
    public static String unsupportedReason(@NotNull GeneratorDefinition generator) {
        CompoundStatement body = generator.getBody();
        Node[] statements = body.getNodes();

        if (countYields(body) == 0) {
            return "generator has no yield statements";
        }
        for (NodeInfo info : generator) {
            if (info.node() instanceof YieldStatement yield && yield.isDelegated()) {
                return "delegated yield must be expanded before lowering";
            }
        }

        int firstYieldingIndex = -1;
        for (int i = 0; i < statements.length; i++) {
            if (countYields(statements[i]) > 0) {
                firstYieldingIndex = i;
                break;
            }
        }

        Node firstYielding = statements[firstYieldingIndex];
        if (firstYielding instanceof Loop) {
            return loopFormReason(statements, firstYieldingIndex);
        }
        if (firstYielding instanceof YieldStatement) {
            return flatFormReason(statements, firstYieldingIndex);
        }
        return "yield nested in " + firstYielding.getClass().getSimpleName()
                + " is neither a yielding loop nor a plain sequence of yields";
    }

    /** Число выдач, принадлежащих именно этому поддереву: во вложенные определения обход не заходит. */
    public static int countYields(@NotNull Node node) {
        return YieldStatement.ownedBy(node).size();
    }

    /** Тело оператора как блок: одиночный оператор заворачивается, блок возвращается как есть. */
    public static CompoundStatement asCompound(@NotNull Statement statement) {
        return statement instanceof CompoundStatement compound
                ? compound : new CompoundStatement(statement);
    }

    @Nullable
    private static String loopFormReason(Node[] statements, int loopIndex) {
        if (loopIndex != statements.length - 1) {
            return "statements after the yielding loop are not supported";
        }
        Loop loop = (Loop) statements[loopIndex];
        if (loop.hasElseBranch()) {
            return "else branch of a yielding loop is not supported";
        }
        if (!(loop instanceof WhileLoop || loop instanceof InfiniteLoop
                || loop instanceof RangeForLoop || loop instanceof GeneralForLoop
                || loop instanceof ForEachLoop)) {
            return "loop of type " + loop.getClass().getSimpleName() + " cannot carry a yield";
        }

        CompoundStatement loopBody = asCompound(loop.getBody());
        int topLevelYields = 0;
        for (Node statement : loopBody.getNodes()) {
            if (statement instanceof YieldStatement yield) {
                topLevelYields++;
                if (!yield.hasValue()) {
                    return "bare yield inside a loop is not supported";
                }
            } else if (countYields(statement) > 0) {
                return "yield nested inside " + statement.getClass().getSimpleName()
                        + " is not supported; only a yield directly in the loop body is";
            }
        }
        if (topLevelYields != 1) {
            return "a yielding loop must contain exactly one yield, found " + topLevelYields;
        }
        if (containsJump(loopBody)) {
            return "break, continue or return inside a yielding loop is not supported";
        }
        return null;
    }

    @Nullable
    private static String flatFormReason(Node[] statements, int firstYieldIndex) {
        for (int i = firstYieldIndex; i < statements.length; i++) {
            if (!(statements[i] instanceof YieldStatement yield)) {
                return "statements between or after yields are not supported outside a loop";
            }
            if (!yield.hasValue()) {
                return "bare yield is not supported";
            }
        }
        return null;
    }

    /**
     * Есть ли в поддереве переход, принадлежащий именно ему. Во вложенные определения обход
     * не заходит по той же причине, что и у выдач: их переходы принадлежат своему телу.
     */
    private static boolean containsJump(Node root) {
        for (NodeInfo info : root.iterate(false)) {
            boolean isJump = info.node() instanceof BreakStatement
                    || info.node() instanceof ContinueStatement
                    || info.node() instanceof ReturnStatement;
            if (!isJump) {
                continue;
            }
            boolean nested = false;
            for (NodeInfo parent = info.parent(); parent != null; parent = parent.parent()) {
                if (parent.node() == root) {
                    break;
                }
                if (parent.node() instanceof Definition) {
                    nested = true;
                    break;
                }
            }
            if (!nested) {
                return true;
            }
        }
        return false;
    }
}
