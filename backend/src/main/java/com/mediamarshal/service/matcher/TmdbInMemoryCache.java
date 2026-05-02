package com.mediamarshal.service.matcher;

import com.mediamarshal.service.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
class TmdbInMemoryCache {

    private final SettingsService settingsService;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    <T> T get(String key, Supplier<T> loader, Function<T, Duration> ttlResolver) {
        return getWithStatus(key, loader, ttlResolver).value();
    }

    @SuppressWarnings("unchecked")
    <T> CacheLookup<T> getWithStatus(String key, Supplier<T> loader, Function<T, Duration> ttlResolver) {
        Instant now = Instant.now();
        CacheEntry existing = cache.get(key);
        if (existing != null && existing.expiresAt().isAfter(now)) {
            return new CacheLookup<>((T) existing.value(), CacheStatus.HIT);
        }
        if (existing != null) {
            cache.remove(key, existing);
        }

        AtomicBoolean createdFuture = new AtomicBoolean(false);
        CompletableFuture<Object> future = inFlight.computeIfAbsent(key, ignored ->
                {
                    createdFuture.set(true);
                    return CompletableFuture.supplyAsync(() -> (Object) loadAndCache(key, loader, ttlResolver))
                            .whenComplete((ignoredValue, ignoredError) -> inFlight.remove(key));
                });

        try {
            CacheStatus status = createdFuture.get() ? CacheStatus.MISS : CacheStatus.IN_FLIGHT;
            return new CacheLookup<>((T) future.join(), status);
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private <T> T loadAndCache(String key, Supplier<T> loader, Function<T, Duration> ttlResolver) {
        T value = loader.get();
        Duration ttl = ttlResolver.apply(value);
        if (ttl != null && !ttl.isNegative() && !ttl.isZero()) {
            trimIfNeeded();
            cache.put(key, new CacheEntry(value, Instant.now().plus(ttl)));
        }
        return value;
    }

    private void trimIfNeeded() {
        int maxSize = getMaxSize();
        if (cache.size() < maxSize) {
            return;
        }
        Instant now = Instant.now();
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        while (cache.size() >= maxSize && !cache.isEmpty()) {
            cache.entrySet().stream()
                    .min(Comparator.comparing(entry -> entry.getValue().expiresAt()))
                    .map(Map.Entry::getKey)
                    .ifPresent(cache::remove);
        }
    }

    private int getMaxSize() {
        String value = settingsService.get("tmdb.cache-max-size", "5000");
        try {
            return Math.max(Integer.parseInt(value), 1);
        } catch (NumberFormatException e) {
            log.warn("Invalid tmdb.cache-max-size='{}', fallback to 5000", value);
            return 5000;
        }
    }

    private record CacheEntry(Object value, Instant expiresAt) {
    }

    enum CacheStatus {
        HIT,
        MISS,
        IN_FLIGHT
    }

    record CacheLookup<T>(T value, CacheStatus status) {
    }
}
