package org.javai.springai.actions.execution;

import java.util.List;
import java.util.Map;

/**
 * Defines how an {@link ExecutionDAG} should be ordered based on the available
 * action metadata. Different strategies can consider static metadata, runtime
 * telemetry, or administrator preferences.
 */
public interface ExecutionOrderStrategy {

	/**
	 * Produces an ordered list of DAG nodes from the supplied metadata and resolved
	 * dependencies.
	 *
	 * @param stepsById stepId -> execution step mapping
	 * @return ordered DAG nodes with populated order indexes
	 */
	List<ExecutionDAG.Node> order(Map<String, ExecutionStep> stepsById);
}

