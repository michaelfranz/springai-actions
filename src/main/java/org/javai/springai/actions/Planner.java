package org.javai.springai.actions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.javai.springai.actions.internal.bind.ActionDescriptor;
import org.javai.springai.actions.internal.bind.ActionDescriptorFilter;
import org.javai.springai.actions.internal.bind.ActionParameterDescriptor;
import org.javai.springai.actions.internal.bind.ActionRegistry;
import org.javai.springai.actions.conversation.ConversationPromptBuilder;
import org.javai.springai.actions.conversation.ConversationState;
import org.javai.springai.actions.internal.parse.RawPlan;
import org.javai.springai.actions.internal.plan.PlanFormulationResult;
import org.javai.springai.actions.internal.plan.PlannerOptions;
import org.javai.springai.actions.internal.plan.PromptPreview;
import org.javai.springai.actions.internal.resolve.DefaultPlanResolver;
import org.javai.springai.actions.PersonaSpec;
import org.javai.springai.actions.internal.prompt.PlanActionsContextContributor;
import org.javai.springai.actions.PromptContributor;
import org.javai.springai.actions.internal.prompt.SystemPromptBuilder;
import org.javai.springai.actions.internal.prompt.SystemPromptContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
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
	private final List<String> promptContributions;
	private final CollectedActions collectedActions;
	private final Object[] toolSources;
	private final List<PromptContributor> promptContributors;
	private final Map<String, Object> promptContext;
	private final boolean capturePromptByDefault;
	private final Consumer<PromptPreview> promptHook;
	private final PersonaSpec persona;

	private Planner(Builder builder) {
		this.chatClient = builder.chatClient;
		this.promptContributions = List.copyOf(builder.promptContributions);
		this.collectedActions = collectActions(builder.actionSources);
		this.toolSources = builder.toolSources != null ? builder.toolSources : new Object[0];
		this.promptContributors = List.copyOf(builder.promptContributors);
		this.promptContext = Map.copyOf(builder.promptContext);
		this.capturePromptByDefault = builder.capturePromptByDefault;
		this.promptHook = builder.promptHook;
		this.persona = builder.persona;
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
		maybeFirePromptHook(preview, effective);

		if (isDryRun(effective)) {
			return formulateDryRunPlan(preview, actionContext);
		}

		String response = invokeModel(preview);
		try {
			Plan plan = parsePlan(response, actionContext.registry());
			maybeFirePromptHook(preview, effective);
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
			maybeFirePromptHook(preview, effective);
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

	private static final String PLANNING_DIRECTIVE_TEMPLATE = """
			🔴 OUTPUT FORMAT - JSON ONLY:
			
			Respond with a JSON object. No prose. No markdown. Just JSON.
			
			Structure:
			{
			  "message": "Brief description of the plan",
			  "steps": [
			    {
			      "actionId": "action-id",
			      "description": "Why this step is needed",
			      "parameters": { "param1": value1, "param2": value2 }
			    }
			  ]
			}
			
			PARAMETER TYPES:
			1. Simple types (string, int, boolean) → use JSON primitives directly
			2. Complex types (objects) → use JSON objects matching the parameter's structure
			3. Query parameters (SQL) → embed as string containing a valid SELECT statement
			
			Available actions identified by actionId:
			%s
			
			STOP after the closing brace. Emit nothing else.""";

	private static String buildPlanningDirective(List<ActionDescriptor> actionDescriptors) {
		StringBuilder actionsList = new StringBuilder();
		if (actionDescriptors != null && !actionDescriptors.isEmpty()) {
			for (ActionDescriptor descriptor : actionDescriptors) {
				String actionId = descriptor.id();
				List<ActionParameterDescriptor> params = descriptor.actionParameterSpecs();
				if (params != null && !params.isEmpty()) {
					StringBuilder paramsStr = new StringBuilder();
					StringBuilder examplesStr = new StringBuilder();
					for (ActionParameterDescriptor p : params) {
						if (!paramsStr.isEmpty()) {
							paramsStr.append(", ");
						}
						paramsStr.append(p.name()).append(": ").append(p.typeId());
						if (p.dslId() != null && !p.dslId().isBlank()) {
							paramsStr.append(" (SQL query)");
						}
						// Include examples for complex types to guide JSON structure
						if (p.examples() != null && p.examples().length > 0) {
							examplesStr.append("\n    Example ").append(p.name()).append(": ").append(p.examples()[0]);
						}
					}
					actionsList.append(String.format("- %s { %s }%s%n", actionId, paramsStr, examplesStr));
				} else {
					actionsList.append(String.format("- %s (no parameters)%n", actionId));
				}
			}
		}
		return String.format(PLANNING_DIRECTIVE_TEMPLATE, actionsList.toString());
	}

	private PromptPreview buildPromptPreview(@NonNull String requestText,
			@NonNull List<ActionDescriptor> actionDescriptors,
			ConversationState state) {
		List<String> systemMessages = new ArrayList<>();

		String systemPrompt = SystemPromptBuilder.build(
				collectedActions.registry(),
				ActionDescriptorFilter.ALL,
				this.promptContributors,
				this.promptContext
		);
		systemMessages.add(systemPrompt);

		if (this.persona != null) {
			systemMessages.add(renderPersona(this.persona));
		}

		// Add contributions from prompt contributors (e.g., SqlCatalogContextContributor)
		SystemPromptContext ctx = new SystemPromptContext(
				collectedActions.registry(), 
				actionDescriptors, 
				ActionDescriptorFilter.ALL, 
				this.promptContext);
		for (PromptContributor contributor : this.promptContributors) {
			if (contributor != null) {
				contributor.contribute(ctx).ifPresent(systemMessages::add);
			}
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

	private void maybeFirePromptHook(PromptPreview preview, PlannerOptions options) {
		if (options.capturePrompt() || capturePromptByDefault) {
			fireHook(preview);
		}
	}

	private boolean isDryRun(PlannerOptions options) {
		return options.dryRun() || chatClient == null;
	}

	private PlanFormulationResult formulateDryRunPlan(PromptPreview preview, CollectedActions actionContext) {
		logger.debug("Dry-run enabled or ChatClient missing; skipping LLM call");
		return new PlanFormulationResult("<dry run>", new Plan("", List.of()), preview, true, actionContext.registry());
	}

	@SuppressWarnings("null")
	private String invokeModel(PromptPreview preview) {
		Objects.requireNonNull(preview, "preview must not be null");
		Objects.requireNonNull(chatClient, "chatClient must not be null when invoking model");
		ChatClient.ChatClientRequestSpec request = chatClient.prompt();
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

	private void fireHook(PromptPreview preview) {
		if (promptHook != null && preview != null) {
			try {
				promptHook.accept(preview);
			}
			catch (Exception ex) {
				logger.warn("Prompt hook threw an exception", ex);
			}
		}
	}

	/**
	 * Parse plan from LLM response. Only supports JSON format.
	 */
	private Plan parsePlan(String response, ActionRegistry actionRegistry) {
		if (response == null || response.isBlank()) {
			throw new PlanParseException("LLM returned empty plan response");
		}

		String jsonContent = extractJsonContent(response);
		if (jsonContent != null) {
			return parseRawPlan(jsonContent, actionRegistry);
		}

		throw new PlanParseException("LLM response does not contain valid JSON plan");
	}

	/**
	 * Extract JSON content from response, handling markdown code blocks.
	 */
	private String extractJsonContent(String response) {
		String trimmed = response.trim();

		// Check for markdown code block
		Matcher matcher = JSON_BLOCK_PATTERN.matcher(trimmed);
		if (matcher.find()) {
			return matcher.group(1).trim();
		}

		// Check if response is direct JSON
		if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
			return trimmed;
		}

		return null;
	}

	/**
	 * Parse JSON plan using RawPlan DTO.
	 */
	private Plan parseRawPlan(String json, ActionRegistry actionRegistry) {
		try {
			RawPlan jsonPlan = JSON_MAPPER.readValue(json, RawPlan.class);
			// Resolve directly to bound Plan (includes validation)
			return new DefaultPlanResolver().resolve(jsonPlan, actionRegistry);
		} catch (JsonProcessingException e) {
			throw new PlanParseException("Failed to parse JSON plan: " + e.getMessage(), e);
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
		private ChatClient chatClient;
		private final List<String> promptContributions = new ArrayList<>();
		private final List<Object> actionSources = new ArrayList<>();
		private Object[] toolSources;
		private final List<PromptContributor> promptContributors = new ArrayList<>();
		private final Map<String, Object> promptContext = new HashMap<>();
		private boolean capturePromptByDefault;
		private Consumer<PromptPreview> promptHook;
		private PersonaSpec persona;

		private Builder() {
		}

		public Builder withChatClient(ChatClient chatClient) {
			this.chatClient = chatClient;
			return this;
		}

		public Builder addPromptContribution(String promptText) {
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

		/**
		 * Add a prompt contributor that provides dynamic context to the system prompt.
		 * @deprecated Use {@link #addPromptContributor(PromptContributor)} instead
		 */
		@Deprecated(forRemoval = true)
		public Builder addDslContextContributor(PromptContributor contributor) {
			return addPromptContributor(contributor);
		}

		/**
		 * Add a prompt contributor that provides dynamic context to the system prompt.
		 */
		public Builder addPromptContributor(PromptContributor contributor) {
			if (contributor != null) {
				this.promptContributors.add(contributor);
			}
			return this;
		}

		/**
		 * Add context data that can be accessed by prompt contributors.
		 * @deprecated Use {@link #addPromptContext(String, Object)} instead
		 */
		@Deprecated(forRemoval = true)
		public Builder addDslContext(String key, Object context) {
			return addPromptContext(key, context);
		}

		/**
		 * Add context data that can be accessed by prompt contributors.
		 */
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

		public Builder enablePromptCapture() {
			this.capturePromptByDefault = true;
			return this;
		}

		public Builder onPrompt(Consumer<PromptPreview> hook) {
			this.promptHook = hook;
			return this;
		}

		public Planner build() {
			// Ensure plan actions contributor is present (provides action catalog)
			boolean hasPlanContributor = this.promptContributors.stream()
					.anyMatch(c -> c instanceof PlanActionsContextContributor);
			if (!hasPlanContributor) {
				this.promptContributors.add(new PlanActionsContextContributor());
			}
			return new Planner(this);
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
