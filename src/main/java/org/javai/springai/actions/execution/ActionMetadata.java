package org.javai.springai.actions.execution;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.javai.springai.actions.api.Mutability;

/**
 * Canonical description of how an executable action should be scheduled. This
 * record is intentionally serialisable so queued actions can survive process
 * restarts.
 */
public record ActionMetadata(
		String stepId,
		String actionName,
		List<String> affinityIds,
		Mutability mutability,
		Set<String> resourceReads,
		Set<String> resourceWrites,
		Set<String> requiresContext,
		Set<String> producesContext,
		Set<String> dependsOn,
		int cost,
		Integer priority,
		Duration timeout,
		int maxRetries,
		boolean idempotent
) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public ActionMetadata {
		stepId = Objects.requireNonNull(stepId, "stepId must not be null");
		actionName = Objects.requireNonNull(actionName, "actionName must not be null");
		affinityIds = immutableList(affinityIds);
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

	public static final class Builder {

		private String stepId = "unspecified";
		private String actionName = "unspecified";
		private final List<String> affinityIds = new ArrayList<>();
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
}

