package org.javai.springai.actions.planning;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.javai.springai.actions.definition.ActionDefinition;
import org.javai.springai.actions.definition.ActionDefinitionFactory;
import org.javai.springai.actions.execution.ActionPlanCompiler;
import org.javai.springai.actions.execution.ActionPlanResult;
import org.javai.springai.actions.execution.ExecutableAction;
import org.javai.springai.actions.execution.ExecutableActionFactory;
import org.springframework.ai.chat.client.ChatClient;

public class PlanningPromptSpec {

	private final ChatClient.ChatClientRequestSpec delegate;
	private final List<String> systemMessages = new ArrayList<>();
	private final List<String> userMessages = new ArrayList<>();
	private final List<ActionDefinition> actions = new ArrayList<>();

	public PlanningPromptSpec(ChatClient.ChatClientRequestSpec delegate) {
		this.delegate = delegate;
	}

	public PlanningPromptSpec system(String text) {
		systemMessages.add(text);
		return this;
	}

	public PlanningPromptSpec user(String text) {
		userMessages.add(text);
		return this;
	}

	public PlanningPromptSpec tools(Object beanWithTools) {
		delegate.tools(beanWithTools);
		return this;
	}

	/**
	 * Register steps definitions from the given bean.
	 * Methods annotated with @Action will be reflected and described
	 * to the LLM in the system prompt.
	 */
	public PlanningPromptSpec actions(Object ... beanWithActions) {
		if (beanWithActions == null) return this;
		for (Object bean : beanWithActions) {
			if (bean == null) continue;
			List<ActionDefinition> defs = ActionDefinitionFactory.from(bean);
			this.actions.addAll(defs);
		}
		return this;
	}

	public ChatClient.CallResponseSpec call() {
		List<String> finalSystem = new ArrayList<>();
		if (!actions.isEmpty()) {
			finalSystem.add(buildActionSchemaMessage());
		}
		finalSystem.addAll(systemMessages);  // user persona, domain instructions

		delegate.system(String.join("\n\n", finalSystem));
		delegate.user(String.join("\n\n", userMessages));

		return delegate.call();
	}

	public ActionPlanResult plan() {

		// 1. Get the plan from LLM
		ActionPlan plan = this.call().entity(ActionPlan.class);

		if (plan == null) {
			throw new IllegalStateException("""
                LLM returned null instead of an ActionPlan.
                This means the model failed to follow the planning instructions.
                """);
		}

		if (plan.steps() == null || plan.steps().isEmpty()) {
			throw new IllegalStateException("""
                LLM returned an ActionPlan with no steps.
                This means the model did not produce a valid plan.
                """);
		}

		// 2. Compile to executable actions using the existing action definitions
		ExecutableActionFactory factory = new ExecutableActionFactory(this.actions);
		ActionPlanCompiler compiler = new ActionPlanCompiler(factory);
		List<ExecutableAction> executables = compiler.compile(plan);

		return new ActionPlanResult(plan, executables);
	}

	private String buildActionSchemaMessage() {
		StringBuilder sb = new StringBuilder();
		sb.append("""
            === ACTION DEFINITIONS ===

            Actions represent side-effectful operations that the application will perform later.

            Your task is to generate a FINAL PLAN consisting ONLY of steps steps.
            Each steps step has the following exact structure:

            {
              "steps": "<actionName>",
              "arguments": { ... }
            }

            The final output MUST be a single, top-level JSON array containing only these steps steps.
            Do NOT wrap the array inside an object.
            Do NOT include any commentary, explanation, natural language, or additional fields.
            Do NOT prefix the plan with text.
            Do NOT append text after the plan.
            
            Here are the available actions and their required argument schemas.
            When constructing the plan:
            - Use each steps's name EXACTLY as specified.
            - Follow the argument schema EXACTLY.
            - Provide values for all required fields.
            - Use information tools (queries) to gather any missing data.
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
            [
              {
                "steps": "sendEmail",
                "arguments": {
                  "to": "someone@example.com",
                  "subject": "Hello",
                  "body": "This is the email body."
                }
              }
            ]

            Remember:
            - The final output MUST be ONLY the JSON array.
            - Do NOT include backticks or code fences.
            """);

		return sb.toString();
	}
}