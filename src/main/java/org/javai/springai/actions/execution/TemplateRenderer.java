package org.javai.springai.actions.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TemplateRenderer {

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)}");

	private TemplateRenderer() {
	}

	static RenderOutcome render(String template, TemplateBindings bindings) {
		if (template == null || template.isBlank()) {
			return new RenderOutcome(true, template, List.of());
		}
		Objects.requireNonNull(bindings, "bindings must not be null");

		Matcher matcher = PLACEHOLDER.matcher(template);
		StringBuilder sb = new StringBuilder();
		boolean resolved = true;
		List<String> missing = new ArrayList<>();

		while (matcher.find()) {
			String key = matcher.group(1).trim();
			String replacement = bindings.resolve(key).orElse(null);
			if (replacement == null) {
				resolved = false;
				missing.add(key);
				matcher.appendReplacement(sb, matcher.group(0));
			} else {
				matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
			}
		}
		matcher.appendTail(sb);

		if (resolved) {
			return new RenderOutcome(true, sb.toString(), List.of());
		}
		return new RenderOutcome(false, template, List.copyOf(missing));
	}

	record RenderOutcome(boolean resolved, String value, List<String> missingPlaceholders) {
	}
}

