package org.javai.springai.dsl.prompt;

import static java.util.Arrays.stream;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.act.ActionDescriptorFilter;
import org.javai.springai.dsl.bind.TypeFactoryBootstrap;
import org.javai.springai.dsl.sql.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Reports footprint metrics (bytes and rough tokens) for full system prompts (actions + DSL guidance)
 * in both SXL and JSON modes across multiple scenarios.
 * Prints results to System.out for inspection.
 */
class SystemPromptFootprintTest {

	private DslGuidanceProvider guidanceProvider;

	@BeforeEach
	void setup() {
		TypeFactoryBootstrap.registerBuiltIns();
		guidanceProvider = new GrammarBackedDslGuidanceProvider(
				List.of("META-INF/sxl-meta-grammar-sql.yml"),
				getClass().getClassLoader());
	}

	@Test
	void reportPromptFootprints() {
		List<Scenario> scenarios = List.of(
				new Scenario("simple-no-embed", this::simpleActions, ActionDescriptorFilter.ALL),
				new Scenario("single-sql-embed", this::singleSqlActions, ActionDescriptorFilter.ALL),
				new Scenario("filtered-single-action", this::multiActions, spec -> spec.id().endsWith("runQuery"))
		);

		for (Scenario scenario : scenarios) {
			ActionRegistry registry = scenario.registrySupplier().get();
			String sxlPrompt = SystemPromptBuilder.build(registry, scenario.filter(), guidanceProvider, SystemPromptBuilder.Mode.SXL, "openai", "gpt-4.1");
			String jsonPrompt = SystemPromptBuilder.build(registry, scenario.filter(), guidanceProvider, SystemPromptBuilder.Mode.JSON, "openai", "gpt-4.1");

			int sxlBytes = sxlPrompt.getBytes(StandardCharsets.UTF_8).length;
			int jsonBytes = jsonPrompt.getBytes(StandardCharsets.UTF_8).length;
			int sxlTokens = roughTokenCount(sxlPrompt);
			int jsonTokens = roughTokenCount(jsonPrompt);

			assertThat(sxlBytes).isPositive();
			assertThat(jsonBytes).isPositive();
			System.out.printf("scenario=%s sxlBytes=%d jsonBytes=%d sxlTokens=%d jsonTokens=%d%n",
					scenario.name(), sxlBytes, jsonBytes, sxlTokens, jsonTokens);
		}
	}

	private ActionRegistry simpleActions() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new SimpleActions());
		return registry;
	}

	private ActionRegistry singleSqlActions() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new SingleSqlAction());
		return registry;
	}

	private ActionRegistry multiActions() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new SingleSqlAction());
		registry.registerActions(new OtherActions());
		return registry;
	}

	private static int roughTokenCount(String text) {
		return (int) stream(text.trim().split("\\s+"))
				.filter(s -> !s.isEmpty())
				.count();
	}

	private record Scenario(String name, Supplier<ActionRegistry> registrySupplier, ActionDescriptorFilter filter) {}

	// Sample action beans
	private static class SimpleActions {
		@Action(description = "Add a note")
		public void addNote(@ActionParam(description = "note content") String note) {
		}
	}

	private static class SingleSqlAction {
		@Action(description = "Run a query")
		public void runQuery(Query query, @ActionParam(description = "note to include") String note) {
		}
	}

	private static class OtherActions {
		@Action(description = "Other action")
		public void otherAction(String input) {
		}
	}
}
