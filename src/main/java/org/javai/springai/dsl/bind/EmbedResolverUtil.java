package org.javai.springai.dsl.bind;

import java.util.List;
import java.util.Objects;
import org.javai.springai.sxl.SxlNode;

/**
 * Utility for resolving EMBED nodes using a provided {@link EmbeddedResolver}.
 */
public final class EmbedResolverUtil {

	private EmbedResolverUtil() {
	}

	/**
	 * Resolve a single EMBED node to a typed object.
	 * @param embedNode the EMBED node
	 * @param resolver resolver to materialize the embedded DSL
	 * @param expectedType target type
	 */
	public static <T> T resolveEmbedded(SxlNode embedNode, EmbeddedResolver resolver, Class<T> expectedType) {
		Objects.requireNonNull(embedNode, "embedNode must not be null");
		Objects.requireNonNull(resolver, "resolver must not be null");
		Objects.requireNonNull(expectedType, "expectedType must not be null");

		if (embedNode.isLiteral()) {
			throw new IllegalStateException("Embed node must be a symbol node");
		}
		if (!"EMBED".equals(embedNode.symbol())) {
			throw new IllegalStateException("Expected EMBED symbol but found: " + embedNode.symbol());
		}

		List<SxlNode> args = embedNode.args();
		if (args.size() < 2) {
			throw new IllegalStateException("Embed step must have a DSL id and a payload node");
		}

		SxlNode dslIdNode = args.getFirst();
		String dslId = extractIdentifier(dslIdNode, "Embed step DSL id must be an identifier or literal string");

		SxlNode payloadNode = args.get(1);
		if (payloadNode.isLiteral()) {
			throw new IllegalStateException("Embed step payload must be a node");
		}

		return resolver.resolve(dslId, payloadNode, expectedType);
	}

	/**
	 * Resolve EMBED to a single-element array, useful where a varargs structure is expected.
	 */
	public static Object[] resolveEmbeddedAsArray(SxlNode embedNode, EmbeddedResolver resolver) {
		return new Object[] { resolveEmbedded(embedNode, resolver, Object.class) };
	}

	private static String extractIdentifier(SxlNode node, String errorMessage) {
		if (node.isLiteral()) {
			return node.literalValue();
		}
		if (node.symbol() != null && node.args().isEmpty()) {
			return node.symbol();
		}
		throw new IllegalStateException(errorMessage);
	}
}
