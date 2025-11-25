package org.javai.springai.actions.execution;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.definition.ActionDefinition;
import org.javai.springai.actions.planning.PlanStep;

public class ExecutableActionFactory {

	private static final Logger logger = LogManager.getLogger(ExecutableActionFactory.class);

	private final List<ActionDefinition> actions;
	private final ActionArgumentBinder binder = new ActionArgumentBinder();

	public ExecutableActionFactory(List<ActionDefinition> actions) {
		this.actions = actions;
	}

	public ExecutableAction from(PlanStep step) {
		ActionDefinition def = findDefinition(step.action());
		Method method = def.method();
		method.setAccessible(true);
		Object bean = def.bean();
		Action actionAnno = method.getAnnotation(Action.class);
		if (actionAnno != null) {
			inspectContextWrites(method, actionAnno);
		}
		ActionMetadata metadata = createMetadata(step, actionAnno);

		return new AbstractExecutableAction(step, metadata) {
			public void perform(ActionContext ctx) throws PlanExecutionException {
				Object[] args = binder.bindArguments(method, step.arguments(), ctx);
				try {
					Object result = method.invoke(bean, args);
					boolean wrotePrimaryContext = actionAnno != null && !actionAnno.contextKey().isBlank() && result != null;
					if (wrotePrimaryContext) {
						ctx.put(actionAnno.contextKey(), result);
					}
					enforceContextContract(actionAnno, ctx, wrotePrimaryContext);
				} catch (Exception e) {
					throw new PlanExecutionException(
							"Exception invoking *** action: %s\n*** bean: %s\n*** method: %s\n*** metadata: %s\n*** plan step: %s"
									.formatted(def.name(),
											bean.getClass().getName(),
											method.getName(),
											metadata.describe(),
											step.describe()), e);
				}
			}
		};
	}

	private void inspectContextWrites(Method method, Action actionAnno) {
		if (actionAnno.additionalContextKeys().length <= 1) {
			return;
		}
		boolean hasActionContextParam = Arrays.stream(method.getParameters())
				.anyMatch(p -> p.getType().equals(ActionContext.class));
		if (!hasActionContextParam) {
			logger.warn(
					"Action '{}' declares multiple additionalContextKeys but has no ActionContext parameter. "
							+ "Consider injecting ActionContext to satisfy the declared contract.",
					method.getName());
		}
	}

	private void enforceContextContract(Action actionAnno, ActionContext ctx, boolean primaryKeyRequired) {
		if (actionAnno == null) {
			return;
		}
		Set<String> expectedKeys = new HashSet<>();
		if (primaryKeyRequired) {
			expectedKeys.add(actionAnno.contextKey());
		}
		for (String key : actionAnno.additionalContextKeys()) {
			if (key != null && !key.isBlank()) {
				expectedKeys.add(key);
			}
		}
		for (String key : expectedKeys) {
			if (!ctx.contains(key)) {
				throw new IllegalStateException(
						"Action '%s' contract violation: expected context key '%s' to be present."
								.formatted(actionAnno.description().isBlank() ? actionAnno.contextKey() : actionAnno.description(), key));
			}
		}
	}

	private ActionDefinition findDefinition(String actionName) {
		return actions.stream()
				.filter(d -> d.name().equals(actionName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"Unknown action: " + actionName));
	}

	private ActionMetadata createMetadata(PlanStep step, Action actionAnno) {
		ActionMetadata.Builder builder = ActionMetadata.builder()
				.stepId(step.action())
				.actionName(step.action());

		if (actionAnno == null) {
			return builder.build();
		}

		TemplateBindings bindings = TemplateBindings.from(step.arguments());

		builder.cost(actionAnno.cost())
				.mutability(actionAnno.mutability());

		validateAffinityUsage(actionAnno);
		addTemplateValue(builder, actionAnno.affinity(), bindings, builder::addAffinityId);
		for (String extraAffinity : actionAnno.affinities()) {
			addTemplateValue(builder, extraAffinity, bindings, builder::addAffinityId);
		}

		for (String produces : actionAnno.additionalContextKeys()) {
			addTemplateValue(builder, produces, bindings, builder::addProducesContext);
		}

		if (!actionAnno.contextKey().isBlank()) {
			builder.addProducesContext(actionAnno.contextKey());
		}

		return builder.build();
	}

	private void addTemplateValue(ActionMetadata.Builder builder,
			String template,
			TemplateBindings bindings,
			java.util.function.Consumer<String> consumer) {
		if (template == null || template.isBlank()) {
			return;
		}
		TemplateRenderer.RenderOutcome outcome = TemplateRenderer.render(template, bindings);
		if (outcome.resolved()) {
			if (!outcome.value().isBlank()) {
				consumer.accept(outcome.value());
			}
		} else {
			builder.addPendingAffinity(template, outcome.missingPlaceholders());
		}
	}

	private void validateAffinityUsage(Action action) {
		boolean hasSingle = action.affinity() != null && !action.affinity().isBlank();
		boolean hasMultiple = action.affinities() != null && action.affinities().length > 0;
		if (hasSingle && hasMultiple) {
			throw new IllegalArgumentException(
					"Action annotation must not define both 'affinity' and 'affinities'.");
		}
	}
}