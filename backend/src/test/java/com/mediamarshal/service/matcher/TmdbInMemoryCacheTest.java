package com.mediamarshal.service.matcher;

import com.mediamarshal.service.settings.SettingsService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TmdbInMemoryCacheTest {

    private final SettingsService settingsService = mock(SettingsService.class);
    private final TmdbInMemoryCache cache = new TmdbInMemoryCache(settingsService);

    TmdbInMemoryCacheTest() {
        when(settingsService.get("tmdb.cache-max-size", "5000")).thenReturn("5000");
    }

    @Test
    void cachesLoadedValueByKey() {
        AtomicInteger loads = new AtomicInteger();

        TmdbInMemoryCache.CacheLookup<String> first = cache.getWithStatus(
                "search|movie|test",
                () -> "value-" + loads.incrementAndGet(),
                ignored -> Duration.ofMinutes(1));
        TmdbInMemoryCache.CacheLookup<String> second = cache.getWithStatus(
                "search|movie|test",
                () -> "value-" + loads.incrementAndGet(),
                ignored -> Duration.ofMinutes(1));

        assertThat(first.value()).isEqualTo("value-1");
        assertThat(first.status()).isEqualTo(TmdbInMemoryCache.CacheStatus.MISS);
        assertThat(second.value()).isEqualTo("value-1");
        assertThat(second.status()).isEqualTo(TmdbInMemoryCache.CacheStatus.HIT);
        assertThat(loads).hasValue(1);
    }

    @Test
    void deduplicatesInFlightLoadsForSameKey() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> cache.getWithStatus("detail|movie|1", () -> {
                loads.incrementAndGet();
                started.countDown();
                await(release);
                return "movie";
            }, ignored -> Duration.ofMinutes(1)));
            started.await();
            var second = executor.submit(() -> cache.getWithStatus("detail|movie|1", () -> {
                loads.incrementAndGet();
                return "duplicate";
            }, ignored -> Duration.ofMinutes(1)));
            sleepBriefly();
            release.countDown();

            assertThat(first.get().value()).isEqualTo("movie");
            assertThat(first.get().status()).isEqualTo(TmdbInMemoryCache.CacheStatus.MISS);
            assertThat(second.get().value()).isEqualTo("movie");
            assertThat(second.get().status()).isEqualTo(TmdbInMemoryCache.CacheStatus.IN_FLIGHT);
            assertThat(loads).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
