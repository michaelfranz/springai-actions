package org.javai.springai.dsl.plan;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.execution.ActionResult;
import org.javai.springai.actions.execution.DefaultPlanExecutor;
import org.javai.springai.actions.execution.ExecutableAction;
import org.javai.springai.actions.execution.ExecutablePlan;
import org.javai.springai.actions.execution.PlanExecutionException;
import org.javai.springai.actions.execution.PlanExecutor;
import org.javai.springai.dsl.act.ActionDescriptor;
import org.javai.springai.dsl.act.ActionDescriptorFilter;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.bind.TypeFactoryBootstrap;
import org.javai.springai.dsl.conversation.ConversationPromptBuilder;
import org.javai.springai.dsl.conversation.ConversationState;
import org.javai.springai.dsl.exec.DefaultPlanResolver;
import org.javai.springai.dsl.exec.PlanResolver;
import org.javai.springai.dsl.exec.PlanVerifier;
import org.javai.springai.dsl.exec.ResolvedArgument;
import org.javai.springai.dsl.exec.ResolvedPlan;
import org.javai.springai.dsl.exec.ResolvedStep;
import org.javai.springai.dsl.prompt.DslContextContributor;
import org.javai.springai.dsl.prompt.DslGrammarSource;
import org.javai.springai.dsl.prompt.DslGuidanceProvider;
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

	private Planner(Builder builder) {
		this.chatClient = builder.chatClient;
		this.grammars = List.copyOf(builder.grammars);
		this.promptContributions = List.copyOf(builder.promptContributions);
		this.collectedActions = collectActions(builder.actionSources);
		this.toolSources = builder.toolSources;
		this.dslContributors = List.copyOf(builder.dslContributors);
		this.dslContext = Map.copyOf(builder.dslContext);
		this.capturePromptByDefault = builder.capturePromptByDefault;
		this.promptHook = builder.promptHook;
		this.validatorRegistry = buildValidatorRegistry(this.grammars);
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
			Plan plan = parsePlan(response);
			plan = new PlanVerifier(actionContext.registry()).verify(plan);
			maybeFirePromptHook(preview, effective);
			return new PlanFormulationResult(response, plan, preview, false, actionContext.registry());
		}
		catch (SxlParseException e) {
			// Surface a structured error plan instead of throwing, so callers can present
			// the issue without crashing the conversation.
			Plan errorPlan = new Plan(
					response,
					List.of(new PlanStep.ErrorStep("Failed to parse plan: " + e.getMessage()))
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

		systemMessages.addAll(promptContributions);

		// Conversation-aware retry addendum
		ConversationPromptBuilder.buildRetryAddendum(state).ifPresent(systemMessages::add);

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

	private String invokeModel(PromptPreview preview) {
		Objects.requireNonNull(preview, "preview must not be null");
		Objects.requireNonNull(chatClient, "chatClient must not be null when invoking model");
		ChatClient.ChatClientRequestSpec request = chatClient.prompt();
		request.tools(toolSources);
		preview.systemMessages().forEach(request::system);
		request.user(Objects.requireNonNull(preview.renderedUser()));
		return request.call().content();
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

	private Plan parsePlan(String response) {
		if (response == null || response.isBlank()) {
			throw new IllegalStateException("LLM returned empty plan llmResponse");
		}
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
			throw new IllegalStateException("LLM returned no parseable plan nodes");
		}
		SxlNode planNode = nodes.getFirst();
		return Plan.of(planNode);
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
				Object[] args = actionStep.arguments().stream().map(ResolvedArgument::value).toArray();
				Object result = actionStep.binding().method().invoke(actionStep.binding().bean(), args);
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
}

