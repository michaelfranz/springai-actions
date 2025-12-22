package org.javai.springai.dsl.prompt;

import java.util.List;
import java.util.Optional;
import org.javai.springai.sxl.grammar.SxlGrammar;
import org.javai.springai.sxl.grammar.SxlGrammarRegistry;

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

	private final SxlGrammarRegistry registry;

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
		this.registry = SxlGrammarRegistry.create();
		// Auto-load universal grammar first, then user-specified grammars
		this.registry.registerUniversal(loader);
		this.registry.registerResources(grammarResourcePaths, loader);
	}

	@Override
	public Optional<String> guidanceFor(String dslId, String providerId, String modelId) {
		SxlGrammar grammar = registry.grammarFor(dslId).orElse(null);
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
		return registry.grammarFor(dslId);
	}
}
