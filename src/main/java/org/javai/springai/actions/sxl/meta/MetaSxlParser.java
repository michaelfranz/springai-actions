package org.javai.springai.actions.sxl.meta;

import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.javai.springai.actions.sxl.SxlParseException;
import org.yaml.snakeyaml.Yaml;

/**
 * Parser for meta-grammar YAML files.
 * Parses YAML and creates an SxlGrammar AST instance.
 */
public class MetaSxlParser {

	private final Yaml yaml = new Yaml();

	/**
	 * Parse a meta-grammar YAML file from a path.
	 */
	public SxlGrammar parse(Path path) {
		try (var reader = Files.newBufferedReader(path)) {
			return parse(reader);
		} catch (Exception e) {
			throw new SxlParseException("Failed to parse meta-grammar from path: " + path, e);
		}
	}

	/**
	 * Parse a meta-grammar YAML file from an input stream.
	 */
	public SxlGrammar parse(InputStream inputStream) {
		try {
			Map<String, Object> data = yaml.load(inputStream);
			return buildGrammar(data);
		} catch (Exception e) {
			throw new SxlParseException("Failed to parse meta-grammar from input stream", e);
		}
	}

	/**
	 * Parse a meta-grammar YAML file from a reader.
	 */
	public SxlGrammar parse(Reader reader) {
		try {
			Map<String, Object> data = yaml.load(reader);
			return buildGrammar(data);
		} catch (Exception e) {
			throw new SxlParseException("Failed to parse meta-grammar from reader", e);
		}
	}

	/**
	 * Parse a meta-grammar YAML file from a string.
	 */
	public SxlGrammar parseString(String yamlContent) {
		try {
			Map<String, Object> data = yaml.load(yamlContent);
			return buildGrammar(data);
		} catch (Exception e) {
			throw new SxlParseException("Failed to parse meta-grammar from string", e);
		}
	}

	@SuppressWarnings("unchecked")
	private SxlGrammar buildGrammar(Map<String, Object> data) {
		String metaGrammarVersion = (String) data.get("meta_grammar_version");
		
		Map<String, Object> dslMap = (Map<String, Object>) data.get("dsl");
		DslMetadata dsl = buildDslMetadata(dslMap);
		
		Map<String, Object> symbolsMap = (Map<String, Object>) data.get("symbols");
		Map<String, SymbolDefinition> symbols = buildSymbols(symbolsMap);
		
		Map<String, Object> literalsMap = (Map<String, Object>) data.get("literals");
		LiteralDefinitions literals = buildLiterals(literalsMap);
		
		Map<String, Object> identifierMap = (Map<String, Object>) data.get("identifier");
		IdentifierRule identifier = buildIdentifier(identifierMap);
		
		List<String> reservedSymbols = (List<String>) data.get("reserved_symbols");
		
		Map<String, Object> embeddingMap = (Map<String, Object>) data.get("embedding");
		EmbeddingConfig embedding = buildEmbedding(embeddingMap);
		
		List<Map<String, Object>> constraintsList = (List<Map<String, Object>>) data.get("constraints");
		List<GlobalConstraint> constraints = buildGlobalConstraints(constraintsList);
		
		Map<String, Object> llmSpecsMap = (Map<String, Object>) data.get("llm_specs");
		LlmSpecs llmSpecs = buildLlmSpecs(llmSpecsMap);
		
		return new SxlGrammar(
			metaGrammarVersion,
			dsl,
			symbols,
			literals,
			identifier,
			reservedSymbols != null ? reservedSymbols : List.of(),
			embedding,
			constraints != null ? constraints : List.of(),
			llmSpecs
		);
	}

	private DslMetadata buildDslMetadata(Map<String, Object> dslMap) {
		if (dslMap == null) {
			throw new SxlParseException("Missing required 'dsl' section");
		}
		return new DslMetadata(
			(String) dslMap.get("id"),
			(String) dslMap.get("description"),
			(String) dslMap.get("version")
		);
	}

