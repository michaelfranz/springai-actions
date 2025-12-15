package org.javai.springai.dsl.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Optional;
import org.javai.springai.sxl.grammar.SxlGrammar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// Note: Using GrammarBackedDslGuidanceProvider directly rather than interface
// because we need access to both DslGuidanceProvider and DslGrammarSource methods

/**
 * Integration tests for the Universal Grammar architecture.
 *
 * These tests verify that Phases 1-4 of the SXL Universal Grammar initiative work correctly:
 * - Phase 1: Universal grammar created (sxl-meta-grammar-universal.yml)
 * - Phase 2: Auto-loading implemented (GrammarBackedDslGuidanceProvider)
 * - Phase 3: Plan grammar refactored (universal guidance removed)
 * - Phase 4: SQL grammar verified (no universal guidance to remove)
 *
 * These tests validate:
 * - DSLs work independently with universal guidance
 * - DSLs work together without guidance duplication
 * - Universal guidance is available in all configurations
 * - No regression from previous functionality
 */
@DisplayName("Universal Grammar Integration Tests")
class UniversalGrammarIntegrationTest {

	private ClassLoader classLoader;

	@BeforeEach
	void setUp() {
		classLoader = getClass().getClassLoader();
	}

	@Nested
	@DisplayName("SQL DSL Independence Tests")
	class SqlDslIndependenceTests {

		@Test
		@DisplayName("SQL DSL should work independently with universal guidance")
		void sqlDslWorksIndependentlyWithUniversalGuidance() {
			// Load only SQL + Universal (no Plan)
			GrammarBackedDslGuidanceProvider provider = new GrammarBackedDslGuidanceProvider(
					List.of("sxl-meta-grammar-sql.yml"),
					classLoader
			);

			// Universal grammar should be auto-loaded
			assertThat(provider.grammarFor("sxl-universal")).isPresent();
			assertThat(provider.grammarFor("sxl-sql")).isPresent();

			// SQL should have its own guidance
			Optional<String> sqlGuidance = provider.guidanceFor("sxl-sql", "openai", null);
			assertThat(sqlGuidance).isPresent();
			assertThat(sqlGuidance.get()).contains("CRITICAL");

			// Universal guidance should be available
			Optional<String> universalGuidance = provider.guidanceFor("sxl-universal", "openai", null);
			assertThat(universalGuidance).isPresent();
			assertThat(universalGuidance.get()).contains("UNIVERSAL SXL RULES");
		}

		@Test
		@DisplayName("SQL guidance should not contain universal S-expression guidance")
		void sqlGuidanceShouldNotContainUniversalGuidance() {
			GrammarBackedDslGuidanceProvider provider = new GrammarBackedDslGuidanceProvider(
					List.of("sxl-meta-grammar-sql.yml"),
					classLoader
			);

			String sqlGuidance = provider.guidanceFor("sxl-sql", null, null).orElseThrow();

			// SQL guidance should NOT contain universal markers
			assertThat(sqlGuidance)
					.doesNotContain("Emit concise, canonical S-expressions only")
					.doesNotContain("UNIVERSAL SXL RULES");
		}
	}

	@Nested
	@DisplayName("Plan DSL Independence Tests")
	class PlanDslIndependenceTests {

		@Test
		@DisplayName("Plan DSL should work independently with universal guidance")
		void planDslWorksIndependentlyWithUniversalGuidance() {
			// Load only Plan + Universal (no SQL)
			GrammarBackedDslGuidanceProvider provider = new GrammarBackedDslGuidanceProvider(
					List.of("sxl-meta-grammar-plan.yml"),
					classLoader
			);

			// Universal grammar should be auto-loaded
			assertThat(provider.grammarFor("sxl-universal")).isPresent();
			assertThat(provider.grammarFor("sxl-plan")).isPresent();

			// Plan should have its own guidance
			Optional<String> planGuidance = provider.guidanceFor("sxl-plan", "openai", null);
			assertThat(planGuidance).isPresent();
			assertThat(planGuidance.get()).contains("PLAN DSL");

			// Universal guidance should be available
			Optional<String> universalGuidance = provider.guidanceFor("sxl-universal", "openai", null);
			assertThat(universalGuidance).isPresent();
			assertThat(universalGuidance.get()).contains("UNIVERSAL SXL RULES");
		}

