package org.vstu.meaningtree.languages.helpers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.ClassDeclaration;
import org.vstu.meaningtree.nodes.declarations.FieldDeclaration;
import org.vstu.meaningtree.nodes.declarations.MethodDeclaration;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.definitions.GeneratorDefinition;
import org.vstu.meaningtree.nodes.definitions.IteratorDefinition;
import org.vstu.meaningtree.nodes.definitions.MethodDefinition;
import org.vstu.meaningtree.nodes.definitions.ObjectConstructorDefinition;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.calls.MethodCall;
import org.vstu.meaningtree.nodes.expressions.comparison.EqOp;
import org.vstu.meaningtree.nodes.expressions.comparison.GeOp;
import org.vstu.meaningtree.nodes.expressions.comparison.GtOp;
import org.vstu.meaningtree.nodes.expressions.comparison.LeOp;
import org.vstu.meaningtree.nodes.expressions.comparison.LtOp;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.ScopedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SelfReference;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.BoolLiteral;
import org.vstu.meaningtree.nodes.expressions.literals.IntegerLiteral;
import org.vstu.meaningtree.nodes.expressions.literals.NullLiteral;
import org.vstu.meaningtree.nodes.expressions.math.AddOp;
import org.vstu.meaningtree.nodes.expressions.math.SubOp;
import org.vstu.meaningtree.nodes.expressions.newexpr.ObjectNewExpression;
import org.vstu.meaningtree.nodes.expressions.other.MemberAccess;
import org.vstu.meaningtree.nodes.expressions.other.Range;
import org.vstu.meaningtree.nodes.interfaces.PrimitiveType;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.ReturnStatement;
import org.vstu.meaningtree.nodes.statements.YieldStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.loops.ForEachLoop;
import org.vstu.meaningtree.nodes.statements.loops.GeneralForLoop;
import org.vstu.meaningtree.nodes.statements.loops.InfiniteLoop;
import org.vstu.meaningtree.nodes.statements.loops.RangeForLoop;
import org.vstu.meaningtree.nodes.statements.loops.WhileLoop;
import org.vstu.meaningtree.nodes.types.GenericInterface;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.builtin.BooleanType;
import org.vstu.meaningtree.nodes.types.builtin.IntType;
import org.vstu.meaningtree.nodes.types.builtin.ReferenceType;
import org.vstu.meaningtree.nodes.types.user.Class;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Понижение функции-генератора в класс-итератор для языков, где генераторов нет.
 * <p>
 * Общее преобразование генератора — машина состояний по всему телу, и её результат нечитаем.
 * Вместо неё распознаются две формы, у которых есть прямой и понятный эквивалент:
 * <ul>
 *   <li><b>Цикловая.</b> Тело — пролог без выдач и один цикл, в теле которого ровно одна
 *       выдача. Точка выдачи — точка приостановки, поэтому промежуток между двумя соседними
 *       выдачами — это «хвост тела после выдачи, проверка условия, голова тела до выдачи».
 *       Отсюда: «есть ли следующий» — условие цикла, «взять следующий» — голова, вычисление
 *       выдаваемого значения, хвост и шаг цикла.</li>
 *   <li><b>Плоская.</b> Тело — пролог без выдач и дальше только выдачи. Получается счётчик
 *       состояния и цепочка ветвлений по нему; выражения по-прежнему вычисляются по одному
 *       на вызов, то есть ленивость выдаваемых значений сохраняется.</li>
 * </ul>
 * Всё остальное не переписывается: неверный перевод хуже отказа. Нераспознанный генератор
 * остаётся в дереве, и его отвергает анализ поддержки языка.
 * <p>
 * Имена протокола обхода приходят снаружи ({@link IteratorProtocol}), потому что они у каждого
 * языка свои. Сам разбор формы от языка не зависит и живёт в {@link GeneratorForm}: понижение
 * строит класс только для формы, которую тот признал, а причину отказа по той же разметке
 * называет правило поддержки языка.
 * <p>
 * <b>Чего преобразование не сохраняет:</b> генератор в исходном языке ленив целиком — его вызов
 * не выполняет ничего до первого обхода, — а у итератора пролог выполняется конструктором. Для
 * генератора с побочными эффектами в прологе меняется момент их исполнения.
 */
