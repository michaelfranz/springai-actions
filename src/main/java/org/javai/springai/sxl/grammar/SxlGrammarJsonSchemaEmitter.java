package org.javai.springai.sxl.grammar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Emits a lightweight JSON schema-like summary from an {@link SxlGrammar}.
 * Not a full JSON Schema, but sufficient for prompt inclusion in JSON mode.
 */
public final class SxlGrammarJsonSchemaEmitter {

	private static final ObjectMapper mapper = new ObjectMapper();

	private SxlGrammarJsonSchemaEmitter() {}

	public static ObjectNode emit(SxlGrammar grammar) {
		ObjectNode root = mapper.createObjectNode();
		root.put("dslId", grammar.dsl().id());
		root.put("description", grammar.dsl().description());

		ArrayNode symbols = root.putArray("symbols");
		grammar.symbols().forEach((name, def) -> {
			ObjectNode s = symbols.addObject();
			s.put("name", name);
			s.put("description", def.description());
			s.put("kind", def.kind().name().toLowerCase());
			ArrayNode params = s.putArray("params");
			def.params().forEach(p -> {
				ObjectNode pNode = params.addObject();
				pNode.put("name", p.name());
				pNode.put("type", p.type());
				pNode.put("cardinality", p.cardinality().name());
				if (p.description() != null) {
					pNode.put("description", p.description());
				}
				if (p.allowedSymbols() != null && !p.allowedSymbols().isEmpty()) {
					ArrayNode allowed = pNode.putArray("allowedSymbols");
					p.allowedSymbols().forEach(allowed::add);
				}
			});
		});

		return root;
	}
}
