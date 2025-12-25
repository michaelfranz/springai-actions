package org.javai.springai.dsl.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.javai.springai.dsl.bind.EmbedResolverUtil;
import org.javai.springai.dsl.bind.EmbeddedResolver;
import org.javai.springai.dsl.bind.RegistryEmbeddedResolver;
import org.javai.springai.sxl.SxlNode;
import org.javai.springai.sxl.SxlNodeVisitor;

/**
 * Visitor that materializes a typed Plan from an SXL AST.
 */
public class PlanNodeVisitor implements SxlNodeVisitor<Plan> {

	public static Plan generate(SxlNode planNode) {
		return new PlanNodeVisitor().generateInternal(planNode);
	}

	private final EmbeddedResolver embeddedResolver;
	private String assistantPlanMessage = "";
	private final List<PlanStep> planSteps = new ArrayList<>();

	public PlanNodeVisitor() {
		this(new RegistryEmbeddedResolver());
	}

	public PlanNodeVisitor(EmbeddedResolver embeddedResolver) {
		this.embeddedResolver = Objects.requireNonNull(embeddedResolver, "embeddedResolver must not be null");
	}

	private Plan generateInternal(SxlNode planNode) {
		if (planNode.isLiteral()) {
			throw new IllegalArgumentException("Cannot create a plan from a literal");
		}
		if (!"P".equals(planNode.symbol())) {
			throw new IllegalArgumentException("Cannot create a plan from a node that is not a plan");
		}
		planNode.accept(this);
		return new Plan(assistantPlanMessage, planSteps);
	}

	@Override
	public Plan visitSymbol(String symbol, List<SxlNode> args) {
		switch (symbol) {
			case "P" -> visitPlan(args);
			case "PS" -> visitPlanStep(args);
			case "ERROR" -> visitError(args);
			case "EMBED" -> throw new IllegalStateException("EMBED must be handled within a plan step");
			default -> throw new IllegalStateException("Unexpected symbol: " + symbol);
		}
		return new Plan(assistantPlanMessage, planSteps);
	}

	private void visitPlan(List<SxlNode> args) {
		if (args.isEmpty()) {
			throw new IllegalStateException("Plan must have a description and zero or more steps arguments");
		}
		SxlNode descriptionNode = args.getFirst();
		if (!descriptionNode.isLiteral()) {
			throw new IllegalStateException("Plan description must be a literal string");
		}
		assistantPlanMessage = descriptionNode.literalValue();
		if (args.size() > 1) {
			args.stream().skip(1).forEach(arg -> arg.accept(this));
		}
	}

	private void visitPlanStep(List<SxlNode> args) {
		if (args.isEmpty()) {
			throw new IllegalStateException("Plan step must have an action id");
		}
		SxlNode actionIdNode = args.getFirst();
		String actionId = extractIdentifier(actionIdNode, "Plan step action id must be an identifier or literal string");
		String actionStepMessage = "";

		// Step items: zero or more. ERROR is not allowed inside PS.
		List<SxlNode> stepItems = args.size() > 1 ? args.subList(1, args.size()) : List.of();

		Map<String, Object> providedParams = new LinkedHashMap<>();
		List<PlanStep.PendingParam> pendingParams = new ArrayList<>();
		for (SxlNode item : stepItems) {
			if (item.isLiteral()) {
				throw new IllegalStateException("Plan step items must be nodes (parameters or EMBED)");
			}
			if ("EMBED".equals(item.symbol())) {
				Object[] embedded = EmbedResolverUtil.resolveEmbeddedAsArray(item, embeddedResolver);
				for (Object emb : embedded) {
					providedParams.put("__embed_" + providedParams.size(), emb);
				}
				continue;
			}
			if ("PA".equals(item.symbol())) {
				if (item.args().size() < 2) {
					throw new IllegalStateException("PA must have a name and at least one literal value");
				}
				// Validate the parameter name even though we only capture positional argument values
				String paramName = extractIdentifier(item.args().getFirst(), "PA name must be an identifier");
				// capture name->value map for provided params
				List<SxlNode> valueNodes = item.args().subList(1, item.args().size());
				List<Object> resolvedValues = new ArrayList<>();
				for (SxlNode valueNode : valueNodes) {
					if (valueNode.isLiteral()) {
						resolvedValues.add(valueNode.literalValue());
						continue;
					}
					if ("EMBED".equals(valueNode.symbol())) {
						Object[] embedded = EmbedResolverUtil.resolveEmbeddedAsArray(valueNode, embeddedResolver);
						for (Object emb : embedded) {
							resolvedValues.add(emb);
						}
						continue;
					}
					throw new IllegalStateException("PA values must be literals or EMBED nodes");
				}

				if (resolvedValues.size() == 1) {
					providedParams.put(paramName, resolvedValues.getFirst());
				}
				else {
					providedParams.put(paramName, List.copyOf(resolvedValues));
				}
				continue;
			}
			if ("PENDING".equals(item.symbol())) {
				if (item.args().size() != 2) {
					throw new IllegalStateException("PENDING must have exactly a name and a message");
				}
				String paramName = extractIdentifier(item.args().getFirst(), "PENDING name must be an identifier");
				SxlNode messageNode = item.args().get(1);
				if (!messageNode.isLiteral()) {
					throw new IllegalStateException("PENDING message must be a literal");
				}
				pendingParams.add(new PlanStep.PendingParam(paramName, messageNode.literalValue()));
				continue;
			}

			throw new IllegalStateException("Unexpected plan step item symbol: " + item.symbol());
		}

		if (!pendingParams.isEmpty()) {
			planSteps.add(new PlanStep.PendingActionStep(actionStepMessage, actionId,
					pendingParams.toArray(new PlanStep.PendingParam[0]),
					Map.copyOf(providedParams)));
		}
		else {
			planSteps.add(new PlanStep.ActionStep(actionStepMessage, actionId,
					providedParams.values().toArray()));
		}
	}

	private String extractIdentifier(SxlNode node, String errorMessage) {
		if (node.isLiteral()) {
			return node.literalValue();
		}
		if (node.symbol() != null && node.args().isEmpty()) {
			return node.symbol();
		}
		throw new IllegalStateException(errorMessage);
	}

	private String extractError(List<SxlNode> args) {
		if (args.isEmpty()) {
			throw new IllegalStateException("Error step must have a reason");
		}
		SxlNode reasonNode = args.getFirst();
		if (!reasonNode.isLiteral()) {
			throw new IllegalStateException("Error step reason must be a literal string");
		}
		return reasonNode.literalValue();
	}

	private void visitError(List<SxlNode> args) {
		planSteps.add(new PlanStep.ErrorStep(extractError(args)));
	}

}

