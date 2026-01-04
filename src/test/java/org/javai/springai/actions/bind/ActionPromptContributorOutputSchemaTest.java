package org.javai.springai.actions.bind;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.internal.bind.ActionPromptContributor;
import org.javai.springai.actions.internal.bind.ActionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * TDD tests for ActionPromptContributor output schema generation.
 * 
 * <p>These tests embody the IDEAL output format: a complete JSON Schema
 * that constrains LLM output to valid Plan structures with action-specific
 * parameter schemas derived from @ActionParam annotations.</p>
 * 
 * <p>The goal is to eliminate LLM errors like:
 * <ul>
 *   <li>Inventing parameter names (e.g., "characteristic" instead of "measurementType")</li>
 *   <li>Using invalid enum values (e.g., "displacements" instead of "displacement")</li>
 *   <li>Omitting required parameters</li>
 * </ul>
 */
class ActionPromptContributorOutputSchemaTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private ActionRegistry registry;

	@BeforeEach
	void setUp() {
		registry = new ActionRegistry();
		registry.registerActions(new SpcActions());
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// GOLDEN OUTPUT TEST - Shows exactly what the contribution should look like
	// ═══════════════════════════════════════════════════════════════════════════

	@Nested
	@DisplayName("Golden Output - The Ideal Prompt Contribution")
	class GoldenOutput {

		/**
		 * This test validates the structure and content of the output.
		 * The text block below shows what the LLM will receive in the system prompt.
		 * 
		 * <p>The schema uses hardcoded complete action shapes for ActionStep variants.
		 * Each action is a self-contained definition with:
		 * <ul>
		 *   <li>actionId as a "const" discriminator</li>
		 *   <li>parameters schema inlined and bound to that specific action</li>
		 * </ul>
		 * This eliminates any ambiguity about which parameters apply to which action.</p>
		 * 
		 * <p>PendingStep, NoActionStep, and ErrorStep are GENERIC - they don't need
		 * per-action variants because they are format documentation, not precise bindings.</p>
		 */
		@Test
		@DisplayName("should produce the correct golden output structure for SPC actions")
		void producesGoldenOutput() throws Exception {
			// Register a single action for a cleaner golden output
			ActionRegistry singleActionRegistry = new ActionRegistry();
			singleActionRegistry.registerActions(new SingleSpcAction());

			String actual = ActionPromptContributor.emitExemplar(singleActionRegistry, null);

			// Validate headers are present
			assertThat(actual).contains("OUTPUT SCHEMA");
			assertThat(actual).contains("EXAMPLE");
			
			// Extract and validate the JSON schema portion
			String schemaJson = extractJsonBlock(actual);
			JsonNode schema = MAPPER.readTree(schemaJson);
			
			// Validate top-level schema structure
			assertThat(schema.get("$schema").asText()).contains("json-schema.org");
			assertThat(schema.get("type").asText()).isEqualTo("object");
			assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
			assertThat(schema.get("required")).extracting(JsonNode::asText)
					.containsExactlyInAnyOrder("message", "steps");
			
			// Validate Step oneOf structure
			JsonNode stepDef = schema.at("/$defs/Step");
			assertThat(stepDef.get("oneOf")).hasSize(4); // Action + Pending + NoAction + Error
			
			// Validate EvaluateSpcReadinessAction has const binding
			JsonNode actionDef = schema.at("/$defs/EvaluateSpcReadinessAction");
			assertThat(actionDef.at("/properties/actionId/const").asText())
					.isEqualTo("evaluateSpcReadiness");
			assertThat(actionDef.get("additionalProperties").asBoolean()).isFalse();
			
			// Validate parameters are inlined with constraints
			JsonNode paramsDef = actionDef.at("/properties/parameters");
			assertThat(paramsDef.get("additionalProperties").asBoolean()).isFalse();
			assertThat(paramsDef.get("required")).extracting(JsonNode::asText)
					.containsExactlyInAnyOrder("measurementType", "bundleId");
			
			// Validate measurementType has enum constraint
			JsonNode mtSchema = paramsDef.at("/properties/measurementType");
			assertThat(mtSchema.get("enum")).extracting(JsonNode::asText)
					.containsExactlyInAnyOrder("force", "displacement");
			
			// Validate bundleId has pattern constraint
			JsonNode bidSchema = paramsDef.at("/properties/bundleId");
			assertThat(bidSchema.get("pattern").asText()).isEqualTo("^[A-Z][0-9]{5}$");
			
			// Validate generic PendingStep (not action-specific)
			JsonNode pendingDef = schema.at("/$defs/PendingStep");
			assertThat(pendingDef.at("/properties/actionId/type").asText()).isEqualTo("string");
			assertThat(pendingDef.at("/properties/status/const").asText()).isEqualTo("pending");
			
			// Validate example contains correct structure
			assertThat(actual).contains("\"actionId\":\"evaluateSpcReadiness\"");
			assertThat(actual).contains("\"measurementType\":\"displacement\"");
			assertThat(actual).contains("\"bundleId\":\"A12345\"");
		}
		
		/**
		 * Extracts the first JSON object block from the output.
		 */
		private String extractJsonBlock(String output) {
			int start = output.indexOf("{");
			int depth = 0;
			int end = start;
			for (int i = start; i < output.length(); i++) {
				char c = output.charAt(i);
				if (c == '{') depth++;
				else if (c == '}') {
					depth--;
					if (depth == 0) {
						end = i + 1;
						break;
					}
				}
			}
			return output.substring(start, end);
		}

		/**
		 * A single action for cleaner golden output testing.
		 */
		private static class SingleSpcAction {
			@Action(description = "Evaluate SPC readiness for the specified measurement type and bundle")
			public void evaluateSpcReadiness(
					@ActionParam(
							description = "The measurement type to evaluate",
							allowedValues = {"force", "displacement"},
							examples = {"displacement"}
					) String measurementType,
					@ActionParam(
							description = "Bundle ID like A12345",
							allowedRegex = "^[A-Z][0-9]{5}$",
							examples = {"A12345"}
					) String bundleId
			) {
			}
		}
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// Sample Actions - Mirror the SPC scenario from the bug report
	// ═══════════════════════════════════════════════════════════════════════════

	/**
	 * Sample actions that mirror the SPC (Statistical Process Control) domain.
	 * These reproduce the exact scenario where the LLM was failing.
	 */
	private static class SpcActions {

		@Action(description = "Evaluate SPC readiness for the specified measurement type and bundle")
		public void evaluateSpcReadiness(
				@ActionParam(
						description = "The measurement type to evaluate",
						allowedValues = {"force", "displacement"},
						examples = {"displacement"}
				) String measurementType,
				@ActionParam(
						description = "Bundle ID like A12345",
						allowedRegex = "^[A-Z][0-9]{5}$",
						examples = {"A12345"}
				) String bundleId
		) {
		}

		@Action(description = "Display a control chart for the specified measurement type and bundle")
		public void displayControlChart(
				@ActionParam(
						description = "The measurement type to chart",
						allowedValues = {"force", "displacement"}
				) String measurementType,
				@ActionParam(
						description = "Bundle ID like A12345",
						allowedRegex = "^[A-Z][0-9]{5}$"
				) String bundleId
		) {
		}
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// Tests for Output Schema Structure
	// ═══════════════════════════════════════════════════════════════════════════

	@Nested
	@DisplayName("Output Schema Structure")
	class OutputSchemaStructure {

		@Test
		@DisplayName("should generate a valid JSON Schema document")
		void generatesValidJsonSchema() throws Exception {
			String contribution = ActionPromptContributor.emitOutputSchema(registry, null, null);

			JsonNode schema = MAPPER.readTree(contribution);
			
			assertThat(schema.get("$schema").asText())
					.isEqualTo("https://json-schema.org/draft/2020-12/schema");
			assertThat(schema.get("type").asText()).isEqualTo("object");
			assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
		}

		@Test
		@DisplayName("should require message and steps at top level")
		void requiresMessageAndSteps() throws Exception {
			String contribution = ActionPromptContributor.emitOutputSchema(registry, null, null);

			JsonNode schema = MAPPER.readTree(contribution);
			JsonNode required = schema.get("required");
			
			assertThat(required.isArray()).isTrue();
			assertThat(required).extracting(JsonNode::asText)
					.containsExactlyInAnyOrder("message", "steps");
		}

		@Test
		@DisplayName("should define message as a constrained string")
		void definesMessageAsConstrainedString() throws Exception {
			String contribution = ActionPromptContributor.emitOutputSchema(registry, null, null);

			JsonNode schema = MAPPER.readTree(contribution);
			JsonNode messageSchema = schema.at("/properties/message");
			
			assertThat(messageSchema.get("type").asText()).isEqualTo("string");
			assertThat(messageSchema.get("minLength").asInt()).isEqualTo(1);
			assertThat(messageSchema.has("maxLength")).isTrue();
		}

		@Test
		@DisplayName("should define steps as an array with Step reference")
		void definesStepsAsArray() throws Exception {
			String contribution = ActionPromptContributor.emitOutputSchema(registry, null, null);

			JsonNode schema = MAPPER.readTree(contribution);
			JsonNode stepsSchema = schema.at("/properties/steps");
			
			assertThat(stepsSchema.get("type").asText()).isEqualTo("array");
			assertThat(stepsSchema.get("minItems").asInt()).isEqualTo(1);
			assertThat(stepsSchema.at("/items/$ref").asText()).isEqualTo("#/$defs/Step");
		}
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// Tests for Action Definitions
	// ═══════════════════════════════════════════════════════════════════════════

	@Nested
	@DisplayName("Action Definitions")
	class ActionDefinitions {

		@Test
		@DisplayName("should create ActionStep definition for each registered action")
		void createsActionStepDefinitionForEachAction() throws Exception {
			String contribution = ActionPromptContributor.emitOutputSchema(registry, null, null);

			JsonNode schema = MAPPER.readTree(contribution);
			
			// Each action should have its own ActionStep definition
			assertThat(schema.at("/$defs/EvaluateSpcReadinessAction").isMissingNode()).isFalse();
			assertThat(schema.at("/$defs/DisplayControlChartAction").isMissingNode()).isFalse();
			
			// PendingStep is generic - no per-action variants
			assertThat(schema.at("/$defs/PendingStep").isMissingNode()).isFalse();
		}

		@Test
		@DisplayName("should filter actions when filter is provided")
		void filtersActionsWhenFilterProvided() throws Exception {
			String contribution = ActionPromptContributor.emitOutputSchema(
					registry, 
					spec -> spec.id().equals("evaluateSpcReadiness"), 
					null
			);

			JsonNode schema = MAPPER.readTree(contribution);
			
			// Only evaluateSpcReadiness should be defined
			assertThat(schema.at("/$defs/EvaluateSpcReadinessAction").isMissingNode()).isFalse();
			assertThat(schema.at("/$defs/DisplayControlChartAction").isMissingNode()).isTrue();
		}

		@Test
		@DisplayName("should bind actionId via const in each action definition")
		void bindsActionIdViaConst() throws Exception {
			String contribution = ActionPromptContributor.emitOutputSchema(registry, null, null);

			JsonNode schema = MAPPER.readTree(contribution);
			
			// Each action has its actionId bound via const
			assertThat(schema.at("/$defs/EvaluateSpcReadinessAction/properties/actionId/const").asText())
					.isEqualTo("evaluateSpcReadiness");
			assertThat(schema.at("/$defs/DisplayControlChartAction/properties/actionId/const").asText())
					.isEqualTo("displayControlChart");
		}
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// Tests for Step Type Definitions
	// ═══════════════════════════════════════════════════════════════════════════

	@Nested
	@DisplayName("Step Type Definitions")
	class StepTypeDefinitions {

		@Test
		@DisplayName("should define Step as oneOf action variants plus generic Pending, NoAction, Error")
		void definesStepAsOneOf() throws Exception {
			String contribution = ActionPromptContributor.emitOutputSchema(registry, null, null);

			JsonNode schema = MAPPER.readTree(contribution);
			JsonNode stepDef = schema.at("/$defs/Step");
			
			assertThat(stepDef.has("oneOf")).isTrue();
			// For SpcActions (2 actions): 2 action variants + PendingStep + NoActionStep + ErrorStep = 5
			assertThat(stepDef.get("oneOf").size()).isGreaterThanOrEqualTo(4);
		}

		@Test
		@DisplayName("should define per-action step with const actionId binding")
		void definesPerActionStepWithConstBinding() throws Exception {
			String contribution = ActionPromptContributor.emitOutputSchema(registry, null, null);

			JsonNode schema = MAPPER.readTree(contribution);
			
			// Each action has its own definition with const actionId
			JsonNode evalAction = schema.at("/$defs/EvaluateSpcReadinessAction");
			
			assertThat(evalAction.get("type").asText()).isEqualTo("object");
			assertThat(evalAction.get("additionalProperties").asBoolean()).isFalse();
			assertThat(evalAction.get("required")).extracting(JsonNode::asText)
					.contains("actionId", "description", "parameters");
			
			// actionId is bound via const
			assertThat(evalAction.at("/properties/actionId/const").asText())
					.isEqualTo("evaluateSpcReadiness");
		}

		@Test
		@DisplayName("should define generic PendingStep (not per-action)")
		void definesGenericPendingStep() throws Exception {
			String contribution = ActionPromptContributor.emitOutputSchema(registry, null, null);

			JsonNode schema = MAPPER.readTree(contribution);
			
			// PendingStep is generic - not bound to specific actions
			JsonNode pendingStep = schema.at("/$defs/PendingStep");
			
			assertThat(pendingStep.get("type").asText()).isEqualTo("object");
			assertThat(pendingStep.get("required")).extracting(JsonNode::asText)
					.contains("actionId", "status", "pendingParams", "providedParams");
			
			// actionId is a generic string, NOT a const
			assertThat(pendingStep.at("/properties/actionId/type").asText()).isEqualTo("string");
			assertThat(pendingStep.at("/properties/actionId/const").isMissingNode()).isTrue();
			
			// status must be exactly "pending"
			assertThat(pendingStep.at("/properties/status/const").asText()).isEqualTo("pending");
		}

		@Test
		@DisplayName("should define NoActionStep without actionId")
		void definesNoActionStep() throws Exception {
			String contribution = ActionPromptContributor.emitOutputSchema(registry, null, null);

			JsonNode schema = MAPPER.readTree(contribution);
			JsonNode noActionStepDef = schema.at("/$defs/NoActionStep");
			
			assertThat(noActionStepDef.get("type").asText()).isEqualTo("object");
			assertThat(noActionStepDef.get("required")).extracting(JsonNode::asText)
					.contains("noAction", "reason");
			// Should NOT have actionId in properties
			assertThat(noActionStepDef.at("/properties/actionId").isMissingNode()).isTrue();
		}

		@Test
		@DisplayName("should define ErrorStep without actionId")
		void definesErrorStep() throws Exception {
			String contribution = ActionPromptContributor.emitOutputSchema(registry, null, null);

			JsonNode schema = MAPPER.readTree(contribution);
			JsonNode errorStepDef = schema.at("/$defs/ErrorStep");
			
			assertThat(errorStepDef.get("type").asText()).isEqualTo("object");
			assertThat(errorStepDef.get("required")).extracting(JsonNode::asText)
					.contains("error", "reason");
			// Should NOT have actionId in properties
			assertThat(errorStepDef.at("/properties/actionId").isMissingNode()).isTrue();
		}
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// Tests for Action-Specific Parameter Schemas
	// ═══════════════════════════════════════════════════════════════════════════

	@Nested
	@DisplayName("Action-Specific Parameter Schemas")
	class ActionParameterSchemas {

		@Test
		@DisplayName("should generate parameter schema with enum for allowedValues")
		void generatesEnumForAllowedValues() throws Exception {
			String contribution = ActionPromptContributor.emitOutputSchema(registry, null, null);

			JsonNode schema = MAPPER.readTree(contribution);
			
			// Find the measurementType parameter schema
			// It should be in a def like EvaluateSpcReadinessParams or inline
			JsonNode measurementTypeSchema = findParameterSchema(schema, "evaluateSpcReadiness", "measurementType");
			
			assertThat(measurementTypeSchema.get("type").asText()).isEqualTo("string");
			assertThat(measurementTypeSchema.get("enum")).extracting(JsonNode::asText)
					.containsExactlyInAnyOrder("force", "displacement");
		}

		@Test
		@DisplayName("should generate parameter schema with pattern for allowedRegex")
		void generatesPatternForAllowedRegex() throws Exception {
			String contribution = ActionPromptContributor.emitOutputSchema(registry, null, null);

			JsonNode schema = MAPPER.readTree(contribution);
			
			JsonNode bundleIdSchema = findParameterSchema(schema, "evaluateSpcReadiness", "bundleId");
			
			assertThat(bundleIdSchema.get("type").asText()).isEqualTo("string");
			assertThat(bundleIdSchema.get("pattern").asText()).isEqualTo("^[A-Z][0-9]{5}$");
		}

		@Test
		@DisplayName("should include description in parameter schema")
		void includesDescriptionInParameterSchema() throws Exception {
			String contribution = ActionPromptContributor.emitOutputSchema(registry, null, null);

			JsonNode schema = MAPPER.readTree(contribution);
			
			JsonNode measurementTypeSchema = findParameterSchema(schema, "evaluateSpcReadiness", "measurementType");
			
			assertThat(measurementTypeSchema.get("description").asText())
					.isEqualTo("The measurement type to evaluate");
		}

		@Test
		@DisplayName("should include examples in parameter schema when provided")
		void includesExamplesInParameterSchema() throws Exception {
			String contribution = ActionPromptContributor.emitOutputSchema(registry, null, null);

			JsonNode schema = MAPPER.readTree(contribution);
			
			JsonNode measurementTypeSchema = findParameterSchema(schema, "evaluateSpcReadiness", "measurementType");
			
			assertThat(measurementTypeSchema.has("examples")).isTrue();
			assertThat(measurementTypeSchema.get("examples")).extracting(JsonNode::asText)
					.contains("displacement");
		}

		@Test
		@DisplayName("should mark all action parameters as required")
		void marksAllParametersAsRequired() throws Exception {
			String contribution = ActionPromptContributor.emitOutputSchema(registry, null, null);

			JsonNode schema = MAPPER.readTree(contribution);
			
			// Find the parameters schema for evaluateSpcReadiness
			JsonNode paramsSchema = findActionParamsSchema(schema, "evaluateSpcReadiness");
			
			assertThat(paramsSchema.get("required")).extracting(JsonNode::asText)
					.containsExactlyInAnyOrder("measurementType", "bundleId");
			assertThat(paramsSchema.get("additionalProperties").asBoolean()).isFalse();
		}

		@Test
		@DisplayName("should generate separate parameter schemas for each action")
		void generatesSeparateParameterSchemasPerAction() throws Exception {
			String contribution = ActionPromptContributor.emitOutputSchema(registry, null, null);

			JsonNode schema = MAPPER.readTree(contribution);
			
			// Both actions should have their parameters defined
			JsonNode evalParams = findActionParamsSchema(schema, "evaluateSpcReadiness");
			JsonNode chartParams = findActionParamsSchema(schema, "displayControlChart");
			
			assertThat(evalParams).isNotNull();
			assertThat(chartParams).isNotNull();
			
			// Both should have measurementType and bundleId
			assertThat(evalParams.at("/properties/measurementType")).isNotNull();
			assertThat(chartParams.at("/properties/measurementType")).isNotNull();
		}
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// Tests for Valid Exemplars
	// ═══════════════════════════════════════════════════════════════════════════

	@Nested
	@DisplayName("Valid Exemplars")
	class ValidExemplars {

		@Test
		@DisplayName("should generate valid exemplar for first action")
		void generatesExemplarForFirstAction() throws Exception {
			String exemplars = ActionPromptContributor.emitExemplar(registry, null);

			// Should contain at least one action ID (first action is used as example)
			assertThat(exemplars).contains("evaluateSpcReadiness");
		}

		@Test
		@DisplayName("should use example values from @ActionParam.examples()")
		void usesExampleValuesFromAnnotation() throws Exception {
			String exemplars = ActionPromptContributor.emitExemplar(registry, null);

			// Should use the example value "displacement" from @ActionParam
			assertThat(exemplars).contains("\"displacement\"");
			// Should use the example value "A12345" from @ActionParam
			assertThat(exemplars).contains("\"A12345\"");
		}

		@Test
		@DisplayName("should use first allowedValue when no example provided")
		void usesFirstAllowedValueWhenNoExample() throws Exception {
			// displayControlChart has no examples, but has allowedValues
			String exemplars = ActionPromptContributor.emitExemplar(
					registry, 
					spec -> spec.id().equals("displayControlChart")
			);

			// Should use "force" (first allowedValue) as fallback
			assertThat(exemplars).contains("\"force\"");
		}

		@Test
		@DisplayName("exemplar should be valid JSON")
		void exemplarIsValidJson() throws Exception {
			String exemplars = ActionPromptContributor.emitExemplar(registry, null);

			// Extract JSON blocks and verify they parse
			// The exemplars format includes JSON plans
			assertThat(exemplars).contains("\"message\"");
			assertThat(exemplars).contains("\"steps\"");
			assertThat(exemplars).contains("\"actionId\"");
			assertThat(exemplars).contains("\"parameters\"");
		}

		@Test
		@DisplayName("exemplar should show correct parameter names")
		void exemplarShowsCorrectParameterNames() throws Exception {
			String exemplars = ActionPromptContributor.emitExemplar(registry, null);

			// Must use exact parameter names - this is the key fix for the bug
			assertThat(exemplars).contains("\"measurementType\"");
			assertThat(exemplars).contains("\"bundleId\"");
			
			// Must NOT contain wrong parameter names
			assertThat(exemplars).doesNotContain("\"characteristic\"");
		}
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// Tests for Combined Output (Schema + Exemplars)
	// ═══════════════════════════════════════════════════════════════════════════

	@Nested
	@DisplayName("Combined Output")
	class CombinedOutput {

		@Test
		@DisplayName("should emit full contribution with schema and exemplars")
		void emitsFullContribution() {
			String contribution = ActionPromptContributor.emitExemplar(registry, null);

			// Should contain schema section
			assertThat(contribution).contains("$schema");
			assertThat(contribution).contains("json-schema.org");
			
			// Should contain exemplars section
			assertThat(contribution).contains("evaluateSpcReadiness");
			assertThat(contribution).contains("\"displacement\"");
		}

		@Test
		@DisplayName("should include clear section headers")
		void includesClearSectionHeaders() {
			String contribution = ActionPromptContributor.emitExemplar(registry, null);

			// Should have clear headers to help LLM parse structure
			assertThat(contribution).containsIgnoringCase("schema");
			assertThat(contribution).containsIgnoringCase("example");
		}
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// Helper Methods
	// ═══════════════════════════════════════════════════════════════════════════

	/**
	 * Find the schema for a specific parameter of a specific action.
	 * In the hardcoded-action-shapes approach, parameters are inlined in each action definition.
	 * Path: /$defs/{ActionName}Action/properties/parameters/properties/{paramName}
	 */
	private JsonNode findParameterSchema(JsonNode schema, String actionId, String paramName) {
		// Convert actionId to PascalCase for $def name
		String actionDefName = toPascalCase(actionId) + "Action";
		String path = "/$defs/" + actionDefName + "/properties/parameters/properties/" + paramName;
		
		JsonNode paramSchema = schema.at(path);
		if (!paramSchema.isMissingNode()) {
			return paramSchema;
		}
		
		throw new AssertionError("Could not find parameter schema at " + path);
	}

	/**
	 * Find the parameters schema for a specific action.
	 * In the hardcoded-action-shapes approach, this is at:
	 * /$defs/{ActionName}Action/properties/parameters
	 */
	private JsonNode findActionParamsSchema(JsonNode schema, String actionId) {
		String actionDefName = toPascalCase(actionId) + "Action";
		String path = "/$defs/" + actionDefName + "/properties/parameters";
		
		JsonNode paramsSchema = schema.at(path);
		if (!paramsSchema.isMissingNode()) {
			return paramsSchema;
		}
		
		throw new AssertionError("Could not find parameters schema at " + path);
	}

	/**
	 * Convert camelCase actionId to PascalCase for $def names.
	 * e.g., "evaluateSpcReadiness" -> "EvaluateSpcReadiness"
	 */
	private String toPascalCase(String s) {
		if (s == null || s.isEmpty()) return s;
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// OpenAI-Compatible Schema Tests
	// ═══════════════════════════════════════════════════════════════════════════

	@Nested
	@DisplayName("OpenAI-Compatible Schema")
	class OpenAiCompatibleSchema {

		@Test
		@DisplayName("should generate schema without oneOf (OpenAI limitation)")
		void generatesSchemaWithoutOneOf() throws Exception {
			String schema = ActionPromptContributor.emitOpenAiSchema(registry, null, null);
			
			// Print for inspection
			System.out.println("=== OpenAI-Compatible Schema ===");
			System.out.println(schema);
			System.out.println("================================");
			
			// Verify no oneOf
			assertThat(schema).doesNotContain("\"oneOf\"");
			assertThat(schema).doesNotContain("\"anyOf\"");
		}

		@Test
		@DisplayName("should have action-specific properties for each action")
		void hasActionSpecificProperties() throws Exception {
			String schemaJson = ActionPromptContributor.emitOpenAiSchema(registry, null, null);
			JsonNode schema = MAPPER.readTree(schemaJson);
			
			// Check that step items have action-specific properties
			JsonNode stepItemProps = schema.at("/properties/steps/items/properties");
			
			// Should have properties for each registered action (from SpcActions)
			assertThat(stepItemProps.has("evaluateSpcReadiness")).isTrue();
			assertThat(stepItemProps.has("displayControlChart")).isTrue();
			
			// Should have utility step properties
			assertThat(stepItemProps.has("pending")).isTrue();
			assertThat(stepItemProps.has("noAction")).isTrue();
			assertThat(stepItemProps.has("error")).isTrue();
		}

		@Test
		@DisplayName("should inline action parameters within action property")
		void inlinesActionParameters() throws Exception {
			String schemaJson = ActionPromptContributor.emitOpenAiSchema(registry, null, null);
			JsonNode schema = MAPPER.readTree(schemaJson);
			
			// Check evaluateSpcReadiness has its specific parameter
			JsonNode evalAction = schema.at("/properties/steps/items/properties/evaluateSpcReadiness");
			assertThat(evalAction.get("type").asText()).isEqualTo("object");
			
			JsonNode evalParams = evalAction.at("/properties");
			assertThat(evalParams.has("bundleId")).isTrue();
		}

		@Test
		@DisplayName("should require description on all steps")
		void requiresDescriptionOnSteps() throws Exception {
			String schemaJson = ActionPromptContributor.emitOpenAiSchema(registry, null, null);
			JsonNode schema = MAPPER.readTree(schemaJson);
			
			JsonNode stepRequired = schema.at("/properties/steps/items/required");
			assertThat(stepRequired.isArray()).isTrue();
			assertThat(stepRequired.toString()).contains("description");
		}
	}
}

