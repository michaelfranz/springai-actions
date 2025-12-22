package org.javai.springai.dsl.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.javai.springai.dsl.act.ActionDescriptor;
import org.javai.springai.dsl.act.ActionParameterDescriptor;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.sxl.SxlNode;
import org.javai.springai.sxl.SxlParser;
import org.javai.springai.sxl.SxlTokenizer;
import org.javai.springai.sxl.grammar.SxlGrammar;
import org.javai.springai.sxl.grammar.SxlGrammarPromptGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Fluent planner API that wraps Spring AI's ChatClient and surfaces prompt previews.
 */
public final class Planner {

	private static final Logger logger = LoggerFactory.getLogger(Planner.class);
	private static final double DEFAULT_TEMPERATURE = 0.0; // A planner should lean strongly towards determinism
	private static final double DEFAULT_TOP_P = 1.0;

	private final ChatClient chatClient;
	private final String model;
	private final double temperature;
	private final double topP;
	private final List<SxlGrammar> grammars;
	private final List<String> promptContributions;
	private final List<Object> actionSources;
	private final boolean capturePromptByDefault;
	private final Consumer<PromptPreview> promptHook;

	private Planner(Builder builder) {
		this.chatClient = builder.chatClient;
		this.model = builder.model;
		this.temperature = builder.temperature;
		this.topP = builder.topP;
		this.grammars = List.copyOf(builder.grammars);
		this.promptContributions = List.copyOf(builder.promptContributions);
		this.actionSources = List.copyOf(builder.actionSources);
		this.capturePromptByDefault = builder.capturePromptByDefault;
		this.promptHook = builder.promptHook;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Generate a plan for the given request with default options.
	 */
	public Plan planActions(String requestText) {
		return planWithDetails(requestText, PlannerOptions.defaults()).plan();
	}

	/**
	 * Generate a plan and capture prompt details with default options.
	 */
	public PlanExecutionResult planWithDetails(String requestText) {
		return planWithDetails(requestText, PlannerOptions.defaults());
	}

	/**
	 * Generate a plan using the provided options (e.g., dry-run, capture prompt).
	 */
	public PlanExecutionResult planWithDetails(String requestText, PlannerOptions options) {
		Objects.requireNonNull(requestText, "requestText must not be null");
		PlannerOptions effective = options != null ? options : PlannerOptions.defaults();
		ActionContext actionContext = collectActions();

		logger.debug("Planner call options: model={}, temperature={}, topP={}", model, temperature, topP);

		PromptPreview preview = buildPromptPreview(requestText, actionContext.descriptors());
		if (effective.capturePrompt() || capturePromptByDefault) {
			fireHook(preview);
		}

		if (effective.dryRun() || chatClient == null) {
			logger.debug("Dry-run enabled or ChatClient missing; skipping LLM call");
			return new PlanExecutionResult(new Plan("", List.of()), preview, true);
		}

		ChatClient.ChatClientRequestSpec request = chatClient.prompt();
		for (String systemMessage : preview.systemMessages()) {
			if (systemMessage != null) {
				request.system(systemMessage);
			}
		}
		String userMessage = preview.renderedUser() != null ? preview.renderedUser() : "";
		request.user(Objects.requireNonNull(userMessage));

		String response = request.call().content();
		Plan plan = parsePlan(response);
		fireHook(preview);
		return new PlanExecutionResult(plan, preview, false);
	}

	/**
	 * Build a prompt preview without calling the LLM.
	 */
	public PromptPreview preview(String requestText) {
		return buildPromptPreview(requestText, collectActions().descriptors());
	}

	private ActionContext collectActions() {
		ActionRegistry registry = new ActionRegistry();
		for (Object source : actionSources) {
			if (source != null) {
				registry.registerActions(source);
			}
		}
		return new ActionContext(registry.getActionDescriptors(), registry);
	}

	private PromptPreview buildPromptPreview(String requestText, List<ActionDescriptor> actionDescriptors) {
		List<String> systemMessages = new ArrayList<>();

		if (!grammars.isEmpty()) {
			SxlGrammarPromptGenerator generator = new SxlGrammarPromptGenerator();
			for (SxlGrammar grammar : grammars) {
				String dslId = grammar.dsl() != null ? grammar.dsl().id() : "(unknown-dsl)";
				systemMessages.add("DSL " + dslId + ":\n" + generator.generate(grammar));
			}
		}

		if (!actionDescriptors.isEmpty()) {
			systemMessages.add(buildActionSchemaMessage(actionDescriptors));
		}

		systemMessages.addAll(promptContributions);

		List<String> userMessages = List.of(requestText);
		List<String> grammarIds = grammars.stream()
				.map(g -> g.dsl() != null ? g.dsl().id() : null)
				.filter(Objects::nonNull)
				.toList();
		List<String> actionNames = actionDescriptors.stream().map(ActionDescriptor::id).toList();

		return new PromptPreview(systemMessages, userMessages, grammarIds, actionNames);
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

	private String buildActionSchemaMessage(List<ActionDescriptor> actions) {
		StringBuilder sb = new StringBuilder();
		sb.append("""
            === PLAN AND ACTION DEFINITIONS ===

            Actions represent side-effectful operations that the application will perform later.

            Your task is to generate a FINAL PLAN consisting ONLY of PlanStep entries.
            Each PlanStep has the following exact structure:

            {
              "action": "<actionName>",
              "arguments": { ... }
            }

            The final output MUST be a single JSON object with exactly one property named "steps".
            The "steps" property MUST contain an array of PlanStep objects.
            Only use actions to create the plan. Tool calls are never part of the plan.
            Do NOT include any commentary, explanation, natural language, or additional fields.
            Do NOT prefix the plan with text.
            Do NOT append text after the plan.

            Here are the available actions and their required argument schemas.
            When constructing the plan:
            - Use each action's name EXACTLY as specified.
            - Follow the argument schema EXACTLY.
            - Provide values for all required fields.
            - Use information tools (function calls) to gather any missing data BEFORE you produce the final JSON plan.
            - Tool calls never appear inside the plan itself; invoke them first, wait for their responses, and only then emit the Plan JSON.
            - Only actions, which are named in the following list, may be used in the plan.
            """);

		for (ActionDescriptor descriptor : actions) {
			sb.append("\n\nAction id: ").append(descriptor.id()).append("\n");
			sb.append("Description: ").append(descriptor.description()).append("\n");
			sb.append("Parameters:\n");
			if (descriptor.actionParameterSpecs().isEmpty()) {
				sb.append("  (none)\n");
			} else {
				for (ActionParameterDescriptor param : descriptor.actionParameterSpecs()) {
					sb.append("  - ").append(param.name())
							.append(" (type: ").append(param.typeId()).append(")");
					if (param.dslId() != null && !param.dslId().isBlank()) {
						sb.append(" [dsl: ").append(param.dslId()).append("]");
					}
					if (param.description() != null && !param.description().isBlank()) {
						sb.append(" - ").append(param.description());
					}
					sb.append("\n");
				}
			}
		}

		sb.append("""

            === PLAN FORMAT EXAMPLE ===
            {
              "steps": [
                {
                  "action": "sendEmail",
                  "arguments": {
                    "to": "someone@example.com",
                    "subject": "Hello",
                    "body": "This is the email body."
                  }
                }
              ]
            }

            Remember:
            - The final output MUST be EXACTLY the JSON object with the "steps" array.
            - Do NOT include backticks or code fences.
            """);

		return sb.toString();
	}

	private Plan parsePlan(String response) {
		if (response == null || response.isBlank()) {
			throw new IllegalStateException("LLM returned empty plan response");
		}
		SxlTokenizer tokenizer = new SxlTokenizer(response);
		var tokens = tokenizer.tokenize();

		// Prefer plan grammar if provided
		SxlGrammar planGrammar = grammars.stream()
				.filter(g -> g.dsl() != null && "sxl-plan".equals(g.dsl().id()))
				.findFirst()
				.orElse(null);

		SxlParser parser = planGrammar != null ? new SxlParser(tokens, planGrammar) : new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		if (nodes.isEmpty()) {
			throw new IllegalStateException("LLM returned no parseable plan nodes");
		}
		SxlNode planNode = nodes.getFirst();
		return Plan.of(planNode);
	}

	public static final class Builder {
		private ChatClient chatClient;
		private String model;
		private double temperature = DEFAULT_TEMPERATURE;
		private double topP = DEFAULT_TOP_P;
		private final List<SxlGrammar> grammars = new ArrayList<>();
		private final List<String> promptContributions = new ArrayList<>();
		private final List<Object> actionSources = new ArrayList<>();
		private boolean capturePromptByDefault;
		private Consumer<PromptPreview> promptHook;

		private Builder() {
		}

		public Builder withChatClient(ChatClient chatClient) {
			this.chatClient = chatClient;
			return this;
		}

		public Builder withModel(String model) {
			this.model = model;
			return this;
		}

		public Builder withTemperature(double temperature) {
			this.temperature = temperature;
			return this;
		}

		public Builder withTopP(double topP) {
			this.topP = topP;
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

		public Builder addActions(Object... actions) {
			if (actions != null) {
				for (Object action : actions) {
					if (action != null) {
						actionSources.add(action);
					}
				}
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
			return new Planner(this);
		}
	}

	private record ActionContext(List<ActionDescriptor> descriptors, ActionRegistry registry) {
	}
}