		@Test
		@DisplayName("Plan guidance should not contain universal S-expression guidance")
		void planGuidanceShouldNotContainUniversalGuidance() {
			GrammarBackedDslGuidanceProvider provider = new GrammarBackedDslGuidanceProvider(
					List.of("sxl-meta-grammar-plan.yml"),
					classLoader
			);

			String planGuidance = provider.guidanceFor("sxl-plan", null, null).orElseThrow();

			// Plan guidance should NOT contain universal markers
			assertThat(planGuidance)
					.doesNotContain("Emit concise, canonical S-expressions only")
					.doesNotContain("UNIVERSAL SXL RULES");
		}
	}

	@Nested
	@DisplayName("DSL Composition Tests")
	class DslCompositionTests {

		@Test
		@DisplayName("SQL + Plan DSLs should work together with universal guidance")
		void sqlPlanDslsWorkTogetherWithUniversalGuidance() {
			// Load SQL + Plan + Universal (all together)
			GrammarBackedDslGuidanceProvider provider = new GrammarBackedDslGuidanceProvider(
					List.of(
							"sxl-meta-grammar-sql.yml",
							"sxl-meta-grammar-plan.yml"
					),
					classLoader
			);

			// All grammars should be available
			assertThat(provider.grammarFor("sxl-universal")).isPresent();
			assertThat(provider.grammarFor("sxl-sql")).isPresent();
			assertThat(provider.grammarFor("sxl-plan")).isPresent();

			// Each should have appropriate guidance
			Optional<String> sqlGuidance = provider.guidanceFor("sxl-sql", null, null);
			Optional<String> planGuidance = provider.guidanceFor("sxl-plan", null, null);
			Optional<String> universalGuidance = provider.guidanceFor("sxl-universal", null, null);

			assertThat(sqlGuidance).isPresent();
			assertThat(planGuidance).isPresent();
			assertThat(universalGuidance).isPresent();

			// Each should be distinct (no overlap/duplication)
			assertThat(sqlGuidance.get()).doesNotContain("PLAN DSL");
			assertThat(planGuidance.get()).doesNotContain("CRITICAL CLAUSE");
		}

		@Test
		@DisplayName("Composition order should not matter for guidance availability")
		void compositionOrderShouldNotMatterForGuidance() {
			// Order 1: SQL -> Plan
			DslGuidanceProvider provider1 = new GrammarBackedDslGuidanceProvider(
					List.of(
							"sxl-meta-grammar-sql.yml",
							"sxl-meta-grammar-plan.yml"
					),
					classLoader
			);

			// Order 2: Plan -> SQL
			DslGuidanceProvider provider2 = new GrammarBackedDslGuidanceProvider(
					List.of(
							"sxl-meta-grammar-plan.yml",
							"sxl-meta-grammar-sql.yml"
					),
					classLoader
			);

			// Guidance should be identical regardless of order
			String sql1 = provider1.guidanceFor("sxl-sql", null, null).orElseThrow();
			String sql2 = provider2.guidanceFor("sxl-sql", null, null).orElseThrow();
			assertThat(sql1).isEqualTo(sql2);

			String plan1 = provider1.guidanceFor("sxl-plan", null, null).orElseThrow();
			String plan2 = provider2.guidanceFor("sxl-plan", null, null).orElseThrow();
			assertThat(plan1).isEqualTo(plan2);

			String universal1 = provider1.guidanceFor("sxl-universal", null, null).orElseThrow();
			String universal2 = provider2.guidanceFor("sxl-universal", null, null).orElseThrow();
			assertThat(universal1).isEqualTo(universal2);
		}
	}

	@Nested
	@DisplayName("Provider-Specific Guidance Tests")
	class ProviderSpecificGuidanceTests {

