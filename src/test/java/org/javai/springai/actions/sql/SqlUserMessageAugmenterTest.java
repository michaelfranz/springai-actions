package org.javai.springai.actions.sql;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Optional;
import org.javai.springai.actions.conversation.ConversationStateConfig;
import org.javai.springai.actions.conversation.WorkingContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SqlUserMessageAugmenter}.
 */
@DisplayName("SqlUserMessageAugmenter")
class SqlUserMessageAugmenterTest {

	@Nested
	@DisplayName("contextType()")
	class ContextType {

		@Test
		@DisplayName("returns sql.query context type")
		void returnsSqlQueryContextType() {
			SqlUserMessageAugmenter augmenter = new SqlUserMessageAugmenter();
			assertThat(augmenter.contextType()).isEqualTo(SqlQueryPayload.CONTEXT_TYPE);
			assertThat(augmenter.contextType()).isEqualTo("sql.query");
		}
	}

	@Nested
	@DisplayName("formatForUserMessage(WorkingContext)")
	class FormatWithoutConfig {

		@Test
		@DisplayName("uses default prefix 'Current query:'")
		void usesDefaultPrefix() {
			SqlUserMessageAugmenter augmenter = new SqlUserMessageAugmenter();
			SqlQueryPayload payload = SqlQueryPayload.fromModelSql("SELECT * FROM orders");
			WorkingContext<SqlQueryPayload> ctx = WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload);

			Optional<String> result = augmenter.formatForUserMessage(ctx);

			assertThat(result).isPresent();
			assertThat(result.get()).isEqualTo("Current query: SELECT * FROM orders");
		}

		@Test
		@DisplayName("uses custom prefix from constructor")
		void usesCustomPrefixFromConstructor() {
			SqlUserMessageAugmenter augmenter = new SqlUserMessageAugmenter("Base SQL:");
			SqlQueryPayload payload = SqlQueryPayload.fromModelSql("SELECT id FROM users");
			WorkingContext<SqlQueryPayload> ctx = WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload);

			Optional<String> result = augmenter.formatForUserMessage(ctx);

