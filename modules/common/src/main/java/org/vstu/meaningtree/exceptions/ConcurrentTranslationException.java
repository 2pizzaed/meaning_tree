package org.vstu.meaningtree.exceptions;

/**
 * Транслятор занят другим потоком.
 * <p>
 * Транслятор и его компоненты (парсер, viewer, токенизатор) держат состояние текущей
 * трансляции — таблицу областей видимости, стек кадров, конструкторы тел, уровень отступа.
 * Состояние это общее на компонент, поэтому две одновременные трансляции на одном
 * трансляторе затирают друг друга: молча, с правдоподобным на вид результатом. Отсюда
 * правило: <b>один транслятор — один поток за раз</b>, а другому потоку нужен свой
 * экземпляр ({@code translator.clone()}).
 * <p>
 * Наследуется от {@link IllegalStateException}, а не от {@link MeaningTreeException},
 * намеренно: это ошибка использования библиотеки, а не разбора или рендеринга. Методы
 * {@code tryGetMeaningTree}/{@code tryGetCode} ловят {@code MeaningTreeException} и вернули
 * бы «не получилось» — то есть спрятали бы дефект вызывающего кода за штатным ответом.
 */
public class ConcurrentTranslationException extends IllegalStateException {
    public ConcurrentTranslationException(String msg) {
        super(msg);
    }
}