		@Test
		@DisplayName("Universal guidance should have provider-specific variations")
		void universalGuidanceShouldHaveProviderSpecificVariations() {
			GrammarBackedDslGuidanceProvider provider = new GrammarBackedDslGuidanceProvider(
					List.of("sxl-meta-grammar-universal.yml"),
					classLoader
			);

			// Default guidance
			String defaultGuidance = provider.guidanceFor("sxl-universal", null, null).orElseThrow();

			// OpenAI-specific guidance
			String openaiGuidance = provider.guidanceFor("sxl-universal", "openai", null).orElseThrow();

			// Anthropic-specific guidance
			String anthropicGuidance = provider.guidanceFor("sxl-universal", "anthropic", null).orElseThrow();

			// All should be present
			assertThat(defaultGuidance).isNotBlank();
			assertThat(openaiGuidance).isNotBlank();
			assertThat(anthropicGuidance).isNotBlank();

			// Provider-specific should be different from default
			assertThat(openaiGuidance).isNotEqualTo(defaultGuidance);
		}

		@Test
		@DisplayName("Model-specific guidance should be available")
		void modelSpecificGuidanceShouldBeAvailable() {
			GrammarBackedDslGuidanceProvider provider = new GrammarBackedDslGuidanceProvider(
					List.of(
							"sxl-meta-grammar-sql.yml",
							"sxl-meta-grammar-plan.yml",
							"sxl-meta-grammar-universal.yml"
					),
					classLoader
			);

			// Check each DSL has model-specific overrides available
			Optional<String> sqlModelGuidance = provider.guidanceFor("sxl-sql", "openai", "gpt-4.1");
			Optional<String> planModelGuidance = provider.guidanceFor("sxl-plan", "openai", "gpt-4.1");

			assertThat(sqlModelGuidance).isPresent();
			assertThat(planModelGuidance).isPresent();
		}
	}

	@Nested
	@DisplayName("Grammar Access Tests")
	class GrammarAccessTests {

		@Test
		@DisplayName("All grammars should be accessible through grammarFor()")
		void allGrammarsShouldBeAccessible() {
			GrammarBackedDslGuidanceProvider provider = new GrammarBackedDslGuidanceProvider(
					List.of(
							"sxl-meta-grammar-sql.yml",
							"sxl-meta-grammar-plan.yml"
					),
					classLoader
			);

			// All grammars should be retrievable
			Optional<SxlGrammar> universalGrammar = provider.grammarFor("sxl-universal");
			Optional<SxlGrammar> sqlGrammar = provider.grammarFor("sxl-sql");
			Optional<SxlGrammar> planGrammar = provider.grammarFor("sxl-plan");

			assertThat(universalGrammar).isPresent();
			assertThat(sqlGrammar).isPresent();
			assertThat(planGrammar).isPresent();

			// Each should have correct metadata
			assertThat(universalGrammar.get().dsl().id()).isEqualTo("sxl-universal");
			assertThat(sqlGrammar.get().dsl().id()).isEqualTo("sxl-sql");
			assertThat(planGrammar.get().dsl().id()).isEqualTo("sxl-plan");
		}

		@Test
		@DisplayName("Non-existent grammar should return empty Optional")
		void nonExistentGrammarShouldReturnEmpty() {
			GrammarBackedDslGuidanceProvider provider = new GrammarBackedDslGuidanceProvider(
					List.of("sxl-meta-grammar-sql.yml"),
					classLoader
			);

			Optional<SxlGrammar> nonExistent = provider.grammarFor("sxl-nonexistent");
			assertThat(nonExistent).isEmpty();
		}
	}

	@Nested
	@DisplayName("Backward Compatibility Tests")
	class BackwardCompatibilityTests {

		@Test
		@DisplayName("Explicit universal grammar loading should not cause duplicates")
		void explicitUniversalLoadingShouldNotCauseDuplicates() {
			// Explicitly load universal grammar (even though it's auto-loaded)
			GrammarBackedDslGuidanceProvider provider = new GrammarBackedDslGuidanceProvider(
					List.of(
							"sxl-meta-grammar-sql.yml",
							"sxl-meta-grammar-universal.yml",  // Explicitly load
							"sxl-meta-grammar-plan.yml"
					),
					classLoader
			);

			// Should not fail with duplicate DSL id error
			assertThat(provider.grammarFor("sxl-universal")).isPresent();
			assertThat(provider.grammarFor("sxl-sql")).isPresent();
			assertThat(provider.grammarFor("sxl-plan")).isPresent();

			// Guidance should be consistent
			String universalGuidance = provider.guidanceFor("sxl-universal", null, null).orElseThrow();
			assertThat(universalGuidance).contains("UNIVERSAL SXL RULES");
		}

