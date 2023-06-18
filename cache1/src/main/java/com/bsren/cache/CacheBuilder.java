package com.bsren.cache;

import com.google.common.base.*;
import com.google.errorprone.annotations.CheckReturnValue;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.*;

public class CacheBuilder<K,V> {

    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    private static final int DEFAULT_EXPIRATION_NANOS = 0;

    private static final int DEFAULT_REFRESH_NANOS = 0;

    static final Supplier<? extends AbstractCache.StatsCounter> NULL_STATS_COUNTER =
            Suppliers.ofInstance(
                    new AbstractCache.StatsCounter() {
                        @Override
                        public void recordHits(int count) {}

                        @Override
                        public void recordMisses(int count) {}

                        @SuppressWarnings("GoodTime") // b/122668874
                        @Override
                        public void recordLoadSuccess(long loadTime) {}

                        @SuppressWarnings("GoodTime") // b/122668874
                        @Override
                        public void recordLoadException(long loadTime) {}

                        @Override
                        public void recordEviction() {}

                        @Override
                        public CacheStats snapshot() {
                            return EMPTY_STATS;
                        }
                    });
    static final CacheStats EMPTY_STATS = new CacheStats(0, 0, 0, 0, 0, 0);

    static final Supplier<AbstractCache.StatsCounter> CACHE_STATS_COUNTER =
            new Supplier<AbstractCache.StatsCounter>() {
                @Override
                public AbstractCache.StatsCounter get() {
                    return new AbstractCache.SimpleStatsCounter();
                }
            };


    static final Ticker NULL_TICKER =
            new Ticker() {
                @Override
                public long read() {
                    return 0;
                }
            };

    static final int UNSET_INT = -1;

    int initialCapacity = UNSET_INT;
    long maximumSize = UNSET_INT;

    Strength keyStrength;
    Strength valueStrength;

    long expireAfterWriteNanos = UNSET_INT;

    long expireAfterAccessNanos = UNSET_INT;

    long refreshNanos = UNSET_INT;

    Equivalence<Object> keyEquivalence;    //比较key是否相等
    Equivalence<Object> valueEquivalence;  //比较value是否相等
    
    Ticker ticker;

    Supplier<? extends AbstractCache.StatsCounter> statsCounterSupplier = NULL_STATS_COUNTER;


    public CacheBuilder() {}

    /**
     * Constructs a new {@code CacheBuilder} instance with default settings, including strong keys,
     * strong values, and no automatic eviction of any kind.
     */
    @CheckReturnValue
    public static CacheBuilder<Object, Object> newBuilder() {
        return new CacheBuilder<>();
    }

    CacheBuilder<K, V> keyEquivalence(Equivalence<Object> equivalence) {
        checkState(keyEquivalence == null, "key equivalence was already set to %s", keyEquivalence);
        keyEquivalence = checkNotNull(equivalence);
        return this;
    }

    Equivalence<Object> getKeyEquivalence() {
        return MoreObjects.firstNonNull(keyEquivalence, getKeyStrength().defaultEquivalence());
    }

    /**
     * Sets a custom {@code Equivalence} strategy for comparing values.
     *
     * <p>By default, the cache uses {@link Equivalence#identity} to determine value equality when
     * {@link #weakValues} or {@link #softValues} is specified, and {@link Equivalence#equals()}
     * otherwise.
     *
     * @return this {@code CacheBuilder} instance (for chaining)
     */
    CacheBuilder<K, V> valueEquivalence(Equivalence<Object> equivalence) {
        checkState(valueEquivalence == null, "value equivalence was already set to %s", valueEquivalence);
        this.valueEquivalence = checkNotNull(equivalence);
        return this;
    }

    Equivalence<Object> getValueEquivalence() {
        return MoreObjects.firstNonNull(valueEquivalence, getValueStrength().defaultEquivalence());
    }

    public CacheBuilder<K, V> initialCapacity(int initialCapacity) {
        checkState(
                this.initialCapacity == UNSET_INT,
                "initial capacity was already set to %s",
                this.initialCapacity);
        checkArgument(initialCapacity >= 0);
        this.initialCapacity = initialCapacity;
        return this;
    }

    int getInitialCapacity() {
        return (initialCapacity == UNSET_INT) ? DEFAULT_INITIAL_CAPACITY : initialCapacity;
    }

    public CacheBuilder<K, V> weakKeys() {
        return setKeyStrength(Strength.WEAK);
    }

    CacheBuilder<K, V> setKeyStrength(Strength strength) {
        checkState(keyStrength == null, "Key strength was already set to %s", keyStrength);
        keyStrength = checkNotNull(strength);
        return this;
    }
    
    Strength getKeyStrength() {
        return MoreObjects.firstNonNull(keyStrength, Strength.STRONG);
    }


    public CacheBuilder<K, V> weakValues() {
        return setValueStrength(Strength.WEAK);
    }

    public CacheBuilder<K, V> softValues() {
        return setValueStrength(Strength.SOFT);
    }


