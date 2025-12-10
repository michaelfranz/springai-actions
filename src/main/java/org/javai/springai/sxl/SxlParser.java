package org.javai.springai.sxl;

import java.util.List;
import org.javai.springai.sxl.grammar.SxlGrammar;

/**
 * S-expression parser with pluggable parsing strategies.
 * 
 * By default, uses UniversalParsingStrategy which accepts any syntactically
 * correct s-expression. Can be configured with a DSL-specific strategy that
 * validates against a meta-grammar.
 * 
 * Example usage:
 * 
 * <pre>
 * // Universal parsing (default)
 * SxlParser parser = new SxlParser(tokens);
 * List&lt;SxlNode&gt; nodes = parser.parse();
 * 
 * // DSL-specific parsing
 * SxlGrammar grammar = metaParser.parse(grammarPath);
 * SxlParser dslParser = new SxlParser(tokens, grammar);
 * List&lt;SxlNode&gt; nodes = dslParser.parse();
 * </pre>
 */
public class SxlParser {

	private final List<SxlToken> tokens;
	private final ParsingStrategy strategy;

	/**
	 * Creates a parser with universal parsing strategy (accepts any valid s-expression).
	 * 
	 * @param tokens the tokens to parse
	 */
	public SxlParser(List<SxlToken> tokens) {
		this.tokens = tokens != null ? tokens : List.of();
		this.strategy = new UniversalParsingStrategy();
	}

	/**
	 * Creates a parser with DSL-specific parsing strategy.
	 * The parser will validate tokens against the provided grammar.
	 * 
	 * @param tokens the tokens to parse
	 * @param grammar the DSL grammar to validate against
	 */
	public SxlParser(List<SxlToken> tokens, SxlGrammar grammar) {
		this.tokens = tokens != null ? tokens : List.of();
		this.strategy = new DslParsingStrategy(grammar);
	}

	/**
	 * Creates a parser with an explicit parsing strategy.
	 * 
	 * @param tokens the tokens to parse
	 * @param strategy the parsing strategy to use
	 */
	public SxlParser(List<SxlToken> tokens, ParsingStrategy strategy) {
		if (strategy == null) {
			throw new IllegalArgumentException("Parsing strategy cannot be null");
		}
		this.tokens = tokens != null ? tokens : List.of();
		this.strategy = strategy;
	}

	/**
	 * Parses the tokens into a list of top-level nodes using the configured strategy.
	 * 
	 * @return list of parsed nodes (may be empty)
	 * @throws SxlParseException if syntax errors are encountered or grammar rules are violated
	 */
	public List<SxlNode> parse() {
		return strategy.parse(tokens);
	}
}

