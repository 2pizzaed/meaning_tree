package org.vstu.meaningtree.languages.helpers;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.expressions.calls.MethodCall;
import org.vstu.meaningtree.nodes.expressions.comprehensions.Comprehension;
import org.vstu.meaningtree.nodes.expressions.comprehensions.ContainerBasedComprehension;
import org.vstu.meaningtree.nodes.expressions.comprehensions.RangeBasedComprehension;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.other.KeyValuePair;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.loops.ForEachLoop;
import org.vstu.meaningtree.nodes.statements.loops.RangeForLoop;
import org.vstu.meaningtree.utils.ReplaceResult;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rewrites collection comprehensions into a declaration followed by a loop before rendering.
 * The returned tree is a clone, so rendering never mutates the caller's meaning tree.
 */
public final class ComprehensionLowerer {
    public enum CollectionKind {
        LIST,
        SET,
        DICTIONARY
    }

    public interface Target {
        Type collectionType(CollectionKind kind);

        Expression createCollection(CollectionKind kind);

        Expression append(SimpleIdentifier collection, CollectionKind kind, List<Expression> values, Node origin);

        String loopVariableName(String originalName, int ordinal);
    }

    private record Plan(NodeInfo comprehensionInfo, Node container, Node anchor, int ordinal) {
    }

    private ComprehensionLowerer() {
    }

    public static MeaningTree lower(MeaningTree source, Target target) {
        MeaningTree result = new MeaningTree(source.getRootNode().clone());
        List<Plan> plans = collectPlans(result);
        Map<Node, List<Plan>> plansByContainer = new IdentityHashMap<>();

        for (Plan plan : plans) {
            Comprehension comprehension = (Comprehension) plan.comprehensionInfo().node();
            SimpleIdentifier temporary = new SimpleIdentifier("_tmp_compreh_" + plan.ordinal()).remap(comprehension);
            ReplaceResult replacement = result.replace(plan.comprehensionInfo(), temporary);
            if (!replacement.isSuccess()) {
                throw new IllegalStateException("Unable to replace comprehension: " + replacement.message());
            }
            plansByContainer.computeIfAbsent(plan.container(), ignored -> new ArrayList<>()).add(plan);
        }

        for (List<Plan> containerPlans : plansByContainer.values()) {
            for (Plan plan : containerPlans) {
                Comprehension comprehension = (Comprehension) plan.comprehensionInfo().node();
                List<Node> lowered = lowerComprehension(comprehension, plan.ordinal(), target);
                insertBefore(plan.container(), plan.anchor(), lowered);
            }
        }
        return result;
    }

    private static List<Plan> collectPlans(MeaningTree tree) {
        List<Plan> plans = new ArrayList<>();
        int ordinal = 1;
        for (NodeInfo info : tree) {
            if (!(info.node() instanceof Comprehension)) {
                continue;
            }
            NodeInfo anchorInfo = info;
            for (NodeInfo parent = info.parent(); parent != null; parent = parent.parent()) {
                if (parent.node() instanceof CompoundStatement || parent.node() instanceof ProgramEntryPoint) {
                    plans.add(new Plan(info, parent.node(), anchorInfo.node(), ordinal++));
                    break;
                }
                anchorInfo = parent;
            }
        }
        return plans;
    }