		@Test
		@DisplayName("Loading order with universal grammar first should work")
		void universalGrammarFirstShouldWork() {
			// Explicitly load universal first
			GrammarBackedDslGuidanceProvider provider = new GrammarBackedDslGuidanceProvider(
					List.of(
							"sxl-meta-grammar-universal.yml",
							"sxl-meta-grammar-sql.yml",
							"sxl-meta-grammar-plan.yml"
					),
					classLoader
			);

			// Everything should work normally
			assertThat(provider.grammarFor("sxl-universal")).isPresent();
			assertThat(provider.grammarFor("sxl-sql")).isPresent();
			assertThat(provider.grammarFor("sxl-plan")).isPresent();

			// Guidance should be correct
			assertThat(provider.guidanceFor("sxl-universal", null, null)).isPresent();
			assertThat(provider.guidanceFor("sxl-sql", null, null)).isPresent();
			assertThat(provider.guidanceFor("sxl-plan", null, null)).isPresent();
		}

		@Test
		@DisplayName("Only universal grammar should be loadable")
		void onlyUniversalGrammarShouldBeLoadable() {
			GrammarBackedDslGuidanceProvider provider = new GrammarBackedDslGuidanceProvider(
					List.of(),  // Load nothing explicitly
					classLoader
			);

			// Universal should still be available (auto-loaded)
			assertThat(provider.grammarFor("sxl-universal")).isPresent();
			assertThat(provider.guidanceFor("sxl-universal", null, null)).isPresent();

			// Other grammars should not be available
			assertThat(provider.grammarFor("sxl-sql")).isEmpty();
			assertThat(provider.grammarFor("sxl-plan")).isEmpty();
		}
	}

	@Nested
	@DisplayName("Edge Case Tests")
	class EdgeCaseTests {

		@Test
		@DisplayName("Requesting guidance for unloaded DSL should return empty")
		void guidanceForUnloadedDslShouldReturnEmpty() {
			GrammarBackedDslGuidanceProvider provider = new GrammarBackedDslGuidanceProvider(
					List.of("sxl-meta-grammar-sql.yml"),
					classLoader
			);

			// Plan DSL not loaded
			Optional<String> planGuidance = provider.guidanceFor("sxl-plan", null, null);
			assertThat(planGuidance).isEmpty();
		}

		@Test
		@DisplayName("Null provider/model should fall back to default guidance")
		void nullProviderModelShouldFallbackToDefault() {
			GrammarBackedDslGuidanceProvider provider = new GrammarBackedDslGuidanceProvider(
					List.of("sxl-meta-grammar-sql.yml"),
					classLoader
			);

			String defaultGuidance = provider.guidanceFor("sxl-sql", null, null).orElseThrow();
			assertThat(defaultGuidance).isNotBlank();
		}

		@Test
		@DisplayName("Universal guidance should be consistent across all access patterns")
		void universalGuidanceConsistentAcrossAccessPatterns() {
			GrammarBackedDslGuidanceProvider provider = new GrammarBackedDslGuidanceProvider(
					List.of("sxl-meta-grammar-sql.yml"),
					classLoader
			);

			String guidance1 = provider.guidanceFor("sxl-universal", null, null).orElseThrow();
			String guidance2 = provider.guidanceFor("sxl-universal", null, null).orElseThrow();
			String guidance3 = provider.guidanceFor("sxl-universal", "openai", null).orElseThrow();

			// First two should be identical (same access pattern)
			assertThat(guidance1).isEqualTo(guidance2);

			// guidance3 is OpenAI-specific, may be different
			assertThat(guidance3).isNotBlank();
		}
	}
}

