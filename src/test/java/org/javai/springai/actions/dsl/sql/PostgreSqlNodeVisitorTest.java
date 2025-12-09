package org.javai.springai.actions.dsl.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.validation.ValidationCapability;
import net.sf.jsqlparser.util.validation.ValidationContext;
import net.sf.jsqlparser.util.validation.ValidationException;
import net.sf.jsqlparser.util.validation.feature.DatabaseType;
import org.javai.springai.actions.sxl.SxlNode;
import org.javai.springai.actions.sxl.SxlParser;
import org.javai.springai.actions.sxl.SxlTokenizer;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for PostgreSqlNodeVisitor.
 * 
 * Inherits from SqlNodeVisitorTest and overrides tests where PostgreSQL
 * differs from standard ANSI SQL.
 */
class PostgreSqlNodeVisitorTest extends SqlNodeVisitorTest {

	@Override
	@Test
	void generateIlikeOperator() {
		// PostgreSQL supports ILIKE natively (unlike ANSI SQL which uses UPPER() LIKE)
		String sxl = "(Q (F customers c) (S c.name) (W (ILIKE c.name '%smith%')))";
		SxlNode node = parse(sxl);
		
		String sql = PostgreSqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		// PostgreSQL uses ILIKE directly, not UPPER() LIKE
		assertThat(sql).contains("ILIKE");
		assertThat(sql).doesNotContain("UPPER(");
		assertThat(sql).contains("'%smith%'");
	}

	/**
	 * Helper method to parse SXL and generate PostgreSQL SQL.
	 */
	private SxlNode parse(String sxl) {
		SxlTokenizer tokenizer = new SxlTokenizer(sxl);
		List<org.javai.springai.actions.sxl.SxlToken> tokens = tokenizer.tokenize();
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		return nodes.getFirst();
	}

	/**
	 * Override syntax validation to use PostgreSQL-specific validation.
	 * Uses JSqlParser's FeatureSetValidation with DatabaseType.POSTGRESQL
	 * to validate PostgreSQL-specific syntax like ILIKE.
	 * 
	 * This method:
	 * 1. Parses the SQL to ensure it's syntactically valid
	 * 2. Validates the parsed statement against PostgreSQL-specific rules
	 * 
	 * If parsing fails, we know the SQL is invalid. If parsing succeeds,
	 * we then validate against PostgreSQL dialect rules to ensure it's
	 * valid PostgreSQL syntax.
	 */
	@Override
	protected void assertCorrectSqlSyntax(String sql) {
		try {
			// First, parse the SQL to ensure it's syntactically valid
			Statement statement = CCJSqlParserUtil.parse(sql);
			assertThat(statement)
				.as("Generated SQL should parse to a valid Statement: %s", sql)
				.isNotNull();
			
			// Then validate against PostgreSQL dialect using FeatureSetValidation
			// DatabaseType.POSTGRESQL implements FeatureSetValidation and can be used directly
			ValidationContext context = new ValidationContext();
			context.setCapabilities(Collections.singletonList(DatabaseType.POSTGRESQL));
			
			Map<ValidationCapability, Set<ValidationException>>
				validationResult = net.sf.jsqlparser.util.validation.Validation.validate(statement, context);
			
			// Check if there are any validation errors
			if (!validationResult.isEmpty()) {
				for (Map.Entry<ValidationCapability, Set<ValidationException>> entry
					: validationResult.entrySet()) {
					if (!entry.getValue().isEmpty()) {
						String errorMessages = entry.getValue().stream()
							.map(ValidationException::getMessage)
							.collect(java.util.stream.Collectors.joining("; "));
						fail("Generated SQL failed PostgreSQL syntax validation: %s%nErrors: %s", sql, errorMessages);
					}
				}
			}
		} catch (net.sf.jsqlparser.JSQLParserException e) {
			// Parsing failed - this means the SQL is syntactically invalid
			fail("Generated SQL failed to parse (invalid SQL syntax): %s%nError: %s", sql, e.getMessage(), e);
		} catch (Exception e) {
			fail("Generated SQL failed PostgreSQL syntax validation: %s%nError: %s", sql, e.getMessage(), e);
		}
	}

	// Additional PostgreSQL-specific tests can be added here as needed
	// For example, if there are PostgreSQL-specific functions or syntax
	// that differ from ANSI SQL
}

