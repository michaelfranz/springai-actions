package org.javai.springai.dsl.exec;

import java.util.List;
import org.javai.springai.dsl.act.ActionBinding;

/**
 * Executable or error step after resolution.
 */
public sealed interface ResolvedStep {

	record ActionStep(ActionBinding binding, List<ResolvedArgument> arguments) implements ResolvedStep {
		public ActionStep {
			arguments = arguments != null ? List.copyOf(arguments) : List.of();
		}
	}

	record ErrorStep(String reason) implements ResolvedStep {
	}
}

