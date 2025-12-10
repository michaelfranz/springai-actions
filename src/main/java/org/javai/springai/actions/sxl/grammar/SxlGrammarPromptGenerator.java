package org.javai.springai.actions.sxl.grammar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Generates system prompt content from DSL grammar definitions.
 * 
 * This class extracts DSL-specific rules and formats them into a string
 * that can be included in a system prompt to inform an LLM about the
 * structure of valid DSL expressions.
 */
public class SxlGrammarPromptGenerator implements SxlGrammarVisitor<String> {

	private static final String TEMPLATE_RESOURCE = "sxl-dsl-prompt-template.txt";

	/**
	 * Generate system prompt content for the given grammar.
	 * 
	 * @param grammar the DSL grammar to generate prompt content for
	 * @return a string containing the DSL-specific rules and structure
	 */
	public String generate(SxlGrammar grammar) {
		String template = loadTemplate();
		
		// Get DSL metadata
		DslMetadata dsl = grammar.dsl();
		String dslId = dsl != null ? dsl.id() : "";
		String dslVersion = dsl != null ? dsl.version() : "";
		String dslDescription = dsl != null ? dsl.description() : "";
		
		// Format each section
		String symbolDefinitions = formatSymbolDefinitions(grammar);
		String literalDefinitions = formatLiteralDefinitions(grammar);
		String identifierRules = formatIdentifierRules(grammar);
		String reservedSymbols = formatReservedSymbols(grammar);
		String embeddingConfig = formatEmbeddingConfig(grammar);
		String globalConstraints = formatGlobalConstraints(grammar);
		
		// Replace placeholders in template
		return template
				.replace("{DSL_ID}", dslId)
				.replace("{DSL_VERSION}", dslVersion)
				.replace("{DSL_DESCRIPTION}", dslDescription)
				.replace("{SYMBOL_DEFINITIONS}", symbolDefinitions)
				.replace("{LITERAL_DEFINITIONS}", literalDefinitions)
				.replace("{IDENTIFIER_RULES}", identifierRules)
				.replace("{RESERVED_SYMBOLS}", reservedSymbols)
				.replace("{EMBEDDING_CONFIG}", embeddingConfig)
				.replace("{GLOBAL_CONSTRAINTS}", globalConstraints);
	}

	private String loadTemplate() {
		try (InputStream stream = getClass().getClassLoader()
				.getResourceAsStream(TEMPLATE_RESOURCE)) {
			if (stream == null) {
				throw new IllegalStateException(
						"Template resource not found: " + TEMPLATE_RESOURCE);
			}
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(
					"Failed to load template: " + TEMPLATE_RESOURCE, e);
		}
	}

	private String formatSymbolDefinitions(SxlGrammar grammar) {
		if (grammar.symbols() == null || grammar.symbols().isEmpty()) {
			return "No symbols defined.";
		}
		
		StringBuilder sb = new StringBuilder();
		grammar.symbols().entrySet().stream()
				.sorted(java.util.Map.Entry.comparingByKey())
				.forEach(entry -> {
					String symbolName = entry.getKey();
					SymbolDefinition symbol = entry.getValue();
					
					sb.append(symbolName);
					sb.append(":\n");
					sb.append("  Description: ").append(symbol.description()).append("\n");
					sb.append("  Kind: ").append(symbol.kind()).append("\n");
					
					if (symbol.params() != null && !symbol.params().isEmpty()) {
						sb.append("  Parameters:\n");
						for (ParameterDefinition param : symbol.params()) {
							sb.append("    - ").append(param.name());
							sb.append(" (type: ").append(param.type());
							sb.append(", cardinality: ").append(param.cardinality()).append(")");
							if (param.description() != null && !param.description().isEmpty()) {
								sb.append(": ").append(param.description());
							}
							if (param.allowedSymbols() != null && !param.allowedSymbols().isEmpty()) {
								sb.append("\n      Allowed symbols: ").append(String.join(", ", param.allowedSymbols()));
							}
							sb.append("\n");
						}
					}
					
					if (symbol.constraints() != null && !symbol.constraints().isEmpty()) {
						sb.append("  Constraints:\n");
						for (SymbolConstraint constraint : symbol.constraints()) {
							sb.append("    - ").append(constraint.rule());
							if (constraint.target() != null) {
								sb.append(" (target: ").append(constraint.target()).append(")");
							}
							sb.append("\n");
						}
					}
					
					if (symbol.examples() != null && !symbol.examples().isEmpty()) {
						sb.append("  Examples:\n");
						for (Example example : symbol.examples()) {
							if (example.label() != null && !example.label().isEmpty()) {
								sb.append("    - ").append(example.label()).append(":\n");
							}
							if (example.code() != null && !example.code().isEmpty()) {
								String[] lines = example.code().split("\n");
								for (String line : lines) {
									sb.append("      ").append(line).append("\n");
								}
							}
						}
					}
					
					sb.append("\n");
				});
		
		return sb.toString().trim();
	}

