package org.javai.springai.scenarios;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.api.ContextKey;
import org.javai.springai.actions.api.FromContext;
import org.javai.springai.actions.execution.DefaultPlanExecutor;
import org.javai.springai.actions.execution.ExecutablePlan;
import org.javai.springai.actions.execution.PlanExecutionException;
import org.javai.springai.actions.execution.PlanExecutor;
import org.javai.springai.actions.planning.PlanningChatClient;
import org.javai.springai.actions.planning.PlanningPromptSpec;
import org.javai.springai.actions.tuning.LlmTuningConfig;
import org.javai.springai.actions.tuning.PlanSupplier;
import org.javai.springai.actions.tuning.ScenarioPlanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.annotation.Tool;

class StarSchemaQueryTest implements ScenarioPlanSupplier {

	private static final String ASSISTENT_PERSONA = """
			You are an expert data engineer whose job is to translate \
			natural-language questions into valid SQL queries for a data \
			warehouse that uses a classical star-schema design.""";

	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");

	private static final ContextKey<String> DISPLAY_FEEDBACK = ContextKey.of("displayFeedback", String.class);

	private static final String METADATA_BRIEF = """
			The warehouse contains:
				•	Multiple fact tables, each representing a business process.
				•	Multiple dimension tables, many of which are shared across fact tables (conformed dimensions).
				•	Fact tables always join to dimension tables using foreign keys.
				•	Dimension tables do not join to each other unless explicitly indicated in metadata.

			Your responsibilities:
				1.	Interpret the user’s natural-language request.
				2.	Identify the correct fact table(s) by using metadata tools whenever necessary.
				•	Always call listFactTables() when deciding which fact table contains the required metrics.
				•	Never assume that a fact table exists without confirming via metadata.
				3.	Identify the required dimension tables by inspecting metadata.
				•	Use listDimensionTables(factTableName) to determine which dimensions are available for joining.
				•	Use getTableSchema(tableName) to inspect column names if mapping is unclear.
				•	Never invent or guess columns—only use what is confirmed in metadata.
				4.	Construct a complete and syntactically valid SQL query that:
				•	Uses explicit JOIN clauses
				•	Selects the correct fields
				•	Applies the necessary filters and grouping
				•	Respects the SQL dialect (PostgreSQL unless otherwise specified)
				•	Uses table aliases where appropriate
				•	Never includes tables or columns that are not present in metadata
				5.	Be deterministic and explicit.
				•	If multiple fact tables could apply, choose the most semantically relevant one.
				•	If the user’s request is ambiguous, ask a clarifying question instead of guessing.
				•	If the request cannot be satisfied with available metadata, state so and explain why.
				6.	Output rules:
				•	Return a .
				•	Do not include commentary, discussion, or reasoning unless requested.

			Failure modes to avoid:
				•	Do not hallucinate table names or column names.
				•	Do not invent join conditions.
				•	Do not rely on vague semantics—use metadata tools.

			Metadata Tools Available (call them as needed):
				•	listFactTables() → returns available fact tables with descriptions
				•	listDimensionTables(factTableName) → returns dimension tables linked to that fact table
				•	getTableSchema(tableName) → returns columns + types for any table
										You must call the factTables tool to obtain a list of fact tables.
									You must call the dimensionTables tool with the fact table name as the argument to obtain a list of dimension tables.
									You must call the tableFields tool with a table name as the argument to obtain a list of fields and their types.
									You must translate the user's input into an SQL query that is as close as possible to the user's intention.
									If you cannot translate the user's input, return an error message.
			""";

	private static final LlmTuningConfig BASELINE_CONFIG = new LlmTuningConfig(
			ASSISTENT_PERSONA,
			0.2,
			0.95
	);

	@Test
	void generateSimpleQuery() throws PlanExecutionException {
		ExecutablePlan plan = planSupplier().get();

		// TODO verify plan correct

		PlanExecutor executor = new DefaultPlanExecutor();
		ActionContext context = executor.execute(plan);

		String query = context.get(DISPLAY_FEEDBACK);
		assertThat(query).isNotBlank();
	}

	@Tool(description = "Return a list of fact tables ")
	public String[] listFactTables() {
		return new String[] { "FCT_ORDER" };
	}

	@Tool(description = "Return a list of dimension table for the given fact table")
	public String[] listDimensionTables(String factTable) {
		return new String[] {
				"DIM_DATE",
				"DIM_TIME",
				"DIM_COUNTRY",
				"DIM_CUSTOMER",
				"DIM_PRODUCT",
				"DIM_STATUS" };
	}

