package org.javai.springai.actions.sxl.meta;

import java.util.List;
import java.util.Map;

/**
 * Represents the complete AST of a meta-grammar DSL definition.
 * This is the root object created by parsing a meta-grammar YAML file.
 */
public record SxlGrammar(
	String metaGrammarVersion,
	DslMetadata dsl,
	Map<String, SymbolDefinition> symbols,
	LiteralDefinitions literals,
	IdentifierRule identifier,
	List<String> reservedSymbols,
	EmbeddingConfig embedding,
	List<GlobalConstraint> constraints,
	LlmSpecs llmSpecs
) {
}

