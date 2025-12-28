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

	/**
	 * Explicit whitelist of allowed values (case sensitivity controlled by {@link #caseInsensitive()}).
	 */
	String[] allowedValues() default {};

	/**
	 * Optional regex pattern that the value must match.
	 */
	String allowedRegex() default "";

	/**
	 * Whether value matching (allowedValues/regex) should be case-insensitive.
	 */
	boolean caseInsensitive() default false;

	/**
	 * Example values for this parameter. Used for generating example plans in system prompts.
	 * If provided, the first example will be used in auto-generated plan examples.
	 */
	String[] examples() default {};
}
