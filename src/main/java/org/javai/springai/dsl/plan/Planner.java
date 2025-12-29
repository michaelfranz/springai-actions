package org.javai.springai.dsl.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.execution.ActionResult;
import org.javai.springai.actions.execution.DefaultPlanExecutor;
import org.javai.springai.actions.execution.ExecutableAction;
import org.javai.springai.actions.execution.ExecutablePlan;
import org.javai.springai.actions.execution.PlanExecutionException;
import org.javai.springai.actions.execution.PlanExecutor;
import org.javai.springai.dsl.act.ActionDescriptor;
import org.javai.springai.dsl.act.ActionDescriptorFilter;
import org.javai.springai.dsl.act.ActionParameterDescriptor;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.bind.TypeFactoryBootstrap;
import org.javai.springai.dsl.conversation.ConversationPromptBuilder;
import org.javai.springai.dsl.conversation.ConversationState;
import org.javai.springai.dsl.exec.DefaultPlanResolver;
import org.javai.springai.dsl.exec.PlanResolver;
import org.javai.springai.dsl.exec.PlanVerifier;
import org.javai.springai.dsl.exec.ResolvedPlan;
import org.javai.springai.dsl.exec.ResolvedStep;
import org.javai.springai.dsl.prompt.DslContextContributor;
import org.javai.springai.dsl.prompt.DslGrammarSource;
import org.javai.springai.dsl.prompt.DslGuidanceProvider;
import org.javai.springai.dsl.prompt.PersonaSpec;
import org.javai.springai.dsl.prompt.PlanActionsContextContributor;
import org.javai.springai.dsl.prompt.SystemPromptBuilder;
import org.javai.springai.sxl.DefaultValidatorRegistry;
import org.javai.springai.sxl.DslParsingStrategy;
import org.javai.springai.sxl.SxlNode;
import org.javai.springai.sxl.SxlParseException;
import org.javai.springai.sxl.SxlParser;
import org.javai.springai.sxl.SxlTokenizer;
import org.javai.springai.sxl.UniversalParsingStrategy;
import org.javai.springai.sxl.ValidatorRegistry;
import org.javai.springai.sxl.grammar.SxlGrammar;
import org.javai.springai.sxl.grammar.SxlGrammarRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.lang.NonNull;

/**
 * Fluent planner API that wraps Spring AI's ChatClient and surfaces prompt previews.
 */
