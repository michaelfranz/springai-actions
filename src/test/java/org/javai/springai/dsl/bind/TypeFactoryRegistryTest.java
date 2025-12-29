package org.javai.springai.dsl.bind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Optional;
import org.javai.springai.dsl.sql.Query;
import org.javai.springai.sxl.SxlNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TypeFactoryRegistryTest {

	@BeforeEach
	void resetRegistry() {
		TypeFactoryRegistry.clearAll(() -> true);
	}

	@AfterEach
	void restoreBuiltIns() {
		TypeFactoryRegistry.clearAll(() -> true);
		TypeFactoryBootstrap.registerBuiltIns();
	}

	@Test
	void registersAndFetchesFactoryWithNormalization() {
		TypeFactory<String> factory = new DummyStringFactory();

		TypeFactoryRegistry.register("  SXL-Test  ", String.class, factory);

		Optional<TypeFactory<?>> retrieved = TypeFactoryRegistry.getFactory("sxl-test");
		assertThat(retrieved).isPresent();
		assertThat(retrieved.get()).isSameAs(factory);

		Optional<TypeFactory<String>> typed = TypeFactoryRegistry.getFactory("SXL-TEST", String.class);
		assertThat(typed).isPresent();
		assertThat(typed.get()).isSameAs(factory);
	}

	@Test
	void allowsIdempotentRegistrationSameType() {
		TypeFactoryRegistry.register("sxl-test", String.class, new DummyStringFactory());

		TypeFactoryRegistry.register("SXL-TEST", String.class, new DummyStringFactory());

		assertThat(TypeFactoryRegistry.getFactory("sxl-test", String.class)).isPresent();
	}

	@Test
	void rejectsNullOrBlankIds() {
		assertThatThrownBy(() -> TypeFactoryRegistry.register(null, String.class, new DummyStringFactory()))
				.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> TypeFactoryRegistry.register("   ", String.class, new DummyStringFactory()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void enforcesTypeAlignmentOnTypedLookup() {
		TypeFactoryRegistry.register("sxl-test", String.class, new DummyStringFactory());

		assertThatThrownBy(() -> TypeFactoryRegistry.getFactory("sxl-test", Integer.class))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Type mismatch");
	}

	@Test
	void requireRegisteredThrowsWhenMissing() {
		assertThatThrownBy(() -> TypeFactoryRegistry.requireRegistered("sxl-nonexistent"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("not registered");
	}

	@Test
	void bootstrapRegistersBuiltIns() {
		TypeFactoryBootstrap.registerBuiltIns();

		// Only SQL is registered as built-in; plans now use JSON format
		assertThat(TypeFactoryRegistry.getFactory("sxl-sql", Query.class)).isPresent();
	}

	@Test
	void resolverResolvesViaRegistry() {
		TypeFactoryRegistry.register("sxl-test", String.class, new DummyStringFactory());
		RegistryEmbeddedResolver resolver = new RegistryEmbeddedResolver();
		SxlNode node = SxlNode.literal("hello");

		String result = resolver.resolve("sxl-test", node, String.class);

		assertThat(result).isEqualTo("hello");
	}

	@Test
	void resolverThrowsWhenFactoryMissing() {
		RegistryEmbeddedResolver resolver = new RegistryEmbeddedResolver();
		SxlNode node = SxlNode.literal("hello");

		assertThatThrownBy(() -> resolver.resolve("missing", node, String.class))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("No factory registered");
	}

	@Test
	void reverseLookupReturnsDslIdForType() {
		TypeFactoryRegistry.register("sxl-test", String.class, new DummyStringFactory());

		assertThat(TypeFactoryRegistry.getDslIdForType(String.class)).contains("sxl-test");
	}

	@Test
	void reverseLookupDetectsTypeCollision() {
		TypeFactoryRegistry.register("sxl-test", String.class, new DummyStringFactory());

		assertThatThrownBy(() -> TypeFactoryRegistry.register("sxl-other", String.class, new DummyStringFactory()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Type already bound to different dslId");
	}

	private static final class DummyStringFactory implements TypeFactory<String> {
		@Override
		public Class<String> getType() {
			return String.class;
		}

		@Override
		public String create(SxlNode rootNode) {
			return rootNode == null ? null : rootNode.literalValue();
		}
	}
}
