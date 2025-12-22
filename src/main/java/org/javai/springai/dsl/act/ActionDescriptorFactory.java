package org.javai.springai.dsl.act;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds LLM-facing {@link ActionDescriptor} instances from annotated beans using {@link ActionRegistry}.
 */
public final class ActionDescriptorFactory {

	private ActionDescriptorFactory() {
	}

	public static List<ActionDescriptor> fromBeans(Object... beans) {
		return fromBeans(ActionDescriptorFilter.ALL, beans);
	}

	public static List<ActionDescriptor> fromBeans(ActionDescriptorFilter filter, Object... beans) {
		Objects.requireNonNull(beans, "beans must not be null");
		if (filter == null) {
			filter = ActionDescriptorFilter.ALL;
		}
		ActionRegistry registry = new ActionRegistry();
		for (Object bean : beans) {
			registry.registerActions(bean);
		}
		return registry.getActionDescriptors().stream()
				.filter(filter::include)
				.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
	}
}

