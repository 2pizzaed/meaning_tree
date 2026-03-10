package org.vstu.meaningtree.utils;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface InternalNode {
    /**
     * Данная аннотаиця указывает на то, что данный типа узла - вспомогательный (чаще всего вложен в другой, более значимый) и исключен из проверки как фича языка
     */
}
