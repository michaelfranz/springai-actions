package org.javai.springai.actions.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.javai.springai.actions.execution.Mutability;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Action {
	String affinity() default "";
	int cost() default 1;
	String description() default "";
	String contextKey() default "";
	Mutability mutability() default Mutability.MUTATE; // Safest but least optimal default
}
