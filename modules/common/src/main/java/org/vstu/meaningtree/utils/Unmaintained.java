package org.vstu.meaningtree.utils;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE,
        ElementType.PACKAGE, ElementType.MODULE, ElementType.RECORD_COMPONENT,
        ElementType.CONSTRUCTOR
})
public @interface Unmaintained {
    String value() default "This API is not maintained by project maintainers and should be excluded from compilation where possible";
}
