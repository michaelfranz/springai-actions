package org.javai.springai.actions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.javai.springai.actions.api.TypeHandlerRegistry;
import org.javai.springai.actions.conversation.ConversationPromptBuilder;
import org.javai.springai.actions.conversation.ConversationState;
import org.javai.springai.actions.internal.bind.ActionDescriptor;
import org.javai.springai.actions.internal.bind.ActionDescriptorFilter;
import org.javai.springai.actions.internal.bind.ActionRegistry;
import org.javai.springai.actions.internal.parse.RawPlan;
import org.javai.springai.actions.internal.plan.PlanFormulationResult;
import org.javai.springai.actions.internal.plan.PlannerOptions;
import org.javai.springai.actions.internal.plan.PromptPreview;
import org.javai.springai.actions.internal.prompt.PlanActionsContextContributor;
import org.javai.springai.actions.internal.prompt.SystemPromptBuilder;
import org.javai.springai.actions.internal.prompt.SystemPromptContext;
import org.javai.springai.actions.internal.resolve.DefaultPlanResolver;
import org.javai.springai.actions.internal.resolve.ResolutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.lang.NonNull;

/**
 * Fluent planner API that wraps Spring AI's ChatClient and surfaces prompt previews.
 * Plans are now exclusively JSON-based - there is no S-expression fallback.
 */
