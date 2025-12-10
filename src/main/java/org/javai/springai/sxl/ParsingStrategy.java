package org.javai.springai.sxl;

import java.util.List;

/**
 * Strategy interface for parsing s-expression token sequences.
 * Different strategies can enforce different parsing rules, from universal
 * s-expression parsing to DSL-specific validation.
 */
public interface ParsingStrategy {

	/**
	 * Parses a list of tokens into AST nodes according to the strategy's rules.
	 * 
	 * @param tokens the tokens to parse
	 * @return list of parsed nodes
	 * @throws SxlParseException if parsing fails or rules are violated
	 */
	List<SxlNode> parse(List<SxlToken> tokens) throws SxlParseException;
}

