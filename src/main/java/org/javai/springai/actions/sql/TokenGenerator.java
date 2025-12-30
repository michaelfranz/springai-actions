package org.javai.springai.actions.sql;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Generates stable, prefixed tokens for database object names.
 * 
 * <p>Tokens are designed to:</p>
 * <ul>
 *   <li>Be opaque - hide real database object names from external LLMs</li>
 *   <li>Be stable - same input always produces same token</li>
 *   <li>Include semantic prefixes - help LLM understand object type</li>
 *   <li>Be unique within a catalog</li>
 * </ul>
 * 
 * <h2>Token Format</h2>
 * <pre>
 * Tables:  {prefix}_{hash6}  e.g., ft_a1b2c3 (fact), dt_d4e5f6 (dimension)
 * Columns: c_{hash6}         e.g., c_789abc
 * </pre>
 * 
 * <h2>Prefixes</h2>
 * <ul>
 *   <li>{@code ft_} - Fact tables</li>
 *   <li>{@code dt_} - Dimension tables</li>
 *   <li>{@code bt_} - Bridge tables</li>
 *   <li>{@code t_} - Generic/unknown table type</li>
 *   <li>{@code c_} - Columns</li>
 * </ul>
 */
public final class TokenGenerator {

	private static final int HASH_LENGTH = 6;
	
	private TokenGenerator() {
		// Utility class
	}

	/**
	 * Generates a token for a table name based on its tags.
	 * 
	 * @param tableName the canonical table name
	 * @param tags optional tags to determine prefix (looks for "fact", "dimension", "bridge")
	 * @return a stable token for the table
	 */
	public static String tableToken(String tableName, String... tags) {
		String prefix = determineTablePrefix(tags);
		String hash = hash(tableName);
		return prefix + hash;
	}

	/**
	 * Generates a token for a column name, scoped to its table.
	 * 
	 * @param tableName the table containing this column
	 * @param columnName the canonical column name
	 * @return a stable token for the column
	 */
	public static String columnToken(String tableName, String columnName) {
		// Include table name in hash to ensure uniqueness across tables
		String hash = hash(tableName + "." + columnName);
		return "c_" + hash;
	}

	/**
	 * Determines the table prefix based on tags.
	 */
	private static String determineTablePrefix(String[] tags) {
		if (tags == null || tags.length == 0) {
			return "t_";
		}
		for (String tag : tags) {
			if (tag == null) continue;
			String lowerTag = tag.toLowerCase();
			if (lowerTag.equals("fact")) return "ft_";
			if (lowerTag.equals("dimension")) return "dt_";
			if (lowerTag.equals("bridge")) return "bt_";
		}
		return "t_";
	}

	/**
	 * Generates a stable hash of the input string.
	 * Uses first HASH_LENGTH characters of SHA-256 hex digest.
	 */
	private static String hash(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			String fullHex = HexFormat.of().formatHex(hashBytes);
			return fullHex.substring(0, HASH_LENGTH);
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 is guaranteed to be available
			throw new RuntimeException("SHA-256 not available", e);
		}
	}

	/**
	 * Checks if a string appears to be a table token.
	 * 
	 * @param value the string to check
	 * @return true if it matches the table token pattern
	 */
	public static boolean isTableToken(String value) {
		if (value == null || value.length() < 3) return false;
		return value.matches("^(ft_|dt_|bt_|t_)[a-f0-9]{" + HASH_LENGTH + "}$");
	}

	/**
	 * Checks if a string appears to be a column token.
	 * 
	 * @param value the string to check
	 * @return true if it matches the column token pattern
	 */
	public static boolean isColumnToken(String value) {
		if (value == null || value.length() < 3) return false;
		return value.matches("^c_[a-f0-9]{" + HASH_LENGTH + "}$");
	}
}

