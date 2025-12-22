package org.javai.springai.dsl.bind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.javai.springai.sxl.SxlNode;
import org.junit.jupiter.api.Test;

class EmbedResolverUtilTest {

	@Test
	void resolvesEmbedWithLiteralDslId() {
		SxlNode embed = SxlNode.symbol("EMBED", List.of(
				SxlNode.literal("sxl-test"),
				SxlNode.symbol("Q")
		));

		Object result = EmbedResolverUtil.resolveEmbedded(embed, new DummyResolver(), Object.class);

		assertThat(result).isEqualTo("resolved:sxl-test");
	}

	@Test
	void resolvesEmbedWithSymbolDslId() {
		SxlNode embed = SxlNode.symbol("EMBED", List.of(
				SxlNode.symbol("sxl-test"),
				SxlNode.symbol("Q")
		));

		Object result = EmbedResolverUtil.resolveEmbedded(embed, new DummyResolver(), Object.class);

		assertThat(result).isEqualTo("resolved:sxl-test");
	}

	@Test
	void rejectsNonEmbedSymbol() {
		SxlNode node = SxlNode.symbol("X");

		assertThatThrownBy(() -> EmbedResolverUtil.resolveEmbedded(node, new DummyResolver(), Object.class))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Expected EMBED");
	}

	@Test
	void rejectsLiteralPayload() {
		SxlNode embed = SxlNode.symbol("EMBED", List.of(
				SxlNode.literal("sxl-test"),
				SxlNode.literal("payload")
		));

		assertThatThrownBy(() -> EmbedResolverUtil.resolveEmbedded(embed, new DummyResolver(), Object.class))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("payload must be a node");
	}

	@Test
	void rejectsMissingArgs() {
		SxlNode embed = SxlNode.symbol("EMBED", List.of(SxlNode.literal("sxl-test")));

		assertThatThrownBy(() -> EmbedResolverUtil.resolveEmbedded(embed, new DummyResolver(), Object.class))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("must have a DSL id and a payload");
	}

	private static final class DummyResolver implements EmbeddedResolver {
		@Override
		public <T> T resolve(String dslId, SxlNode node, Class<T> expectedType) {
			return expectedType.cast("resolved:" + dslId);
		}
	}
}
