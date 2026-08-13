package org.vstu.meaningtree.languages.support.features;

import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.SemanticFeature;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.types.builtin.PointerType;
import org.vstu.meaningtree.nodes.types.builtin.ReferenceType;

/**
 * Константный тип в сигнатуре функции: у аргумента (const int& a) или у возвращаемого значения (const int& f()).
 * В отличие от константного объявления переменной, такую константность нельзя выразить
 * в языках, где неизменяемость относится к самой переменной, а не к типу.
 */
public class ConstInFunctionSignatureFeature extends SemanticFeature {
    @Override
    public String id() {
        return "feature-const-in-function-signature";
    }

    @Override
    public boolean matches(Node node, FeatureContext featureContext) {
        return getConstType(node) != null;
    }

    @Override
    public String description(Node node) {
        if (node instanceof DeclarationArgument argument) {
            return "Constant type of function argument %s is not supported".formatted(argument.getName());
        }
        return "Constant return type of function %s is not supported".formatted(((FunctionDeclaration) node).getName());
    }

    private Type getConstType(Node node) {
        Type type = switch (node) {
            case DeclarationArgument argument -> argument.getType();
            case FunctionDeclaration declaration -> declaration.getReturnType();
            default -> null;
        };
        // const может стоять как на самой обёртке (int* const), так и на её цели (const int&)
        while (type != null) {
            if (type.isConst()) {
                return type;
            }
            type = switch (type) {
                case ReferenceType reference -> reference.getTargetType();
                case PointerType pointer -> pointer.getTargetType();
                default -> null;
            };
        }
        return null;
    }
}
