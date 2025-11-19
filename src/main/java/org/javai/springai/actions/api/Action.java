package org.javai.springai.actions.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an executable action in the planning/execution DSL.
 * <p>
 * Example:
 *
 * <pre>
 * {@code
 * @Action(
 *     description = "Send a welcome email",
 *     affinity = "email:{recipient}",
 *     mutability = Mutability.MUTATE,
 *     produces = "emailText",
 *     contextKey = "emailText"
 * )
 * public String sendEmail(String recipient, String body) {
 *     ...
 * }
 * }
 * </pre>
 *
 * The annotation captures:
 * <ul>
 * <li>An optional affinity template (or list via {@link #affinities()}) to route
 * actions to the right serialized queue, e.g. {@code "customer:{customerId}"}.</li>
 * <li>Execution cost, description, and mutability hints for schedulers.</li>
 * <li>Context keys the action writes via {@link #contextKey()} or
 * {@link #additionalContextKeys()}.</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Action {

	/**
	 * Template describing a single affinity lane such as {@code "tenant:{id}"}.
	 * Mutually exclusive with {@link #affinities()}.
	 */
	String affinity() default "";

	/**
	 * Alternative to {@link #affinity()} when multiple lanes should be tagged,
	 * e.g. {@code {"tenant:{tenantId}", "customer:{customerId}"}}.
	 */
	String[] affinities() default {};

	/**
	 * Relative cost estimate used by schedulers when competing actions exist.
	 */
	int cost() default 1;

	/**
	 * Human-readable purpose of the action, forwarded to planner prompts.
	 */
	String description() default "";

	/**
	 * Context key where the return value should be stored. Useful for simple
	 * actions that output a single value, e.g. {@code "emailText"}.
	 */
	String contextKey() default "";

	/**
	 * Additional context keys produced by this action, allowing the planner to
	 * build dependencies, e.g. {@code {"profile", "orderSummary"}}.
	 */
	String[] additionalContextKeys() default {};

	/**
	 * Indicates whether the action is read-only, creates data, or mutates existing
	 * resources. The default value is {@link Mutability#MUTATE}, which safest but least optimal default.
	 */
	Mutability mutability() default Mutability.MUTATE;
}
