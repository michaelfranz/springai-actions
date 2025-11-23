package org.javai.springai.actions.tuning;

/**
 * Describes a reusable scenario that can create plans for a given {@link LlmTuningConfig}.
 * Applications can expose their plan creation logic through this hook so the tuning
 * framework can sweep across prompt/model parameters without duplicating scenario code.
 */
public interface ScenarioPlanSupplier {

	/**
	 * Stable identifier for the scenario (e.g. {@code customer-order-cancellation}).
	 */
	String scenarioId();

	/**
	 * Human-readable summary shown in reports.
	 */
	String description();

	/**
	 * Baseline configuration the scenario expects when no overrides are provided.
	 */
	LlmTuningConfig defaultConfig();

	/**
	 * Returns a {@link PlanSupplier} that can generate plans using the provided configuration.
	 * @param config tuning configuration, defaults to {@link #defaultConfig()} when {@code null}
	 */
	PlanSupplier planSupplier(LlmTuningConfig config);

	default PlanSupplier planSupplier() {
		return planSupplier(defaultConfig());
	}
}

