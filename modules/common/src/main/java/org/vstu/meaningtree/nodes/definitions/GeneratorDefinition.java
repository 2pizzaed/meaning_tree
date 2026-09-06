package org.vstu.meaningtree.nodes.definitions;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;

/**
 * Функция-генератор: функция, тело которой выдаёт значения оператором
 * {@link org.vstu.meaningtree.nodes.statements.YieldStatement}, а вызов возвращает ленивую
 * последовательность.
 * <p>
 * Отличается от {@link FunctionDefinition} только этим смыслом, поэтому наследует его, а не
 * заводит собственную структуру. Новых полей нет: {@link FunctionDeclaration#getReturnType()}
 * хранит <b>тип элемента</b> последовательности, а не тип самого генератора. Языки, у которых
 * генератор — отдельный тип (<code>Iterator[T]</code> в python), разворачивают эту обёртку при
 * разборе и надевают обратно при отрисовке; языкам без генераторов тип элемента нужен как есть,
 * чтобы получился <code>Iterator&lt;T&gt;</code>.
 * <p>
 * Генератор-метод не выражается: {@link MethodDefinition} наследует тот же
 * {@link FunctionDefinition}, и при одиночном наследовании «генератор» и «метод» одним классом
 * не совмещаются. Понижение генератора в класс-итератор всё равно ограничено функциями уровня
 * модуля, потому что для метода пришлось бы протаскивать приёмник вызова в конструктор.
 */
public class GeneratorDefinition extends FunctionDefinition {
    public GeneratorDefinition(@NotNull FunctionDeclaration declaration, @NotNull CompoundStatement body) {
        super(declaration, body);
    }

    /**
     * Тип выдаваемого элемента. Это и есть тип возврата декларации — см. описание класса.
     */
    public Type getElementType() {
        return getDeclaration().getReturnType();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GeneratorDefinition)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public GeneratorDefinition clone() {
        return (GeneratorDefinition) super.clone();
    }
}
