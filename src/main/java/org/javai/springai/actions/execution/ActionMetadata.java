package org.javai.springai.actions.execution;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.api.Mutability;

/**
 * Canonical description of how an executable action should be scheduled. This
 * record is intentionally serialisable so queued actions can survive process
 * restarts.
 */
public record ActionMetadata(
		/** Unique identifier for the plan step (used in dependency edges). */
		String stepId,
		/** Human-readable action name (method name or alias). */
		String actionName,
		/** Fully resolved affinity IDs determining serialized execution lanes. */
		List<String> affinityIds,
		/** Affinity templates that must be resolved from context at runtime. */
		List<PendingAffinity> pendingAffinities,
		/** Mutability contract (read-only, create, mutate) for scheduling rules. */
		Mutability mutability,
		/** Logical resources/context keys this action reads. */
		Set<String> resourceReads,
		/** Logical resources/context keys this action writes or mutates. */
		Set<String> resourceWrites,
		/** Context keys or resources that must exist before execution. */
		Set<String> requiresContext,
		/** Context keys or resources produced by the action. */
		Set<String> producesContext,
		/** Step IDs that must complete before this action can begin. */
		Set<String> dependsOn,
		/** Relative scheduling cost used when ordering competing actions. */
		int cost,
		/** Optional priority override for finer-grained scheduling. */
		Integer priority,
		/** Maximum allowed execution time before the action is considered timed out. */
		Duration timeout,
		/** Maximum number of retries permitted on failure. */
		int maxRetries,
		/** Whether the action can be safely retried without side effects. */
		boolean idempotent
) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public ActionMetadata {
		stepId = Objects.requireNonNull(stepId, "stepId must not be null");
		actionName = Objects.requireNonNull(actionName, "actionName must not be null");
		affinityIds = immutableList(affinityIds);
		pendingAffinities = immutableList(pendingAffinities);
		mutability = mutability != null ? mutability : Mutability.MUTATE;
		resourceReads = immutableSet(resourceReads);
		resourceWrites = immutableSet(resourceWrites);
		requiresContext = immutableSet(requiresContext);
		producesContext = immutableSet(producesContext);
		dependsOn = immutableSet(dependsOn);
		if (cost <= 0) {
			throw new IllegalArgumentException("cost must be positive");
		}
		if (maxRetries < 0) {
			throw new IllegalArgumentException("maxRetries must be >= 0");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static ActionMetadata empty() {
		return builder()
				.stepId("unspecified")
				.actionName("unspecified")
				.build();
	}

	private static <T> List<T> immutableList(List<T> source) {
		if (source == null || source.isEmpty()) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(new ArrayList<>(source));
	}

	private static <T> Set<T> immutableSet(Set<T> source) {
		if (source == null || source.isEmpty()) {
			return Collections.emptySet();
		}
		return Collections.unmodifiableSet(new LinkedHashSet<>(source));
	}

	public List<String> resolvedAffinityIds(ActionContext ctx) {
		if (pendingAffinities.isEmpty()) {
			return affinityIds;
		}
		List<String> resolved = new ArrayList<>(affinityIds);
		for (PendingAffinity pending : pendingAffinities) {
			resolved.add(resolvePendingAffinity(ctx, pending));
		}
		return Collections.unmodifiableList(resolved);
	}

	private String resolvePendingAffinity(ActionContext ctx, PendingAffinity pending) {
		Map<String, String> values = new HashMap<>();
		for (String placeholder : pending.placeholders()) {
			values.put(placeholder, ContextExpressionResolver.resolve(ctx, placeholder));
		}
		TemplateBindings runtimeBindings = TemplateBindings.fromMap(values);
		TemplateRenderer.RenderOutcome outcome = TemplateRenderer.render(pending.template(), runtimeBindings);
		if (!outcome.resolved()) {
			throw new IllegalStateException(
					"Unable to resolve affinity template '%s' using context keys %s"
							.formatted(pending.template(), pending.placeholders()));
		}
		return outcome.value();
	}

	/**
	 * Returns a developer-friendly string representation of this metadata for debugging purposes.
	 * This method provides more useful information than the default toString() implementation.
	 *
	 * @return a formatted string describing the action metadata
	 */
	public String describe() {
		StringBuilder sb = new StringBuilder();
		sb.append("ActionMetadata[");
		sb.append("stepId='").append(stepId).append("'");
		sb.append(", actionName='").append(actionName).append("'");
		sb.append(", mutability=").append(mutability);
		
		if (!affinityIds.isEmpty()) {
			sb.append(", affinityIds=").append(affinityIds);
		}
		if (!pendingAffinities.isEmpty()) {
			sb.append(", pendingAffinities=").append(
					pendingAffinities.stream()
							.map(pa -> pa.template() + " (missing: " + pa.placeholders() + ")")
							.toList());
		}
		
		if (!requiresContext.isEmpty()) {
			sb.append(", requiresContext=").append(requiresContext);
		}
		if (!producesContext.isEmpty()) {
			sb.append(", producesContext=").append(producesContext);
		}
		
		if (!resourceReads.isEmpty()) {
			sb.append(", resourceReads=").append(resourceReads);
		}
		if (!resourceWrites.isEmpty()) {
			sb.append(", resourceWrites=").append(resourceWrites);
		}
		
		if (!dependsOn.isEmpty()) {
			sb.append(", dependsOn=").append(dependsOn);
		}
		
		sb.append(", cost=").append(cost);
		if (priority != null) {
			sb.append(", priority=").append(priority);
		}
		if (timeout != null) {
			sb.append(", timeout=").append(timeout);
		}
		sb.append(", maxRetries=").append(maxRetries);
		sb.append(", idempotent=").append(idempotent);
		
		sb.append("]");
		return sb.toString();
	}

	public static final class Builder {

		private String stepId = "unspecified";
		private String actionName = "unspecified";
		private final List<String> affinityIds = new ArrayList<>();
		private final List<PendingAffinity> pendingAffinities = new ArrayList<>();
		private Mutability mutability = Mutability.MUTATE;
		private final Set<String> resourceReads = new LinkedHashSet<>();
		private final Set<String> resourceWrites = new LinkedHashSet<>();
		private final Set<String> requiresContext = new LinkedHashSet<>();
		private final Set<String> producesContext = new LinkedHashSet<>();
		private final Set<String> dependsOn = new LinkedHashSet<>();
		private int cost = 1;
		private Integer priority;
		private Duration timeout;
		private int maxRetries;
		private boolean idempotent;

		public Builder stepId(String stepId) {
			this.stepId = Objects.requireNonNull(stepId, "stepId must not be null");
			return this;
		}

		public Builder actionName(String actionName) {
			this.actionName = Objects.requireNonNull(actionName, "actionName must not be null");
			return this;
		}

		public Builder addAffinityId(String affinityId) {
			if (affinityId != null && !affinityId.isBlank()) {
				this.affinityIds.add(affinityId);
			}
			return this;
		}

		public Builder addPendingAffinity(String template, List<String> placeholders) {
			if (template != null && !template.isBlank() && placeholders != null && !placeholders.isEmpty()) {
				this.pendingAffinities.add(new PendingAffinity(template, placeholders));
			}
			return this;
		}

		public Builder mutability(Mutability mutability) {
			if (mutability != null) {
				this.mutability = mutability;
			}
			return this;
		}

		public Builder addResourceRead(String resource) {
			if (resource != null && !resource.isBlank()) {
				this.resourceReads.add(resource);
			}
			return this;
		}

		public Builder addResourceWrite(String resource) {
			if (resource != null && !resource.isBlank()) {
				this.resourceWrites.add(resource);
			}
			return this;
		}

		public Builder addRequiresContext(String key) {
			if (key != null && !key.isBlank()) {
				this.requiresContext.add(key);
			}
			return this;
		}

		public Builder addProducesContext(String key) {
			if (key != null && !key.isBlank()) {
				this.producesContext.add(key);
			}
			return this;
		}

		public Builder addDependency(String stepId) {
			if (stepId != null && !stepId.isBlank()) {
				this.dependsOn.add(stepId);
			}
			return this;
		}

		public Builder cost(int cost) {
			if (cost > 0) {
				this.cost = cost;
			}
			return this;
		}

		public Builder priority(Integer priority) {
			this.priority = priority;
			return this;
		}

		public Builder timeout(Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		public Builder maxRetries(int maxRetries) {
			if (maxRetries >= 0) {
				this.maxRetries = maxRetries;
			}
			return this;
		}

		public Builder idempotent(boolean idempotent) {
			this.idempotent = idempotent;
			return this;
		}

		public ActionMetadata build() {
			return new ActionMetadata(
					stepId,
					actionName,
					affinityIds,
					pendingAffinities,
					mutability,
					resourceReads,
					resourceWrites,
					requiresContext,
					producesContext,
					dependsOn,
					cost,
					priority,
					timeout,
					maxRetries,
					idempotent);
		}
	}

	public record PendingAffinity(String template, List<String> placeholders) {
		public PendingAffinity {
			Objects.requireNonNull(template, "template must not be null");
			if (placeholders == null || placeholders.isEmpty()) {
				throw new IllegalArgumentException("placeholders must not be empty");
			}
			placeholders = List.copyOf(placeholders);
		}
	}
}

