package org.javai.springai.dsl.bind;

import org.javai.springai.dsl.sql.Query;
import org.javai.springai.dsl.sql.SqlTypeFactory;

/**
 * Explicit bootstrap for registering built-in DSL type factories.
 *
 * Applications should invoke {@link #registerBuiltIns()} during startup to ensure
 * all default DSLs are available, rather than relying on static initializers.
 */
public final class TypeFactoryBootstrap {

	private TypeFactoryBootstrap() {
	}

	public static void registerBuiltIns() {
		TypeFactoryRegistry.register("sxl-sql", Query.class, new SqlTypeFactory());
		TypeFactoryRegistry.requireRegistered("sxl-sql");
	}
}
