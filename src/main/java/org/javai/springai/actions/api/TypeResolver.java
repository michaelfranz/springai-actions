package org.javai.springai.actions.api;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves raw LLM output to a specific Java type.
 * 
 * <p>Implementations handle the conversion of LLM-provided JSON values to
 * complex types that require special handling beyond simple JSON mapping.</p>
 * 
 * <p>Example: A Query resolver extracts the "sql" field, validates it,
 * and constructs a Query object.</p>
 */
public interface TypeResolver {

	/**
	 * Returns the Java type this resolver handles.
	 */
	Class<?> supportedType();

	/**
	 * Attempts to resolve the raw value to the target type.
	 * 
	 * @param raw the raw value from LLM output (may be String, Map, etc.)
	 * @param context resolution context containing additional info (e.g., catalog)
	 * @return resolved value, or empty if resolution failed
	 */
	ResolveResult resolve(Object raw, Map<String, Object> context);

	/**
	 * Result of a type resolution attempt.
	 */
	sealed interface ResolveResult {
		
		record Success(Object resolvedValue) implements ResolveResult {}
		
		record Failure(String reason) implements ResolveResult {}

		static ResolveResult success(Object value) {
			return new Success(value);
		}

		static ResolveResult failure(String reason) {
			return new Failure(reason);
		}

		default boolean isSuccess() {
			return this instanceof Success;
		}

		default Optional<Object> value() {
			return this instanceof Success s ? Optional.of(s.resolvedValue()) : Optional.empty();
		}

		default Optional<String> failureReason() {
			return this instanceof Failure f ? Optional.of(f.reason()) : Optional.empty();
		}
	}
}

