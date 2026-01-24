package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.ParenthesesFiller;
import org.vstu.meaningtree.utils.tokens.OperatorToken;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * ПРЕДУПРЕЖДЕНИЕ: Не используйте в реализациях языков перегрузку toString для добавления отображения каждого узла
 * Это сломает многие фичи, т.к. toString(Node node) не только перенаправляет запросы к специальному методу,
 * но и добавляет логику хуков!
 */
abstract public class LanguageViewer extends TranslatorComponent {
    protected MeaningTree origin;
    protected ParenthesesFiller parenFiller;

    private List<BiFunction<Node, String, String>> postProcessFunctions = new ArrayList<>();

    public LanguageViewer(LanguageTranslator translator) {
        super(translator);
        this.parenFiller = new ParenthesesFiller(this::mapToToken);
    }

    public boolean registerPostprocessFunction(BiFunction<Node, String, String> function) {
        return this.postProcessFunctions.add(function);
    }

    public boolean removePostprocessFunction(BiFunction<Node, String, String> function) {
        return this.postProcessFunctions.remove(function);
    }

    protected abstract String formString(Node node);

    protected String applyHooks(Node node, String result) {
        for (var function : postProcessFunctions) {
            result = function.apply(node, result);
        }
        return result;
    }

    public final String toString(Node node) {
        var result = formString(node);
        return applyHooks(node, result);
    }

    public abstract OperatorToken mapToToken(Expression expr);

    public String toString(MeaningTree mt) {
        origin = mt;
        return toString(mt.getRootNode());
    }
}
