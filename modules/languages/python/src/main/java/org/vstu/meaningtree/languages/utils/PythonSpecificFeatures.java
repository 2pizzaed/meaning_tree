package org.vstu.meaningtree.languages.utils;

import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.calls.MethodCall;
import org.vstu.meaningtree.nodes.expressions.identifiers.ScopedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.io.InputCommand;
import org.vstu.meaningtree.nodes.io.PrintCommand;

import java.util.ArrayList;
import java.util.List;


public class PythonSpecificFeatures {

    static SimpleIdentifier getSpecificFunctionName(FunctionCall call) {
        if (call instanceof PrintCommand) {
            return new SimpleIdentifier("print").remap(call);
        } else if (call instanceof InputCommand) {
            return new SimpleIdentifier("input").remap(call);
        }
        return null;
    }

    public static Identifier getFunctionName(FunctionCall call) {
        SimpleIdentifier specific = getSpecificFunctionName(call);
        if (specific != null) {
            return specific;
        }
        if (call instanceof MethodCall method) {
            if (method.getObject() instanceof ScopedIdentifier scoped) {
                List<SimpleIdentifier> identifierList = new ArrayList<>(scoped.getScopeResolution());
                identifierList.add((SimpleIdentifier) method.getFunctionName());
                return new ScopedIdentifier(identifierList.toArray(new SimpleIdentifier[0]))
                        .remap(method.getFunctionName());
            } else if (method.getObject() instanceof SimpleIdentifier obj) {
                return new ScopedIdentifier(obj, (SimpleIdentifier) method.getFunctionName())
                        .remap(method.getFunctionName());
            }
        }
        return call.getFunctionName();
    }

    public static Expression getFunctionExpression(FunctionCall call) {
        SimpleIdentifier specific = getSpecificFunctionName(call);
        if (specific != null) {
            return specific;
        }
        if (call instanceof MethodCall method) {
            if (method.getObject() instanceof ScopedIdentifier scoped) {
                List<SimpleIdentifier> identifierList = new ArrayList<>(scoped.getScopeResolution());
                identifierList.add((SimpleIdentifier) method.getFunctionName());
                return new ScopedIdentifier(identifierList.toArray(new SimpleIdentifier[0]))
                        .remap(method.getFunction());
            } else if (method.getObject() instanceof SimpleIdentifier obj) {
                return new ScopedIdentifier(obj, (SimpleIdentifier) method.getFunctionName())
                        .remap(method.getFunction());
            }
        }
        return call.getFunction();
    }
}
