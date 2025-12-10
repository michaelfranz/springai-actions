package org.javai.springai.actions.sxl.grammar;

/**
 * Visitor interface for traversing the SxlGrammar AST.
 * 
 * @param <R> The return type of visitor methods
 */
public interface SxlGrammarVisitor<R> {
	
	default R visitSxlGrammar(SxlGrammar grammar) { return null; }
	
	default R visitDslMetadata(DslMetadata metadata) { return null; }
	
	default R visitSymbolDefinition(SymbolDefinition symbol) { return null; }
	
	default R visitParameterDefinition(ParameterDefinition parameter) { return null; }
	
	default R visitSymbolConstraint(SymbolConstraint constraint) { return null; }
	
	default R visitExample(Example example) { return null; }
	
	default R visitLiteralDefinitions(LiteralDefinitions literals) { return null; }
	
	default R visitLiteralRule(LiteralRule rule) { return null; }
	
	default R visitIdentifierRule(IdentifierRule rule) { return null; }
	
	default R visitIdentifierRules(IdentifierRules rules) { return null; }
	
	default R visitEmbeddingConfig(EmbeddingConfig config) { return null; }
	
	default R visitGlobalConstraint(GlobalConstraint constraint) { return null; }
	
	default R visitLlmSpecs(LlmSpecs specs) { return null; }
	
	default R visitLlmDefaults(LlmDefaults defaults) { return null; }
	
	default R visitLlmProviderDefaults(LlmProviderDefaults defaults) { return null; }
	
	default R visitLlmModelOverrides(LlmModelOverrides overrides) { return null; }
	
	default R visitLlmOverrides(LlmOverrides overrides) { return null; }
	
	default R visitLlmProfile(LlmProfile profile) { return null; }
}