public final class Planner {
	private static final Logger logger = LoggerFactory.getLogger(Planner.class);
	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
	private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*\\n?(\\{.*?\\})\\s*```", Pattern.DOTALL);

	private final ChatClient chatClient;
	private final List<SxlGrammar> grammars;
	private final List<String> promptContributions;
	private final CollectedActions collectedActions;
	private final Object[] toolSources;
	private final List<DslContextContributor> dslContributors;
	private final Map<String, Object> dslContext;
	private final boolean capturePromptByDefault;
	private final Consumer<PromptPreview> promptHook;
	private final ValidatorRegistry validatorRegistry;
	private final PersonaSpec persona;

	private Planner(Builder builder) {
		this.chatClient = builder.chatClient;
		this.grammars = List.copyOf(builder.grammars);
		this.promptContributions = List.copyOf(builder.promptContributions);
		this.collectedActions = collectActions(builder.actionSources);
		this.toolSources = builder.toolSources != null ? builder.toolSources : new Object[0];
		this.dslContributors = List.copyOf(builder.dslContributors);
		this.dslContext = Map.copyOf(builder.dslContext);
		this.capturePromptByDefault = builder.capturePromptByDefault;
		this.promptHook = builder.promptHook;
		this.validatorRegistry = buildValidatorRegistry(this.grammars);
		this.persona = builder.persona;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Convenience: plan, resolve and execute in one step using defaults.
	 */
	public PlanRunResult planResolveAndExecute(@NonNull String requestText) throws PlanExecutionException {
		return planResolveAndExecute(requestText, new DefaultPlanResolver(), new DefaultPlanExecutor());
	}

	/**
	 * Convenience: plan, resolve and execute in one step with custom resolver/executor.
	 */
	public PlanRunResult planResolveAndExecute(@NonNull String requestText, PlanResolver resolver, PlanExecutor executor)
			throws PlanExecutionException {
		Objects.requireNonNull(requestText, "requestText must not be null");
		Objects.requireNonNull(resolver, "resolver must not be null");
		Objects.requireNonNull(executor, "executor must not be null");

		PlanFormulationResult planning = formulatePlan(requestText);
		ResolvedPlan resolution = resolver.resolve(planning.plan(), planning.actionRegistry());

		if (resolution.status() != org.javai.springai.dsl.plan.PlanStatus.READY) {
			throw new PlanExecutionException("Resolved plan is not READY: " + resolution.status(), null);
		}

		ExecutablePlan executablePlan = toExecutablePlan(resolution);
		ActionContext context = executor.execute(executablePlan);
		return PlanRunResult.success(planning, resolution, context);
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
			plan = new PlanVerifier(actionContext.registry()).verify(plan);
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
			3. Query parameters (marked S-expression) → embed as string: "(Q (F table t) ...)"
			
			Available actions:
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
							paramsStr.append(" (S-expression)");
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

		DslGuidanceProvider guidanceProvider = new InlineGrammarGuidanceProvider(this.grammars);
		String systemPrompt = SystemPromptBuilder.build(
				collectedActions.registry(),
				ActionDescriptorFilter.ALL,
				guidanceProvider,
				SystemPromptBuilder.Mode.SXL,
				this.dslContributors,
				this.dslContext,
				null,
				null
		);
		systemMessages.add(systemPrompt);

		if (this.persona != null) {
			systemMessages.add(renderPersona(this.persona));
		}

		systemMessages.addAll(promptContributions);

		// Conversation-aware retry addendum
		ConversationPromptBuilder.buildRetryAddendum(state).ifPresent(systemMessages::add);

		// Planning directive: placed LAST, immediately before user message, for maximum salience
		// Generate dynamically based on actual actions registered for this planner
		systemMessages.add(buildPlanningDirective(actionDescriptors));

		List<String> userMessages = List.of(requestText);
		List<String> grammarIds = grammars.stream()
				.map(g -> g.dsl() != null ? g.dsl().id() : null)
				.filter(Objects::nonNull)
				.toList();
		List<String> actionNames = actionDescriptors.stream().map(ActionDescriptor::id).toList();

		return new PromptPreview(
				Objects.requireNonNull(systemMessages),
				Objects.requireNonNull(userMessages),
				grammarIds,
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
	 * Parse plan from LLM response. Tries JSON first, falls back to S-expression for backwards compatibility.
	 */
	private Plan parsePlan(String response, ActionRegistry actionRegistry) {
		if (response == null || response.isBlank()) {
			throw new PlanParseException("LLM returned empty plan response");
		}

		String jsonContent = extractJsonContent(response);
		if (jsonContent != null) {
			return parseJsonPlan(jsonContent, actionRegistry);
		}

		// Fallback to S-expression parsing for backwards compatibility
		return parseSxlPlan(response);
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
	 * Parse JSON plan using JsonPlan DTO.
	 */
	private Plan parseJsonPlan(String json, ActionRegistry actionRegistry) {
		try {
			JsonPlan jsonPlan = JSON_MAPPER.readValue(json, JsonPlan.class);

			// Build parameter order map from action registry
			Map<String, String[]> parameterOrders = new HashMap<>();
			for (ActionDescriptor descriptor : actionRegistry.getActionDescriptors()) {
				List<ActionParameterDescriptor> params = descriptor.actionParameterSpecs();
				if (params != null && !params.isEmpty()) {
					String[] orderedNames = params.stream()
							.map(ActionParameterDescriptor::name)
							.toArray(String[]::new);
					parameterOrders.put(descriptor.id(), orderedNames);
				}
			}

			return jsonPlan.toPlan(parameterOrders);
		} catch (JsonProcessingException e) {
			throw new PlanParseException("Failed to parse JSON plan: " + e.getMessage(), e);
		}
	}

	/**
	 * Fallback S-expression parsing for backwards compatibility.
	 */
	private Plan parseSxlPlan(String response) {
		try {
			SxlTokenizer tokenizer = new SxlTokenizer(response);
			var tokens = tokenizer.tokenize();

			// Prefer plan grammar if provided
			SxlGrammar planGrammar = grammars.stream()
					.filter(g -> g.dsl() != null && "sxl-plan".equals(g.dsl().id()))
					.findFirst()
					.orElse(null);

			var strategy = planGrammar != null
					? new DslParsingStrategy(planGrammar, this.validatorRegistry)
					: new UniversalParsingStrategy();
			SxlParser parser = new SxlParser(tokens, strategy);
			List<SxlNode> nodes = parser.parse();
			if (nodes.isEmpty()) {
				throw new PlanParseException("LLM returned no parseable plan nodes");
			}
			SxlNode planNode = nodes.getFirst();
			return Plan.of(planNode);
		} catch (SxlParseException e) {
			throw new PlanParseException("Failed to parse S-expression plan: " + e.getMessage(), e);
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

	private static ValidatorRegistry buildValidatorRegistry(List<SxlGrammar> grammars) {
		DefaultValidatorRegistry registry = new DefaultValidatorRegistry();
		if (grammars != null) {
			for (SxlGrammar grammar : grammars) {
				if (grammar != null && grammar.dsl() != null && grammar.dsl().id() != null
						&& !grammar.dsl().id().isBlank()) {
					registry.addGrammar(grammar.dsl().id(), grammar);
				}
			}
		}
		return registry;
	}

	private ExecutablePlan toExecutablePlan(ResolvedPlan resolvedPlan) {
		List<ExecutableAction> actions = new ArrayList<>();
		for (ResolvedStep step : resolvedPlan.steps()) {
			if (step instanceof ResolvedStep.ActionStep actionStep) {
				actions.add(toExecutableAction(actionStep));
			}
		}
		return new ExecutablePlan(actions);
	}

	private ExecutableAction toExecutableAction(ResolvedStep.ActionStep actionStep) {
		return ctx -> {
			try {
				var binding = actionStep.binding();
				var method = binding.method();
				var params = method.getParameters();
				Object[] args = new Object[params.length];
				int argIdx = 0;
				for (int i = 0; i < params.length; i++) {
					if (params[i].getType() == ActionContext.class) {
						args[i] = ctx;
					}
					else {
						args[i] = actionStep.arguments().get(argIdx++).value();
					}
				}
				Object result = method.invoke(binding.bean(), args);
				if (binding.contextKey() != null && !binding.contextKey().isBlank()) {
					ctx.put(binding.contextKey(), result);
				}
				return new ActionResult.Success(result);
			}
			catch (InvocationTargetException ex) {
				Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
				return new ActionResult.Failure(new String[] { cause.getMessage() });
			}
			catch (Exception ex) {
				return new ActionResult.Failure(new String[] { ex.getMessage() });
			}
		};
	}

	public static final class Builder {
		private ChatClient chatClient;
		private final List<SxlGrammar> grammars = new ArrayList<>();
		private final List<String> promptContributions = new ArrayList<>();
		private final List<Object> actionSources = new ArrayList<>();
		private Object[] toolSources;
		private final List<DslContextContributor> dslContributors = new ArrayList<>();
		private final Map<String, Object> dslContext = new HashMap<>();
		private boolean capturePromptByDefault;
		private Consumer<PromptPreview> promptHook;
		private PersonaSpec persona;

		private Builder() {
		}

		public Builder withChatClient(ChatClient chatClient) {
			this.chatClient = chatClient;
			return this;
		}

		public Builder addGrammar(SxlGrammar grammar) {
			if (grammar != null) {
				String dslId = grammar.dsl() != null ? grammar.dsl().id() : null;
				boolean alreadyPresent = dslId != null && grammars.stream()
						.anyMatch(g -> g.dsl() != null && dslId.equals(g.dsl().id()));
				if (!alreadyPresent) {
					grammars.add(grammar);
				}
			}
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

		public Builder addDslContextContributor(DslContextContributor contributor) {
			if (contributor != null) {
				this.dslContributors.add(contributor);
			}
			return this;
		}

		public Builder addDslContext(String dslId, Object context) {
			if (dslId != null && !dslId.isBlank() && context != null) {
				this.dslContext.put(dslId, context);
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
			// Ensure built-in DSL type mappings exist (plan/sql)
			TypeFactoryBootstrap.registerBuiltIns();

			// Auto-discover grammars: explicit additions win, then defaults/meta-inf.
			SxlGrammarRegistry registry = SxlGrammarRegistry.create();
			registry.registerUniversal(Planner.class.getClassLoader());
			registry.registerResource("META-INF/sxl-meta-grammar-plan.yml");
			registry.registerResource("META-INF/sxl-meta-grammar-sql.yml");
			registry.registerMetaInfGrammars(Planner.class.getClassLoader());

			Map<String, SxlGrammar> merged = new LinkedHashMap<>();
			for (SxlGrammar grammar : this.grammars) {
				if (grammar != null && grammar.dsl() != null && grammar.dsl().id() != null) {
					merged.putIfAbsent(grammar.dsl().id(), grammar);
				}
			}
			for (SxlGrammar grammar : registry.grammars()) {
				if (grammar != null && grammar.dsl() != null && grammar.dsl().id() != null) {
					merged.putIfAbsent(grammar.dsl().id(), grammar);
				}
			}
			this.grammars.clear();
			this.grammars.addAll(merged.values());

			// Ensure plan actions contributor is present
			boolean hasPlanContributor = this.dslContributors.stream()
					.anyMatch(c -> c != null && "sxl-plan".equals(c.dslId()));
			if (!hasPlanContributor) {
				this.dslContributors.add(new PlanActionsContextContributor());
			}
			return new Planner(this);
		}
	}

	private record CollectedActions(List<ActionDescriptor> descriptors, ActionRegistry registry) {
	}

	/**
	 * Lightweight provider that serves guidance and grammar access from in-memory grammars.
	 */
	private static final class InlineGrammarGuidanceProvider implements DslGuidanceProvider, DslGrammarSource {

		private final Map<String, SxlGrammar> grammarsById;

		InlineGrammarGuidanceProvider(List<SxlGrammar> grammars) {
			this.grammarsById = new LinkedHashMap<>();
			if (grammars != null) {
				for (SxlGrammar grammar : grammars) {
					if (grammar != null && grammar.dsl() != null && grammar.dsl().id() != null) {
						this.grammarsById.putIfAbsent(grammar.dsl().id(), grammar);
					}
				}
			}
		}

		@Override
		public Optional<String> guidanceFor(String dslId, String providerId, String modelId) {
			SxlGrammar grammar = grammarsById.get(dslId);
			if (grammar == null || grammar.llmSpecs() == null) {
				return Optional.empty();
			}

			// model-specific override wins if present
			if (providerId != null && modelId != null && grammar.llmSpecs().models() != null) {
				var providerModels = grammar.llmSpecs().models().get(providerId);
				if (providerModels != null) {
					var modelOverrides = providerModels.get(modelId);
					if (modelOverrides != null && modelOverrides.overrides() != null) {
						String guidance = clean(modelOverrides.overrides().guidance());
						if (guidance != null) {
							return Optional.of(guidance);
						}
					}
				}
			}

			// provider-level default next
			if (providerId != null && grammar.llmSpecs().providerDefaults() != null) {
				var providerDefaults = grammar.llmSpecs().providerDefaults().get(providerId);
				if (providerDefaults != null) {
					String guidance = clean(providerDefaults.guidance());
					if (guidance != null) {
						return Optional.of(guidance);
					}
				}
			}

			// fall back to defaults
			if (grammar.llmSpecs().defaults() != null) {
				String guidance = clean(grammar.llmSpecs().defaults().guidance());
				if (guidance != null) {
					return Optional.of(guidance);
				}
			}

			return Optional.empty();
		}

		@Override
		public Optional<SxlGrammar> grammarFor(String dslId) {
			return Optional.ofNullable(grammarsById.get(dslId));
		}

		private static String clean(String guidance) {
			if (guidance == null || guidance.isBlank()) {
				return null;
			}
			return guidance.trim();
		}
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

