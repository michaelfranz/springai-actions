package org.javai.springai.actions.dsl.sql;

import org.javai.springai.actions.sxl.SxlNode;
import org.javai.springai.actions.sxl.SxlNodeVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Visitor that generates ANSI SQL from an SXL AST.
 * 
 * This visitor traverses an AST representing SQL SELECT queries and generates
 * standard ANSI SQL strings.
 */
public class SqlNodeVisitor implements SxlNodeVisitor<String> {

	protected final StringBuilder sql = new StringBuilder();
	protected int indentLevel = 0;
	protected boolean needsSpace = false;

	/**
	 * Generate SQL from a node AST.
	 */
	public static String generate(SxlNode node) {
		SqlNodeVisitor visitor = new SqlNodeVisitor();
		return node.accept(visitor);
	}

	/**
	 * Factory method to create a new visitor of the same type.
	 * Subclasses should override this to return their own type.
	 */
	protected SqlNodeVisitor createVisitor() {
		return new SqlNodeVisitor();
	}

	@Override
	public String visitSymbol(String symbol, List<SxlNode> args) {
		switch (symbol) {
			case "Q" -> visitQuery(args);
			case "D" -> visitDistinct();
			case "F" -> visitFrom(args);
			case "J" -> visitInnerJoin(args);
			case "J_LEFT" -> visitLeftJoin(args);
			case "J_RIGHT" -> visitRightJoin(args);
			case "J_FULL" -> visitFullJoin(args);
			case "S" -> visitSelect(args);
			case "AS" -> visitAlias(args);
			case "W" -> visitWhere(args);
			case "G" -> visitGroupBy(args);
			case "H" -> visitHaving(args);
			case "O" -> visitOrderBy(args);
			case "L" -> visitLimit(args);
			// Operators
			case "EQ" -> visitOperator("=", args);
			case "NE" -> visitOperator("!=", args);
			case "LT" -> visitOperator("<", args);
			case "GT" -> visitOperator(">", args);
			case "LE" -> visitOperator("<=", args);
			case "GE" -> visitOperator(">=", args);
			case "ADD" -> visitOperator("+", args);
			case "SUB" -> visitOperator("-", args);
			case "MUL" -> visitOperator("*", args);
			case "DIV" -> visitOperator("/", args);
			case "AND" -> visitAnd(args);
			case "OR" -> visitOr(args);
			case "NOT" -> visitNot(args);
			case "LIKE" -> visitLike(args);
			case "ILIKE" -> visitIlike(args);
			case "BETWEEN" -> visitBetween(args);
			case "IN" -> visitIn(args);
			case "NOT_IN" -> visitNotIn(args);
			case "IS_NULL" -> visitIsNull(args);
			case "IS_NOT_NULL" -> visitIsNotNull(args);
			// Functions
			case "COUNT" -> visitCount(args);
			case "DATE_TRUNC" -> visitDateTrunc(args);
			case "EXTRACT" -> visitExtract(args);
			// Standard SQL functions (SUM, AVG, MIN, MAX, UPPER, and any others added to grammar)
			// These all follow the pattern: FUNCTION_NAME(expr1, expr2, ...)
			case "SUM", "AVG", "MIN", "MAX", "UPPER" -> visitStandardFunction(symbol, args);
			// ORDER BY keywords
			case "ASC" -> visitAsc();
			case "DESC" -> visitDesc();
			default -> {
				// For unknown symbols with arguments, check if it's likely a function
				// Standard SQL functions follow the pattern: FUNCTION_NAME(args)
				// If a symbol has args and isn't one of our special cases, treat it as a function
				if (!args.isEmpty() && isLikelyFunction(symbol)) {
					visitStandardFunction(symbol, args);
				} else {
					visitIdentifierOrExpression(symbol, args);
				}
			}
		}
		return sql.toString();
	}

	@Override
	public String visitLiteral(String value) {
		// Try to determine if it's a number or string
		if (isNumeric(value)) {
			append(value);
		} else {
			// String literal - use single quotes for SQL
			append("'");
			append(escapeString(value));
			append("'");
		}
		return sql.toString();
	}

