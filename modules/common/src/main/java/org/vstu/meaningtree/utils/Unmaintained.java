package org.vstu.meaningtree.utils;

import java.lang.annotation.*;

/**
 * Данная аннотация нужна для разработчиков, читающих и поддерживающих код, который временно не обслуживается, либо нуждается в новом мейнтейнере.
 * Не исключено, что в будущем он станет Deprecated
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE,
        ElementType.PACKAGE, ElementType.MODULE, ElementType.RECORD_COMPONENT,
        ElementType.CONSTRUCTOR
})
public @interface Unmaintained {
    String value() default "This API is not maintained by project maintainers and should be excluded from compilation where possible";
}