	private String formatLiteralDefinitions(SxlGrammar grammar) {
		if (grammar.literals() == null) {
			return "No literal definitions.";
		}
		
		StringBuilder sb = new StringBuilder();
		LiteralDefinitions literals = grammar.literals();
		
		if (literals.string() != null) {
			sb.append("string:\n");
			if (literals.string().regex() != null && !literals.string().regex().isEmpty()) {
				sb.append("  Regex: ").append(literals.string().regex()).append("\n");
			}
			if (literals.string().values() != null && !literals.string().values().isEmpty()) {
				sb.append("  Values: ").append(literals.string().values().stream()
						.map(v -> v == null ? "null" : v.toString())
						.collect(Collectors.joining(", "))).append("\n");
			}
			sb.append("\n");
		}
		
		if (literals.number() != null) {
			sb.append("number:\n");
			if (literals.number().regex() != null && !literals.number().regex().isEmpty()) {
				sb.append("  Regex: ").append(literals.number().regex()).append("\n");
			}
			if (literals.number().values() != null && !literals.number().values().isEmpty()) {
				sb.append("  Values: ").append(literals.number().values().stream()
						.map(v -> v == null ? "null" : v.toString())
						.collect(Collectors.joining(", "))).append("\n");
			}
			sb.append("\n");
		}
		
		if (literals.boolean_() != null) {
			sb.append("boolean:\n");
			if (literals.boolean_().regex() != null && !literals.boolean_().regex().isEmpty()) {
				sb.append("  Regex: ").append(literals.boolean_().regex()).append("\n");
			}
			if (literals.boolean_().values() != null && !literals.boolean_().values().isEmpty()) {
				sb.append("  Values: ").append(literals.boolean_().values().stream()
						.map(v -> v == null ? "null" : v.toString())
						.collect(Collectors.joining(", "))).append("\n");
			}
			sb.append("\n");
		}
		
		if (literals.null_() != null) {
			sb.append("null:\n");
			if (literals.null_().regex() != null && !literals.null_().regex().isEmpty()) {
				sb.append("  Regex: ").append(literals.null_().regex()).append("\n");
			}
			if (literals.null_().values() != null && !literals.null_().values().isEmpty()) {
				sb.append("  Values: ").append(literals.null_().values().stream()
						.map(v -> v == null ? "null" : v.toString())
						.collect(Collectors.joining(", "))).append("\n");
			}
			sb.append("\n");
		}
		
		String result = sb.toString().trim();
		return result.isEmpty() ? "No literal definitions." : result;
	}

