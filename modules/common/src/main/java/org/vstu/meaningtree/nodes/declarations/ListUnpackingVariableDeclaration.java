package org.vstu.meaningtree.nodes.declarations;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.literals.IntegerLiteral;
import org.vstu.meaningtree.nodes.expressions.other.IndexExpression;
import org.vstu.meaningtree.nodes.interfaces.HasVariableDeclaration;

import java.util.ArrayList;
import java.util.List;

public class ListUnpackingVariableDeclaration extends Declaration implements HasVariableDeclaration {
    @TreeNode private List<Identifier> variableNames;
    @TreeNode private Type type;
    @TreeNode private Expression value;

    public ListUnpackingVariableDeclaration(Type type, Expression value, List<Identifier> variableNames) {
        this.value = value;
        this.variableNames = List.copyOf(variableNames);
    }

    public ListUnpackingVariableDeclaration(Type type, Expression value, Identifier ... variableNames) {
        this.value = value;
        this.variableNames = List.of(variableNames);
    }

    public List<Identifier> getVariableNames() {
        return variableNames;
    }

    public Expression getValue() {
        return value;
    }

    public VariableDeclaration toVariableDeclaration() {
        List<VariableDeclarator> declarators = new ArrayList<>();
        for (int i = 0; i < variableNames.size(); i++) {
            declarators.add(new VariableDeclarator(variableNames.get(i).getSimpleIdentifierOrThrow(), new IndexExpression(value, new IntegerLiteral(i))));
        }
        return new VariableDeclaration(type, declarators);
    }
}