    private static List<Node> lowerComprehension(Comprehension comprehension, int ordinal, Target target) {
        CollectionKind kind = kindOf(comprehension);
        SimpleIdentifier temporary = new SimpleIdentifier("_tmp_compreh_" + ordinal).remap(comprehension);
        Type collectionType = remapSyntheticTree(target.collectionType(kind), comprehension);
        Expression initialization = target.createCollection(kind);
        if (initialization != null) {
            remapSyntheticTree(initialization, comprehension);
        }
        VariableDeclaration declaration = new VariableDeclaration(
                collectionType,
                temporary.clone(),
                initialization
        ).remap(comprehension);

        String originalVariableName;
        SimpleIdentifier loopVariable;
        Statement loop;
        List<Expression> itemValues = itemValues(comprehension);
        Expression condition = comprehension.hasCondition() ? comprehension.getCondition().clone() : null;

        if (comprehension instanceof RangeBasedComprehension rangeBased) {
            originalVariableName = rangeBased.getRangeVariableIdentifier().getName();
            loopVariable = new SimpleIdentifier(target.loopVariableName(originalVariableName, ordinal))
                    .remap(rangeBased.getRangeVariableIdentifier());
            replaceIdentifier(itemValues, condition, originalVariableName, loopVariable);
            loop = new RangeForLoop(
                    rangeBased.getRange().clone(),
                    loopVariable,
                    makeLoopBody(temporary, kind, itemValues, condition, target, comprehension)
            ).remap(comprehension);
        } else if (comprehension instanceof ContainerBasedComprehension containerBased) {
            originalVariableName = containerBased.getContainerItemDeclaration().getFirstDeclarator().getIdentifier().getName();
            loopVariable = new SimpleIdentifier(target.loopVariableName(originalVariableName, ordinal))
                    .remap(containerBased.getContainerItemDeclaration().getFirstDeclarator().getIdentifier());
            replaceIdentifier(itemValues, condition, originalVariableName, loopVariable);
            VariableDeclaration item = new VariableDeclaration(
                    containerBased.getContainerItemDeclaration().getType().clone(),
                    loopVariable
            ).remap(containerBased.getContainerItemDeclaration());
            loop = new ForEachLoop(
                    item,
                    containerBased.getContainerExpression().clone(),
                    makeLoopBody(temporary, kind, itemValues, condition, target, comprehension)
            ).remap(comprehension);
        } else {
            throw new IllegalArgumentException("Unknown comprehension type: " + comprehension.getClass().getName());
        }

        return List.of(declaration, loop);
    }

    private static Statement makeLoopBody(SimpleIdentifier temporary, CollectionKind kind, List<Expression> itemValues,
                                          Expression condition, Target target, Comprehension origin) {
        Expression append = target.append(temporary.clone(), kind, itemValues, origin).remap(origin);
        Statement statement = new ExpressionStatement(append).remap(origin);
        if (condition != null) {
            statement = new IfStatement(condition, new CompoundStatement(statement).remap(origin)).remap(origin);
        }
        return new CompoundStatement(statement).remap(origin);
    }

    private static CollectionKind kindOf(Comprehension comprehension) {
        return switch (comprehension.getItem()) {
            case Comprehension.ListItem ignored -> CollectionKind.LIST;
            case Comprehension.SetItem ignored -> CollectionKind.SET;
            case KeyValuePair ignored -> CollectionKind.DICTIONARY;
            default -> throw new IllegalArgumentException("Unknown comprehension item: " + comprehension.getItem().getClass().getName());
        };
    }

    private static List<Expression> itemValues(Comprehension comprehension) {
        return switch (comprehension.getItem()) {
            case Comprehension.ListItem item -> List.of(item.value().clone());
            case Comprehension.SetItem item -> List.of(item.value().clone());
            case KeyValuePair pair -> List.of(pair.key().clone(), pair.value().clone());
            default -> throw new IllegalArgumentException("Unknown comprehension item: " + comprehension.getItem().getClass().getName());
        };
    }

    private static void replaceIdentifier(List<Expression> itemValues, Expression condition, String oldName,
                                          SimpleIdentifier replacement) {
        for (Expression value : itemValues) {
            value.replaceAll(
                    info -> info.node() instanceof SimpleIdentifier identifier && identifier.getName().equals(oldName),
                    ignored -> replacement.clone()
            );
        }
        if (condition != null) {
            condition.replaceAll(
                    info -> info.node() instanceof SimpleIdentifier identifier && identifier.getName().equals(oldName),
                    ignored -> replacement.clone()
            );
        }
    }

    private static <T extends Node> T remapSyntheticTree(T node, Node origin) {
        node.remap(origin);
        for (NodeInfo info : node) {
            info.node().remap(origin);
        }
        return node;
    }

    private static void insertBefore(Node container, Node anchor, List<Node> nodes) {
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
        throw new IllegalArgumentException("Unsupported comprehension container: " + container.getClass().getName());
    }

    private static int indexOf(Node[] nodes, Node target) {
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] == target) {
                return i;
            }
        }
        throw new IllegalStateException("Comprehension anchor was not found in its container");
    }
}
