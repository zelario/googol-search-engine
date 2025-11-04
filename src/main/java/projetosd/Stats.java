package projetosd;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistics collector for search queries and barrel performance.
 */
public class Stats implements Serializable {

	/**
     * Count occurrences of search queries
     */
	private final ConcurrentMap<String, AtomicLong> queryCounts;

	/**
     * Active barrels and their index sizes
     */
	private final ConcurrentMap<Integer, Long> barrelIndexSizes;

	/**
     * Barrel response time stats class
     */
	private static class ResponseTime implements Serializable {
		final AtomicLong total = new AtomicLong(0);
		final AtomicLong count = new AtomicLong(0);
	}

	/**
	 * Barrel response times
	 */
	private final ConcurrentMap<Integer, ResponseTime> barrelTimes;

	/**
	 * Constructs the Stats object.
	 */
	public Stats() {
		queryCounts = new ConcurrentHashMap<>();
		barrelIndexSizes = new ConcurrentHashMap<>();
		barrelTimes = new ConcurrentHashMap<>();
	}

	//---------------------------------- STATS UPDATE METHODS -----------------------------------------//

	/**
	 * Record a search query occurrence.
	 * @param query Raw user query
	 */
	public void updateQueryOccurrence(String query) {
		String cleanedQuery = query.trim().toLowerCase();
		if (cleanedQuery.isEmpty()){
            return;
		}
	queryCounts.computeIfAbsent(cleanedQuery, key -> new AtomicLong(0)).incrementAndGet();
	}

	/**
	 * Record a search response time for a barrel.
	 * @param barrelPort barrel RMI port
	 * @param millis response time in milliseconds
	 */
	public void updateSearchTime(int barrelPort, long millis) {
		ResponseTime responseTime = barrelTimes.computeIfAbsent(barrelPort, key -> new ResponseTime());
		responseTime.total.addAndGet(millis);
		responseTime.count.incrementAndGet();
	}

	/**
	 * Update a barrel's index size.
	 * @param barrelPort Barrel port
	 * @param indexSize Number of indexed pages
	 */
	public void updateBarrelIndexSize(int barrelPort, long indexSize) {
		// Store the reported index size (overwrite) instead of incrementing; callers should report the current size.
		barrelIndexSizes.put(barrelPort, indexSize);
	}

	//-------------------------------------- END OF STATS UPDATE METHODS -----------------------------------------------//

	//-------------------------------------- STATS RETRIEVAL METHODS -----------------------------------------------//

	/**
	 * Return the top 10 searches by count.
	 *
	 * @return Map of query -> count, ordered by count descending (limited to 10 entries).
	 */
	public Map<String, Long> getTopSearches() {
		List<Map.Entry<String, Long>> entries = new ArrayList<>();
		for (Map.Entry<String, AtomicLong> entry : queryCounts.entrySet()) {
			entries.add(new java.util.AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue().get()));
		}
		entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

		Map<String, Long> topSearches = new java.util.LinkedHashMap<>();
		for (int i = 0; i < Math.min(entries.size(), 10); i++) {
			Map.Entry<String, Long> entry = entries.get(i);
			topSearches.put(entry.getKey(), entry.getValue());
		}
		return topSearches;
	}

	/**
	 * Return average response time in tenths of seconds for each barrel.
	 * Map key = barrelPort, value = avg in tenths 
	 * @return Map of barrel ports to average response times in tenths of seconds.
	 */
	public Map<Integer, Long> getAverageResponse() {
		Map<Integer, Long> averages = new HashMap<>();
		for (Map.Entry<Integer, ResponseTime> entry : barrelTimes.entrySet()) {
			Integer barrelPort = entry.getKey();
			ResponseTime responseTime = entry.getValue();
			long count = responseTime.count.get();

			long totalMillis = responseTime.total.get();
			long millis = count == 0 ? 0 : totalMillis / count;
			long tenths = Math.round(millis / 100.0);
			averages.put(barrelPort, tenths);
		}
		return averages;
	}

	/**
	 * Snapshot of active barrels and their sizes.
	 * @return Map of barrel ports to index sizes.
	 */
	public Map<Integer, Long> getActiveBarrels() {
		return new HashMap<>(barrelIndexSizes);
	}

	/**
	 * Remove a barrel from active list.
     * @param barrelPort Port of barrel to be removed
	 */
	public void removeBarrelStats(int barrelPort) {
		barrelIndexSizes.remove(barrelPort);
		barrelTimes.remove(barrelPort);
	}
}

	//-------------------------------- END OF STATS RETRIEVAL METHODS -----------------------------------------//