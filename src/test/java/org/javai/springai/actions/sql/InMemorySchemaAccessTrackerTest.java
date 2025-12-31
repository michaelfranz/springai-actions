package org.javai.springai.actions.sql;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InMemorySchemaAccessTracker")
class InMemorySchemaAccessTrackerTest {

	private InMemorySchemaAccessTracker tracker;

	@BeforeEach
	void setUp() {
		tracker = new InMemorySchemaAccessTracker();
	}

	@Nested
	@DisplayName("recordTableAccess")
	class RecordTableAccess {

		@Test
		@DisplayName("increments count for new table")
		void incrementsCountForNewTable() {
			tracker.recordTableAccess("fct_orders");

			assertThat(tracker.getAccessCount("fct_orders")).isEqualTo(1);
		}

		@Test
		@DisplayName("increments count for existing table")
		void incrementsCountForExistingTable() {
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("fct_orders");

			assertThat(tracker.getAccessCount("fct_orders")).isEqualTo(3);
		}

		@Test
		@DisplayName("tracks multiple tables independently")
		void tracksMultipleTablesIndependently() {
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("dim_customer");

			assertThat(tracker.getAccessCount("fct_orders")).isEqualTo(2);
			assertThat(tracker.getAccessCount("dim_customer")).isEqualTo(1);
		}

		@Test
		@DisplayName("ignores null table name")
		void ignoresNullTableName() {
			tracker.recordTableAccess(null);

			assertThat(tracker.getAllAccessCounts()).isEmpty();
		}
	}

	@Nested
	@DisplayName("getAccessCount")
	class GetAccessCount {

		@Test
		@DisplayName("returns zero for unknown table")
		void returnsZeroForUnknownTable() {
			assertThat(tracker.getAccessCount("unknown")).isZero();
		}

		@Test
		@DisplayName("returns correct count for known table")
		void returnsCorrectCountForKnownTable() {
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("fct_orders");

			assertThat(tracker.getAccessCount("fct_orders")).isEqualTo(2);
		}
	}

	@Nested
	@DisplayName("getHotTables")
	class GetHotTables {

		@Test
		@DisplayName("returns empty set when no tables accessed")
		void returnsEmptySetWhenNoTablesAccessed() {
			assertThat(tracker.getHotTables(1)).isEmpty();
		}

		@Test
		@DisplayName("returns tables meeting threshold")
		void returnsTablesMeetingThreshold() {
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("dim_customer");
			tracker.recordTableAccess("dim_customer");
			tracker.recordTableAccess("dim_date");

			Set<String> hotTables = tracker.getHotTables(2);

			assertThat(hotTables).containsExactlyInAnyOrder("fct_orders", "dim_customer");
		}

		@Test
		@DisplayName("excludes tables below threshold")
		void excludesTablesBelowThreshold() {
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("dim_customer");

			Set<String> hotTables = tracker.getHotTables(2);

			assertThat(hotTables).containsExactly("fct_orders");
			assertThat(hotTables).doesNotContain("dim_customer");
		}

		@Test
		@DisplayName("threshold of 1 returns all accessed tables")
		void thresholdOfOneReturnsAllAccessedTables() {
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("dim_customer");
			tracker.recordTableAccess("dim_date");

			Set<String> hotTables = tracker.getHotTables(1);

			assertThat(hotTables).containsExactlyInAnyOrder("fct_orders", "dim_customer", "dim_date");
		}

		@Test
		@DisplayName("high threshold returns empty set")
		void highThresholdReturnsEmptySet() {
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("fct_orders");

			Set<String> hotTables = tracker.getHotTables(100);

			assertThat(hotTables).isEmpty();
		}
	}

	@Nested
	@DisplayName("getAllAccessCounts")
	class GetAllAccessCounts {

		@Test
		@DisplayName("returns empty map when no tables accessed")
		void returnsEmptyMapWhenNoTablesAccessed() {
			assertThat(tracker.getAllAccessCounts()).isEmpty();
		}

		@Test
		@DisplayName("returns all counts")
		void returnsAllCounts() {
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("dim_customer");

			Map<String, Integer> counts = tracker.getAllAccessCounts();

			assertThat(counts).containsEntry("fct_orders", 2);
			assertThat(counts).containsEntry("dim_customer", 1);
		}

		@Test
		@DisplayName("returns defensive copy")
		void returnsDefensiveCopy() {
			tracker.recordTableAccess("fct_orders");

			Map<String, Integer> counts = tracker.getAllAccessCounts();
			counts.put("hacked", 999);

			assertThat(tracker.getAccessCount("hacked")).isZero();
		}
	}

	@Nested
	@DisplayName("reset")
	class Reset {

		@Test
		@DisplayName("clears all access counts")
		void clearsAllAccessCounts() {
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("dim_customer");

			tracker.reset();

			assertThat(tracker.getAllAccessCounts()).isEmpty();
			assertThat(tracker.getAccessCount("fct_orders")).isZero();
		}
	}

	@Nested
	@DisplayName("thread safety")
	class ThreadSafety {

		@Test
		@DisplayName("handles concurrent access correctly")
		void handlesConcurrentAccessCorrectly() throws InterruptedException {
			int threadCount = 10;
			int accessesPerThread = 100;
			ExecutorService executor = Executors.newFixedThreadPool(threadCount);
			CountDownLatch latch = new CountDownLatch(threadCount);

			for (int i = 0; i < threadCount; i++) {
				executor.submit(() -> {
					try {
						for (int j = 0; j < accessesPerThread; j++) {
							tracker.recordTableAccess("fct_orders");
						}
					} finally {
						latch.countDown();
					}
				});
			}

			latch.await(5, TimeUnit.SECONDS);
			executor.shutdown();

			assertThat(tracker.getAccessCount("fct_orders"))
					.isEqualTo(threadCount * accessesPerThread);
		}
	}
}

