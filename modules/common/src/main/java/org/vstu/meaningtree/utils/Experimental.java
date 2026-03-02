package org.vstu.meaningtree.utils;

import java.lang.annotation.*;

/**
 * Данная аннотация указывает пользователям библиотеки, что функциональность - экспериментальная
 * В будущем, она может измениться, исчезнуть, или она не является достаточно отлаженной/покрытой тестами
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE,
        ElementType.PACKAGE, ElementType.MODULE, ElementType.RECORD_COMPONENT,
        ElementType.CONSTRUCTOR
})
public @interface Experimental {
    String value() default "This is an experimental feature and may change or be removed in future versions";
}
