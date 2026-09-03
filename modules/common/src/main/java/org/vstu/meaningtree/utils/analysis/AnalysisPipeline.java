package org.vstu.meaningtree.utils.analysis;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.languages.LanguageBehavior;
import org.vstu.meaningtree.languages.LanguageTranslator;
import org.vstu.meaningtree.utils.analysis.expressions.ExpressionValueEvaluator;
import org.vstu.meaningtree.utils.analysis.imports.ImportResolver;
import org.vstu.meaningtree.utils.analysis.loops.LoopIterationAnalyzer;
import org.vstu.meaningtree.utils.analysis.symbols.OverloadCallResolver;
import org.vstu.meaningtree.utils.analysis.symbols.OverloadIndexer;
import org.vstu.meaningtree.utils.analysis.symbols.OverrideResolver;
import org.vstu.meaningtree.utils.analysis.symbols.SymbolResolver;
import org.vstu.meaningtree.utils.analysis.types.conversion.TypeConversionAnalyzer;
import org.vstu.meaningtree.utils.analysis.types.conversion.TypeConversionReport;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.Objects;
import java.util.Optional;

/**
 * Конвейер анализа как самостоятельная вызываемая единица.
 * <p>
 * Раньше конвейер был зашит в {@code LanguageParser}: единственная точка входа — только через
 * разбор исходника, единственный источник {@code ScopeTable} — контекст парсера. Здесь тот же
 * конвейер принимает {@link MeaningTree} и {@link ScopeTable} явными аргументами, поэтому его
 * можно запустить на дереве, собранном программно, десериализованном из JSON, или доработать
 * задним числом после правки дерева. {@code LanguageParser} — один из вызывающих, а не
 * единственный вход.
 * <p>
 * Языковые правила (перегрузки, преобразования типов, резолвинг импортов, контекст проекта)
 * конвейер берёт у {@link LanguageTranslator}, а не принимает по отдельности: транслятор — уже
 * существующая точка, где эти правила для языка собраны вместе, и он же знает, задан ли контекст
 * проекта. Вызывающему не нужно самому решать, звать ли резолвинг импортов отдельно — это часть
 * {@link #run()}.
 * <p>
 * Порядок проходов задан зависимостями по данным, но единой цепочки они не образуют.
 * {@code OverrideResolver} заполняет {@code overriddenFrom} и не зависит ни от кого. Отдельную
 * цепочку образуют {@code SymbolResolver} (дописывает типы полей, найденных по присваиваниям
 * вида {@code self.x = ...}), {@code OverloadIndexer} (наполняет {@code ScopeTable} членами
 * типов и группами перегрузок), {@code OverloadCallResolver} и {@code TypeConversionAnalyzer} —
 * здесь каждому следующему нужен полный результат предыдущего по всему дереву. Третья ветвь —
 * {@code ExpressionValueEvaluator} и питающийся его оценками {@code LoopIterationAnalyzer}.
 * <p>
 * Резолвинг импортов стоит особняком: это единственный проход с внешней зависимостью — файловой
 * системой, а не только данными дерева. Поэтому его можно не выполнять, не трогая остального:
 * см. {@link #run(boolean)}. Без контекста проекта ({@code translator.hasSourceContext()}) или
 * без резолвера для языка он и так не выполняется — штатное состояние для перевода одиночного
 * файла.
 * <p>
 * Конвейер идемпотентен: повторный {@code run()} на том же дереве и той же таблице даёт тот же
 * результат, а не дописывает к прежнему. Производные индексы таблицы пересоздаются
 * ({@code ScopeTable.clearAnalysisIndexes()}), а связи на узлах — {@code overriddenFrom},
 * {@code resolvedDeclaration} — переприсваиваются целиком, включая обнуление исчезнувших. Это то,
 * ради чего конвейер и вынесен из разбора: дерево можно доработать и проанализировать заново.
 */
public final class AnalysisPipeline {
    private final MeaningTree tree;
    private final ScopeTable scope;
    private final LanguageTranslator translator;

    private TypeConversionReport typeConversionReport;

    public AnalysisPipeline(MeaningTree tree, ScopeTable scope, LanguageTranslator translator) {
        this.tree = Objects.requireNonNull(tree, "tree must not be null");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.translator = Objects.requireNonNull(translator, "translator must not be null");
    }

    /** Запускает все проходы анализа, включая резолвинг импортов. */
    public AnalysisPipeline run() {
        return run(true);
    }

    /**
     * @param withImports выполнять ли резолвинг импортов — единственный проход, который читает
     *                    файловую систему. Отключается, когда нужен только анализ дерева:
     *                    например, отчёт о преобразованиях типов не должен стоить обхода
     *                    каталогов проекта
     */
    public AnalysisPipeline run(boolean withImports) {
        // Правила берутся один раз: все проходы обязаны описывать один и тот же язык, а
        // повторный опрос транслятора этого не гарантирует и ничего не даёт взамен
        LanguageBehavior behavior = translator.getLanguageBehavior();

        new OverrideResolver(tree, scope).resolve();
        new SymbolResolver(tree, scope).resolve();
        new OverloadIndexer(tree, scope, behavior.overloadSemantics()).index();
        TypeConversionAnalyzer typeConversionAnalyzer = new TypeConversionAnalyzer(
                behavior.typeConversionSemantics(), behavior.overloadSemantics());
        new OverloadCallResolver(tree, scope, typeConversionAnalyzer, behavior.overloadSemantics())
                .resolveAll();
        typeConversionReport = typeConversionAnalyzer.analyze(tree, scope);
        ExpressionValueEvaluator evaluator = new ExpressionValueEvaluator(tree, scope);
        evaluator.analyze();
        new LoopIterationAnalyzer().analyze(tree, evaluator);
        if (withImports) {
            resolveImports();
        }
        return this;
    }

    private void resolveImports() {
        ImportResolver resolver = translator.getImportResolver();
        if (resolver == null || !translator.hasSourceContext()) {
            return;
        }
        resolver.resolve(
                tree,
                translator.getProjectRootPath().orElseThrow(),
                translator.getCurrentFileRelPath().orElseThrow()
        );
    }

    public Optional<TypeConversionReport> getTypeConversionReport() {
        return Optional.ofNullable(typeConversionReport);
    }
}
