package org.javai.springai.dsl.prompt;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.javai.springai.sxl.grammar.SxlGrammar;
import org.javai.springai.sxl.grammar.SxlGrammarParser;

/**
 * Loads DSL guidance from SxlGrammar resources.
 */
public class GrammarBackedDslGuidanceProvider implements DslGuidanceProvider, DslGrammarSource {

	private final Map<String, SxlGrammar> grammars;

	/**
	 * Load grammars from resource paths; the DSL id is taken from each grammar's dsl.id.
	 */
	public GrammarBackedDslGuidanceProvider(List<String> grammarResourcePaths, ClassLoader loader) {
		SxlGrammarParser parser = new SxlGrammarParser();
		this.grammars = new LinkedHashMap<>();
		for (String path : grammarResourcePaths) {
			try (InputStream is = loader.getResourceAsStream(path)) {
				if (is == null) {
					throw new IllegalArgumentException("Resource not found: " + path);
				}
				SxlGrammar grammar = parser.parse(is);
				String dslId = grammar.dsl().id();
				if (grammars.putIfAbsent(dslId, grammar) != null) {
					throw new IllegalStateException("Duplicate DSL id detected while loading grammars: " + dslId);
				}
			} catch (Exception e) {
				throw new IllegalStateException("Failed to load grammar from: " + path, e);
			}
		}
	}

	@Override
	public Optional<String> guidanceFor(String dslId, String providerId, String modelId) {
		SxlGrammar grammar = grammars.get(dslId);
		if (grammar == null || grammar.llmSpecs() == null || grammar.llmSpecs().defaults() == null) {
			return Optional.empty();
		}
		// model-specific override
		if (providerId != null && modelId != null) {
			var providerModels = grammar.llmSpecs().models().get(providerId);
			if (providerModels != null) {
				var modelOverrides = providerModels.get(modelId);
				if (modelOverrides != null && modelOverrides.overrides() != null && modelOverrides.overrides().guidance() != null) {
					return Optional.of(modelOverrides.overrides().guidance());
				}
			}
		}
		// provider-level
		if (providerId != null) {
			var providerDefaults = grammar.llmSpecs().providerDefaults().get(providerId);
			if (providerDefaults != null && providerDefaults.guidance() != null) {
				return Optional.of(providerDefaults.guidance());
			}
		}
		// default
		return Optional.ofNullable(grammar.llmSpecs().defaults().guidance());
	}

	@Override
	public Optional<SxlGrammar> grammarFor(String dslId) {
		return Optional.ofNullable(grammars.get(dslId));
	}
}
