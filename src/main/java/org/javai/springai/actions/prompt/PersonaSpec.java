package org.javai.springai.actions.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Placeholder Persona specification for early DX exploration.
 * Fields may evolve; kept minimal for current tests.
 */
public final class PersonaSpec {

	private final String name;
	private final String role;
	private final List<String> principles;
	private final List<String> constraints;
	private final List<String> styleGuidance;

	private PersonaSpec(Builder builder) {
		this.name = builder.name;
		this.role = builder.role;
		this.principles = List.copyOf(builder.principles);
		this.constraints = List.copyOf(builder.constraints);
		this.styleGuidance = List.copyOf(builder.styleGuidance);
	}

	public String name() {
		return name;
	}

	public String role() {
		return role;
	}

	public List<String> principles() {
		return principles;
	}

	public List<String> constraints() {
		return constraints;
	}

	public List<String> styleGuidance() {
		return styleGuidance;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String name;
		private String role;
		private final List<String> principles = new ArrayList<>();
		private final List<String> constraints = new ArrayList<>();
		private final List<String> styleGuidance = new ArrayList<>();

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder role(String role) {
			this.role = role;
			return this;
		}

		public Builder principles(List<String> values) {
			if (values != null) {
				this.principles.addAll(values);
			}
			return this;
		}

		public Builder constraints(List<String> values) {
			if (values != null) {
				this.constraints.addAll(values);
			}
			return this;
		}

		public Builder styleGuidance(List<String> values) {
			if (values != null) {
				this.styleGuidance.addAll(values);
			}
			return this;
		}

		public PersonaSpec build() {
			Objects.requireNonNull(name, "name must not be null");
			return new PersonaSpec(this);
		}
	}
}

