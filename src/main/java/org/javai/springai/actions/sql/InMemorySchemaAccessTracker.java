package org.javai.springai.actions.sql;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link SchemaAccessTracker}.
 * 
 * <p>Thread-safe implementation suitable for testing and single-instance
 * deployments. For distributed systems, consider a persistent implementation
 * backed by a shared data store.</p>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * SchemaAccessTracker tracker = new InMemorySchemaAccessTracker();
 * 
 * // Record accesses as they happen
 * tracker.recordTableAccess("fct_orders");
 * tracker.recordTableAccess("dim_customer");
 * tracker.recordTableAccess("fct_orders");  // accessed twice
 * 
 * // Query hot tables
 * Set<String> hotTables = tracker.getHotTables(2);  // Returns {"fct_orders"}
 * }</pre>
 * 
 * @see SchemaAccessTracker
 */
public class InMemorySchemaAccessTracker implements SchemaAccessTracker {

	private final ConcurrentHashMap<String, AtomicInteger> accessCounts = new ConcurrentHashMap<>();

	@Override
	public void recordTableAccess(String tableName) {
		if (tableName != null) {
			accessCounts.computeIfAbsent(tableName, k -> new AtomicInteger(0))
					.incrementAndGet();
		}
	}

	@Override
	public int getAccessCount(String tableName) {
		AtomicInteger count = accessCounts.get(tableName);
		return count != null ? count.get() : 0;
	}

	@Override
	public Set<String> getHotTables(int threshold) {
		return accessCounts.entrySet().stream()
				.filter(e -> e.getValue().get() >= threshold)
				.map(Map.Entry::getKey)
				.collect(Collectors.toSet());
	}

	@Override
	public Map<String, Integer> getAllAccessCounts() {
		return accessCounts.entrySet().stream()
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						e -> e.getValue().get()));
	}

	@Override
	public void reset() {
		accessCounts.clear();
	}
}

