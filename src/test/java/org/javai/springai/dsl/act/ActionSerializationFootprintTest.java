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
			List<ActionDescriptor> descriptors = scenario.specSupplier().get();
			String json = mapper.writeValueAsString(ActionDescriptorJsonMapper.toJsonArray(descriptors));
			String sxl = descriptors.stream().map(ActionDescriptor::toSxl).reduce((a, b) -> a + " " + b).orElse("");

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

	private static List<ActionDescriptor> simpleSpec() {
		ActionParameterDescriptor p = new ActionParameterDescriptor(
				"note", "java.lang.String", "String", "Note content", null, new String[0], "", false, new String[0]);
		return List.of(new ActionDescriptor("addNote", "Add a note", List.of(p), null));
	}

	private static List<ActionDescriptor> singleEmbedSpec(boolean complexSql) {
		ActionParameterDescriptor embedded = new ActionParameterDescriptor(
				"payload",
				"org.javai.springai.dsl.sql.Query",
				"sxl-sql:Query",
				complexSql ? "Complex SQL payload" : "SQL payload",
				"sxl-sql",
				new String[0],
				"",
				false,
				new String[0]
		);
		ActionParameterDescriptor plain = new ActionParameterDescriptor(
				"note",
				"java.lang.String",
				"String",
				"Additional note",
				null,
				new String[0],
				"",
				false,
				new String[0]
		);
		return List.of(new ActionDescriptor(
				complexSql ? "fetchOrdersComplex" : "fetchOrders",
				"Fetch orders via SQL",
				complexSql ? List.of(embedded, complexSqlParam()) : List.of(embedded, plain),
				null
		));
	}

	private static ActionParameterDescriptor complexSqlParam() {
		String complexDesc = "SQL payload with joins, filters, grouping, ordering, pagination";
		return new ActionParameterDescriptor(
				"payload2",
				"org.javai.springai.dsl.sql.Query",
				"sxl-sql:Query",
				complexDesc,
				"sxl-sql",
				new String[0],
				"",
				false,
				new String[0]
		);
	}

	private static List<ActionDescriptor> multiEmbedSpec() {
		ActionParameterDescriptor sql = new ActionParameterDescriptor(
				"sqlQuery", "org.javai.springai.dsl.sql.Query", "sxl-sql:Query", "SQL payload", "sxl-sql",
				new String[0], "", false, new String[0]);
		ActionParameterDescriptor plan = new ActionParameterDescriptor(
				"subPlan", "org.javai.springai.dsl.plan.Plan", "sxl-plan:Plan", "Nested plan", "sxl-plan",
				new String[0], "", false, new String[0]);
		ActionParameterDescriptor note = new ActionParameterDescriptor(
				"note", "java.lang.String", "String", "Extra note", null,
				new String[0], "", false, new String[0]);
		return List.of(new ActionDescriptor("multiEmbedAction", "Do multiple things", List.of(sql, plan, note), null));
	}

	private static List<ActionDescriptor> manyParamsSpec() {
		List<ActionParameterDescriptor> params = Stream.of(
						new ActionParameterDescriptor("p1", "java.lang.String", "String", "param1", null, new String[0], "", false, new String[0]),
						new ActionParameterDescriptor("p2", "java.lang.String", "String", "param2", null, new String[0], "", false, new String[0]),
						new ActionParameterDescriptor("p3", "java.lang.String", "String", "param3", null, new String[0], "", false, new String[0]),
						new ActionParameterDescriptor("p4", "java.lang.String", "String", "param4", null, new String[0], "", false, new String[0]),
						new ActionParameterDescriptor("p5", "java.lang.String", "String", "param5", null, new String[0], "", false, new String[0]),
						new ActionParameterDescriptor("p6", "java.lang.String", "String", "param6", null, new String[0], "", false, new String[0]),
						new ActionParameterDescriptor("p7", "java.lang.String", "String", "param7", null, new String[0], "", false, new String[0]),
						new ActionParameterDescriptor("p8", "java.lang.String", "String", "param8", null, new String[0], "", false, new String[0]),
						new ActionParameterDescriptor("p9", "java.lang.String", "String", "param9", null, new String[0], "", false, new String[0]),
						new ActionParameterDescriptor("payload", "org.javai.springai.dsl.sql.Query", "sxl-sql:Query", "SQL payload", "sxl-sql",
								new String[0], "", false, new String[0])
				)
				.toList();
		return List.of(new ActionDescriptor("manyParams", "Action with many params", params, null));
	}

	private static int roughTokenCount(String text) {
		// Simple approximation: split on whitespace; good enough for relative comparison.
		return (int) stream(text.trim().split("\\s+"))
				.filter(s -> !s.isEmpty())
				.count();
	}

	private record Scenario(String name, Supplier<List<ActionDescriptor>> specSupplier) {}
}
