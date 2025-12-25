package org.javai.springai.dsl.prompt;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.javai.springai.dsl.act.ActionDescriptor;
import org.javai.springai.dsl.act.ActionDescriptorFilter;
import org.javai.springai.dsl.act.ActionRegistry;

/**
 * Context passed to DSL context contributors for rendering dynamic prompt additions.
 */
public record SystemPromptContext(
		ActionRegistry registry,
		List<ActionDescriptor> selectedDescriptors,
		ActionDescriptorFilter filter,
		Map<String, Object> dslContext
) {

	public Optional<Object> contextFor(String dslId) {
		return Optional.ofNullable(dslContext != null ? dslContext.get(dslId) : null);
	}
}