public final class GeneratorLowerer {
    /**
     * Имена протокола обхода в целевом языке.
     *
     * @param interfaceName  интерфейс итератора, который реализует класс. Может быть составным
     *                       именем через точку (Java: {@code java.util.Iterator}) — язык сам
     *                       решает, нужно ли имя квалифицировать
     * @param iteratorMethod метод получения итератора у коллекции (Java: {@code iterator})
     * @param hasNextMethod  метод «есть ли следующий элемент» (Java: {@code hasNext})
     * @param nextMethod     метод «взять следующий элемент» (Java: {@code next})
     */
    public record IteratorProtocol(String interfaceName,
                                   String iteratorMethod,
                                   String hasNextMethod,
                                   String nextMethod) {
    }

    private static final String STATE_FIELD = "_state";
    private static final String SOURCE_FIELD = "_source";
    private static final String VALUE_LOCAL = "_value";

    private GeneratorLowerer() {
    }

    private static Identifier interfaceIdentifier(IteratorProtocol protocol) {
        String[] parts = protocol.interfaceName().split("\\.");
        if (parts.length == 1) {
            return new SimpleIdentifier(parts[0]);
        }
        return new ScopedIdentifier(Arrays.stream(parts).map(SimpleIdentifier::new).toList());
    }

    /**
     * Заменяет распознанные генераторы на классы-итераторы, а вызовы этих генераторов — на
     * создание объекта соответствующего класса. Работает на клоне входного дерева.
     * Нераспознанные генераторы остаются на месте.
     */
    public static MeaningTree lower(@NotNull MeaningTree source, @NotNull IteratorProtocol protocol) {
        if (!containsGenerator(source)) {
            return source;
        }

        MeaningTree result = new MeaningTree(source.getRootNode().clone());

        Set<String> loweredNames = new LinkedHashSet<>();
        for (GeneratorDefinition generator : collectGenerators(result)) {
            expandDelegations(generator);
            if (GeneratorForm.unsupportedReason(generator) != null) {
                continue;
            }
            IteratorDefinition iterator = buildIterator(generator, protocol);
            loweredNames.add(generator.getName().toString());
            result.replaceAll(info -> info.node() == generator, node -> iterator);
        }

        if (!loweredNames.isEmpty()) {
            rewriteGeneratorCalls(result, loweredNames);
        }
        return result;
    }

