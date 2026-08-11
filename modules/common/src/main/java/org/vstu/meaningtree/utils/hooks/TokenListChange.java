package org.vstu.meaningtree.utils.hooks;

import org.vstu.meaningtree.utils.ListModificationType;
import org.vstu.meaningtree.utils.tokens.Token;

/**
 * Описание изменения списка токенов для фазы {@link HookPhase#ON_TOKEN_LIST_CHANGE}.
 *
 * @param index позиция изменённого элемента
 * @param token сам токен
 * @param type  вид изменения
 */
public record TokenListChange(int index, Token token, ListModificationType type) {
}