    CacheBuilder<K, V> setValueStrength(Strength strength) {
        checkState(valueStrength == null, "Value strength was already set to %s", valueStrength);
        valueStrength = checkNotNull(strength);
        return this;
    }

    Strength getValueStrength() {
        return valueStrength==null?Strength.STRONG:valueStrength;
    }

    public CacheBuilder<K, V> refreshAfterWrite(long duration, TimeUnit unit) {
        checkNotNull(unit);
        checkState(refreshNanos == UNSET_INT, "refresh was already set to %s ns", refreshNanos);
        checkArgument(duration > 0, "duration must be positive: %s %s", duration, unit);
        this.refreshNanos = unit.toNanos(duration);
        return this;
    }


    public CacheBuilder<K, V> expireAfterWrite(long duration, TimeUnit unit) {
        checkState(
                expireAfterWriteNanos == UNSET_INT,
                "expireAfterWrite was already set to %s ns",
                expireAfterWriteNanos);
        checkArgument(duration >= 0, "duration cannot be negative: %s %s", duration, unit);
        this.expireAfterWriteNanos = unit.toNanos(duration);
        return this;
    }

    long getExpireAfterWriteNanos() {
        return (expireAfterWriteNanos == UNSET_INT) ? DEFAULT_EXPIRATION_NANOS : expireAfterWriteNanos;
    }


    public CacheBuilder<K, V> expireAfterAccess(long duration, TimeUnit unit) {
        checkState(
                expireAfterAccessNanos == UNSET_INT,
                "expireAfterAccess was already set to %s ns",
                expireAfterAccessNanos);
        checkArgument(duration >= 0, "duration cannot be negative: %s %s", duration, unit);
        this.expireAfterAccessNanos = unit.toNanos(duration);
        return this;
    }

    long getExpireAfterAccessNanos() {
        return (expireAfterAccessNanos == UNSET_INT)
                ? DEFAULT_EXPIRATION_NANOS
                : expireAfterAccessNanos;
    }



    long getRefreshNanos() {
        return (refreshNanos == UNSET_INT) ? DEFAULT_REFRESH_NANOS : refreshNanos;
    }

    public CacheBuilder<K, V> ticker(Ticker ticker) {
        checkState(this.ticker == null);
        this.ticker = checkNotNull(ticker);
        return this;
    }

    Ticker getTicker(boolean recordsTime) {
        if (ticker != null) {
            return ticker;
        }
        return recordsTime ? Ticker.systemTicker() : NULL_TICKER;
    }


    public CacheBuilder<K, V> recordStats() {
        statsCounterSupplier = CACHE_STATS_COUNTER;
        return this;
    }

    boolean isRecordingStats() {
        return statsCounterSupplier == CACHE_STATS_COUNTER;
    }

    Supplier<? extends AbstractCache.StatsCounter> getStatsCounterSupplier() {
        return statsCounterSupplier;
    }

    /**
     * Builds a cache, which either returns an already-loaded value for a given key or atomically
     * computes or retrieves it using the supplied {@code CacheLoader}. If another thread is currently
     * loading the value for this key, simply waits for that thread to finish and returns its loaded
     * value. Note that multiple threads can concurrently load values for distinct keys.
     *
     * <p>This method does not alter the state of this {@code CacheBuilder} instance, so it can be
     * invoked again to create multiple independent caches.
     *
     * @param loader the cache loader used to obtain new values
     * @return a cache having the requested features
     */
    public <K1 extends K, V1 extends V> LoadingCache<K1, V1> build(
            CacheLoader<? super K1, V1> loader) {
        return new LocalLoadingCache(this, loader);
    }

    public <K1 extends K, V1 extends V> Cache<K1, V1> build() {
        return new LocalManualCache<>(this);
    }

    /**
     * Returns a string representation for this CacheBuilder instance. The exact form of the returned
     * string is not specified.
     */
    @Override
    public String toString() {
        MoreObjects.ToStringHelper s = MoreObjects.toStringHelper(this);
        if (initialCapacity != UNSET_INT) {
            s.add("initialCapacity", initialCapacity);
        }

        if (maximumSize != UNSET_INT) {
            s.add("maximumSize", maximumSize);
        }
        if (expireAfterWriteNanos != UNSET_INT) {
            s.add("expireAfterWrite", expireAfterWriteNanos + "ns");
        }
        if (expireAfterAccessNanos != UNSET_INT) {
            s.add("expireAfterAccess", expireAfterAccessNanos + "ns");
        }
        if (keyStrength != null) {
            s.add("keyStrength", Ascii.toLowerCase(keyStrength.toString()));
        }
        if (valueStrength != null) {
            s.add("valueStrength", Ascii.toLowerCase(valueStrength.toString()));
        }
        if (keyEquivalence != null) {
            s.addValue("keyEquivalence");
        }
        if (valueEquivalence != null) {
            s.addValue("valueEquivalence");
        }
        return s.toString();
    }
}