	protected void visitQuery(List<SxlNode> args) {
		sql.setLength(0); // Reset
		needsSpace = false;
		
		// Separate clauses by type
		boolean hasDistinct = false;
		boolean hasSelect = false;
		List<SxlNode> fromClause = null;
		List<SxlNode> joinClauses = new ArrayList<>();
		List<SxlNode> selectItems = null;
		List<SxlNode> whereClause = null;
		List<SxlNode> groupByItems = null;
		List<SxlNode> havingClause = null;
		List<SxlNode> orderByItems = null;
		List<SxlNode> limitClause = null;
		
		for (SxlNode arg : args) {
			switch (arg.symbol()) {
				case "D" -> hasDistinct = true;
				case "F" -> fromClause = arg.args();
				case "J", "J_LEFT", "J_RIGHT", "J_FULL" -> joinClauses.add(arg);
				case "S" -> {
					selectItems = arg.args();
					hasSelect = true;
				}
				case "W" -> whereClause = arg.args();
				case "G" -> groupByItems = arg.args();
				case "H" -> havingClause = arg.args();
				case "O" -> orderByItems = arg.args();
				case "L" -> limitClause = arg.args();
			}
		}
		
		if (!hasSelect) {
			throw new IllegalStateException("SELECT clause is required in query");
		}
		
		// Build SQL in correct order
		// SELECT [DISTINCT] ...
		append("SELECT");
		if (hasDistinct) {
			append(" DISTINCT");
		}
		if (selectItems != null && !selectItems.isEmpty()) {
			append(" ");
			String items = selectItems.stream()
				.map(item -> {
					SqlNodeVisitor v = createVisitor();
					return item.accept(v);
				})
				.collect(Collectors.joining(", "));
			append(items);
		}
		
		// FROM ...
		if (fromClause != null && fromClause.size() >= 2) {
			append(" FROM ");
			append(fromClause.get(0).symbol());
			append(" AS ");
			append(fromClause.get(1).symbol());
		}
		
		// JOIN ...
		for (SxlNode join : joinClauses) {
			join.accept(this);
		}
		
		// WHERE ...
		if (whereClause != null && !whereClause.isEmpty()) {
			append(" WHERE ");
			SqlNodeVisitor visitor = createVisitor();
			whereClause.get(0).accept(visitor);
			append(visitor.sql.toString());
		}
		
		// GROUP BY ...
		if (groupByItems != null && !groupByItems.isEmpty()) {
			append(" GROUP BY ");
			String items = groupByItems.stream()
				.map(item -> {
					SqlNodeVisitor visitor = createVisitor();
					return item.accept(visitor);
				})
				.collect(Collectors.joining(", "));
			append(items);
		}
		
		// HAVING ...
		if (havingClause != null && !havingClause.isEmpty()) {
			append(" HAVING ");
			SqlNodeVisitor visitor = createVisitor();
			havingClause.get(0).accept(visitor);
			append(visitor.sql.toString());
		}
		
		// ORDER BY ...
		if (orderByItems != null && !orderByItems.isEmpty()) {
			append(" ORDER BY ");
			List<String> orderItemStrings = new ArrayList<>();
			
			for (SxlNode item : orderByItems) {
				orderItemStrings.add(visitOrderByItem(item));
			}
			
			append(String.join(", ", orderItemStrings));
		}
		
		// LIMIT ...
		if (limitClause != null && !limitClause.isEmpty() && limitClause.get(0).isLiteral()) {
			append(" LIMIT ");
			append(limitClause.get(0).literalValue());
		}
	}

	protected void visitDistinct() {
		// Handled in visitQuery
	}

	protected void visitFrom(List<SxlNode> args) {
		// Handled in visitQuery
		if (args.size() >= 2) {
			append(args.get(0).symbol());
			append(" AS ");
			append(args.get(1).symbol());
		}
	}

	protected void visitInnerJoin(List<SxlNode> args) {
		visitJoin("INNER JOIN", args);
	}

	protected void visitLeftJoin(List<SxlNode> args) {
		visitJoin("LEFT JOIN", args);
	}

	protected void visitRightJoin(List<SxlNode> args) {
		visitJoin("RIGHT JOIN", args);
	}

