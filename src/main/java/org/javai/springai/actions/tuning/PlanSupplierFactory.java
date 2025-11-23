package org.javai.springai.actions.tuning;

public interface PlanSupplierFactory {

	PlanSupplier getPlanSupplier(LlmTuningConfig config);

}
