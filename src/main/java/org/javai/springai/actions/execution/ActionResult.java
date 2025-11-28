package org.javai.springai.actions.execution;

public sealed interface ActionResult {
	record Success(Object value) implements ActionResult {}
	record Failure(String... errors) implements ActionResult {}
}