	protected void visitFullJoin(List<SxlNode> args) {
		visitJoin("FULL OUTER JOIN", args);
	}

	protected void visitJoin(String joinType, List<SxlNode> args) {
		if (args.size() >= 3) {
			append(" ");
			append(joinType);
			append(" ");
			append(args.get(0).symbol());
			append(" AS ");
			append(args.get(1).symbol());
			append(" ON ");
			SqlNodeVisitor visitor = createVisitor();
			args.get(2).accept(visitor);
			append(visitor.sql.toString());
		}
	}

	protected void visitSelect(List<SxlNode> args) {
		// Handled in visitQuery
		if (args.isEmpty()) {
			append("*");
		} else {
			String items = args.stream()
				.map(arg -> arg.accept(new SqlNodeVisitor()))
				.collect(Collectors.joining(", "));
			append(items);
		}
	}

	protected void visitAlias(List<SxlNode> args) {
		if (args.size() >= 2) {
			// Expression
			SqlNodeVisitor visitor = createVisitor();
			args.get(0).accept(visitor);
			append(visitor.sql.toString());
			append(" AS ");
			// Alias name (identifier)
			append(args.get(1).symbol());
		}
	}

	protected void visitWhere(List<SxlNode> args) {
		if (!args.isEmpty()) {
			append(" WHERE ");
			args.get(0).accept(this);
			needsSpace = true;
		}
	}

	protected void visitGroupBy(List<SxlNode> args) {
		append(" GROUP BY ");
		String items = args.stream()
			.map(arg -> {
				SqlNodeVisitor visitor = new SqlNodeVisitor();
				return arg.accept(visitor);
			})
			.collect(Collectors.joining(", "));
		append(items);
		needsSpace = true;
	}

	protected void visitHaving(List<SxlNode> args) {
		if (!args.isEmpty()) {
			append(" HAVING ");
			args.get(0).accept(this);
			needsSpace = true;
		}
	}

	protected void visitOrderBy(List<SxlNode> args) {
		// Handled in visitQuery
		if (!args.isEmpty()) {
			List<String> orderItems = new ArrayList<>();
			
			for (SxlNode item : args) {
				orderItems.add(visitOrderByItem(item));
			}
			
			append(String.join(", ", orderItems));
		}
	}
	
	protected String visitOrderByItem(SxlNode item) {
		StringBuilder itemBuilder = new StringBuilder();
		SqlNodeVisitor visitor = createVisitor();
		
		// ORDER BY items can be:
		// 1. Simple expression: (expr) - e.g., (o.amount) or (SUM o.amount)
		// 2. Expression with DESC: (DESC (expr)) - e.g., (DESC o.amount) or (DESC (SUM o.amount))
		// 3. Expression with ASC: (ASC (expr)) - e.g., (ASC o.amount) or (ASC (SUM o.amount))
		// 4. Multiple ORDER BY items are separate arguments to O
		
		if (!item.args().isEmpty()) {
			String symbol = item.symbol();
			
			// Check if this item is wrapped in DESC or ASC
			if ("DESC".equals(symbol)) {
				// This is (DESC (expr)) - output the expression followed by DESC
				if (!item.args().isEmpty()) {
					itemBuilder.append(item.args().get(0).accept(visitor));
					itemBuilder.append(" DESC");
				}
			} else if ("ASC".equals(symbol)) {
				// This is (ASC (expr)) - output the expression followed by ASC
				if (!item.args().isEmpty()) {
					itemBuilder.append(item.args().get(0).accept(visitor));
					itemBuilder.append(" ASC");
				}
			} else {
				// This is just an expression without DESC/ASC
				itemBuilder.append(item.accept(visitor));
			}
		} else {
			// Simple identifier with no args
			itemBuilder.append(item.accept(visitor));
		}
		
		return itemBuilder.toString();
	}

	protected void visitLimit(List<SxlNode> args) {
		if (!args.isEmpty() && args.get(0).isLiteral()) {
			append(" LIMIT ");
			append(args.get(0).literalValue());
		}
	}