	private String formatIdentifierRules(SxlGrammar grammar) {
		if (grammar.identifier() == null) {
			return "No identifier rules defined.";
		}
		
		StringBuilder sb = new StringBuilder();
		if (grammar.identifier().description() != null && !grammar.identifier().description().isEmpty()) {
			sb.append(grammar.identifier().description()).append("\n");
		}
		if (grammar.identifier().pattern() != null && !grammar.identifier().pattern().isEmpty()) {
			sb.append("Pattern: ").append(grammar.identifier().pattern());
		}
		
		return sb.toString().trim();
	}

	private String formatReservedSymbols(SxlGrammar grammar) {
		if (grammar.reservedSymbols() == null || grammar.reservedSymbols().isEmpty()) {
			return "No reserved symbols.";
		}
		
		return String.join(", ", grammar.reservedSymbols());
	}

	private String formatEmbeddingConfig(SxlGrammar grammar) {
		if (grammar.embedding() == null) {
			return "Embedding not configured.";
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append("Enabled: ").append(grammar.embedding().enabled());
		if (grammar.embedding().symbol() != null && !grammar.embedding().symbol().isEmpty()) {
			sb.append("\nSymbol: ").append(grammar.embedding().symbol());
		}
		
		return sb.toString();
	}

	private String formatGlobalConstraints(SxlGrammar grammar) {
		if (grammar.constraints() == null || grammar.constraints().isEmpty()) {
			return "No global constraints.";
		}
		
		StringBuilder sb = new StringBuilder();
		for (GlobalConstraint constraint : grammar.constraints()) {
			sb.append("- ").append(constraint.rule());
			if (constraint.symbol() != null && !constraint.symbol().isEmpty()) {
				sb.append(" (symbol: ").append(constraint.symbol()).append(")");
			}
			if (constraint.target() != null && !constraint.target().isEmpty()) {
				sb.append(" (target: ").append(constraint.target()).append(")");
			}
			if (constraint.dependsOn() != null && !constraint.dependsOn().isEmpty()) {
				sb.append(" (depends on: ").append(constraint.dependsOn()).append(")");
			}
			sb.append("\n");
		}
		
		return sb.toString().trim();
	}

	@Override
	public String visitSxlGrammar(SxlGrammar grammar) {
		return grammar.toString();
	}

	@Override
	public String visitDslMetadata(DslMetadata metadata) {
		return metadata.toString();
	}

	@Override
	public String visitSymbolDefinition(SymbolDefinition symbol) {
		return symbol.toString();
	}

	@Override
	public String visitParameterDefinition(ParameterDefinition parameter) {
		return parameter.toString();
	}

	@Override
	public String visitSymbolConstraint(SymbolConstraint constraint) {
		return constraint.toString();
	}

	@Override
	public String visitExample(Example example) {
		return example.toString();
	}

	@Override
	public String visitLiteralDefinitions(LiteralDefinitions literals) {
		return literals.toString();
	}

	@Override
	public String visitLiteralRule(LiteralRule rule) {
		return rule.toString();
	}

	@Override
	public String visitIdentifierRule(IdentifierRule rule) {
		return rule.toString();
	}

	@Override
	public String visitIdentifierRules(IdentifierRules rules) {
		return rules.toString();
	}

	@Override
	public String visitEmbeddingConfig(EmbeddingConfig config) {
		return config.toString();
	}

	@Override
	public String visitGlobalConstraint(GlobalConstraint constraint) {
		return constraint.toString();
	}

	@Override
	public String visitLlmSpecs(LlmSpecs specs) {
		return specs.toString();
	}

	@Override
	public String visitLlmDefaults(LlmDefaults defaults) {
		return defaults.toString();
	}

	@Override
	public String visitLlmProviderDefaults(LlmProviderDefaults defaults) {
		return defaults.toString();
	}

	@Override
	public String visitLlmModelOverrides(LlmModelOverrides overrides) {
		return overrides.toString();
	}

	@Override
	public String visitLlmOverrides(LlmOverrides overrides) {
		return overrides.toString();
	}

	@Override
	public String visitLlmProfile(LlmProfile profile) {
		return profile.toString();
	}
}