	@Tool(description = "Return a list of fields and their types for the given fact table")
	public String[] listTableFields(String table) {
		return switch (table) {
			case "FCT_ORDER" -> new String[] {
					"ID UUID",
					"VALUE_CHF NUMERIC(12,5) -- the value of the order",
					"FK_DATE INTEGER -- fk to DIM_DATE",
					"FK_TIME SMALLINT -- fk to DIM_TIME",
					"FK_COUNTRY UUID -- fk to DIM_COUNTRY",
					"FK_CUSTOMER UUID -- fk to DIM_CUSTOMER",
					"FK_PRODUCT UUID -- fk to DIM_PRODUCT",
					"FK_STATUS UUID -- fk to DIM_STATUS" };
			case "DIM_DATE" -> new String[] {
					"ID INTEGER",
					"YEAR SMALLINT -- 1990-2030",
					"MONTH_OF_YEAR SMALLINT -- 1-12",
					"DAY_OF_MONTH SMALLINT -- 1-31",
					"MONTH_NAME VARCHAR(10) -- name of month"
			};
			case "DIM_TIME" -> new String[] {
					"HOUR SMALLINT -- hour of the day"
			};
			case "DIM_COUNTRY" -> new String[] {
					"ID UUID",
					"NAME VARCHAR(30) -- name of country"
			};
			case "DIM_CUSTOMER" -> new String[] {
					"ID UUID",
					"NAME VARCHAR(30) -- name of customer",
					"EMAIL VARCHAR(30) -- email address of customer"
			};
			case "DIM_PRODUCT" -> new String[] {
					"ID UUID",
					"MANUFACTURER VARCHAR(20) -- manufacturer of product",
					"TYPE VARCHAR(20) -- type of product",
					"MODEL VARCHAR(20) -- model of product"
			};
			case "DIM_STATUS" -> new String[] {
					"ID UUID",
					"STATUS VARCHAR(10) -- PLACED, CANCELLED, COMPLETED"
			};
			default -> new String[] {};
		};
	}

	@Action(description = "Execute the given SQL statement",
			contextKey = "sqlResultSet")
	public ResultSet executeSqlQuery(
			@ActionParam(description = "The SQL query") String sqlQuery
	) {
		System.out.println("Executing SQL query: " + sqlQuery);
		// TODO perform a real SQL query and return a real result set
		return new EmptyResultSet();
	}

	@Action(description = "Display search results", contextKey = "displayFeedback")
	public String displayResultSet(@FromContext("sqlResultSet") ResultSet resultSet) {
		try {
			while (resultSet.next()) {
				System.out.println(resultSet.getString(1));
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return "OK";
	}

	@Override
	public String scenarioId() {
		return "star-schema-query";
	}

	@Override
	public String description() {
		return "Translates NL requests into SQL using metadata-backed star schema tools.";
	}

	@Override
	public LlmTuningConfig defaultConfig() {
		return BASELINE_CONFIG;
	}

	@Override
	public PlanSupplier planSupplier(LlmTuningConfig config) {
		LlmTuningConfig effective = config != null ? config : defaultConfig();
		return () -> createPlan(effective);
	}

	private ExecutablePlan createPlan(LlmTuningConfig config) {
		PlanningPromptSpec prompt = basePrompt(config);
		return prompt
				.user("""
						Find the total order value of orders placed in germany in 2001.
						Then display the search results.
						""")
				.plan();
	}

	private PlanningPromptSpec basePrompt(LlmTuningConfig config) {
		PlanningPromptSpec prompt = createPlanningClient(config).prompt();
		if (config.systemPrompt() != null && !config.systemPrompt().isBlank()) {
			prompt = prompt.system(config.systemPrompt());
		}
		return prompt
				.system(METADATA_BRIEF)
				.tools(this)
				.actions(this);
	}

	private PlanningChatClient createPlanningClient(LlmTuningConfig config) {
		ensureApiKeyPresent();
		OpenAiApi openAiApi = OpenAiApi.builder().apiKey(OPENAI_API_KEY).build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder().openAiApi(openAiApi).build();
		OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder().model("gpt-4.1-mini");
		if (config.temperature() != null) {
			optionsBuilder.temperature(config.temperature());
		}
		if (config.topP() != null) {
			optionsBuilder.topP(config.topP());
		}
		ChatClient springAiChatClient = ChatClient.builder(Objects.requireNonNull(chatModel))
				.defaultOptions(Objects.requireNonNull(optionsBuilder.build()))
				.build();
		return new PlanningChatClient(springAiChatClient);
	}

	private void ensureApiKeyPresent() {
		if (OPENAI_API_KEY == null || OPENAI_API_KEY.isBlank()) {
			throw new IllegalStateException("Missing OPENAI_API_KEY environment variable. Please export OPENA_API_KEY before running the tests.");
		}
	}
}

