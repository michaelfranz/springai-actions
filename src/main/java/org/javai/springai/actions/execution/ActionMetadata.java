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

/**
 * Canonical description of how an executable action should be scheduled. This
 * record is intentionally serialisable so queued actions can survive process
 * restarts.
 */
public final class ActionMetadata implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final String stepId;
	private final String actionName;
	private final List<String> affinityIds;
	private final Mutability mutability;
	private final Set<String> resourceReads;
	private final Set<String> resourceWrites;
	private final Set<String> requiresContext;
	private final Set<String> producesContext;
	private final Set<String> dependsOn;
	private final int cost;
	private final Integer priority;
	private final Duration timeout;
	private final int maxRetries;
	private final boolean idempotent;

	private ActionMetadata(Builder builder) {
		this.stepId = builder.stepId;
		this.actionName = builder.actionName;
		this.affinityIds = Collections.unmodifiableList(new ArrayList<>(builder.affinityIds));
		this.mutability = builder.mutability;
		this.resourceReads = Collections.unmodifiableSet(new LinkedHashSet<>(builder.resourceReads));
		this.resourceWrites = Collections.unmodifiableSet(new LinkedHashSet<>(builder.resourceWrites));
		this.requiresContext = Collections.unmodifiableSet(new LinkedHashSet<>(builder.requiresContext));
		this.producesContext = Collections.unmodifiableSet(new LinkedHashSet<>(builder.producesContext));
		this.dependsOn = Collections.unmodifiableSet(new LinkedHashSet<>(builder.dependsOn));
		this.cost = builder.cost;
		this.priority = builder.priority;
		this.timeout = builder.timeout;
		this.maxRetries = builder.maxRetries;
		this.idempotent = builder.idempotent;
	}

	public String stepId() {
		return stepId;
	}

	public String actionName() {
		return actionName;
	}

	public List<String> affinityIds() {
		return affinityIds;
	}

	public Mutability mutability() {
		return mutability;
	}

	public Set<String> resourceReads() {
		return resourceReads;
	}

	public Set<String> resourceWrites() {
		return resourceWrites;
	}

	public Set<String> requiresContext() {
		return requiresContext;
	}

	public Set<String> producesContext() {
		return producesContext;
	}

	public Set<String> dependsOn() {
		return dependsOn;
	}

	public int cost() {
		return cost;
	}

	public Integer priority() {
		return priority;
	}

	public Duration timeout() {
		return timeout;
	}

	public int maxRetries() {
		return maxRetries;
	}

	public boolean idempotent() {
		return idempotent;
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
			return new ActionMetadata(this);
		}
	}
}

