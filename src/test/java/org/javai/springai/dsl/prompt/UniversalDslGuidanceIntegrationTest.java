package org.javai.springai.dsl.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for universal DSL guidance.
 *
 * These tests verify that:
 * - Universal guidance appears in system prompts
 * - Universal guidance does NOT appear multiple times (no duplication)
 * - Universal guidance is composed correctly from loaded grammars
 */
@DisplayName("Universal DSL Guidance Integration Tests")
class UniversalDslGuidanceIntegrationTest {

	private ClassLoader classLoader;

	@BeforeEach
	void setUp() {
		classLoader = getClass().getClassLoader();
	}

	@Test
	@DisplayName("Should include universal guidance when loading universal grammar")
	void shouldIncludeUniversalGuidanceWhenLoaded() {
		DslGuidanceProvider guidanceProvider = new GrammarBackedDslGuidanceProvider(
				List.of("META-INF/sxl-meta-grammar-universal.yml"),
				classLoader
		);

		// Universal guidance should be available
		Optional<String> guidance = guidanceProvider.guidanceFor("sxl-universal", null, null);
		assertThat(guidance).isPresent();
		assertThat(guidance.get()).contains("UNIVERSAL SXL RULES");
		assertThat(guidance.get()).contains("S-expressions");
		assertThat(guidance.get()).contains("canonical");
	}

	@Test
	@DisplayName("CRITICAL: Universal guidance should appear EXACTLY ONCE when loading SQL + Universal")
	void universalGuidanceShouldNotBeDuplicatedAcrossGrammars() {
		// This test documents the core requirement: universal guidance must not be duplicated
		// We test this by checking that the guidance retrieved for each DSL doesn't contain
		// the same universal text appearing multiple times when grammars are composed together
		
		DslGuidanceProvider guidanceProvider = new GrammarBackedDslGuidanceProvider(
				List.of(
						"META-INF/sxl-meta-grammar-sql.yml",
						"META-INF/sxl-meta-grammar-universal.yml"
				),
				classLoader
		);

		// Get guidance for each DSL
		String sqlGuidance = guidanceProvider.guidanceFor("sxl-sql", "openai", null).orElseThrow();
		String universalGuidance = guidanceProvider.guidanceFor("sxl-universal", "openai", null).orElseThrow();

		// The CRITICAL requirement: SQL guidance should NOT contain universal rules
		// (they should be in universal grammar only)
		assertThat(sqlGuidance)
				.as("SQL guidance should NOT contain universal rules - it should only have SQL-specific guidance.")
				.doesNotContain("UNIVERSAL SXL RULES");

		// Universal guidance SHOULD be in the universal grammar only
		assertThat(universalGuidance)
				.as("Universal guidance should be present in universal grammar")
				.contains("UNIVERSAL SXL RULES");
	}

	@Test
	@DisplayName("Universal guidance should be present regardless of which grammars are loaded")
	void universalGuidanceShouldBeAvailableInAllConfigurations() {
		// Configuration 1: SQL + Universal
		GrammarBackedDslGuidanceProvider provider1 = new GrammarBackedDslGuidanceProvider(
				List.of(
						"META-INF/sxl-meta-grammar-sql.yml",
						"META-INF/sxl-meta-grammar-universal.yml"
				),
				classLoader
		);

		// Configuration 2: Universal only
		GrammarBackedDslGuidanceProvider provider2 = new GrammarBackedDslGuidanceProvider(
				List.of("META-INF/sxl-meta-grammar-universal.yml"),
				classLoader
		);

		// Both configurations should include universal guidance
		Optional<String> guidance1 = provider1.guidanceFor("sxl-universal", null, null);
		Optional<String> guidance2 = provider2.guidanceFor("sxl-universal", null, null);

		assertThat(guidance1).isPresent();
		assertThat(guidance1.get()).contains("UNIVERSAL SXL RULES");

		assertThat(guidance2).isPresent();
		assertThat(guidance2.get()).contains("UNIVERSAL SXL RULES");
	}

	@Test
	@DisplayName("Universal guidance should not be lost when other domain-specific grammars are added")
	void universalGuidanceShouldSurviveAddingOtherGrammars() {
		// Load universal only
		DslGuidanceProvider providerUniversalOnly = new GrammarBackedDslGuidanceProvider(
				List.of("META-INF/sxl-meta-grammar-universal.yml"),
				classLoader
		);

		// Load universal + SQL
		DslGuidanceProvider providerWithDomainGrammars = new GrammarBackedDslGuidanceProvider(
				List.of(
						"META-INF/sxl-meta-grammar-universal.yml",
						"META-INF/sxl-meta-grammar-sql.yml"
				),
				classLoader
		);

		String universalGuidanceOnly = providerUniversalOnly.guidanceFor("sxl-universal", null, null).orElseThrow();
		String universalGuidanceWithDomains = providerWithDomainGrammars.guidanceFor("sxl-universal", null, null).orElseThrow();

		// The universal guidance should be the same in both cases
		assertThat(universalGuidanceWithDomains)
				.as("Universal guidance should not change when domain-specific grammars are added")
				.isEqualTo(universalGuidanceOnly);
	}
}
