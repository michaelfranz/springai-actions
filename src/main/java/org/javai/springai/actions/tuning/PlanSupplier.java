package org.javai.springai.actions.tuning;

import java.util.function.Supplier;
import org.javai.springai.actions.execution.ExecutablePlan;

@FunctionalInterface
public interface PlanSupplier extends Supplier<ExecutablePlan> {
}
