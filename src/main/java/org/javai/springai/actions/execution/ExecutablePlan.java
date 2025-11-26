package org.javai.springai.actions.execution;

import java.util.List;

public record ExecutablePlan(List<ExecutableAction> executables) {
	public String describe() {
		return "Executable Plan: [\n%s]"
				.formatted(executables.stream()
						.map(ExecutableAction::describe)
						.reduce("", (a, b) -> a + "\n" + b));
	}
}