	@SuppressWarnings("unchecked")
	private Map<String, SymbolDefinition> buildSymbols(Map<String, Object> symbolsMap) {
		if (symbolsMap == null) {
			return Map.of();
		}
		
		Map<String, SymbolDefinition> symbols = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : symbolsMap.entrySet()) {
			String symbolName = entry.getKey();
			
			// EMBED is a universal reserved symbol and cannot be defined by any DSL
			if ("EMBED".equals(symbolName)) {
				throw new SxlParseException(
					"Symbol 'EMBED' is a universal reserved symbol and cannot be defined in a DSL grammar. " +
					"EMBED is automatically available to all DSLs for embedding other DSLs.");
			}
			
			Map<String, Object> symbolData = (Map<String, Object>) entry.getValue();
			symbols.put(symbolName, buildSymbolDefinition(symbolData));
		}
		return symbols;
	}

	@SuppressWarnings("unchecked")
	private SymbolDefinition buildSymbolDefinition(Map<String, Object> symbolData) {
		String description = (String) symbolData.get("description");
		
		String kindStr = (String) symbolData.get("kind");
		SymbolKind kind = kindStr != null ? SymbolKind.valueOf(kindStr) : SymbolKind.node;
		
		List<Map<String, Object>> paramsList = (List<Map<String, Object>>) symbolData.get("params");
		List<ParameterDefinition> params = paramsList != null 
			? paramsList.stream().map(this::buildParameter).collect(Collectors.toList())
			: List.of();
		
		List<Map<String, Object>> constraintsList = (List<Map<String, Object>>) symbolData.get("constraints");
		List<SymbolConstraint> constraints = constraintsList != null
			? constraintsList.stream().map(this::buildSymbolConstraint).collect(Collectors.toList())
			: List.of();
		
		List<Map<String, Object>> examplesList = (List<Map<String, Object>>) symbolData.get("examples");
		List<Example> examples = examplesList != null
			? examplesList.stream().map(this::buildExample).collect(Collectors.toList())
			: List.of();
		
		return new SymbolDefinition(description, kind, params, constraints, examples);
	}

	@SuppressWarnings("unchecked")
	private ParameterDefinition buildParameter(Map<String, Object> paramData) {
		String name = (String) paramData.get("name");
		String description = (String) paramData.get("description");
		String type = (String) paramData.get("type");
		
		List<String> allowedSymbols = (List<String>) paramData.get("allowed_symbols");
		
		String cardinalityStr = (String) paramData.get("cardinality");
		Cardinality cardinality = cardinalityStr != null 
			? Cardinality.valueOf(cardinalityStr) 
			: Cardinality.required;
		
		Boolean ordered = (Boolean) paramData.get("ordered");
		if (ordered == null) {
			ordered = true;  // default
		}
		
		Map<String, Object> identifierRulesMap = (Map<String, Object>) paramData.get("identifier_rules");
		IdentifierRules identifierRules = identifierRulesMap != null
			? new IdentifierRules((String) identifierRulesMap.get("pattern"))
			: null;
		
		return new ParameterDefinition(
			name,
			description,
			type,
			allowedSymbols != null ? allowedSymbols : List.of(),
			cardinality,
			ordered,
			identifierRules
		);
	}

	@SuppressWarnings("unchecked")
	private SymbolConstraint buildSymbolConstraint(Map<String, Object> constraintData) {
		return new SymbolConstraint(
			(String) constraintData.get("rule"),
			(String) constraintData.get("target"),
			(String) constraintData.get("symbol"),
			(List<String>) constraintData.get("items"),
			(String) constraintData.get("when")
		);
	}

	private Example buildExample(Map<String, Object> exampleData) {
		return new Example(
			(String) exampleData.get("label"),
			(String) exampleData.get("code")
		);
	}

	@SuppressWarnings("unchecked")
	private LiteralDefinitions buildLiterals(Map<String, Object> literalsMap) {
		if (literalsMap == null) {
			return new LiteralDefinitions(null, null, null, null);
		}
		
		Map<String, Object> stringMap = (Map<String, Object>) literalsMap.get("string");
		LiteralRule string = stringMap != null 
			? new LiteralRule((String) stringMap.get("regex"), null)
			: null;
		
		Map<String, Object> numberMap = (Map<String, Object>) literalsMap.get("number");
		LiteralRule number = numberMap != null
			? new LiteralRule((String) numberMap.get("regex"), null)
			: null;
		
		Map<String, Object> booleanMap = (Map<String, Object>) literalsMap.get("boolean");
		LiteralRule boolean_ = booleanMap != null
			? new LiteralRule(null, (List<Object>) booleanMap.get("values"))
			: null;
		
		Map<String, Object> nullMap = (Map<String, Object>) literalsMap.get("null");
		LiteralRule null_ = nullMap != null
			? new LiteralRule(null, (List<Object>) nullMap.get("values"))
			: null;
		
		return new LiteralDefinitions(string, number, boolean_, null_);
	}

	private IdentifierRule buildIdentifier(Map<String, Object> identifierMap) {
		if (identifierMap == null) {
			return null;
		}
		return new IdentifierRule(
			(String) identifierMap.get("description"),
			(String) identifierMap.get("pattern")
		);
	}

	private EmbeddingConfig buildEmbedding(Map<String, Object> embeddingMap) {
		if (embeddingMap == null) {
			return null;
		}
		
		Boolean enabled = (Boolean) embeddingMap.get("enabled");
		String symbol = (String) embeddingMap.get("symbol");
		Boolean autoRegisterSymbol = (Boolean) embeddingMap.get("auto_register_symbol");
		
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> paramsList = (List<Map<String, Object>>) embeddingMap.get("params");
		List<ParameterDefinition> params = paramsList != null
			? paramsList.stream().map(this::buildParameter).collect(Collectors.toList())
			: List.of();
		
		return new EmbeddingConfig(enabled, symbol, autoRegisterSymbol, params);
	}

	private List<GlobalConstraint> buildGlobalConstraints(List<Map<String, Object>> constraintsList) {
		if (constraintsList == null) {
			return List.of();
		}
		
		List<GlobalConstraint> constraints = new ArrayList<>();
		for (Map<String, Object> constraintData : constraintsList) {
			constraints.add(new GlobalConstraint(
				(String) constraintData.get("rule"),
				(String) constraintData.get("target"),
				(String) constraintData.get("symbol"),
				(String) constraintData.get("depends_on")
			));
		}
		return constraints;
	}

	@SuppressWarnings("unchecked")
	private LlmSpecs buildLlmSpecs(Map<String, Object> llmSpecsMap) {
		if (llmSpecsMap == null) {
			return null;
		}
		
		Map<String, Object> defaultsMap = (Map<String, Object>) llmSpecsMap.get("defaults");
		LlmDefaults defaults = buildLlmDefaults(defaultsMap);
		
		Map<String, Object> providerDefaultsMap = (Map<String, Object>) llmSpecsMap.get("provider_defaults");
		Map<String, LlmProviderDefaults> providerDefaults = buildProviderDefaults(providerDefaultsMap);
		
		Map<String, Object> modelsMap = (Map<String, Object>) llmSpecsMap.get("models");
		Map<String, Map<String, LlmModelOverrides>> models = buildModels(modelsMap);
		
		Map<String, Object> profilesMap = (Map<String, Object>) llmSpecsMap.get("profiles");
		Map<String, LlmProfile> profiles = buildProfiles(profilesMap);
		
		return new LlmSpecs(defaults, providerDefaults, models, profiles);
	}

	private LlmDefaults buildLlmDefaults(Map<String, Object> defaultsMap) {
		if (defaultsMap == null) {
			return null;
		}
		return new LlmDefaults(
			(String) defaultsMap.get("style"),
			(Integer) defaultsMap.get("max_examples"),
			(Boolean) defaultsMap.get("include_constraints"),
			(Boolean) defaultsMap.get("include_symbol_summaries"),
			(Boolean) defaultsMap.get("include_literal_rules"),
			(Boolean) defaultsMap.get("include_identifier_rules"),
			(String) defaultsMap.get("formatting"),
			(Boolean) defaultsMap.get("enforce_canonical_form"),
			(Boolean) defaultsMap.get("preamble"),
			(Boolean) defaultsMap.get("postamble")
		);
	}

	@SuppressWarnings("unchecked")
	private Map<String, LlmProviderDefaults> buildProviderDefaults(Map<String, Object> providerDefaultsMap) {
		if (providerDefaultsMap == null) {
			return Map.of();
		}
		
		Map<String, LlmProviderDefaults> providerDefaults = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : providerDefaultsMap.entrySet()) {
			String provider = entry.getKey();
			Map<String, Object> providerData = (Map<String, Object>) entry.getValue();
			providerDefaults.put(provider, new LlmProviderDefaults(
				(String) providerData.get("style"),
				(Integer) providerData.get("max_examples")
			));
		}
		return providerDefaults;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Map<String, LlmModelOverrides>> buildModels(Map<String, Object> modelsMap) {
		if (modelsMap == null) {
			return Map.of();
		}
		
		Map<String, Map<String, LlmModelOverrides>> models = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : modelsMap.entrySet()) {
			String provider = entry.getKey();
			Map<String, Object> providerModels = (Map<String, Object>) entry.getValue();
			
			Map<String, LlmModelOverrides> modelOverrides = new LinkedHashMap<>();
			for (Map.Entry<String, Object> modelEntry : providerModels.entrySet()) {
				String modelName = modelEntry.getKey();
				Map<String, Object> modelData = (Map<String, Object>) modelEntry.getValue();
				Map<String, Object> overridesMap = (Map<String, Object>) modelData.get("overrides");
				LlmOverrides overrides = buildLlmOverrides(overridesMap);
				modelOverrides.put(modelName, new LlmModelOverrides(overrides));
			}
			models.put(provider, modelOverrides);
		}
		return models;
	}

	private LlmOverrides buildLlmOverrides(Map<String, Object> overridesMap) {
		if (overridesMap == null) {
			return null;
		}
		return new LlmOverrides(
			(String) overridesMap.get("style"),
			(Integer) overridesMap.get("max_examples"),
			(Boolean) overridesMap.get("include_constraints"),
			(String) overridesMap.get("formatting")
		);
	}

	@SuppressWarnings("unchecked")
	private Map<String, LlmProfile> buildProfiles(Map<String, Object> profilesMap) {
		if (profilesMap == null) {
			return Map.of();
		}
		
		Map<String, LlmProfile> profiles = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : profilesMap.entrySet()) {
			String profileName = entry.getKey();
			Map<String, Object> profileData = (Map<String, Object>) entry.getValue();
			profiles.put(profileName, new LlmProfile(
				(String) profileData.get("style"),
				(Boolean) profileData.get("include_constraints"),
				(Integer) profileData.get("max_examples")
			));
		}
		return profiles;
	}
}

