package org.javai.springai.dsl.plan;

import java.util.ArrayList;
import java.util.List;
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
		if (args.size() < 3) {
			throw new IllegalStateException("Plan step must have description, an action id, and a step content node");
		}
		SxlNode descriptionNode = args.getFirst();
		if (!descriptionNode.isLiteral()) {
			throw new IllegalStateException("Plan step description must be a literal string");
		}
		SxlNode actionIdNode = args.get(1);
		String actionStepMessage = descriptionNode.literalValue();
		String actionId = extractIdentifier(actionIdNode, "Plan step action id must be an identifier or literal string");

		SxlNode contentNode = args.get(2);
		if (contentNode.isLiteral()) {
			throw new IllegalStateException("Plan step content must be a node");
		}
		switch (contentNode.symbol()) {
			case "EMBED" -> planSteps.add(new PlanStep.Action(actionStepMessage, actionId, EmbedResolverUtil.resolveEmbeddedAsArray(contentNode, embeddedResolver)));
			case "ERROR" -> {
				String reason = extractError(contentNode.args());
				planSteps.add(new PlanStep.Error(reason));
			}
			default -> throw new IllegalStateException("Unexpected step content symbol: " + contentNode.symbol());
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
		planSteps.add(new PlanStep.Error(extractError(args)));
	}

}

