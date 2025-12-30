package org.javai.springai.actions.api;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.springai.actions.sql.Query;
import org.junit.jupiter.api.Test;

class TypeHandlerRegistryTest {

	@Test
	void discoverLoadsQueryHandlersViaSpi() {
		TypeHandlerRegistry registry = TypeHandlerRegistry.discover();

		// Verify QuerySpecProvider was discovered
		assertThat(registry.specProvider(Query.class))
				.isPresent()
				.hasValueSatisfying(provider -> 
						assertThat(provider.getClass().getSimpleName()).isEqualTo("QuerySpecProvider"));

		// Verify QueryResolver was discovered
		assertThat(registry.resolver(Query.class))
				.isPresent()
				.hasValueSatisfying(resolver -> 
						assertThat(resolver.getClass().getSimpleName()).isEqualTo("QueryResolver"));
	}

	@Test
	void discoverReturnsEmptyForUnregisteredType() {
		TypeHandlerRegistry registry = TypeHandlerRegistry.discover();

		// String has no custom handler
		assertThat(registry.specProvider(String.class)).isEmpty();
		assertThat(registry.resolver(String.class)).isEmpty();
	}

	@Test
	void manualRegistrationWorks() {
		TypeHandlerRegistry registry = new TypeHandlerRegistry();
		
		// Initially empty
		assertThat(registry.specProvider(Query.class)).isEmpty();
		
		// Register manually
		registry.register(new org.javai.springai.actions.sql.QuerySpecProvider());
		registry.register(new org.javai.springai.actions.sql.QueryResolver());
		
		// Now present
		assertThat(registry.specProvider(Query.class)).isPresent();
		assertThat(registry.resolver(Query.class)).isPresent();
	}
}

