package org.javai.springai.actions.execution;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TemplateRenderer {

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)}");

	private TemplateRenderer() {
	}

	static String render(String template, TemplateBindings bindings) {
		if (template == null || template.isBlank()) {
			return template;
		}
		Objects.requireNonNull(bindings, "bindings must not be null");

		Matcher matcher = PLACEHOLDER.matcher(template);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			String key = matcher.group(1).trim();
			String value = bindings.resolve(key)
					.orElseThrow(() -> new IllegalArgumentException(
							"Missing template value for '" + key + "' in template '" + template + "'"));
			matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}
}

