package org.vstu.meaningtree.iterators.utils;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Помечает поле {@code Node} как неструктурную ссылку, а не как дочерний узел дерева.
 * <p>
 * Целевой узел уже может быть достижим из другого места дерева MeaningTree, поэтому обход этого поля
 * способен превратить дерево в граф. В отличие от {@link TreeNode}, итераторы дерева не должны
 * переходить по такой ссылке. Если ссылка встраивается в JSON как полный узел, имя её поля должно
 * оканчиваться на {@code _ref}; ссылки, сериализуемые только как идентификаторы, сохраняют суффикс
 * {@code _id} или {@code _ids}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NodeReference {
}
