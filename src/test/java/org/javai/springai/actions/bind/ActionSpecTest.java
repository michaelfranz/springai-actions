package org.javai.springai.actions.bind;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.javai.springai.actions.internal.bind.ActionDescriptor;
import org.javai.springai.actions.internal.bind.ActionDescriptorJsonMapper;
import org.javai.springai.actions.internal.bind.ActionParameterDescriptor;
import org.junit.jupiter.api.Test;

class ActionSpecTest {

	@Test
	void rendersSxlWithEmbeddedAndPlainParams() {
		ActionParameterDescriptor embedded = new ActionParameterDescriptor(
				"query",
				"org.javai.springai.dsl.sql.Query",
				"sxl-sql:Query",
				"SQL query payload",
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
		ActionDescriptor spec = new ActionDescriptor(
				"fetchOrders",
				"Fetch orders via SQL",
				List.of(embedded, plain),
				null
		);

		String sxl = spec.toSxl();

		assertThat(sxl)
				.contains("(PS fetchOrders \"Fetch orders via SQL\"")
				.contains("(query (EMBED sxl-sql ...))")
				.contains("(note String)")
				.endsWith("))");
	}

	@Test
	void rendersJsonWithDslIdWhenPresent() {
		ActionParameterDescriptor withDsl = new ActionParameterDescriptor(
				"query",
				"org.javai.springai.dsl.sql.Query",
				"sxl-sql:Query",
				"SQL query payload",
				"sxl-sql",
				new String[0],
				"",
				false,
				new String[0]
		);
		ActionParameterDescriptor withoutDsl = new ActionParameterDescriptor(
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
		ActionDescriptor spec = new ActionDescriptor(
				"fetchOrders",
				"Fetch orders via SQL",
				List.of(withDsl, withoutDsl),
				null
		);

		ArrayNode array = ActionDescriptorJsonMapper.toJsonArray(List.of(spec));
		assertThat(array).hasSize(1);
		ObjectNode json = (ObjectNode) array.get(0);

		assertThat(json.get("id").asText()).isEqualTo("fetchOrders");
		assertThat(json.get("description").asText()).isEqualTo("Fetch orders via SQL");

		var params = json.get("parameters");
		assertThat(params.size()).isEqualTo(2);
		assertThat(params.get(0).get("name").asText()).isEqualTo("query");
		assertThat(params.get(0).get("type").asText()).isEqualTo("org.javai.springai.dsl.sql.Query");
		assertThat(params.get(0).get("typeId").asText()).isEqualTo("sxl-sql:Query");
		assertThat(params.get(0).get("dslId").asText()).isEqualTo("sxl-sql");

		assertThat(params.get(1).get("name").asText()).isEqualTo("note");
		assertThat(params.get(1).get("type").asText()).isEqualTo("java.lang.String");
		assertThat(params.get(1).get("typeId").asText()).isEqualTo("String");
		assertThat(params.get(1).has("dslId")).isFalse();
	}
}
