package org.javai.springai.dsl.prompt;

import java.util.Optional;
import org.javai.springai.sxl.grammar.SxlGrammar;

/**
 * Optional capability for guidance providers that can expose loaded grammars.
 */
public interface DslGrammarSource {
	Optional<SxlGrammar> grammarFor(String dslId);
}