    /* ------------------------------------------------------------------ */
    /* Разворачивание yield from                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Заменяет <code>yield from X</code> на цикл по <code>X</code>, выдающий каждый элемент.
     * Делается до разбора формы, поэтому делегирование попадает под цикловую форму наравне
     * с написанным вручную циклом.
     */
    public static void expandDelegations(@NotNull GeneratorDefinition generator) {
        List<YieldStatement> delegations = new ArrayList<>();
        for (NodeInfo info : generator) {
            if (info.node() instanceof YieldStatement yield && yield.isDelegated()) {
                delegations.add(yield);
            }
        }

        int ordinal = 1;
        for (YieldStatement delegation : delegations) {
            SimpleIdentifier item = new SimpleIdentifier("_delegated_" + ordinal++);
            ForEachLoop loop = new ForEachLoop(
                    new VariableDeclaration(new UnknownType(), item.clone()),
                    delegation.getValue().clone(),
                    new CompoundStatement(new YieldStatement(item.clone()))
            );
            remapSyntheticTree(loop, delegation);
            generator.replaceAll(info -> info.node() == delegation, node -> loop);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Построение итератора                                                */
    /* ------------------------------------------------------------------ */

    private static IteratorDefinition buildIterator(GeneratorDefinition generator,
                                                    IteratorProtocol protocol) {
        SimpleIdentifier className = new SimpleIdentifier(generator.getName().toString());
        Type elementType = generator.getElementType().clone();

        ClassDeclaration declaration = ClassDeclaration.withTypeNode(
                List.of(),
                className.clone(),
                List.of(),
                new Class(className.clone()),
                new GenericInterface(interfaceIdentifier(protocol), asObjectType(elementType))
        );

        CompoundStatement body = new Builder(generator, protocol, elementType).build(className);

        IteratorDefinition iterator = new IteratorDefinition(
                declaration, body, elementType.clone(),
                new SimpleIdentifier(protocol.hasNextMethod()),
                new SimpleIdentifier(protocol.nextMethod())
        );
        return remapSyntheticTree(iterator, generator);
    }

    /**
     * Сборка тела класса: поля состояния, конструктор и два метода протокола.
     * Все обращения к полям записываются через явную ссылку на объект, потому что в части
     * языков поле от локальной переменной иначе неотличимо.
     */
    private static final class Builder {
        private final GeneratorDefinition generator;
        private final IteratorProtocol protocol;
        private final Type elementType;

        private final List<Node> fields = new ArrayList<>();
        private final List<Node> constructorBody = new ArrayList<>();
        private final Set<String> fieldNames = new LinkedHashSet<>();

        private Builder(GeneratorDefinition generator, IteratorProtocol protocol, Type elementType) {
            this.generator = generator;
            this.protocol = protocol;
            this.elementType = elementType;
        }

        private CompoundStatement build(SimpleIdentifier className) {
            Node[] statements = generator.getBody().getNodes();
            int firstYieldingIndex = 0;
            while (GeneratorForm.countYields(statements[firstYieldingIndex]) == 0) {
                firstYieldingIndex++;
            }

            for (var argument : generator.getDeclaration().getArguments()) {
                addField(argument.getType().clone(), argument.getName(), null);
            }
            for (int i = 0; i < firstYieldingIndex; i++) {
                addPrologueStatement(statements[i]);
            }

            Expression hasNextCondition;
            List<Node> nextBody;
            if (statements[firstYieldingIndex] instanceof Loop loop) {
                hasNextCondition = buildLoopState(loop);
                nextBody = buildLoopNextBody(loop);
            } else {
                hasNextCondition = buildFlatState(statements, firstYieldingIndex);
                nextBody = buildFlatNextBody(statements, firstYieldingIndex);
            }

            // Обращения к состоянию записываются через явную ссылку на объект: в части языков
            // поле от локальной переменной иначе неотличимо. Присваивания параметров в поля
            // строятся после этого прохода — их правая часть должна остаться параметром
            constructorBody.forEach(this::qualifyFieldReferences);
            nextBody.forEach(this::qualifyFieldReferences);
            // Условие может само оказаться чтением поля (`while running:`), поэтому оно
            // заменяется целиком, а не правится внутри
            hasNextCondition = hasNextCondition instanceof SimpleIdentifier identifier
                    && fieldNames.contains(identifier.getName())
                    ? fieldAccess(identifier)
                    : qualifyFieldReferences(hasNextCondition);

            List<Node> constructorStatements = new ArrayList<>();
            for (var argument : generator.getDeclaration().getArguments()) {
                constructorStatements.add(new AssignmentStatement(
                        fieldAccess(argument.getName()), argument.getName().clone()));
            }
            constructorStatements.addAll(constructorBody);

            List<Node> members = new ArrayList<>(fields);
            members.add(makeConstructor(className, constructorStatements));
            members.add(makeMethod(className, protocol.hasNextMethod(), new BooleanType(),
                    List.of(new ReturnStatement(hasNextCondition))));
            members.add(makeMethod(className, protocol.nextMethod(), asObjectType(elementType), nextBody));
            return new CompoundStatement(members);
        }

        /* --- поля и пролог --- */

        private void addField(Type type, SimpleIdentifier name, @Nullable Expression initial) {
            fields.add(new FieldDeclaration(type, name.clone(), List.of(DeclarationModifier.PRIVATE)));
            fieldNames.add(name.getName());
            if (initial != null) {
                constructorBody.add(new AssignmentStatement(fieldAccess(name), initial));
            }
        }

        private void addPrologueStatement(Node statement) {
            if (statement instanceof VariableDeclaration declaration) {
                for (VariableDeclarator declarator : declaration.getDeclarators()) {
                    addField(declaration.getType().clone(), declarator.getIdentifier(),
                            declarator.hasInitialization() ? declarator.getRValue().clone() : null);
                }
                return;
            }
            constructorBody.add(statement.clone());
        }

        /* --- цикловая форма --- */

        private Expression buildLoopState(Loop loop) {
            if (loop instanceof InfiniteLoop) {
                return new BoolLiteral(true);
            }
            if (loop instanceof WhileLoop whileLoop) {
                return whileLoop.getCondition().clone();
            }
            if (loop instanceof RangeForLoop rangeLoop) {
                addField(new IntType(), rangeLoop.getIdentifier(), rangeLoop.getStart().clone());
                return rangeCondition(rangeLoop);
            }
            if (loop instanceof GeneralForLoop forLoop) {
                if (forLoop.hasInitializer()) {
                    addPrologueStatement(forLoop.getInitializer());
                }
                return forLoop.hasCondition() ? forLoop.getCondition().clone() : new BoolLiteral(true);
            }
            if (loop instanceof ForEachLoop forEach) {
                // Обход коллекции выражается вложенным итератором: у класса-итератора нет
                // состояния «текущая позиция цикла», в котором обход можно приостановить
                SimpleIdentifier source = new SimpleIdentifier(SOURCE_FIELD);
                fields.add(new FieldDeclaration(
                        new GenericInterface(interfaceIdentifier(protocol),
                                asObjectType(forEach.getItem().getType())),
                        source.clone(),
                        List.of(DeclarationModifier.PRIVATE)));
                fieldNames.add(SOURCE_FIELD);
                constructorBody.add(new AssignmentStatement(
                        fieldAccess(source),
                        new MethodCall(forEach.getExpression().clone(),
                                new SimpleIdentifier(protocol.iteratorMethod()))));
                return new MethodCall(fieldAccess(source), new SimpleIdentifier(protocol.hasNextMethod()));
            }
            throw new IllegalStateException("Unrecognized loop reached lowering: " + loop.getClass().getName());
        }

        private List<Node> buildLoopNextBody(Loop loop) {
            Node[] statements = GeneratorForm.asCompound(loop.getBody()).getNodes();
            int yieldIndex = 0;
            while (!(statements[yieldIndex] instanceof YieldStatement)) {
                yieldIndex++;
            }

            List<Node> result = new ArrayList<>();
            if (loop instanceof ForEachLoop forEach) {
                VariableDeclaration item = forEach.getItem();
                result.add(new VariableDeclaration(
                        item.getType().clone(),
                        item.getFirstDeclarator().getIdentifier().clone(),
                        new MethodCall(fieldAccess(new SimpleIdentifier(SOURCE_FIELD)),
                                new SimpleIdentifier(protocol.nextMethod()))));
            }
            for (int i = 0; i < yieldIndex; i++) {
                result.add(statements[i].clone());
            }

            // Значение снимается во временную переменную: хвост тела и шаг цикла выполняются
            // до возврата и могут изменить всё, из чего оно вычислено
            SimpleIdentifier value = new SimpleIdentifier(VALUE_LOCAL);
            result.add(new VariableDeclaration(elementType.clone(), value.clone(),
                    ((YieldStatement) statements[yieldIndex]).getValue().clone()));

            for (int i = yieldIndex + 1; i < statements.length; i++) {
                result.add(statements[i].clone());
            }
            advance(loop).ifPresent(result::add);
            result.add(new ReturnStatement(value.clone()));
            return result;
        }

        private Optional<Node> advance(Loop loop) {
            if (loop instanceof RangeForLoop rangeLoop) {
                Expression step = rangeLoop.getStep() == null
                        ? new IntegerLiteral("1") : rangeLoop.getStep().clone();
                Expression advanced = rangeLoop.getRangeType() == Range.Direction.DOWN
                        ? new SubOp(fieldAccess(rangeLoop.getIdentifier()), step)
                        : new AddOp(fieldAccess(rangeLoop.getIdentifier()), step);
                return Optional.of(new AssignmentStatement(fieldAccess(rangeLoop.getIdentifier()), advanced));
            }
            if (loop instanceof GeneralForLoop forLoop && forLoop.hasUpdate()) {
                return Optional.of(new ExpressionStatement(forLoop.getUpdate().clone()));
            }
            return Optional.empty();
        }

        private Expression rangeCondition(RangeForLoop loop) {
            Expression current = fieldAccess(loop.getIdentifier());
            Expression stop = loop.getStop().clone();
            boolean excluding = loop.getRange().isExcludingEnd();
            if (loop.getRangeType() == Range.Direction.DOWN) {
                return excluding ? new GtOp(current, stop) : new GeOp(current, stop);
            }
            return excluding ? new LtOp(current, stop) : new LeOp(current, stop);
        }

        /* --- плоская форма --- */

        private Expression buildFlatState(Node[] statements, int firstYieldIndex) {
            addField(new IntType(), new SimpleIdentifier(STATE_FIELD), new IntegerLiteral("0"));
            int yields = statements.length - firstYieldIndex;
            return new LtOp(fieldAccess(new SimpleIdentifier(STATE_FIELD)), new IntegerLiteral(Integer.toString(yields)));
        }

        private List<Node> buildFlatNextBody(Node[] statements, int firstYieldIndex) {
            SimpleIdentifier state = new SimpleIdentifier(STATE_FIELD);
            List<Node> result = new ArrayList<>();
            for (int i = firstYieldIndex; i < statements.length; i++) {
                int ordinal = i - firstYieldIndex;
                CompoundStatement branch = new CompoundStatement(
                        new AssignmentStatement(fieldAccess(state), new IntegerLiteral(Integer.toString(ordinal + 1))),
                        new ReturnStatement(((YieldStatement) statements[i]).getValue().clone())
                );
                result.add(new IfStatement(new EqOp(fieldAccess(state), new IntegerLiteral(Integer.toString(ordinal))), branch));
            }
            result.add(new ReturnStatement(new NullLiteral()));
            return result;
        }

        /* --- члены класса --- */

        private ObjectConstructorDefinition makeConstructor(SimpleIdentifier className, List<Node> body) {
            return new ObjectConstructorDefinition(
                    new Class(className.clone()), className.clone(), List.of(),
                    List.of(DeclarationModifier.PUBLIC),
                    generator.getDeclaration().getArguments().stream().map(argument -> argument.clone()).toList(),
                    new CompoundStatement(new ArrayList<>(body))
            );
        }

        /**
         * Заменяет упоминания имён полей в поддереве на обращение через ссылку на объект.
         * Пропускаются позиции, где идентификатор — не чтение переменной: имя члена в доступе
         * к члену, имя вызываемой функции, имя в объявлении.
         */
        private <T extends Node> T qualifyFieldReferences(T root) {
            root.replaceAll(
                    info -> info.node() instanceof SimpleIdentifier identifier
                            && !(identifier instanceof SelfReference)
                            && fieldNames.contains(identifier.getName())
                            && isReadPosition(info),
                    node -> fieldAccess((SimpleIdentifier) node)
            );
            return root;
        }

        private boolean isReadPosition(NodeInfo info) {
            if (info.field() == null || info.parentNode() == null) {
                return false;
            }
            String slot = info.field().getName();
            return switch (info.parentNode()) {
                case MemberAccess ignored -> !slot.equals("member");
                case FunctionCall ignored -> !slot.equals("function");
                case VariableDeclarator ignored -> !slot.equals("identifier");
                default -> !slot.equals("name");
            };
        }

        private MethodDefinition makeMethod(SimpleIdentifier className, String name,
                                            Type returnType, List<Node> body) {
            MethodDeclaration declaration = new MethodDeclaration(
                    new Class(className.clone()), new SimpleIdentifier(name), returnType,
                    List.of(), List.of(DeclarationModifier.PUBLIC));
            return new MethodDefinition(declaration, new CompoundStatement(new ArrayList<>(body)));
        }

        private Expression fieldAccess(SimpleIdentifier name) {
            return new MemberAccess(new SelfReference("this"), name.clone());
        }
    }

    /* ------------------------------------------------------------------ */
    /* Вызовы генератора                                                   */
    /* ------------------------------------------------------------------ */

    private static void rewriteGeneratorCalls(MeaningTree tree, Set<String> generatorNames) {
        tree.replaceAll(
                info -> info.node() instanceof FunctionCall call
                        && !(call instanceof MethodCall)
                        && call.getFunctionName() instanceof SimpleIdentifier name
                        && generatorNames.contains(name.getName()),
                node -> {
                    FunctionCall call = (FunctionCall) node;
                    SimpleIdentifier name = (SimpleIdentifier) call.getFunctionName();
                    ObjectNewExpression created = new ObjectNewExpression(
                            new Class(name.clone()),
                            call.getArguments().stream().map(Expression::clone).toList()
                    );
                    return remapSyntheticTree(created, call);
                }
        );
    }

    /* ------------------------------------------------------------------ */
    /* Утилиты                                                             */
    /* ------------------------------------------------------------------ */

    private static boolean containsGenerator(MeaningTree tree) {
        for (NodeInfo info : tree) {
            if (info.node() instanceof GeneratorDefinition) {
                return true;
            }
        }
        return false;
    }

    private static List<GeneratorDefinition> collectGenerators(MeaningTree tree) {
        List<GeneratorDefinition> generators = new ArrayList<>();
        for (NodeInfo info : tree) {
            if (info.node() instanceof GeneratorDefinition generator) {
                generators.add(generator);
            }
        }
        return generators;
    }

    /**
     * Тип элемента в позиции, где нужен объектный тип: параметр обобщённого типа и возврат
     * метода протокола. Примитив заворачивается в ссылочный тип — языки, различающие примитив
     * и объект, отрисовывают его типом-обёрткой.
     */
    private static Type asObjectType(Type type) {
        return type instanceof PrimitiveType ? new ReferenceType(type.clone()) : type.clone();
    }

    private static <T extends Node> T remapSyntheticTree(T node, Node origin) {
        node.remap(origin);
        for (NodeInfo info : node) {
            info.node().remap(origin);
        }
        return node;
    }
}
