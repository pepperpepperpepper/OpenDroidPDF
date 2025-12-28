package android.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Minimal stub of the legacy android.test annotation used by the deprecated UIAutomator v1 runner.
 * Some modern system images omit android.test.base.jar, but the runner still references this symbol.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface RepetitiveTest {
    int numIterations() default 1;
}