	protected void visitOperator(String op, List<SxlNode> args) {
		if (args.size() == 2) {
			append("(");
			SqlNodeVisitor visitor = createVisitor();
			args.get(0).accept(visitor);
			append(visitor.sql.toString());
			append(" ");
			append(op);
			append(" ");
			visitor = createVisitor();
			args.get(1).accept(visitor);
			append(visitor.sql.toString());
			append(")");
		} else if (args.size() == 1) {
			append(op);
			append("(");
			SqlNodeVisitor visitor = createVisitor();
			args.get(0).accept(visitor);
			append(visitor.sql.toString());
			append(")");
		}
	}

	protected void visitAnd(List<SxlNode> args) {
		String conditions = args.stream()
			.map(arg -> {
				SqlNodeVisitor visitor = createVisitor();
				return "(" + arg.accept(visitor) + ")";
			})
			.collect(Collectors.joining(" AND "));
		append(conditions);
	}

	protected void visitOr(List<SxlNode> args) {
		String conditions = args.stream()
			.map(arg -> {
				SqlNodeVisitor visitor = createVisitor();
				return "(" + arg.accept(visitor) + ")";
			})
			.collect(Collectors.joining(" OR "));
		append(conditions);
	}

	protected void visitNot(List<SxlNode> args) {
		if (!args.isEmpty()) {
			append("NOT (");
			SqlNodeVisitor visitor = createVisitor();
			args.get(0).accept(visitor);
			append(visitor.sql.toString());
			append(")");
		}
	}

	protected void visitLike(List<SxlNode> args) {
		if (args.size() == 2) {
			SqlNodeVisitor visitor = createVisitor();
			args.get(0).accept(visitor);
			append(visitor.sql.toString());
			append(" LIKE ");
			visitor = createVisitor();
			args.get(1).accept(visitor);
			append(visitor.sql.toString());
		}
	}

	protected void visitIlike(List<SxlNode> args) {
		// ILIKE is PostgreSQL-specific, but for ANSI SQL we'll use UPPER() LIKE
		if (args.size() == 2) {
			append("UPPER(");
			SqlNodeVisitor visitor = createVisitor();
			args.get(0).accept(visitor);
			append(visitor.sql.toString());
			append(") LIKE UPPER(");
			visitor = createVisitor();
			args.get(1).accept(visitor);
			append(visitor.sql.toString());
			append(")");
		}
	}

	protected void visitBetween(List<SxlNode> args) {
		if (args.size() == 3) {
			SqlNodeVisitor visitor = createVisitor();
			args.get(0).accept(visitor);
			append(visitor.sql.toString());
			append(" BETWEEN ");
			visitor = createVisitor();
			args.get(1).accept(visitor);
			append(visitor.sql.toString());
			append(" AND ");
			visitor = createVisitor();
			args.get(2).accept(visitor);
			append(visitor.sql.toString());
		}
	}

	protected void visitIn(List<SxlNode> args) {
		if (args.size() >= 2) {
			SqlNodeVisitor visitor = createVisitor();
			args.get(0).accept(visitor);
			append(visitor.sql.toString());
			append(" IN (");
			String values = args.stream()
				.skip(1)
				.map(arg -> {
					SqlNodeVisitor v = createVisitor();
					return arg.accept(v);
				})
				.collect(Collectors.joining(", "));
			append(values);
			append(")");
		}
	}

	protected void visitNotIn(List<SxlNode> args) {
		if (args.size() >= 2) {
			SqlNodeVisitor visitor = createVisitor();
			args.get(0).accept(visitor);
			append(visitor.sql.toString());
			append(" NOT IN (");
			String values = args.stream()
				.skip(1)
				.map(arg -> {
					SqlNodeVisitor v = createVisitor();
					return arg.accept(v);
				})
				.collect(Collectors.joining(", "));
			append(values);
			append(")");
		}
	}

	protected void visitIsNull(List<SxlNode> args) {
		if (!args.isEmpty()) {
			SqlNodeVisitor visitor = createVisitor();
			args.get(0).accept(visitor);
			append(visitor.sql.toString());
			append(" IS NULL");
		}
	}