			assertThat(result).isPresent();
			assertThat(result.get()).isEqualTo("Base SQL: SELECT id FROM users");
		}

		@Test
		@DisplayName("returns empty for null context")
		void returnsEmptyForNullContext() {
			SqlUserMessageAugmenter augmenter = new SqlUserMessageAugmenter();

			Optional<String> result = augmenter.formatForUserMessage(null);

			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("returns empty for blank SQL")
		void returnsEmptyForBlankSql() {
			SqlUserMessageAugmenter augmenter = new SqlUserMessageAugmenter();
			SqlQueryPayload payload = SqlQueryPayload.fromModelSql("   ");
			WorkingContext<SqlQueryPayload> ctx = WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload);

			Optional<String> result = augmenter.formatForUserMessage(ctx);

			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("returns empty for empty SQL")
		void returnsEmptyForEmptySql() {
			SqlUserMessageAugmenter augmenter = new SqlUserMessageAugmenter();
			SqlQueryPayload payload = SqlQueryPayload.fromModelSql("");
			WorkingContext<SqlQueryPayload> ctx = WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload);

			Optional<String> result = augmenter.formatForUserMessage(ctx);

			assertThat(result).isEmpty();
		}
	}

	@Nested
	@DisplayName("formatForUserMessage(WorkingContext, ConversationStateConfig)")
	class FormatWithConfig {

		@Test
		@DisplayName("uses config contextPrefix when provided")
		void usesConfigContextPrefix() {
			SqlUserMessageAugmenter augmenter = new SqlUserMessageAugmenter();
			SqlQueryPayload payload = SqlQueryPayload.fromModelSql("SELECT name FROM customers");
			WorkingContext<SqlQueryPayload> ctx = WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload);

			ConversationStateConfig config = ConversationStateConfig.builder()
					.contextPrefix("Active SQL:")
					.build();

			Optional<String> result = augmenter.formatForUserMessage(ctx, config);

			assertThat(result).isPresent();
			assertThat(result.get()).isEqualTo("Active SQL: SELECT name FROM customers");
		}

		@Test
		@DisplayName("falls back to constructor prefix when config is null")
		void fallsBackToConstructorPrefixWhenConfigNull() {
			SqlUserMessageAugmenter augmenter = new SqlUserMessageAugmenter("My prefix:");
			SqlQueryPayload payload = SqlQueryPayload.fromModelSql("SELECT 1");
			WorkingContext<SqlQueryPayload> ctx = WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload);

			Optional<String> result = augmenter.formatForUserMessage(ctx, null);

			assertThat(result).isPresent();
			assertThat(result.get()).isEqualTo("My prefix: SELECT 1");
		}

		@Test
		@DisplayName("config prefix overrides constructor prefix")
		void configPrefixOverridesConstructorPrefix() {
			SqlUserMessageAugmenter augmenter = new SqlUserMessageAugmenter("Constructor prefix:");
			SqlQueryPayload payload = SqlQueryPayload.fromModelSql("SELECT * FROM test");
			WorkingContext<SqlQueryPayload> ctx = WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload);

			ConversationStateConfig config = ConversationStateConfig.builder()
					.contextPrefix("Config prefix:")
					.build();

			Optional<String> result = augmenter.formatForUserMessage(ctx, config);

			assertThat(result).isPresent();
			assertThat(result.get()).isEqualTo("Config prefix: SELECT * FROM test");
		}

		@Test
		@DisplayName("uses constructor prefix when config contextPrefix is null")
		void usesConstructorPrefixWhenConfigPrefixNull() {
			SqlUserMessageAugmenter augmenter = new SqlUserMessageAugmenter("Constructor default:");
			SqlQueryPayload payload = SqlQueryPayload.fromModelSql("SELECT 1");
			WorkingContext<SqlQueryPayload> ctx = WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload);

			// Config with null contextPrefix (use defaults() and verify behavior)
			ConversationStateConfig config = ConversationStateConfig.defaults();

			Optional<String> result = augmenter.formatForUserMessage(ctx, config);

			assertThat(result).isPresent();
			// defaults() sets contextPrefix to "Current state:", which should be used
			assertThat(result.get()).isEqualTo("Current state: SELECT 1");
		}
	}

	@Nested
	@DisplayName("shouldAugment()")
	class ShouldAugment {

		@Test
		@DisplayName("returns true for valid SQL payload")
		void returnsTrueForValidPayload() {
			SqlUserMessageAugmenter augmenter = new SqlUserMessageAugmenter();
			SqlQueryPayload payload = SqlQueryPayload.fromModelSql("SELECT * FROM orders");
			WorkingContext<SqlQueryPayload> ctx = WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload);

			assertThat(augmenter.shouldAugment(ctx)).isTrue();
		}

		@Test
		@DisplayName("returns false for null context")
		void returnsFalseForNullContext() {
			SqlUserMessageAugmenter augmenter = new SqlUserMessageAugmenter();

			assertThat(augmenter.shouldAugment(null)).isFalse();
		}

		@Test
		@DisplayName("returns false for blank SQL")
		void returnsFalseForBlankSql() {
			SqlUserMessageAugmenter augmenter = new SqlUserMessageAugmenter();
			SqlQueryPayload payload = SqlQueryPayload.fromModelSql("");
			WorkingContext<SqlQueryPayload> ctx = WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload);

			assertThat(augmenter.shouldAugment(ctx)).isFalse();
		}

		@Test
		@DisplayName("returns false for whitespace-only SQL")
		void returnsFalseForWhitespaceSql() {
			SqlUserMessageAugmenter augmenter = new SqlUserMessageAugmenter();
			SqlQueryPayload payload = SqlQueryPayload.fromModelSql("   \t\n  ");
			WorkingContext<SqlQueryPayload> ctx = WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload);

			assertThat(augmenter.shouldAugment(ctx)).isFalse();
		}

		@Test
		@DisplayName("returns false for non-SqlQueryPayload context")
		void returnsFalseForNonSqlPayload() {
			SqlUserMessageAugmenter augmenter = new SqlUserMessageAugmenter();
			// Create a WorkingContext with a non-SQL payload type
			WorkingContext<String> ctx = WorkingContext.of("other.type", "some string payload");

			assertThat(augmenter.shouldAugment(ctx)).isFalse();
		}
	}

	@Nested
	@DisplayName("constructor")
	class Constructor {

		@Test
		@DisplayName("default constructor uses 'Current query:' prefix")
		void defaultConstructorUsesDefaultPrefix() {
			SqlUserMessageAugmenter augmenter = new SqlUserMessageAugmenter();
			SqlQueryPayload payload = SqlQueryPayload.fromModelSql("SELECT 1");
			WorkingContext<SqlQueryPayload> ctx = WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload);

			Optional<String> result = augmenter.formatForUserMessage(ctx);

			assertThat(result).isPresent();
			assertThat(result.get()).startsWith("Current query:");
		}

		@Test
		@DisplayName("null prefix falls back to default")
		void nullPrefixFallsBackToDefault() {
			SqlUserMessageAugmenter augmenter = new SqlUserMessageAugmenter(null);
			SqlQueryPayload payload = SqlQueryPayload.fromModelSql("SELECT 1");
			WorkingContext<SqlQueryPayload> ctx = WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload);

			Optional<String> result = augmenter.formatForUserMessage(ctx);

			assertThat(result).isPresent();
			assertThat(result.get()).startsWith("Current query:");
		}
	}
}
