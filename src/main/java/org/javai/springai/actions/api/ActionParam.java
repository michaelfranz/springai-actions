package org.javai.springai.actions.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ActionParam {

	/**
	 * Human-readable description of the parameter, used to guide the LLM
	 * when constructing an steps in the final plan.
	 */
	String description() default "";
}
