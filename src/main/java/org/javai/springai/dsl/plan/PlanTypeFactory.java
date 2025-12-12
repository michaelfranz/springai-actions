package org.javai.springai.dsl.plan;

import org.javai.springai.dsl.bind.TypeFactory;
import org.javai.springai.sxl.SxlNode;

public class PlanTypeFactory implements TypeFactory<Plan> {

	@Override
	public Class<Plan> getType() { return Plan.class; }

	@Override
	public Plan create(SxlNode rootNode) {
		return Plan.of(rootNode);
	}
}
