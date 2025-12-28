package org.javai.springai.scenarios.protocol;

/**
 * Mutable builder for constructing protocol-based notebooks.
 */
public final class NotebookBuilder {
	private final StringBuilder content = new StringBuilder();

	public NotebookBuilder addMarkdown(String markdown) {
		content.append(markdown).append("\n\n");
		return this;
	}

	public Notebook build() {
		return new Notebook(content.toString());
	}
}