	protected void visitIsNotNull(List<SxlNode> args) {
		if (!args.isEmpty()) {
			SqlNodeVisitor visitor = createVisitor();
			args.get(0).accept(visitor);
			append(visitor.sql.toString());
			append(" IS NOT NULL");
		}
	}

	protected void visitCount(List<SxlNode> args) {
		append("COUNT(");
		if (args.isEmpty()) {
			append("*");
		} else {
			String items = args.stream()
				.map(arg -> {
					SqlNodeVisitor visitor = createVisitor();
					return arg.accept(visitor);
				})
				.collect(Collectors.joining(", "));
			append(items);
		}
		append(")");
	}

	/**
	 * Generic handler for standard SQL functions.
	 * Handles any function that follows the pattern: FUNCTION_NAME(expr1, expr2, ...)
	 * 
	 * This allows new functions to be added to the grammar without requiring
	 * code changes in the visitor. Functions like SUM, AVG, MIN, MAX, UPPER
	 * all follow this standard pattern.
	 * 
	 * Special functions that require different syntax (like COUNT with optional args,
	 * DATE_TRUNC, EXTRACT) should have their own visit methods.
	 * 
	 * @param functionName The name of the function (e.g., "SUM", "AVG", "MIN")
	 * @param args The function arguments
	 */
	protected void visitStandardFunction(String functionName, List<SxlNode> args) {
		append(functionName);
		append("(");
		String items = args.stream()
			.map(arg -> {
				SqlNodeVisitor visitor = createVisitor();
				return arg.accept(visitor);
			})
			.collect(Collectors.joining(", "));
		append(items);
		append(")");
	}

	protected void visitDateTrunc(List<SxlNode> args) {
		if (args.size() == 2) {
			append("DATE_TRUNC(");
			SqlNodeVisitor visitor = createVisitor();
			args.get(0).accept(visitor); // unit (string literal)
			append(visitor.sql.toString());
			append(", ");
			visitor = createVisitor();
			args.get(1).accept(visitor); // date expression
			append(visitor.sql.toString());
			append(")");
		}
	}

	protected void visitExtract(List<SxlNode> args) {
		if (args.size() == 2) {
			append("EXTRACT(");
			SqlNodeVisitor visitor = createVisitor();
			args.get(0).accept(visitor); // part (string literal)
			append(visitor.sql.toString());
			append(" FROM ");
			visitor = createVisitor();
			args.get(1).accept(visitor); // date expression
			append(visitor.sql.toString());
			append(")");
		}
	}

	protected void visitAsc() {
		append("ASC");
	}

	protected void visitDesc() {
		append("DESC");
	}

	protected void visitIdentifierOrExpression(String symbol, List<SxlNode> args) {
		// This is an identifier (column reference, table name, etc.)
		if (args.isEmpty()) {
			append(symbol);
		} else {
			// This shouldn't happen for identifiers, but handle it
			append(symbol);
			append("(");
			String items = args.stream()
				.map(arg -> {
					SqlNodeVisitor visitor = createVisitor();
					return arg.accept(visitor);
				})
				.collect(Collectors.joining(", "));
			append(items);
			append(")");
		}
	}

	protected void append(String text) {
		sql.append(text);
	}

	protected boolean isNumeric(String value) {
		if (value == null || value.isEmpty()) {
			return false;
		}
		try {
			Double.parseDouble(value);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	protected String escapeString(String value) {
		return value.replace("'", "''");
	}

	/**
	 * Determines if a symbol is likely a SQL function.
	 * SQL functions are typically:
	 * - All uppercase (e.g., SUM, AVG, COUNT)
	 * - Not containing dots (column references like o.id have dots)
	 * - Not one of the special SQL keywords/clauses
	 * 
	 * This allows new functions added to the grammar to be automatically
	 * handled without code changes.
	 */
	protected boolean isLikelyFunction(String symbol) {
		if (symbol == null || symbol.isEmpty()) {
			return false;
		}
		// Column references and identifiers often contain dots (e.g., o.id, table.column)
		if (symbol.contains(".")) {
			return false;
		}
		// SQL functions are typically all uppercase
		// If the symbol is all uppercase, it's likely a function
		return symbol.equals(symbol.toUpperCase());
	}
}

