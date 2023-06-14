package com.bsren.cache.abstractCache;

import com.bsren.cache.abstractCache.longadder.LongAddable;
import com.bsren.cache.abstractCache.longadder.LongAddables;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

public abstract class AbstractCache<K,V> implements Cache<K,V> {

    protected AbstractCache() {}

    @Override
    public V get(K key, Callable<V> valueLoader) throws ExecutionException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void cleanUp() {}


    @Override
    public long size() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void invalidate(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CacheStats stats() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ConcurrentMap<K, V> asMap() {
        throw new UnsupportedOperationException();
    }

    public interface StatsCounter{

        void recordHits(int count);

        void recordMisses(int count);

        void recordLoadSuccess(long loadTime);

        void recordLoadException(long loadTime);

        void recordEviction();

        CacheStats snapshot();
    }

    public static final class SimpleStatsCounter implements StatsCounter {

        private final LongAddable hitCount = LongAddables.create();
        private final LongAddable missCount = LongAddables.create();
        private final LongAddable loadSuccessCount = LongAddables.create();
        private final LongAddable loadExceptionCount = LongAddables.create();
        private final LongAddable totalLoadTime = LongAddables.create();
        private final LongAddable evictionCount = LongAddables.create();

        public SimpleStatsCounter() {}

        @Override
        public void recordHits(int count) {
            hitCount.add(count);
        }


        @Override
        public void recordMisses(int count) {
            missCount.add(count);
        }

        @Override
        public void recordLoadSuccess(long loadTime) {
            loadSuccessCount.increment();
            totalLoadTime.add(loadTime);
        }

        @Override
        public void recordLoadException(long loadTime) {
            loadExceptionCount.increment();
            totalLoadTime.add(loadTime);
        }

        @Override
        public void recordEviction() {
            evictionCount.increment();
        }

        @Override
        public CacheStats snapshot() {
            return new CacheStats(
                    negativeToMaxValue(hitCount.sum()),
                    negativeToMaxValue(missCount.sum()),
                    negativeToMaxValue(loadSuccessCount.sum()),
                    negativeToMaxValue(loadExceptionCount.sum()),
                    negativeToMaxValue(totalLoadTime.sum()),
                    negativeToMaxValue(evictionCount.sum()));
        }

        private static long negativeToMaxValue(long value) {
            return (value >= 0) ? value : Long.MAX_VALUE;
        }

        public void incrementBy(StatsCounter other) {
            CacheStats otherStats = other.snapshot();
            hitCount.add(otherStats.hitCount());
            missCount.add(otherStats.missCount());
            loadSuccessCount.add(otherStats.loadSuccessCount());
            loadExceptionCount.add(otherStats.loadExceptionCount());
            totalLoadTime.add(otherStats.totalLoadTime());
            evictionCount.add(otherStats.evictionCount());
        }
    }
}
