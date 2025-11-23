package org.javai.springai.actions.tuning;

public interface TuningExecutor {
	/**
	 * Runs a tuning experiment and returns results.
	 */
	TuningExperimentResult execute(TuningExperiment experiment);
}