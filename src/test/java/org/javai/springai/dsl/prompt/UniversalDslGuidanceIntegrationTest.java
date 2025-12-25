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
 *
 * NOTE: These tests will FAIL until Phase 4-5 removes universal guidance
 * from domain-specific grammars (META-INF/sxl-meta-grammar-plan.yml, META-INF/sxl-meta-grammar-sql.yml).
 * The failure documents that the architecture still needs refinement.
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
	@DisplayName("CRITICAL: Universal guidance should appear EXACTLY ONCE when loading SQL + Plan + Universal")
	void universalGuidanceShouldNotBeDuplicatedAcrossGrammars() {
		// This test documents the core requirement: universal guidance must not be duplicated
		// We test this by checking that the guidance retrieved for each DSL doesn't contain
		// the same universal text appearing multiple times when grammars are composed together
		
		DslGuidanceProvider guidanceProvider = new GrammarBackedDslGuidanceProvider(
				List.of(
						"META-INF/sxl-meta-grammar-sql.yml",
						"META-INF/sxl-meta-grammar-plan.yml",
						"META-INF/sxl-meta-grammar-universal.yml"
				),
				classLoader
		);

		// Get guidance for each DSL
		String sqlGuidance = guidanceProvider.guidanceFor("sxl-sql", "openai", null).orElseThrow();
		String planGuidance = guidanceProvider.guidanceFor("sxl-plan", "openai", null).orElseThrow();
		String universalGuidance = guidanceProvider.guidanceFor("sxl-universal", "openai", null).orElseThrow();

		// Debug output
		System.out.println("\n=== DEBUG: Guidance Comparison ===");
		System.out.println("SQL Guidance length: " + sqlGuidance.length());
		System.out.println("Plan Guidance length: " + planGuidance.length());
		System.out.println("Universal Guidance length: " + universalGuidance.length());
		System.out.println("SQL contains 'UNIVERSAL SXL RULES': " + sqlGuidance.contains("UNIVERSAL SXL RULES"));
		System.out.println("Plan contains 'UNIVERSAL SXL RULES': " + planGuidance.contains("UNIVERSAL SXL RULES"));
		System.out.println("Universal contains 'UNIVERSAL SXL RULES': " + universalGuidance.contains("UNIVERSAL SXL RULES"));

		// The CRITICAL requirement: SQL and Plan guidance should NOT contain universal rules
		// (they should be in universal grammar only)
		// This test will FAIL until we remove universal guidance from Plan and SQL grammars
		assertThat(sqlGuidance)
				.as("SQL guidance should NOT contain universal rules - it should only have SQL-specific guidance. " +
						"Currently it contains universal guidance because it hasn't been separated yet. " +
						"Remove universal guidance from META-INF/sxl-meta-grammar-sql.yml to make this pass.")
				.doesNotContain("UNIVERSAL SXL RULES");

		assertThat(planGuidance)
				.as("Plan guidance should NOT contain universal rules - it should only have Plan-specific guidance. " +
						"Currently it contains universal guidance because it hasn't been separated yet. " +
						"Remove universal guidance from META-INF/sxl-meta-grammar-plan.yml to make this pass.")
				.doesNotContain("UNIVERSAL SXL RULES");

		// Universal guidance SHOULD be in the universal grammar only
		assertThat(universalGuidance)
				.as("Universal guidance should be present in universal grammar")
				.contains("UNIVERSAL SXL RULES");
	}

	@Test
	@DisplayName("Universal guidance should be present regardless of which grammars are loaded")
	void universalGuidanceShouldBeAvailableInAllConfigurations() {
		// Configuration 1: SQL + Universal (no Plan)
		GrammarBackedDslGuidanceProvider provider1 = new GrammarBackedDslGuidanceProvider(
				List.of(
						"META-INF/sxl-meta-grammar-sql.yml",
						"META-INF/sxl-meta-grammar-universal.yml"
				),
				classLoader
		);

		// Configuration 2: Plan + Universal (no SQL)
		GrammarBackedDslGuidanceProvider provider2 = new GrammarBackedDslGuidanceProvider(
				List.of(
						"META-INF/sxl-meta-grammar-plan.yml",
						"META-INF/sxl-meta-grammar-universal.yml"
				),
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

		// Load universal + SQL + Plan
		DslGuidanceProvider providerWithDomainGrammars = new GrammarBackedDslGuidanceProvider(
				List.of(
						"META-INF/sxl-meta-grammar-universal.yml",
						"META-INF/sxl-meta-grammar-sql.yml",
						"META-INF/sxl-meta-grammar-plan.yml"
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

