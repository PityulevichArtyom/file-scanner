package com.azati.file_scanner;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ScanCache {

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    @Value("${file.scanner.cache.ttl}")
    private long cacheTtlMillis;

    public static class CacheEntry {
        public final List<String> files;
        public final long timestamp;

        public CacheEntry(List<String> files, long timestamp) {
            this.files = files;
            this.timestamp = timestamp;
        }
    }

    public String generateCacheKey(String directoryPath, String fileMask,
                                   Long minSizeKB, Long maxSizeKB,
                                   String modifiedAfter, String modifiedBefore,
                                   String containsText) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(directoryPath).append("|");
        keyBuilder.append(fileMask).append("|");
        keyBuilder.append(minSizeKB != null ? minSizeKB : "null").append("|");
        keyBuilder.append(maxSizeKB != null ? maxSizeKB : "null").append("|");
        keyBuilder.append(modifiedAfter != null ? modifiedAfter : "null").append("|");
        keyBuilder.append(modifiedBefore != null ? modifiedBefore : "null").append("|");
        keyBuilder.append(containsText != null ? containsText : "null");
        return keyBuilder.toString();
    }

    public CacheEntry getValidCacheEntry(String cacheKey) {
        CacheEntry cachedResult = cache.get(cacheKey);
        if (cachedResult != null && (System.currentTimeMillis() - cachedResult.timestamp) < cacheTtlMillis) {
            System.out.println("Returning result from cache for key: " + cacheKey);
            return cachedResult;
        }
        return null;
    }


    public void put(String cacheKey, List<String> files) {
        cache.put(cacheKey, new CacheEntry(files, System.currentTimeMillis()));
        System.out.println("Saved result to cache for key: " + cacheKey);
    }
    public void clear() {
        cache.clear();
        System.out.println("Cache cleared.");
    }
}