package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.exceptions.MeaningTreeException;
import org.vstu.meaningtree.iterators.utils.CollectionFieldDescriptor;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.languages.utils.ExpressionDAG;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.declarations.MethodDeclaration;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.definitions.MethodDefinition;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.BinaryExpression;
import org.vstu.meaningtree.nodes.expressions.ParenthesizedExpression;
import org.vstu.meaningtree.nodes.expressions.comparison.*;
import org.vstu.meaningtree.nodes.expressions.identifiers.SelfReference;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SuperClassReference;
import org.vstu.meaningtree.nodes.expressions.literals.BoolLiteral;
import org.vstu.meaningtree.nodes.expressions.logical.NotOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitAndOp;
import org.vstu.meaningtree.nodes.expressions.other.AssignmentExpression;
import org.vstu.meaningtree.nodes.interfaces.HasBodyStatement;
import org.vstu.meaningtree.nodes.interfaces.HasInitialization;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.DeleteStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.conditions.SwitchStatement;
import org.vstu.meaningtree.nodes.statements.conditions.components.CaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.ConditionBranch;
import org.vstu.meaningtree.nodes.statements.loops.DoWhileLoop;
import org.vstu.meaningtree.nodes.statements.loops.ForLoop;
import org.vstu.meaningtree.nodes.statements.loops.GeneralForLoop;
import org.vstu.meaningtree.nodes.statements.loops.WhileLoop;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.ContinueStatement;

import java.util.*;


public class PythonSpecialNodeTransformations {
    public static Node[] representGeneralFor(GeneralForLoop generalFor) {
        boolean needDeleting = false;
        HasInitialization initializer = null;
        if (generalFor.hasInitializer()) {
            initializer = generalFor.getInitializer();
            needDeleting = initializer instanceof VariableDeclaration;
        }

        Expression condition = new BoolLiteral(true);
        if (generalFor.hasCondition()) {
            condition = generalFor.getCondition();
        }
        Node update = null;
        if (generalFor.hasUpdate()) {
            update = generalFor.getUpdate();
            if (update instanceof AssignmentExpression a) {
                update = a.toStatement();
            }
        }
        Statement stmt = generalFor.getBody();
        CompoundStatement body;
        if (stmt instanceof CompoundStatement compound) {
            body = compound;
        } else {
            body = new CompoundStatement(stmt);
        }
        _prepend_continue_with_expression(body, update);
        if (update != null) {
            body.insert(body.getLength(), update);
        }
        List<Node> result = new ArrayList<>();
        result.add((Node) initializer);
        result.add(new WhileLoop(condition, body));
        if (needDeleting) {
            VariableDeclaration varDecl = (VariableDeclaration) initializer;
            for (VariableDeclarator declarator : varDecl.getDeclarators()) {
                result.add(new DeleteStatement(declarator.getIdentifier()));
            }
        }
        return new Node[] {(Node)initializer, new WhileLoop(condition, body)};
    }

