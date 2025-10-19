package projetosd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistics collector for search queries and barrel performance.
 * 
 * @author Jose Amado e José Capinha
 * @version 1.0
 */
public class Stats {

	/*
     * Count occurrences of search queries
     */
	private final ConcurrentMap<String, AtomicLong> queryCounts = new ConcurrentHashMap<>();

	/*
     * Active barrels and their index sizes
     */
	private final ConcurrentMap<String, Long> barrelIndexSizes = new ConcurrentHashMap<>();

	/*
     * Barrel response time stats class
     */
	private static class ResponseTime {
		final AtomicLong total = new AtomicLong(0);
		final AtomicLong count = new AtomicLong(0);
	}

	private final ConcurrentMap<String, ResponseTime> barrelTimes = new ConcurrentHashMap<>();

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
	 * @param barrelId unique barrel identifier
	 * @param millis response time in milliseconds
	 */
	public void updateSearchTime(String barrelId, long millis) {
		if (barrelId == null) return;
		ResponseTime responseTime = barrelTimes.computeIfAbsent(barrelId, key -> new ResponseTime());
		responseTime.total.addAndGet(millis);
		responseTime.count.incrementAndGet();
	}

	/**
	 * Update a barrel's index size.
	 * @param barrelId Barrel port
	 * @param indexSize Number of indexed pages
	 */
	public void updateBarrelIndexSize(String barrelId, long indexSize) {
		if (barrelId == null) return;
		Long current = barrelIndexSizes.get(barrelId);
		if (current == null) {
			barrelIndexSizes.put(barrelId, 1L);
		} else {
			barrelIndexSizes.put(barrelId, current + 1L);
		}
	}

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
	 * Map key = barrelId, value = avg in tenths 
     * @return Map of barrel IDs to average response times in tenths of seconds.
	 */
	public Map<String, Long> getAverageResponse() {
		Map<String, Long> averages = new HashMap<>();
		for (Map.Entry<String, ResponseTime> entry : barrelTimes.entrySet()) {
			String barrelId = entry.getKey();
			ResponseTime responseTime = entry.getValue();
			long count = responseTime.count.get();

			long totalMillis = responseTime.total.get();
			long millis = totalMillis / count;
			long tenths = Math.round(millis / 100.0);
			averages.put(barrelId, tenths);
		}
		return averages;
	}

	/**
	 * Snapshot of active barrels and their sizes.
     * @return Map of barrel IDs to index sizes.
	 */
	public Map<String, Long> getActiveBarrels() {
		return new HashMap<>(barrelIndexSizes);
	}

	/**
	 * Remove a barrel from active list.
	 */
	public void removeBarrel(String barrelId) {
		if (barrelId == null) return;
		barrelIndexSizes.remove(barrelId);
		barrelTimes.remove(barrelId);
	}
}
