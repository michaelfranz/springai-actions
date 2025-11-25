package org.javai.springai.actions.planning;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.javai.springai.actions.definition.ActionDefinition;
import org.javai.springai.actions.definition.ActionDefinitionFactory;
import org.javai.springai.actions.execution.ExecutableAction;
import org.javai.springai.actions.execution.ExecutableActionFactory;
import org.javai.springai.actions.execution.ExecutablePlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

public class PlanningPromptSpec {

	private static final Logger logger = LoggerFactory.getLogger(PlanningPromptSpec.class);

	private final ChatClient.ChatClientRequestSpec delegate;
	private final List<String> systemMessages = new ArrayList<>();
	private final List<String> userMessages = new ArrayList<>();
	private final List<ActionDefinition> actions = new ArrayList<>();

	public PlanningPromptSpec(ChatClient.ChatClientRequestSpec delegate) {
		logger.debug("PlanningPromptSpec() called with delegate: {}", delegate);
		this.delegate = delegate;
	}

	public PlanningPromptSpec system(String text) {
		logger.debug("system() invoked with text: {}", text);
		systemMessages.add(text);
		return this;
	}

	public PlanningPromptSpec user(String text) {
		logger.debug("user() invoked with text: {}", text);
		userMessages.add(text);
		return this;
	}

	public PlanningPromptSpec tools(Object beanWithTools) {
		logger.debug("tools() invoked with bean: {}", beanWithTools);
		delegate.tools(beanWithTools);
		return this;
	}

	/**
	 * Register steps definitions from the given bean.
	 * Methods annotated with @Action will be reflected and described
	 * to the LLM in the system prompt.
	 */
	public PlanningPromptSpec actions(Object ... beanWithActions) {
		logger.debug("actions() invoked with {} bean(s)", beanWithActions == null ? 0 : beanWithActions.length);
		if (beanWithActions == null) return this;
		for (Object bean : beanWithActions) {
			if (bean == null) continue;
			List<ActionDefinition> defs = ActionDefinitionFactory.from(bean);
			this.actions.addAll(defs);
		}
		return this;
	}

	ChatClient.CallResponseSpec call() {
		List<String> finalSystem = new ArrayList<>();
		if (!actions.isEmpty()) {
			finalSystem.add(buildActionSchemaMessage());
		}
		finalSystem.addAll(systemMessages);  // user persona, domain instructions

		delegate.system(String.join("\n\n", finalSystem));
		delegate.user(String.join("\n\n", userMessages));

		return delegate.call();
	}

	public ExecutablePlan plan() {
		logger.debug("plan() invoked with {} registered action definition(s)", this.actions.size());
		Plan plan = ensureValidPlan(this.call().entity(Plan.class));
		return new ExecutablePlan(compilePlan(plan));
	}

	private Plan ensureValidPlan(Plan plan) {
		if (plan == null) {
			throw new IllegalStateException("""
                LLM returned null instead of an ActionPlan.
                This means the model failed to follow the planning instructions.
                """);
		}

		if (plan.steps() == null) {
			throw new IllegalStateException("""
                LLM returned null instead of plan steps.
                This means the model did not produce a valid plan.
                """);
		}

		return plan;
	}

	private List<ExecutableAction> compilePlan(Plan plan) {
		ExecutableActionFactory factory = new ExecutableActionFactory(this.actions);
		return plan.steps().stream()
				// should not be necessary: .filter(step -> factory.actionExists(step.action()))
				.map(factory::from)
				.toList();
	}

	private String buildActionSchemaMessage() {
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

		for (ActionDefinition def : actions) {
			sb.append("\n\nAction name: ").append(def.name()).append("\n");
			sb.append("Description: ").append(def.description()).append("\n");
			sb.append("Argument schema:\n");
			JsonNode schema = def.argumentSchema();
			sb.append(schema.toPrettyString()).append("\n");
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
}