    private static void _prepend_continue_with_expression(CompoundStatement compound, Node update) {
        if (update == null) {
            return;
        }

        int found = -1;
        Node[] nodes = compound.getNodes();
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] instanceof ContinueStatement) {
                found = i;
            }
        }
        if (found != -1) {
            compound.insert(found, update);
        }
        for (Node node : compound.getNodes()) {
            if (node instanceof ForLoop || node instanceof WhileLoop) {
                return;
            } else if (node instanceof IfStatement ifStmt) {
                ifStmt.makeCompoundBranches();
                for (ConditionBranch branch : ifStmt.getBranches()) {
                    _prepend_continue_with_expression((CompoundStatement) branch.getBody(), update);
                }
                if (ifStmt.hasElseBranch()) {
                    _prepend_continue_with_expression((CompoundStatement) ifStmt.getElseBranch(), update);
                }
            } else if (node instanceof SwitchStatement switchStmt) {
                //TODO: it is correct (continue usage) in programming languages?
                switchStmt.makeCompoundBranches();
                for (CaseBlock branch : switchStmt.getCases()) {
                    _prepend_continue_with_expression((CompoundStatement) branch.getBody(), update);
                }
                if (switchStmt.hasDefaultCase()) {
                    _prepend_continue_with_expression((CompoundStatement) switchStmt.getDefaultCase().getBody(), update);
                }
            } else if (node instanceof HasBodyStatement hasBodyStmt) {
                hasBodyStmt.makeCompoundBody();
                _prepend_continue_with_expression((CompoundStatement) hasBodyStmt.getBody(), update);
            }
        }
    }

    public static Node representDoWhile(DoWhileLoop doWhile) {
        Expression condition = doWhile.getCondition();
        if (condition instanceof BinaryComparison cmp) {
            condition = cmp.inverse();
        } else {
            condition = new NotOp(new ParenthesizedExpression(condition));
        }
        IfStatement breakCondition = new IfStatement(condition, new BreakStatement(), null);
        List<Node> body;
        if (doWhile.getBody() instanceof CompoundStatement compound) {
            body = new ArrayList<>(List.of(compound.getNodes()));
        } else {
            body = new ArrayList<>();
            body.add(doWhile.getBody());
        }
        body.add(breakCondition);
        return new WhileLoop(new BoolLiteral(true), new CompoundStatement(body));
    }

    public static Node detectCompoundComparison(Node expressionNode) {
        if (expressionNode instanceof ShortCircuitAndOp op) {

            // Список выражений, которые не будут участвовать в составном сравнении.
            // Например: a == b, a != b или выражения отличные от сравнения
            ArrayList<Expression> secondary = new ArrayList<>();

            // Список сравнений, который может быть сгруппирован в составные сравнения
            ArrayList<BinaryComparison> primary = new ArrayList<>();

            // Разбираем выражение AND на нужные для составного сравнения и ненужные
            // Те, что secondary будут просто присоединены к итоговому выражению с помощью AND
            _collectCompoundStatementElements(op, primary, secondary);

            // Построим орграф из выражений и создадим связи на основе сравнений. Стрелка указывает на больший элемент
            Set<Expression> vertices = new LinkedHashSet<>();
            for (BinaryComparison cmp : primary) {
                vertices.add(cmp.getLeft());
                vertices.add(cmp.getRight());
            }
            ExpressionDAG graph = new ExpressionDAG(vertices.toArray(new Expression[0]));
            for (BinaryComparison cmp : primary) {
                if (cmp instanceof LtOp) {
                    graph.addEdge(cmp.getLeft(), cmp.getRight());
                } else if (cmp instanceof LeOp) {
                    graph.addTaggedEdge(cmp.getLeft(), cmp.getRight());
                } else if (cmp instanceof GtOp) {
                    graph.addEdge(cmp.getRight(), cmp.getLeft());
                } else if (cmp instanceof GeOp) {
                    graph.addTaggedEdge(cmp.getRight(), cmp.getLeft());
                } else {
                    return expressionNode;
                }
            }

            // Итоговый результат обработки
            ArrayList<Expression> result = new ArrayList<>();

            // Для каждого слабосвязанного компонента графа
            for (ExpressionDAG dag : graph.weaklyConnectedComponents()) {
                Set<Expression> visited = new HashSet<>();
                int initialLength =  dag.getVertices().length;

                // Пока не обработаем все вершины графа
                while (visited.size() < initialLength) {
                    // Ищем самый длинный путь в графе
                    List<Expression> longestPath = dag.findLongestPath();
                    boolean cyclic = new HashSet<>(longestPath).size() == longestPath.size() - 1;

                    ArrayList<BinaryComparison> compound = new ArrayList<>();
                    // Строим по этому пути новое составное сравнение, попутно удаляя обработанные связи
                    for (int i = 0; i < longestPath.size() - 1; i++) {
                        Expression u = longestPath.get(i);
                        Expression v = longestPath.get(i + 1);
                        if (!cyclic) {
                            if (dag.isTagged(u, v)) {
                                compound.add(new LeOp(u, v));
                            } else {
                                compound.add(new LtOp(u, v));
                            }
                        } else {
                            // Путь цикличен, значит не производим составление составного сравнения
                            if (dag.isTagged(u, v)) {
                                result.add(new LeOp(u, v));
                            } else {
                                result.add(new LtOp(u, v));
                            }
                        }
                        visited.add(u);
                        visited.add(v);
                        // Удаляем связь, которую обработали
                        dag.removeEdge(u, v);
                    }
                    if (!compound.isEmpty()) {
                        if (compound.size() == 1) {
                            result.add(compound.getFirst());
                        } else {
                            result.add(new CompoundComparison(compound.toArray(new BinaryComparison[0])));
                        }
                    } else if (!cyclic) {
                        throw new MeaningTreeException("Something bad in DAG longest path");
                    }
                    // Убираем ненужные вершины, которые ни с чем не связаны
                    dag.removeOrphanedVertices();
                }
            }
            result.addAll(secondary);
            return BinaryExpression.fromManyOperands(result.toArray(new Expression[0]), 0, ShortCircuitAndOp.class);
        }
        return expressionNode;
    }

    private static void _collectCompoundStatementElements(Expression node, List<BinaryComparison> primary, List<Expression> secondary) {
        // Точка входа в эту функцию всегда ShortCircuitOp, именно с нее начинается сбор всех частей составного сравнения
        if (node instanceof BinaryComparison op) {
            if (op instanceof NotEqOp || op instanceof EqOp || op.getLeft().equals(op.getRight())) {
                secondary.add(op);
            } else {
                primary.add(op);
            }
        } else if (node instanceof ShortCircuitAndOp op) {
            _collectCompoundStatementElements(op.getLeft(), primary, secondary);
            _collectCompoundStatementElements(op.getRight(), primary, secondary);
        } else {
            secondary.add(node);
        }
    }

    @SuppressWarnings("unchecked")
    public static MethodDefinition detectInstanceReferences(MethodDefinition def) {
        MethodDeclaration decl = (MethodDeclaration)def.getDeclaration();
        if (decl.getArguments().isEmpty() || decl.getModifiers().contains(DeclarationModifier.STATIC)) {
            return def;
        }
        SimpleIdentifier instanceName = decl.getArguments().getFirst().getName();
        CollectionFieldDescriptor newArgsDescr = (CollectionFieldDescriptor) decl.getFieldDescriptor("arguments");
        newArgsDescr.ensureWritable();
        List<? extends Node> newArgs = null;
        try {
            newArgs = newArgsDescr.asList();
        } catch (IllegalAccessException e) {
            newArgs = new ArrayList<>();
        }
        newArgs.removeFirst();

        for (NodeInfo info : def.getBody()) {
            if (info.node() instanceof SimpleIdentifier ident && ident.equals(instanceName)) {
                info.field().substitute(new SelfReference(instanceName.getName()));
            } else if (info.node() instanceof SimpleIdentifier ident && ident.getName().equals("super")) {
                info.field().substitute(new SuperClassReference());
            }
        }

        return def;
    }

    /*
    TODO:
    - convert assignment statement of first variable usage to variable declaration
     */
}
