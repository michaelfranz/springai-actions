package org.javai.springai.actions.sxl.grammar;

/**
 * Default LLM specification settings.
 */
public record LlmDefaults(
	String style,  // strict | concise | verbose | explanatory
	Integer maxExamples,
	Boolean includeConstraints,
	Boolean includeSymbolSummaries,
	Boolean includeLiteralRules,
	Boolean includeIdentifierRules,
	String formatting,  // block | compact | markdown
	Boolean enforceCanonicalForm,
	Boolean preamble,
	Boolean postamble
) {
	public <R> R accept(SxlGrammarVisitor<R> visitor) {
		return visitor.visitLlmDefaults(this);
	}
}

