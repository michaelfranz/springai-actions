package org.javai.springai.sxl.grammar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class SxlGrammarRegistryTest {

	@Test
	void shouldLoadGrammarFromResource() {
		SxlGrammarRegistry registry = SxlGrammarRegistry.create();
		SxlGrammar grammar = registry.registerResource("META-INF/sxl-meta-grammar-sql.yml", getClass().getClassLoader());

		assertThat(grammar).isNotNull();
		assertThat(grammar.dsl().id()).isEqualTo("sxl-sql");
		assertThat(registry.grammarFor("sxl-sql")).contains(grammar);
	}

	@Test
	void shouldDeduplicateByDslId() {
		SxlGrammarRegistry registry = SxlGrammarRegistry.create();
		SxlGrammar first = registry.registerResource("META-INF/sxl-meta-grammar-sql.yml", getClass().getClassLoader());
		SxlGrammar second = registry.registerResource("META-INF/sxl-meta-grammar-sql.yml", getClass().getClassLoader());

		assertThat(second).isSameAs(first);
		assertThat(registry.grammars()).hasSize(1);
	}

	@Test
	void shouldRegisterFromPath() throws Exception {
		SxlGrammarRegistry registry = SxlGrammarRegistry.create();
		URL resource = Objects.requireNonNull(getClass().getClassLoader().getResource("META-INF/sxl-meta-grammar-sql.yml"));
		Path path = Path.of(resource.toURI());

		SxlGrammar grammar = registry.registerPath(path);

		assertThat(grammar.dsl().id()).isEqualTo("sxl-sql");
		assertThat(registry.grammarFor("sxl-sql")).contains(grammar);
	}

	@Test
	void shouldFailWhenResourceIsMissing() {
		SxlGrammarRegistry registry = SxlGrammarRegistry.create();

		assertThatThrownBy(() -> registry.registerResource("does-not-exist.yml", getClass().getClassLoader()))
				.isInstanceOf(IllegalArgumentException.class);
	}
}

