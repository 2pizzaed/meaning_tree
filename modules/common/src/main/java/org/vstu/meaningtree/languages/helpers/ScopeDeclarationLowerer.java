package org.vstu.meaningtree.languages.helpers;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.ScopeDeclarationStatement;

/**
 * Снимает объявления {@code global} для языков с явным объявлением переменных.
 * <p>
 * В C-семействе присваивание имени, объявленного снаружи, и так уходит наружу: локальную
 * переменную там вводит объявление с типом, а не присваивание. Поэтому {@code global x}
 * ничего не добавляет к смыслу тела и записать его нечем — узел просто удаляется, а
 * привязка, которую он объявил, уже учтена таблицей областей: разбор не выпустил для
 * такого имени объявление переменной.
 * <p>
 * {@code nonlocal} этим проходом не снимается: он перенаправляет имя в промежуточную область,
 * которой в C-семействе соответствовать нечему, и молчаливое удаление изменило бы смысл
 * программы. Такой узел объявлен неподдерживаемым
 * ({@code org.vstu.meaningtree.languages.support.features.NonlocalBindingFeature}).
 * <p>
 * Проход работает на клоне входного дерева и никогда не меняет дерево вызывающего.
 */
public final class ScopeDeclarationLowerer {
    private ScopeDeclarationLowerer() {
    }

    public static MeaningTree dropGlobals(MeaningTree source) {
        if (!hasDroppableDeclarations(source)) {
            return source;
        }

        MeaningTree result = new MeaningTree(source.getRootNode().clone());
        for (NodeInfo info : result.iterate()) {
            if (!(info.node() instanceof CompoundStatement body)) {
                continue;
            }
            for (Node child : body.getNodeList()) {
                if (isDroppable(child)) {
                    body.remove(child);
                }
            }
        }
        return result;
    }

    private static boolean hasDroppableDeclarations(MeaningTree tree) {
        for (NodeInfo info : tree) {
            if (isDroppable(info.node())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDroppable(Node node) {
        return node instanceof ScopeDeclarationStatement declaration
                && declaration.getKind() == ScopeDeclarationStatement.Kind.GLOBAL;
    }
}
