package org.javai.springai.dsl.prompt;

import java.util.stream.Collectors;
import org.javai.springai.sxl.grammar.ParameterDefinition;
import org.javai.springai.sxl.grammar.SxlGrammar;

/**
 * Emits a concise, model-facing guidance slice from an SxlGrammar for S-expression mode.
 */
public final class GrammarPromptSummarizer {

	private GrammarPromptSummarizer() {
	}

	public static String summarize(SxlGrammar grammar) {
		StringBuilder sb = new StringBuilder();
		sb.append("Description: ").append(grammar.dsl().description()).append("\n");

		if (grammar.embedding() != null && Boolean.TRUE.equals(grammar.embedding().enabled())) {
			String symbol = grammar.embedding().symbol() == null ? "EMBED" : grammar.embedding().symbol();
			sb.append("Embedding: use ").append(symbol)
					.append(" as (").append(symbol).append(" <dsl-id> <payload>) for any embedded DSL instance.\n");
		}

		// Explicit canonical-form reminder to prevent natural-language symbol names.
		sb.append("Use exact symbol names as defined below; do NOT invent words like 'select' or 'from'.\n");

		if (grammar.reservedSymbols() != null && !grammar.reservedSymbols().isEmpty()) {
			sb.append("Reserved symbols: ").append(String.join(", ", grammar.reservedSymbols())).append("\n");
		}

		if (grammar.symbols() != null && !grammar.symbols().isEmpty()) {
			sb.append("Symbols (name: kind, params...):\n");
			grammar.symbols().forEach((name, def) -> {
				sb.append(" - ").append(name).append(": ").append(def.kind().name().toLowerCase());
				if (!def.params().isEmpty()) {
					String params = def.params().stream()
							.map(GrammarPromptSummarizer::formatParam)
							.collect(Collectors.joining("; "));
					sb.append(" params[").append(params).append("]");
				}
				sb.append("\n");
			});
		}
		sb.append("Emit canonical S-expressions only; use DSL symbols exactly as defined; no free text.");
		return sb.toString();
	}

	private static String formatParam(ParameterDefinition p) {
		StringBuilder sb = new StringBuilder();
		sb.append(p.name())
				.append(":")
				.append(p.type())
				.append("(")
				.append(p.cardinality().name())
				.append(")");
		if (p.allowedSymbols() != null && !p.allowedSymbols().isEmpty()) {
			sb.append("{allowed=").append(String.join("|", p.allowedSymbols())).append("}");
		}
		return sb.toString();
	}
}
