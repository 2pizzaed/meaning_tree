package org.vstu.meaningtree.languages.helpers;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.DeleteStatement;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.nodes.statements.ResourceContextStatement;
import org.vstu.meaningtree.nodes.statements.exceptions.ExceptionCatchStatement;

import java.util.ArrayList;
import java.util.List;

/**
 * Снимает владение ресурсами для языков, где java-try-with-resources записать нечем.
 * <p>
 * {@link #nest} нужен языку, у которого своя конструкция владения есть, но с {@code catch}
 * она не совмещается (python-{@code with}): ресурсы уходят из {@link ExceptionCatchStatement}
 * во вложенный {@link ResourceContextStatement}, то есть {@code with} внутрь {@code try}.
 * <p>
 * {@link #flatten} нужен языку, где такой конструкции нет вовсе (C++): владение
 * разворачивается в плоский блок — объявления, тело, освобождение каждого именованного
 * ресурса в обратном порядке. У {@code try} ресурсы вносятся внутрь его тела, а не перед
 * конструкцией: в Java ошибку захвата ресурса ловят те же ветви, что и ошибки тела.
 * <p>
 * Оба прохода работают на клоне входного дерева и никогда не меняют дерево вызывающего.
 */
public final class ResourceContextLowerer {
    private ResourceContextLowerer() {
    }

    public static MeaningTree nest(MeaningTree source) {
        if (!hasOwnedResources(source)) {
            return source;
        }

        MeaningTree result = new MeaningTree(source.getRootNode().clone());
        for (ExceptionCatchStatement stmt : collectCatchesWithResources(result)) {
            List<Node> resources = stmt.getResourceDeclarations();
            stmt.setResourceDeclarations(List.of());

            ResourceContextStatement context = new ResourceContextStatement(resources, stmt.getBody());
            context.remap(stmt);
            // Тело оборачивается в составной оператор: языки с блочным синтаксисом не умеют
            // отступать одиночный оператор в этой позиции
            stmt.setBody(context);
            stmt.makeCompoundBody().remap(stmt);
        }
        return result;
    }

    public static MeaningTree flatten(MeaningTree source) {
        if (!hasOwnedResources(source)) {
            return source;
        }

        MeaningTree result = new MeaningTree(source.getRootNode().clone());

        for (ExceptionCatchStatement stmt : collectCatchesWithResources(result)) {
            List<Node> resources = stmt.getResourceDeclarations();
            stmt.setResourceDeclarations(List.of());

            CompoundStatement body = stmt.makeCompoundBody();
            body.remap(stmt);
            int index = 0;
            for (Node acquisition : acquisitions(resources)) {
                body.insert(index++, acquisition);
            }
            for (Node release : releases(resources, stmt)) {
                body.insert(body.getLength(), release);
            }
        }

        // Замена идёт через общий механизм, а не поиском узла в составном теле: `with` может
        // стоять и там, где составного тела нет вовсе — телом ветви или самим корнем дерева
        result.replaceAll(
                info -> info.node() instanceof ResourceContextStatement,
                node -> flattenContext((ResourceContextStatement) node)
        );
        return result;
    }

    private static CompoundStatement flattenContext(ResourceContextStatement stmt) {
        List<Node> resources = stmt.getResourceDeclarations();

        List<Node> nodes = new ArrayList<>(acquisitions(resources));
        // Тело разворачивается, а не вкладывается: лишний блок внутри блока ничего не
        // ограничивает, потому что объявления ресурсов и так лежат снаружи него
        if (stmt.getBody() instanceof CompoundStatement compound) {
            nodes.addAll(compound.getNodeList());
        } else {
            nodes.add(stmt.getBody());
        }
        nodes.addAll(releases(resources, stmt));

        CompoundStatement block = new CompoundStatement(nodes);
        block.remap(stmt);
        return block;
    }

    /**
     * Захват ресурса: объявление переходит в блок как есть, а ресурс без имени становится
     * обычным оператором-выражением — вычислить его всё равно надо.
     */
    private static List<Node> acquisitions(List<Node> resources) {
        List<Node> acquisitions = new ArrayList<>();
        for (Node resource : resources) {
            acquisitions.add(resource instanceof Expression expression
                    ? new ExpressionStatement(expression).remapTree(resource)
                    : resource);
        }
        return acquisitions;
    }

    /**
     * Освобождение — в обратном порядке захвата, как в языках-источниках. У ресурса без
     * имени освобождать нечего: обратиться к нему после тела уже не по чему.
     */
    private static List<Statement> releases(List<Node> resources, Node origin) {
        List<Statement> releases = new ArrayList<>();
        for (Node resource : resources.reversed()) {
            SimpleIdentifier name = ResourceContextStatement.resourceName(resource);
            if (name == null) {
                continue;
            }
            // freshClone, а не clone: обычный clone сохраняет id, и имя в delete совпало бы
            // по id с именем в объявлении, дав в дереве повторяющиеся id
            releases.add(new DeleteStatement((SimpleIdentifier) name.freshClone()).remapTree(origin));
        }
        return releases;
    }

    /**
     * Дерево без владения ресурсами возвращается как есть — не только ради экономии:
     * клон дерева, в котором один узел достижим из двух мест, разводит его в два экземпляра
     * с одинаковым id, и {@code MeaningTree.makeIndex} на таком дереве падает.
     */
    private static boolean hasOwnedResources(MeaningTree tree) {
        for (NodeInfo info : tree) {
            if (info.node() instanceof ResourceContextStatement) {
                return true;
            }
            if (info.node() instanceof ExceptionCatchStatement stmt && stmt.hasResourceDeclarations()) {
                return true;
            }
        }
        return false;
    }

    private static List<ExceptionCatchStatement> collectCatchesWithResources(MeaningTree tree) {
        List<ExceptionCatchStatement> found = new ArrayList<>();
        for (NodeInfo info : tree) {
            if (info.node() instanceof ExceptionCatchStatement stmt && stmt.hasResourceDeclarations()) {
                found.add(stmt);
            }
        }
        return found;
    }
}
