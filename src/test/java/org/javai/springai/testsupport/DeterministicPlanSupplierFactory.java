package org.javai.springai.testsupport;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.javai.springai.actions.execution.ExecutablePlan;
import org.javai.springai.actions.tuning.LlmTuningConfig;
import org.javai.springai.actions.tuning.PlanSupplier;
import org.javai.springai.actions.tuning.PlanSupplierFactory;

/**
 * Test-only {@link PlanSupplierFactory} that returns deterministic {@link ExecutablePlan}s
 * without calling an LLM. Each registered configuration maps to a {@link Supplier} that
 * creates the plan, and invocation counts are tracked for assertions.
 */
public final class DeterministicPlanSupplierFactory implements PlanSupplierFactory {

	private final Map<LlmTuningConfig, Supplier<ExecutablePlan>> planSuppliers = new ConcurrentHashMap<>();
	private final Map<LlmTuningConfig, AtomicInteger> invocationCounts = new ConcurrentHashMap<>();

	public DeterministicPlanSupplierFactory register(LlmTuningConfig config, Supplier<ExecutablePlan> planSupplier) {
		planSuppliers.put(Objects.requireNonNull(config, "config must not be null"),
				Objects.requireNonNull(planSupplier, "planSupplier must not be null"));
		return this;
	}

	@Override
	public PlanSupplier getPlanSupplier(LlmTuningConfig config) {
		Supplier<ExecutablePlan> delegate = planSuppliers.get(config);
		if (delegate == null) {
			throw new IllegalArgumentException("No deterministic plan registered for config " + config);
		}
		return () -> {
			invocationCounts.computeIfAbsent(config, ignored -> new AtomicInteger()).incrementAndGet();
			return delegate.get();
		};
	}

	public int invocationsFor(LlmTuningConfig config) {
		return invocationCounts.getOrDefault(config, new AtomicInteger()).get();
	}
}


