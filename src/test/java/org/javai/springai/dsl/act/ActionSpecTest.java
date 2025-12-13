package org.javai.springai.dsl.act;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActionSpecTest {

	@Test
	void rendersSxlWithEmbeddedAndPlainParams() {
		ActionParameterSpec embedded = new ActionParameterSpec(
				"query",
				"org.javai.springai.dsl.sql.Query",
				"sxl-sql:Query",
				"SQL query payload",
				"sxl-sql"
		);
		ActionParameterSpec plain = new ActionParameterSpec(
				"note",
				"java.lang.String",
				"String",
				"Additional note",
				null
		);
		ActionSpec spec = new ActionSpec(
				"fetchOrders",
				"Fetch orders via SQL",
				List.of(embedded, plain)
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
		ActionParameterSpec withDsl = new ActionParameterSpec(
				"query",
				"org.javai.springai.dsl.sql.Query",
				"sxl-sql:Query",
				"SQL query payload",
				"sxl-sql"
		);
		ActionParameterSpec withoutDsl = new ActionParameterSpec(
				"note",
				"java.lang.String",
				"String",
				"Additional note",
				null
		);
		ActionSpec spec = new ActionSpec(
				"fetchOrders",
				"Fetch orders via SQL",
				List.of(withDsl, withoutDsl)
		);

		ArrayNode array = ActionSpecJsonMapper.toJsonArray(List.of(spec));
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
