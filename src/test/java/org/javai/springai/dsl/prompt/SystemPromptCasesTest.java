package org.javai.springai.dsl.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.act.ActionSpecFilter;
import org.javai.springai.dsl.bind.TypeFactoryBootstrap;
import org.javai.springai.dsl.sql.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Emits example system prompts (SXL and JSON) for inspection, writing them under build/prompt-samples.
 */
class SystemPromptCasesTest {

	private DslGuidanceProvider guidanceProvider;
	private Path outputDir;

	@BeforeEach
	void setup() throws Exception {
		TypeFactoryBootstrap.registerBuiltIns();
		guidanceProvider = new GrammarBackedDslGuidanceProvider(
				List.of(
						"sxl-meta-grammar-sql.yml",
						"sxl-meta-grammar-plan.yml"
				),
				getClass().getClassLoader()
		);
		outputDir = Path.of("build", "prompt-samples");
		Files.createDirectories(outputDir);
	}

	@Test
	void emitSamplePromptsForScenarios() throws Exception {
		List<Scenario> scenarios = List.of(
				new Scenario("simple-no-embed", this::simpleActions, ActionSpecFilter.ALL),
				new Scenario("single-sql-embed", this::singleSqlActions, ActionSpecFilter.ALL),
				new Scenario("multi-actions-filtered", this::multiActions, spec -> spec.id().endsWith("runQuery"))
		);

		for (Scenario scenario : scenarios) {
			ActionRegistry registry = scenario.registrySupplier().get();
			writePrompt(scenario.name(), "sxl",
					SystemPromptBuilder.build(registry, scenario.filter(), guidanceProvider, SystemPromptBuilder.Mode.SXL, "openai", "gpt-4.1"));
			writePrompt(scenario.name(), "json",
					SystemPromptBuilder.build(registry, scenario.filter(), guidanceProvider, SystemPromptBuilder.Mode.JSON, "openai", "gpt-4.1"));
		}
	}

	private void writePrompt(String scenario, String mode, String content) throws Exception {
		Path file = outputDir.resolve(scenario + "-" + mode + ".txt");
		Files.writeString(file, content, StandardCharsets.UTF_8);
		assertThat(Files.exists(file)).isTrue();
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

	private record Scenario(String name, Supplier<ActionRegistry> registrySupplier, ActionSpecFilter filter) {}

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
