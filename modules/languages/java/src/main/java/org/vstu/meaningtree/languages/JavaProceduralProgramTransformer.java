package org.vstu.meaningtree.languages;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.declarations.FieldDeclaration;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.definitions.FunctionDefinition;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class JavaProceduralProgramTransformer {
    private JavaProceduralProgramTransformer() {
    }

    public static ProgramEntryPoint transform(List<Node> statements,
                                              @Nullable ClassDefinition mainClass,
                                              @Nullable FunctionDefinition mainMethod) {
        if (mainClass == null || mainMethod == null || !isHoistable(mainMethod)) {
            return new ProgramEntryPoint(statements, mainClass, mainMethod);
        }

        if (isSimpleEquivalentProgram(statements, mainClass, mainMethod)) {
            return new ProgramEntryPoint(Arrays.asList(mainMethod.getBody().getNodes()), mainMethod);
        }

        List<Node> body = new ArrayList<>();
        FunctionDefinition entryPoint = null;

        for (Node node : statements) {
            if (node != mainClass) {
                body.add(node);
                continue;
            }

            List<Node> remainingClassMembers = new ArrayList<>();
            for (Node member : mainClass.getBody().getNodes()) {
                if (isHoistable(member)) {
                    Node hoisted = hoist(member);
                    body.add(hoisted);
                    if (entryPoint == null
                            && hoisted instanceof FunctionDefinition functionDefinition
                            && functionDefinition.getName().toString().equals("main")) {
                        entryPoint = functionDefinition;
                    }
                } else {
                    remainingClassMembers.add(member.clone());
                }
            }

            if (!remainingClassMembers.isEmpty()) {
                body.add(new ClassDefinition(
                        mainClass.getDeclaration().clone(),
                        new CompoundStatement(remainingClassMembers)
                ));
            }
        }

        return new ProgramEntryPoint(body, entryPoint);
    }

    private static boolean isSimpleEquivalentProgram(List<Node> statements,
                                                     ClassDefinition mainClass,
                                                     FunctionDefinition mainMethod) {
        return statements.size() == 1
                && statements.getFirst() == mainClass
                && mainClass.getBody().getLength() == 1
                && mainClass.getBody().getNodes()[0] == mainMethod;
    }

    private static boolean isHoistable(Node member) {
        return switch (member) {
            case FunctionDefinition functionDefinition -> isHoistable(functionDefinition);
            case FieldDeclaration fieldDeclaration -> fieldDeclaration.getModifiers().contains(DeclarationModifier.STATIC);
            default -> false;
        };
    }

    private static boolean isHoistable(FunctionDefinition functionDefinition) {
        if (functionDefinition instanceof org.vstu.meaningtree.nodes.definitions.MethodDefinition methodDefinition) {
            return methodDefinition.getDeclaration().getModifiers().contains(DeclarationModifier.STATIC);
        }
        return true;
    }

    private static Node hoist(Node member) {
        if (member instanceof org.vstu.meaningtree.nodes.definitions.MethodDefinition methodDefinition) {
            return toFunctionDefinition(methodDefinition);
        }
        if (member instanceof FunctionDefinition functionDefinition) {
            return functionDefinition.clone();
        }
        return member.clone();
    }

    private static FunctionDefinition toFunctionDefinition(
            org.vstu.meaningtree.nodes.definitions.MethodDefinition methodDefinition) {
        var declaration = methodDefinition.getDeclaration();
        var functionDeclaration = new FunctionDeclaration(
                declaration.getQualifiedName().clone(),
                declaration.getReturnType().clone(),
                declaration.getAnnotations().stream().map(annotation -> annotation.clone()).toList(),
                declaration.getArguments().stream().map(JavaProceduralProgramTransformer::cloneArgument).toList()
        );
        return new FunctionDefinition(functionDeclaration, methodDefinition.getBody().clone());
    }

    private static DeclarationArgument cloneArgument(DeclarationArgument argument) {
        if (argument.isListUnpacking()) {
            return DeclarationArgument.listUnpacking(argument.getElementType().clone(), argument.getName().clone());
        }
        if (argument.isDictUnpacking()) {
            return DeclarationArgument.dictUnpacking(argument.getElementType().clone(), argument.getName().clone());
        }

        return new DeclarationArgument(
                argument.getType().clone(),
                argument.getName().clone(),
                argument.hasInitialExpression() ? argument.getInitialExpression().clone() : null
        );
    }
}
