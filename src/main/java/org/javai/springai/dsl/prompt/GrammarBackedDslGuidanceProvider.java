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
 * 
 * ARCHITECTURE: This provider automatically loads the universal grammar
 * (sxl-meta-grammar-universal.yml) FIRST, ensuring all DSLs receive
 * universal S-expression guidance. Domain-specific grammars are then
 * loaded, allowing them to override or extend universal guidance.
 * 
 * This design enables:
 * - DSL Independence: Each DSL works standalone with universal guidance
 * - Composability: Mix and match DSLs without coupling
 * - Consistency: All DSLs follow the same S-expression fundamentals
 */
public class GrammarBackedDslGuidanceProvider implements DslGuidanceProvider, DslGrammarSource {

	private final Map<String, SxlGrammar> grammars;
	private static final String UNIVERSAL_GRAMMAR_RESOURCE = "sxl-meta-grammar-universal.yml";

	/**
	 * Load grammars from resource paths; the DSL id is taken from each grammar's dsl.id.
	 * 
	 * The universal grammar (sxl-meta-grammar-universal.yml) is ALWAYS loaded first,
	 * ensuring universal guidance is available to all DSLs. User-specified grammars
	 * are loaded after in the order provided.
	 * 
	 * @param grammarResourcePaths paths to DSL grammars (universal grammar is auto-loaded)
	 * @param loader ClassLoader to load resources from
	 */
	public GrammarBackedDslGuidanceProvider(List<String> grammarResourcePaths, ClassLoader loader) {
		SxlGrammarParser parser = new SxlGrammarParser();
		this.grammars = new LinkedHashMap<>();
		
		// PHASE 2: Auto-load universal grammar FIRST
		loadGrammar(parser, UNIVERSAL_GRAMMAR_RESOURCE, loader);
		
		// Then load user-specified grammars
		for (String path : grammarResourcePaths) {
			loadGrammar(parser, path, loader);
		}
	}

	/**
	 * Helper method to load a single grammar and add it to the registry.
	 * 
	 * @param parser the grammar parser
	 * @param path the resource path to load
	 * @param loader the ClassLoader
	 * @throws IllegalStateException if loading or parsing fails
	 * @throws IllegalStateException if a duplicate DSL id is detected
	 */
	private void loadGrammar(SxlGrammarParser parser, String path, ClassLoader loader) {
			try (InputStream is = loader.getResourceAsStream(path)) {
				if (is == null) {
					throw new IllegalArgumentException("Resource not found: " + path);
				}
				SxlGrammar grammar = parser.parse(is);
				String dslId = grammar.dsl().id();
			
			// Skip if already loaded (e.g., universal grammar auto-loaded, then user explicitly loads it)
			if (grammars.containsKey(dslId)) {
				return;
			}
			
			grammars.put(dslId, grammar);
			} catch (Exception e) {
				throw new IllegalStateException("Failed to load grammar from: " + path, e);
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
