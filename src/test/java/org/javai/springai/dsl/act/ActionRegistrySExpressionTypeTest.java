package org.javai.springai.dsl.act;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.springai.actions.api.Action;
import org.javai.springai.dsl.bind.TypeFactoryBootstrap;
import org.javai.springai.dsl.sql.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Ensures DSL-backed parameter types (via SExpressionType/registry) populate dslId in descriptors.
 */
class ActionRegistrySExpressionTypeTest {

	private ActionRegistry registry;

	@BeforeEach
	void setUp() {
		TypeFactoryBootstrap.registerBuiltIns();
		registry = new ActionRegistry();
		registry.registerActions(new SqlActions());
	}

	@Test
	void dslBackedParameterCarriesDslId() {
		ActionDescriptor descriptor = registry.getActionDescriptors().getFirst();
		ActionParameterDescriptor param = descriptor.actionParameterSpecs().getFirst();

		assertThat(param.dslId()).isEqualTo("sxl-sql");
	}

	private static class SqlActions {
		@Action(description = "Display SQL query")
		public void displaySqlQuery(Query query) {
			// no-op
		}
	}
}

