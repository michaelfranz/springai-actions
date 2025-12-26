package org.javai.springai.dsl.plan;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.dsl.bind.TypeFactory;
import org.javai.springai.dsl.bind.TypeFactoryRegistry;
import org.javai.springai.sxl.SExpressionType;
import org.javai.springai.sxl.SxlNode;
import org.junit.jupiter.api.Test;

class PlannerMetaInfGrammarBootstrapTest {

	@Test
	void loadsMetaInfGrammarsIntoPlannerPrompts() {
		TypeFactoryRegistry.register("sxl-test", TestDslType.class, new TestDslTypeFactory());

		Planner planner = Planner.builder()
				.actions(new TestActions())
				.build();

		PromptPreview preview = planner.preview("hello");
		String systemPrompt = preview.systemMessages().getFirst();

		assertThat(systemPrompt).as(systemPrompt)
				.contains("DSL sxl-test")
				.contains("Test DSL loaded from META-INF");
	}

	private static final class TestActions {

		@Action(description = "Uses a meta-inf DSL type")
		public void run(@ActionParam(description = "payload") TestDslType spec) {
		}
	}

	private record TestDslType(String value) implements SExpressionType {

		@Override
		public String dslId() {
			return "sxl-test";
		}
	}

	private static final class TestDslTypeFactory implements TypeFactory<TestDslType> {

		@Override
		public Class<TestDslType> getType() {
			return TestDslType.class;
		}

		@Override
		public TestDslType create(SxlNode rootNode) {
			return new TestDslType(rootNode.literalValue() != null ? rootNode.literalValue() : "");
		}
	}
}

