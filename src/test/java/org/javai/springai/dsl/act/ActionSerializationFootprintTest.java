package org.javai.springai.dsl.act;

import static java.util.Arrays.stream;
import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Reports serialization footprint (bytes and rough tokens) between JSON specs and S-expression (SXL) specs
 * across multiple scenarios. This is diagnostic only; it does not assert which is smaller.
 *
 * Note: token counting here is an approximation (whitespace split) intended only for relative comparison.
 */
class ActionSerializationFootprintTest {

	private static final ObjectMapper mapper = new ObjectMapper();

	@Test
	void reportFootprintsAcrossScenarios() throws Exception {
		List<Scenario> scenarios = List.of(
				new Scenario("simple-no-embed", ActionSerializationFootprintTest::simpleSpec),
				new Scenario("single-embed-simple", () -> singleEmbedSpec(false)),
				new Scenario("single-embed-complex-sql", () -> singleEmbedSpec(true)),
				new Scenario("multi-embed", ActionSerializationFootprintTest::multiEmbedSpec),
				new Scenario("many-params", ActionSerializationFootprintTest::manyParamsSpec)
		);

		for (Scenario scenario : scenarios) {
			List<ActionSpec> specs = scenario.specSupplier().get();
			String json = mapper.writeValueAsString(ActionSpecJsonMapper.toJsonArray(specs));
			String sxl = specs.stream().map(ActionSpec::toSxl).reduce((a, b) -> a + " " + b).orElse("");

			int jsonBytes = json.getBytes(StandardCharsets.UTF_8).length;
			int sxlBytes = sxl.getBytes(StandardCharsets.UTF_8).length;

			int jsonTokens = roughTokenCount(json);
			int sxlTokens = roughTokenCount(sxl);

			assertThat(jsonBytes).isPositive();
			assertThat(sxlBytes).isPositive();
			assertThat(jsonTokens).isPositive();
			assertThat(sxlTokens).isPositive();

			System.out.printf("scenario=%s jsonBytes=%d sxlBytes=%d jsonTokens=%d sxlTokens=%d%n",
					scenario.name(), jsonBytes, sxlBytes, jsonTokens, sxlTokens);
		}
	}

	private static List<ActionSpec> simpleSpec() {
		ActionParameterSpec p = new ActionParameterSpec(
				"note", "java.lang.String", "String", "Note content", null);
		return List.of(new ActionSpec("addNote", "Add a note", List.of(p)));
	}

	private static List<ActionSpec> singleEmbedSpec(boolean complexSql) {
		ActionParameterSpec embedded = new ActionParameterSpec(
				"payload",
				"org.javai.springai.dsl.sql.Query",
				"sxl-sql:Query",
				complexSql ? "Complex SQL payload" : "SQL payload",
				"sxl-sql"
		);
		ActionParameterSpec plain = new ActionParameterSpec(
				"note",
				"java.lang.String",
				"String",
				"Additional note",
				null
		);
		return List.of(new ActionSpec(
				complexSql ? "fetchOrdersComplex" : "fetchOrders",
				"Fetch orders via SQL",
				complexSql ? List.of(embedded, complexSqlParam()) : List.of(embedded, plain)
		));
	}

	private static ActionParameterSpec complexSqlParam() {
		String complexDesc = "SQL payload with joins, filters, grouping, ordering, pagination";
		return new ActionParameterSpec(
				"payload2",
				"org.javai.springai.dsl.sql.Query",
				"sxl-sql:Query",
				complexDesc,
				"sxl-sql"
		);
	}

	private static List<ActionSpec> multiEmbedSpec() {
		ActionParameterSpec sql = new ActionParameterSpec(
				"sqlQuery", "org.javai.springai.dsl.sql.Query", "sxl-sql:Query", "SQL payload", "sxl-sql");
		ActionParameterSpec plan = new ActionParameterSpec(
				"subPlan", "org.javai.springai.dsl.plan.Plan", "sxl-plan:Plan", "Nested plan", "sxl-plan");
		ActionParameterSpec note = new ActionParameterSpec(
				"note", "java.lang.String", "String", "Extra note", null);
		return List.of(new ActionSpec("multiEmbedAction", "Do multiple things", List.of(sql, plan, note)));
	}

	private static List<ActionSpec> manyParamsSpec() {
		List<ActionParameterSpec> params = Stream.of(
						new ActionParameterSpec("p1", "java.lang.String", "String", "param1", null),
						new ActionParameterSpec("p2", "java.lang.String", "String", "param2", null),
						new ActionParameterSpec("p3", "java.lang.String", "String", "param3", null),
						new ActionParameterSpec("p4", "java.lang.String", "String", "param4", null),
						new ActionParameterSpec("p5", "java.lang.String", "String", "param5", null),
						new ActionParameterSpec("p6", "java.lang.String", "String", "param6", null),
						new ActionParameterSpec("p7", "java.lang.String", "String", "param7", null),
						new ActionParameterSpec("p8", "java.lang.String", "String", "param8", null),
						new ActionParameterSpec("p9", "java.lang.String", "String", "param9", null),
						new ActionParameterSpec("payload", "org.javai.springai.dsl.sql.Query", "sxl-sql:Query", "SQL payload", "sxl-sql")
				)
				.toList();
		return List.of(new ActionSpec("manyParams", "Action with many params", params));
	}

	private static int roughTokenCount(String text) {
		// Simple approximation: split on whitespace; good enough for relative comparison.
		return (int) stream(text.trim().split("\\s+"))
				.filter(s -> !s.isEmpty())
				.count();
	}

	private record Scenario(String name, Supplier<List<ActionSpec>> specSupplier) {}
}