public final class Planner {
	private static final Logger logger = LoggerFactory.getLogger(Planner.class);
	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
	private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*\\n?(\\{.*?\\})\\s*```", Pattern.DOTALL);

	private final ChatClient chatClient;
	private final List<ChatClientTier> chatClientTiers;
	private final List<String> promptContributions;
	private final CollectedActions collectedActions;
	private final Object[] toolSources;
	private final List<PromptContributor> promptContributors;
	private final Map<String, Object> promptContext;
	private final PersonaSpec persona;
	private final TypeHandlerRegistry typeHandlerRegistry;

	private Planner(Builder builder) {
		// Get first tier's client for legacy compatibility (isDryRun, invokeModel)
		this.chatClient = builder.chatClientTiers.isEmpty() 
				? null 
				: builder.chatClientTiers.getFirst().chatClient();
		this.chatClientTiers = List.copyOf(builder.chatClientTiers);
		this.promptContributions = List.copyOf(builder.promptContributions);
		this.collectedActions = collectActions(builder.actionSources);
		this.toolSources = builder.toolSources != null ? builder.toolSources : new Object[0];
		this.promptContributors = List.copyOf(builder.promptContributors);
		this.promptContext = Map.copyOf(builder.promptContext);
		this.persona = builder.persona;
		this.typeHandlerRegistry = builder.typeHandlerRegistry;
	}

	public static Builder builder() {
		return new Builder();
	}


	// Conversation-aware entry point. Supply the rolling conversation state; this is the public API.
	public PlanFormulationResult formulatePlan(@NonNull String requestText, ConversationState state) {
		return formulatePlan(requestText, PlannerOptions.defaults(), state);
	}

	/**
	 * Convenience for tests: formulate with an initial state (first turn).
	 */
	PlanFormulationResult formulatePlan(@NonNull String requestText) {
		return formulatePlan(Objects.requireNonNull(requestText), PlannerOptions.defaults(), ConversationState.initial(requestText));
	}

	/**
	 * Internal/test-only: generate a plan using the provided options (e.g., dry-run, capture prompt).
	 */
	PlanFormulationResult formulatePlan(@NonNull String requestText, PlannerOptions options) {
		return formulatePlan(Objects.requireNonNull(requestText), options, ConversationState.initial(requestText));
	}

	/**
	 * Core formulation path.
	 */
	private PlanFormulationResult formulatePlan(@NonNull String requestText, PlannerOptions options, ConversationState state) {
		PlannerOptions effective = options != null ? options : PlannerOptions.defaults();
		CollectedActions actionContext = collectActions();
		List<ActionDescriptor> actionDescriptors = actionContext.descriptors();
		if (actionDescriptors == null) {
			actionDescriptors = List.of();
		}

		PromptPreview preview = buildPromptPreview(Objects.requireNonNull(requestText),
				Objects.requireNonNull(actionDescriptors),
				state);

		if (isDryRun(effective)) {
			return formulateDryRunPlan(preview, actionContext);
		}

		// Use tiered retry if configured, otherwise fall back to legacy single-client behavior
		if (!chatClientTiers.isEmpty()) {
			return formulatePlanWithRetry(preview, effective, actionContext);
		}

		// Dry-run path: no chat clients configured, return empty plan preview
		String response = invokeModel(preview);
		try {
			Plan plan = parsePlan(response, actionContext.registry());
			return new PlanFormulationResult(response, plan, preview, false, actionContext.registry());
		}
		catch (PlanParseException e) {
			// Surface a structured error plan instead of throwing, so callers can present
			// the issue without crashing the conversation.
			String snippet = response != null ? response : "<null>";
			if (snippet.length() > 800) {
				snippet = snippet.substring(0, 800) + "...";
			}
			String reason = "Failed to parse plan: " + e.getMessage() + " | raw response: " + snippet;
			Plan errorPlan = new Plan(
					response,
					List.of(new PlanStep.ErrorStep(reason))
			);
			return new PlanFormulationResult(response, errorPlan, preview, false, actionContext.registry());
		}
	}

	/**
	 * Build a prompt preview without calling the LLM.
	 */
	public PromptPreview preview(String requestText) {
		Objects.requireNonNull(requestText, "requestText must not be null");
		List<ActionDescriptor> descriptors = collectActions().descriptors();
		if (descriptors == null) {
			descriptors = List.of();
		}
		return buildPromptPreview(Objects.requireNonNull(requestText), Objects.requireNonNull(descriptors));
	}

	public ActionRegistry actionRegistry() {
		return collectedActions.registry();
	}

	private CollectedActions collectActions() {
		return collectedActions;
	}

	private CollectedActions collectActions(List<Object> sources) {
		ActionRegistry registry = new ActionRegistry();
		if (sources != null) {
			for (Object source : sources) {
				if (source != null) {
					registry.registerActions(source);
				}
			}
		}
		return new CollectedActions(registry.getActionDescriptors(), registry);
	}

	private PromptPreview buildPromptPreview(String requestText, List<ActionDescriptor> actionDescriptors) {
		String nonNullRequest = Objects.requireNonNull(requestText, "requestText must not be null");
		List<ActionDescriptor> safeDescriptors = actionDescriptors != null ? actionDescriptors : List.of();
		return buildPromptPreview(nonNullRequest, Objects.requireNonNull(safeDescriptors), null);
	}

	/**
	 * Builds the core planning directive that applies to ALL applications using this framework.
	 * This is domain-agnostic guidance about producing execution plans.
	 * Domain-specific guidance should come from the PersonaSpec.
	 */
	private static String buildPlanningDirective(List<ActionDescriptor> actionDescriptors) {
		StringBuilder sb = new StringBuilder();
		
		// Core identity - framework level
		sb.append("🎯 CORE DIRECTIVE:\n\n");
		sb.append("You are a PLANNER producing an execution plan. You are NOT a chatbot.\n");
		sb.append("Your output is a JSON plan that the application will execute.\n\n");
		
		// Output format
		sb.append("🔴 OUTPUT FORMAT - JSON ONLY:\n\n");
		sb.append("Respond with a JSON object. No prose. No markdown. Just JSON.\n\n");
		
		// List valid action IDs
		if (actionDescriptors != null && !actionDescriptors.isEmpty()) {
			sb.append("VALID ACTION IDs:\n");
			for (ActionDescriptor ad : actionDescriptors) {
				sb.append("  - \"").append(ad.id()).append("\"\n");
			}
			sb.append("\n");
		}
		
		// Step types - tightened per expert feedback to eliminate common misfires
		sb.append("📋 STEP TYPES (choose exactly one):\n\n");
		
		// 1. Action Step
		sb.append("ACTION STEP:\n");
		sb.append("- Use ONLY when ALL required params are present.\n");
		sb.append("- MUST include actionId and parameters.\n");
		sb.append("{\n");
		sb.append("  \"message\": \"Short summary.\",\n");
		sb.append("  \"steps\": [{\"actionId\": \"<id>\", \"description\": \"...\", \"parameters\": {...}}]\n");
		sb.append("}\n\n");
		
		// 2. Pending Step - with partial providedParams example
		sb.append("PENDING STEP:\n");
		sb.append("- Use when the correct action is known but a REQUIRED user-facing param is missing.\n");
		sb.append("- MUST include actionId, status:\"pending\", pendingParams, and providedParams.\n");
		sb.append("- IMPORTANT: NEVER assume missing required values. If a required field is not given, use PENDING.\n");
		sb.append("- providedParams should contain any values you DO have; pendingParams lists what's missing.\n");
		sb.append("{\n");
		sb.append("  \"message\": \"I need more information.\",\n");
		sb.append("  \"steps\": [{\n");
		sb.append("    \"actionId\": \"<id>\",\n");
		sb.append("    \"description\": \"...\",\n");
		sb.append("    \"status\": \"pending\",\n");
		sb.append("    \"pendingParams\": [{\"name\": \"<param>\", \"prompt\": \"What value?\"}],\n");
		sb.append("    \"providedParams\": { <any params you already have> }\n");
		sb.append("  }]\n");
		sb.append("}\n\n");
		
		// 3. No Action Step - note: no actionId
		sb.append("NO ACTION STEP:\n");
		sb.append("- Use ONLY when request is outside domain.\n");
		sb.append("- MUST NOT include actionId.\n");
		sb.append("{\n");
		sb.append("  \"message\": \"I can only help with [domain] tasks.\",\n");
		sb.append("  \"steps\": [{\"noAction\": true, \"reason\": \"...\"}]\n");
		sb.append("}\n\n");
		
		// 4. Error Step - note: no actionId
		sb.append("ERROR STEP:\n");
		sb.append("- Use ONLY when a tool/plan generation failure occurs.\n");
		sb.append("- MUST NOT include actionId.\n");
		sb.append("{\n");
		sb.append("  \"message\": \"There was a problem.\",\n");
		sb.append("  \"steps\": [{\"error\": true, \"reason\": \"...\"}]\n");
		sb.append("}\n\n");
		
		// Critical rules - framework level
		sb.append("🚨 CRITICAL RULES:\n");
		sb.append("- actionId is REQUIRED for ACTION and PENDING steps, FORBIDDEN for NO ACTION and ERROR steps.\n");
		sb.append("- Parameter names MUST be EXACTLY as shown in PLAN STEP OPTIONS—never invent keys.\n");
		sb.append("- If PLAN STEP OPTIONS shows NO parameters for an action, use ACTION STEP with empty \"parameters\": {}\n");
		sb.append("- For 'query' parameters: YOU generate the SQL from the user request and catalog. NEVER use PENDING.\n");
		sb.append("- For user-provided params (names, dates, amounts): use PENDING if missing.\n");
		sb.append("- NEVER use PENDING to ask for system data (basket, session, inventory)—actions have this automatically.\n");
		sb.append("- The 'message' is a SHORT UI summary. NEVER ask questions in it.\n");
		sb.append("\nSTOP after the closing brace. Emit nothing else.");
		
		return sb.toString();
	}

	private PromptPreview buildPromptPreview(@NonNull String requestText,
			@NonNull List<ActionDescriptor> actionDescriptors,
			ConversationState state) {
		List<String> systemMessages = new ArrayList<>();

		String systemPrompt = SystemPromptBuilder.build(
				collectedActions.registry(),
				ActionDescriptorFilter.ALL,
				this.promptContributors,
				this.promptContext,
				this.typeHandlerRegistry
		);
		if (!systemPrompt.isBlank()) {
			systemMessages.add(systemPrompt);
		}

		if (this.persona != null) {
			systemMessages.add(renderPersona(this.persona));
		}

		// Add contributions from prompt contributors (e.g., SqlCatalogContextContributor)
		// Merge conversation state into prompt context for context-aware contributors
		Map<String, Object> mergedContext = new HashMap<>(this.promptContext);
		if (state != null) {
			mergedContext.put("conversationState", state);
			if (state.workingContext() != null) {
				mergedContext.put("workingContext", state.workingContext());
			}
		}
		SystemPromptContext ctx = new SystemPromptContext(
				collectedActions.registry(), 
				actionDescriptors, 
				ActionDescriptorFilter.ALL, 
				mergedContext);
		for (PromptContributor contributor : this.promptContributors) {
			if (contributor != null) {
				contributor.contribute(ctx).ifPresent(systemMessages::add);
			}
		}

		// Add type-specific guidance from registered type handlers
		String typeGuidance = org.javai.springai.actions.internal.bind.ActionSchemaGenerator
				.collectTypeGuidance(collectedActions.registry(), this.typeHandlerRegistry);
		if (!typeGuidance.isBlank()) {
			systemMessages.add(typeGuidance);
		}

		systemMessages.addAll(promptContributions);

		// Conversation-aware retry addendum
		ConversationPromptBuilder.buildRetryAddendum(state).ifPresent(systemMessages::add);

		// Planning directive: placed LAST, immediately before user message, for maximum salience
		// Generate dynamically based on actual actions registered for this planner
		systemMessages.add(buildPlanningDirective(actionDescriptors));

		List<String> userMessages = List.of(requestText);
		List<String> actionNames = actionDescriptors.stream().map(ActionDescriptor::id).toList();

		return new PromptPreview(
				Objects.requireNonNull(systemMessages),
				Objects.requireNonNull(userMessages),
				List.of(),  // No grammar IDs - we use JSON now
				actionNames);
	}

	private boolean isDryRun(PlannerOptions options) {
		return options.dryRun() || (chatClient == null && chatClientTiers.isEmpty());
	}

	private PlanFormulationResult formulateDryRunPlan(PromptPreview preview, CollectedActions actionContext) {
		logger.debug("Dry-run enabled or ChatClient missing; skipping LLM call");
		return new PlanFormulationResult(
				"<dry run>", 
				new Plan("", List.of()), 
				preview, 
				true, 
				actionContext.registry(),
				PlanningMetrics.empty()
		);
	}

	@SuppressWarnings("null")
	private String invokeModel(PromptPreview preview) {
		Objects.requireNonNull(preview, "preview must not be null");
		Objects.requireNonNull(chatClient, "chatClient must not be null when invoking model");
		return invokeModelWith(chatClient, preview);
	}

	/**
	 * Invoke the specified chat client with the given prompt.
	 */
	@SuppressWarnings("null")
	private String invokeModelWith(ChatClient client, PromptPreview preview) {
		ChatClient.ChatClientRequestSpec request = client.prompt();
		request.tools(toolSources);
		preview.systemMessages().forEach(request::system);
		request.user(Objects.requireNonNull(preview.renderedUser()));
		String sys = String.join("\n---\n", preview.systemMessages());
		var response = request.call();
		String content = response.content();
		logger.info("System messages:\n{}", sys);
		logger.info("User message:\n{}", preview.renderedUser());
		logger.info("LLM response:\n{}", content);
		return content;
	}

	/**
	 * Attempt plan formulation with a specific chat client.
	 * Captures the outcome as an InvocationResult for retry logic.
	 */
	private InvocationResult attemptPlanFormulation(
			ChatClient client,
			PromptPreview preview,
			CollectedActions actionContext
	) {
		long startTime = System.currentTimeMillis();
		try {
			String response = invokeModelWith(client, preview);
			long duration = System.currentTimeMillis() - startTime;

			try {
				Plan plan = parsePlan(response, actionContext.registry());
				
				// Check if plan has errors (validation failed)
				if (plan.status() == PlanStatus.ERROR) {
					String errorDetail = plan.planSteps().stream()
							.filter(s -> s instanceof PlanStep.ErrorStep)
							.map(s -> ((PlanStep.ErrorStep) s).reason())
							.findFirst()
							.orElse("Plan contains errors");
					return InvocationResult.validationFailed(response, plan, errorDetail, duration);
				}
				
				return InvocationResult.success(response, plan, duration);
			} catch (PlanParseException e) {
				return InvocationResult.parseFailed(response, e.getMessage(), duration);
			}
		} catch (Exception e) {
			long duration = System.currentTimeMillis() - startTime;
			logger.warn("Network/API error during plan formulation: {}", e.getMessage());
			return InvocationResult.networkError(e.getMessage(), duration);
		}
	}

	/**
	 * Formulate plan with retry across tiered chat clients.
	 */
	private PlanFormulationResult formulatePlanWithRetry(
			PromptPreview preview,
			PlannerOptions options,
			CollectedActions actionContext
	) {
		List<AttemptRecord> attempts = new ArrayList<>();
		String lastResponse = null;
		Plan lastPlan = null;

		for (int tierIndex = 0; tierIndex < chatClientTiers.size(); tierIndex++) {
			ChatClientTier tier = chatClientTiers.get(tierIndex);
			String modelLabel = tier.modelId() != null ? tier.modelId() : "tier-" + tierIndex;

			for (int attempt = 1; attempt <= tier.maxAttempts(); attempt++) {
				logger.info("[Model Fallback] Invoking model '{}' - attempt {}/{}", 
						modelLabel, attempt, tier.maxAttempts());

				InvocationResult result = attemptPlanFormulation(
						tier.chatClient(), preview, actionContext);

				attempts.add(new AttemptRecord(
						tier.modelId(),
						tierIndex,
						attempt,
						result.outcome(),
						result.durationMillis(),
						result.errorDetails()
				));

				lastResponse = result.response();
				if (result.plan() != null) {
					lastPlan = result.plan();
				}

				if (result.isSuccess()) {
					logger.info("[Model Fallback] Success on model '{}' attempt {}/{} (total attempts: {})",
							modelLabel, attempt, tier.maxAttempts(), attempts.size());
					PlanningMetrics metrics = new PlanningMetrics(
							tier.modelId(), attempts.size(), attempts);
					return new PlanFormulationResult(
							result.response(), result.plan(), preview, false,
							actionContext.registry(), metrics);
				}

				logger.warn("[Model Fallback] Attempt {}/{} on model '{}' failed: {} - {}",
						attempt, tier.maxAttempts(), modelLabel, result.outcome(), result.errorDetails());
			}

			// Exhausted attempts for this tier
			logger.warn("[Model Fallback] Exhausted {} attempts on model '{}', moving to next fallback",
					tier.maxAttempts(), modelLabel);
		}

		// All tiers exhausted - return error plan with full metrics
		logger.error("[Model Fallback] All {} tiers exhausted after {} total attempts - returning error plan",
				chatClientTiers.size(), attempts.size());
		PlanningMetrics metrics = new PlanningMetrics(null, attempts.size(), attempts);

		Plan errorPlan;
		if (lastPlan != null) {
			errorPlan = lastPlan;
		} else {
			// Include the last error details in the reason for backward compatibility
			AttemptRecord lastAttempt = attempts.isEmpty() ? null : attempts.getLast();
			String lastError = lastAttempt != null && lastAttempt.errorDetails() != null 
					? lastAttempt.errorDetails() 
					: "unknown error";
			String snippet = lastResponse != null ? lastResponse : "<null>";
			if (snippet.length() > 800) {
				snippet = snippet.substring(0, 800) + "...";
			}
			String reason = "Failed to parse plan: " + lastError + " | raw response: " + snippet;
			errorPlan = new Plan(lastResponse, List.of(new PlanStep.ErrorStep(reason)));
		}

		return new PlanFormulationResult(
				lastResponse, errorPlan, preview, false,
				actionContext.registry(), metrics);
	}

	/**
	 * Parse plan from LLM response. Only supports JSON format.
	 */
	private Plan parsePlan(String response, ActionRegistry actionRegistry) {
		if (response == null || response.isBlank()) {
			throw new PlanParseException("LLM returned empty plan response");
		}

		return extractJsonContent(response)
				.map(json -> parseRawPlan(json, actionRegistry))
				.orElseThrow(() -> new PlanParseException("LLM response does not contain valid JSON plan"));
	}

	/**
	 * Extract JSON content from response, handling markdown code blocks.
	 */
	private Optional<String> extractJsonContent(String response) {
		String trimmed = response.trim();

		// Check for markdown code block
		Matcher matcher = JSON_BLOCK_PATTERN.matcher(trimmed);
		if (matcher.find()) {
			return Optional.of(matcher.group(1).trim());
		}

		// Check if response is direct JSON
		if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
			return Optional.of(trimmed);
		}

		return Optional.empty();
	}

	/**
	 * Parse JSON plan using RawPlan DTO.
	 */
	private Plan parseRawPlan(String json, ActionRegistry actionRegistry) {
		try {
			RawPlan jsonPlan = JSON_MAPPER.readValue(json, RawPlan.class);
			// Pass type handlers and prompt context to resolver for domain-specific handling
			ResolutionContext context = ResolutionContext.of(actionRegistry, typeHandlerRegistry, promptContext);
			// Resolve directly to bound Plan (includes validation)
			return new DefaultPlanResolver().resolve(jsonPlan, context);
		} catch (JsonProcessingException e) {
			throw new PlanParseException("Failed to parse JSON plan: " + e.getMessage(), e);
		}
	}

	/**
	 * Internal result of a single plan formulation attempt.
	 */
	private record InvocationResult(
			String response,
			Plan plan,
			AttemptOutcome outcome,
			String errorDetails,
			long durationMillis
	) {
		static InvocationResult success(String response, Plan plan, long durationMillis) {
			return new InvocationResult(response, plan, AttemptOutcome.SUCCESS, null, durationMillis);
		}

		static InvocationResult parseFailed(String response, String error, long durationMillis) {
			return new InvocationResult(response, null, AttemptOutcome.PARSE_FAILED, error, durationMillis);
		}

		static InvocationResult validationFailed(String response, Plan errorPlan, String error, long durationMillis) {
			return new InvocationResult(response, errorPlan, AttemptOutcome.VALIDATION_FAILED, error, durationMillis);
		}

		static InvocationResult networkError(String error, long durationMillis) {
			return new InvocationResult(null, null, AttemptOutcome.NETWORK_ERROR, error, durationMillis);
		}

		boolean isSuccess() {
			return outcome == AttemptOutcome.SUCCESS;
		}
	}

	/**
	 * Exception for plan parsing failures.
	 */
	public static class PlanParseException extends RuntimeException {
		public PlanParseException(String message) {
			super(message);
		}

		public PlanParseException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	public static final class Builder {
		private final List<ChatClientTier> chatClientTiers = new ArrayList<>();
		private boolean defaultClientSet = false;
		private ChatModel chatModel;
		private final List<ModelTierConfig> modelTierConfigs = new ArrayList<>();
		private final List<String> promptContributions = new ArrayList<>();
		private final List<Object> actionSources = new ArrayList<>();
		private Object[] toolSources;
		private final List<PromptContributor> promptContributors = new ArrayList<>();
		private final Map<String, Object> promptContext = new HashMap<>();
		private PersonaSpec persona;
		private TypeHandlerRegistry typeHandlerRegistry;

		private Builder() {
		}

		/**
		 * Directly inject ChatClient tiers. For testing with mocked clients.
		 */
		public Builder chatClientTiers(ChatClientTier... tiers) {
			this.chatClientTiers.addAll(Arrays.asList(tiers));
			this.defaultClientSet = !this.chatClientTiers.isEmpty();
			return this;
		}

		/**
		 * Set the ChatModel to use. Configure model tiers via {@link #tier(String, Consumer)}.
		 *
		 * @param chatModel the Spring AI ChatModel (e.g., OpenAiChatModel)
		 * @return this builder
		 */
		public Builder chatModel(ChatModel chatModel) {
			if (this.defaultClientSet) {
				throw new IllegalStateException("Cannot use chatModel() with chatClientTiers()");
			}
			this.chatModel = Objects.requireNonNull(chatModel, "chatModel must not be null");
			return this;
		}

		/**
		 * Configure a model tier with the given model name and options.
		 * 
		 * <p>Tiers are tried in order. The first tier is the primary model; subsequent tiers
		 * are fallbacks used when the primary exhausts its retry attempts.</p>
		 * 
		 * <p>Example:</p>
		 * <pre>{@code
		 * Planner.builder()
		 *     .actions(myActions)
		 *     .chatModel(openAiChatModel)
		 *     .tier("gpt-4.1-mini", tier -> tier.maxAttempts(2).temperature(1.0))
		 *     .tier("gpt-4o", tier -> tier.maxAttempts(2))
		 *     .build()
		 * }</pre>
		 *
		 * @param modelName the model identifier (e.g., "gpt-4.1-mini", "gpt-4o")
		 * @param configurer a function to configure tier options
		 * @return this builder
		 * @throws IllegalStateException if chatModel() was not called first
		 */
		public Builder tier(String modelName, Consumer<ModelTierConfig.Builder> configurer) {
			if (this.chatModel == null) {
				throw new IllegalStateException("Must call chatModel() before tier()");
			}
			if (this.defaultClientSet) {
				throw new IllegalStateException("Cannot use tier() with chatClientTiers()");
			}
			ModelTierConfig.Builder tierBuilder = ModelTierConfig.builder(modelName);
			configurer.accept(tierBuilder);
			this.modelTierConfigs.add(tierBuilder.build());
			return this;
		}

		public Builder promptContribution(String promptText) {
			if (promptText != null && !promptText.isBlank()) {
				promptContributions.add(promptText);
			}
			return this;
		}

		public Builder actions(Object... actions) {
			if (actions != null) {
				for (Object action : actions) {
					if (action != null) {
						actionSources.add(action);
					}
				}
			}
			return this;
		}

		public Builder tools(Object... tools) {
			toolSources = tools;
			return this;
		}

		public Builder promptContributor(PromptContributor contributor) {
			if (contributor != null) {
				this.promptContributors.add(contributor);
			}
			return this;
		}

		public Builder addPromptContext(String key, Object context) {
			if (key != null && !key.isBlank() && context != null) {
				this.promptContext.put(key, context);
			}
			return this;
		}

		public Builder persona(PersonaSpec persona) {
			this.persona = persona;
			return this;
		}

		public Planner build() {
			boolean hasPlanContributor = this.promptContributors.stream()
					.anyMatch(c -> c instanceof PlanActionsContextContributor);
			if (!hasPlanContributor) {
				this.promptContributors.add(new PlanActionsContextContributor());
			}
			if (this.typeHandlerRegistry == null) {
				this.typeHandlerRegistry = TypeHandlerRegistry.discover();
			}
			if (this.chatModel != null && !this.modelTierConfigs.isEmpty()) {
				createChatClientsFromTierConfigs();
			}
			return new Planner(this);
		}

		private void createChatClientsFromTierConfigs() {
			// Note: Structured outputs (ResponseFormat with JSON schema) is not used because:
			// 1. OpenAI strict mode requires ALL properties in 'required', but our schema
			//    has optional action properties (only one filled per step)
			// 2. The tagged-union schema format differs from RawPlanStep's expected format
			// We rely on prompt-based guidance via PlanActionsContextContributor.

			boolean first = true;
			for (ModelTierConfig tierConfig : this.modelTierConfigs) {
				OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
						.model(tierConfig.modelName());
				
				if (tierConfig.temperature() != null) {
					optionsBuilder.temperature(tierConfig.temperature());
				}
				if (tierConfig.topP() != null) {
					optionsBuilder.topP(tierConfig.topP());
				}
				
				ChatClient client = ChatClient.builder(this.chatModel)
						.defaultOptions(optionsBuilder.build())
						.build();
				
				this.chatClientTiers.add(new ChatClientTier(client, tierConfig.maxAttempts(), tierConfig.modelName()));
				
				if (first) {
					this.defaultClientSet = true;
					first = false;
				}
			}
		}
	}

	private record CollectedActions(List<ActionDescriptor> descriptors, ActionRegistry registry) {
	}

	private static String renderPersona(PersonaSpec persona) {
		StringBuilder sb = new StringBuilder("PERSONA:\n");
		if (persona.role() != null && !persona.role().isBlank()) {
			sb.append("Role: ").append(persona.role().trim()).append("\n");
		}
		if (persona.principles() != null && !persona.principles().isEmpty()) {
			sb.append("Principles:\n");
			for (String p : persona.principles()) {
				if (p != null && !p.isBlank()) {
					sb.append("- ").append(p.trim()).append("\n");
				}
			}
		}
		if (persona.constraints() != null && !persona.constraints().isEmpty()) {
			sb.append("Constraints:\n");
			for (String c : persona.constraints()) {
				if (c != null && !c.isBlank()) {
					sb.append("- ").append(c.trim()).append("\n");
				}
			}
		}
		if (persona.styleGuidance() != null && !persona.styleGuidance().isEmpty()) {
			sb.append("Style:\n");
			for (String s : persona.styleGuidance()) {
				if (s != null && !s.isBlank()) {
					sb.append("- ").append(s.trim()).append("\n");
				}
			}
		}
		return sb.toString().trim();
	}
}
