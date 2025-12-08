package org.javai.springai.actions.sxl.meta;

/**
 * DSL metadata (id, description, version).
 */
public record DslMetadata(
	String id,
	String description,
	String version
) {
}

