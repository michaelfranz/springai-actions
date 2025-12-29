package org.javai.springai.actions.exec;

import java.util.List;
import java.util.Map;
import org.javai.springai.actions.bind.ActionBinding;

/**
 * Executable or error step after resolution.
 */
public sealed interface ResolvedStep {

	record ActionStep(ActionBinding binding, List<ResolvedArgument> arguments) implements ResolvedStep {
		public ActionStep {
			arguments = arguments != null ? List.copyOf(arguments) : List.of();
		}
	}

	record PendingActionStep(String assistantMessage, String actionId, PendingParam[] pendingParams,
							 Map<String, Object> providedParams)
			implements ResolvedStep {
	}

	record ErrorStep(String reason) implements ResolvedStep {
	}

	record PendingParam(String name, String message) {
	}
}

