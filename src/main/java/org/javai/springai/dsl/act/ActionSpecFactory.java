package org.javai.springai.dsl.act;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds LLM-facing {@link ActionSpec} instances from annotated beans using {@link ActionRegistry}.
 */
public final class ActionSpecFactory {

	private ActionSpecFactory() {
	}

	/**
	 * Register the given beans and return the accumulated action specs.
	 * @param beans one or more beans containing @Action methods
	 * @return immutable list of action specs
	 */
	public static List<ActionSpec> fromBeans(Object... beans) {
		return fromBeans(ActionSpecFilter.ALL, beans);
	}

	public static List<ActionSpec> fromBeans(ActionSpecFilter filter, Object... beans) {
		Objects.requireNonNull(beans, "beans must not be null");
		if (filter == null) {
			filter = ActionSpecFilter.ALL;
		}
		ActionRegistry registry = new ActionRegistry();
		for (Object bean : beans) {
			registry.registerActions(bean);
		}
		return registry.getActionSpecs().stream()
				.filter(filter::include)
				.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
	}
}
