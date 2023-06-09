package com.bsren.cache;


import com.bsren.cache.listeners.RemovalCause;
import com.bsren.cache.listeners.RemovalListener;
import com.bsren.cache.listeners.RemovalNotification;
import com.bsren.cache.loading.Unset;
import com.bsren.cache.queue.AccessQueue;
import com.bsren.cache.queue.WriteQueue;
import com.bsren.cache.weigher.Weigher;
import com.google.common.base.Equivalence;
import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.j2objc.annotations.Weak;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.bsren.cache.CacheBuilder.UNSET_INT;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.common.util.concurrent.Uninterruptibles.getUninterruptibly;
import static java.util.Collections.unmodifiableSet;


/**
 * 1.put
 * 2.get
 * 3.扩容
 * 4.设置读超时和写超时
 * 5.过期回收
 */
public class LocalCache<K, V> {

    //最大容量
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * 读-清洗的阈值
     */
    final int DRAIN_THRESHOLD = 0x3F;

    static final int DRAIN_MAX = 16;


    //每个segment是一个hashMap
    Segment<K, V>[] segments;

    /**
     * segments.length-1
     */
    int segmentMask;

    /**
     * index for segment，防止在同一个segment里的entry也落在同一个index中
     */
    int segmentShift;

    /**
     * 统计
     */
    AbstractCache.StatsCounter globalStatsCounter;

    /**
     * 公用的超时读时间
     */
    long expireAfterAccessNanos;

    /**
     * 公用的超时写时间
     */
    long expireAfterWriteNanos;

    /**
     * default cache loader
     */
    CacheLoader<? super K, V> defaultLoader;

    /**
     * Strategy for comparing keys
     */
    Equivalence<Object> keyEquivalence;

    /**
     * Strategy for comparing values.
     */
    Equivalence<Object> valueEquivalence;

    /**
     * 公共的删除监听器
     */
    RemovalListener<K, V> removalListener;

    /**
     * 监听队列
     */
    Queue<RemovalNotification<K, V>> removalNotificationQueue;

    /**
     * 公共的刷新时间
     */
    long refreshNanos;

    /**
     * 根据key和value来计算entry的权重
     */
    Weigher<K, V> weigher;


    /**
     * 计时器
     */
    Ticker ticker;

    /**
     * Strategy for referencing keys
     */
    Strength keyStrength;

    /**
     * Strategy for referencing values.
     */
    Strength valueStrength;


    /**
     * entry工厂
     */
    EntryFactory entryFactory;

    long maxWeight;

    public LocalCache(
            CacheBuilder<? super K, ? super V> builder,
            CacheLoader<? super K, V> loader) {

        keyStrength = builder.getKeyStrength();
        valueStrength = builder.getValueStrength();
        keyEquivalence = builder.getKeyEquivalence();
        valueEquivalence = builder.getValueEquivalence();

        defaultLoader = loader;
        this.globalStatsCounter = new AbstractCache.SimpleStatsCounter();
        ticker = builder.getTicker(recordsTime());
        entryFactory = EntryFactory.getFactory(keyStrength, usesAccessEntries(), usesWriteEntries());

        weigher = builder.getWeigher();
        maxWeight = builder.getMaximumWeight();

        expireAfterAccessNanos = builder.getExpireAfterAccessNanos();
        expireAfterWriteNanos = builder.getExpireAfterWriteNanos();
        refreshNanos = builder.getRefreshNanos();

        removalListener = builder.getRemovalListener();
        removalNotificationQueue = (removalListener == CacheBuilder.NullListener.INSTANCE) ?
                LocalCache.discardingQueue() : new ConcurrentLinkedDeque<>();

        int initialCapacity = Math.min(MAXIMUM_CAPACITY, builder.getInitialCapacity());
        if (evictsBySize() && !customWeigher()) {
            initialCapacity = (int) Math.min(initialCapacity, maxWeight);
        }


        int segmentShift = 0;
        int segmentCount = 1;
        while (segmentCount * 20 <= initialCapacity) {
            ++segmentShift;
            segmentCount <<= 1;
        }
        this.segmentShift = 32 - segmentShift;
        segmentMask = segmentCount - 1;
        this.segments = newSegmentArray(segmentCount);

        int segmentCapacity = initialCapacity / segmentCount;
        if (segmentCapacity * segmentCount < initialCapacity) {
            segmentCapacity++;
        }
        int segmentSize = 1;
        while (segmentSize < segmentCapacity) {
            segmentSize <<= 1;
        }
        if (evictsBySize()) {
            // Ensure sum of segment max weights = overall max weights
            long maxSegmentWeight = maxWeight / segmentCount + 1;
            long remainder = maxWeight % segmentCount;
            for (int i = 0; i < this.segments.length; ++i) {
                if (i == remainder) {
                    maxSegmentWeight--;
                }
                this.segments[i] =
                        createSegment(segmentSize, maxSegmentWeight, builder.getStatsCounterSupplier().get());
            }
        } else {
            for (int i = 0; i < this.segments.length; ++i) {
                this.segments[i] =
                        createSegment(segmentSize, UNSET_INT, builder.getStatsCounterSupplier().get());
            }
        }
    }


    boolean evictsBySize() {
        return maxWeight >= 0;
    }

    boolean customWeigher() {
        return weigher != CacheBuilder.OneWeigher.INSTANCE;
    }

    boolean expires() {
        return expiresAfterAccess() || expiresAfterWrite();
    }

    boolean expiresAfterWrite() {
        return expireAfterWriteNanos > 0;
    }

    boolean expiresAfterAccess() {
        return expireAfterAccessNanos > 0;
    }

    boolean refreshes() {
        return refreshNanos > 0;
    }

    private boolean useAccessQueue() {
        return expiresAfterAccess();
    }

    private boolean useWriteQueue() {
        return expiresAfterWrite();
    }

    private boolean recordsWrite() {
        return expiresAfterWrite() || refreshes();
    }

    private boolean recordsAccess() {
        return expiresAfterAccess();
    }

    private boolean recordsTime() {
        return expiresAfterWrite() || expiresAfterAccess();
    }

    boolean usesWriteEntries() {
        return usesWriteQueue() || recordsWrite();
    }

    boolean usesKeyReferences() {
        return keyStrength != Strength.STRONG;
    }

    boolean usesValueReferences() {
        return valueStrength != Strength.STRONG;
    }


    boolean usesWriteQueue() {
        return expiresAfterWrite();
    }


    private boolean usesAccessEntries() {
        return usesAccessQueue() || recordsAccess();
    }

    boolean usesAccessQueue() {
        return expiresAfterAccess() || evictsBySize();
    }

    static int rehash(int h) {
        h += (h << 15) ^ 0xffffcd7d;
        h ^= (h >>> 10);
        h += (h << 3);
        h ^= (h >>> 6);
        h += (h << 2) + (h << 14);
        return h ^ (h >>> 16);
    }

    int hash(Object key) {
        int h = keyEquivalence.hash(key);
        return rehash(h);
    }

    void reclaimValue(ValueReference<K, V> valueReference) {
        ReferenceEntry<K, V> entry = valueReference.getEntry();
        int hash = entry.getHash();
        segmentFor(hash).reclaimValue(entry.getKey(), hash, valueReference);
    }

    void reclaimKey(ReferenceEntry<K, V> entry) {
        int hash = entry.getHash();
        segmentFor(hash).reclaimKey(entry, hash);
    }

    Segment<K, V> segmentFor(int hash) {
        return segments[(hash >>> segmentShift) & segmentMask];
    }


    Segment<K, V> createSegment(int initialCapacity, long maxSegmentWeight, AbstractCache.StatsCounter statsCounter) {
        return new Segment<>(this, initialCapacity, maxSegmentWeight, statsCounter);
    }


    private boolean isExpired(ReferenceEntry<K, V> entry, long now) {
        checkNotNull(entry);
        if (expiresAfterAccess() && (now - entry.getAccessTime()) > expireAfterAccessNanos) {
            return true;
        }
        if (expiresAfterWrite() && (now - entry.getWriteTime() > expireAfterWriteNanos)) {
            return true;
        }
        return false;
    }


    /**
     * 通知监听者entry已经被removed，因为过期、驱逐或者垃圾回收
     * 是一个异步方法，一定会在expireEntry和evictEntry后被调用
     */
    void processPendingNotifications() {
        RemovalNotification<K, V> notification;
        while ((notification = removalNotificationQueue.poll()) != null) {
            try {
                removalListener.onRemoval(notification);
            } catch (Throwable e) {
                logger.log(Level.WARNING, "Exception thrown by removal listener", e);
            }
        }
    }


    final Segment<K, V>[] newSegmentArray(int size) {
        return new Segment[size];
    }

    static class Segment<K, V> extends ReentrantLock {

        @Weak
        final LocalCache<K, V> map;

        volatile AtomicReferenceArray<ReferenceEntry<K, V>> table;

        int threshold;

        volatile int count;

        int modCount;

        AbstractCache.StatsCounter statsCounter;

        Queue<ReferenceEntry<K, V>> recencyQueue;

        AtomicInteger readCount = new AtomicInteger();

        Queue<ReferenceEntry<K, V>> accessQueue;

        Queue<ReferenceEntry<K, V>> writeQueue;

        ReferenceQueue<K> keyReferenceQueue;

        ReferenceQueue<V> valueReferenceQueue;

        @GuardedBy("this")
        long totalWeight;
        private long maxSegmentWeight;

        Segment(LocalCache<K, V> map,
                int initialCapacity,
                long maxSegmentWeight,
                AbstractCache.StatsCounter statsCounter) {
            this.map = map;
            this.maxSegmentWeight = maxSegmentWeight;
            this.statsCounter = statsCounter;

            initTable(newEntryArray(initialCapacity));

            accessQueue = map.useAccessQueue() ?
                    new AccessQueue<>() : LocalCache.discardingQueue();

            writeQueue = map.useWriteQueue() ?
                    new WriteQueue<>() : LocalCache.discardingQueue();
            recencyQueue = map.useAccessQueue() ? new ConcurrentLinkedDeque<>() : LocalCache.discardingQueue();
        }


        AtomicReferenceArray<ReferenceEntry<K, V>> newEntryArray(int size) {
            return new AtomicReferenceArray<>(size);
        }

        private void initTable(AtomicReferenceArray<ReferenceEntry<K, V>> newEntryArray) {
            this.threshold = newEntryArray.length() * 3 / 4;
            this.table = newEntryArray;
        }


        @GuardedBy("this")
        ReferenceEntry<K, V> newEntry(K key, int hash, ReferenceEntry<K, V> next) {
            return map.entryFactory.newEntry(this, checkNotNull(key), hash, next);
        }

        /**
         * 如果key或者value被回收，则return null
         * 否则copy一份
         */
        @GuardedBy("this")
        ReferenceEntry<K, V> copyEntry(ReferenceEntry<K, V> original, ReferenceEntry<K, V> newNext) {
            if (original.getKey() == null) {
                // key collected
                return null;
            }

            ValueReference<K, V> valueReference = original.getValueReference();
            V value = valueReference.get();
            if ((value == null) && valueReference.isActive()) {
                // value collected
                return null;
            }

            ReferenceEntry<K, V> newEntry = map.entryFactory.copyEntry(this, original, newNext);
            newEntry.setValueReference(valueReference.copyFor(this.valueReferenceQueue, value, newEntry));
            return newEntry;
        }




        @GuardedBy("this")
        private void setValue(ReferenceEntry<K, V> entry, K key, V newValue, long now) {
            ValueReference<K, V> previous = entry.getValueReference();
            int weight = map.weigher.weigh(key, newValue);
            checkState(weight >= 0, "Weights must be non-negative");
            ValueReference<K, V> valueReference =
                    map.valueStrength.referenceValue(this, entry, newValue, 1);
            entry.setValueReference(valueReference);
            recordWrite(entry, weight, now);
            previous.notifyNewValue(newValue);
        }


        /**
         * 读到live entry,如果返回null则直接返回
         * 拿到entry的value,如果非null，则记录下该次读取，然后根据defaultLoader重新加载该cache
         */
        V get(Object key, int hash) {
            try {
                if (count != 0) {
                    long now = map.ticker.read();
                    ReferenceEntry<K, V> e = getLiveEntry(key, hash, now);
                    if (e == null) {
                        return null;
                    }
                    V value = e.getValueReference().get();
                    if (value != null) {
                        recordRead(e, now);
                        return scheduleRefresh(e, e.getKey(), hash, value, now, map.defaultLoader);
                    }
                    tryDrainReferenceQueues();
                }
                return null;
            } finally {
                postReadCleanup();
            }
        }

        V get(K key, int hash, CacheLoader<? super K, V> loader) throws Exception {
            checkNotNull(key);
            checkNotNull(loader);
            try {
                if (count != 0) {
                    //don't call getLiveEntry, which would ignore loading values
                    ReferenceEntry<K, V> e = getEntry(key, hash);
                    if (e != null) {
                        long now = map.ticker.read();
                        V value = getLiveValue(e, now);
                        if (value != null) {
                            recordRead(e, now);
                            statsCounter.recordHits(1);
                            return scheduleRefresh(e, key, hash, value, now, loader);
                        }
                        ValueReference<K, V> valueReference = e.getValueReference();
                        if (valueReference.isLoading()) {
                            return waitForLoadingValue(e, key, valueReference);
                        }
                    }
                }
                // at this point e is either null or expired;
                // 这里有可能创建新的value，所以要加锁
                // 但是也可能别的线程已经创建好了或者在创建中，所以可以get返回
                // 也就是说锁住的是进入的过程，loading的过程是不锁的
                return lockedGetOrLoad(key, hash, loader);
            } catch (ExecutionException ee) {
                Throwable cause = ee.getCause();
                if (cause instanceof Error) {
                    throw new ExecutionError((Error) cause);
                } else if (cause instanceof RuntimeException) {
                    throw new UncheckedExecutionException(cause);
                }
                throw ee;
            } finally {
                postReadCleanup();
            }
        }

        private V lockedGetOrLoad(K key, int hash, CacheLoader<? super K, V> loader) throws Exception {
            ReferenceEntry<K, V> e;
            ValueReference<K, V> valueReference = null;
            LoadingValueReference<K, V> loadingValueReference = null;
            boolean createNewEntry = true;
            lock();
            try {
                long now = map.ticker.read();
                preWriteCleanup(now);

                int newCount = this.count - 1;
                AtomicReferenceArray<ReferenceEntry<K, V>> table = this.table;
                int index = hash & (table.length() - 1);
                ReferenceEntry<K, V> first = table.get(index);

                for (e = first; e != null; e = e.getNext()) {
                    K entryKey = e.getKey();
                    if (e.getHash() == hash && equalsKey(key, entryKey)) {
                        valueReference = e.getValueReference();
                        if (valueReference.isLoading()) {   //正在加载
                            createNewEntry = false;
                        } else {
                            V value = valueReference.get();
                            if (value == null) {              //value的值为空
                                enqueueNotification(entryKey, null, valueReference.getWeight(), RemovalCause.COLLECTED);
                            } else if (map.isExpired(e, now)) {  //或者过期了
                                enqueueNotification(entryKey, value, valueReference.getWeight(), RemovalCause.EXPIRED);
                            } else {
                                recordLockedRead(e, now);    //value有值，并且没有过期，是一次成功的get
                                statsCounter.recordHits(1);
                                return value;
                            }
                            // immediately reuse invalid entries
                            writeQueue.remove(e);
                            accessQueue.remove(e);
                            this.count = newCount; // write-volatile
                        }
                        break;
                    }
                }
                //并没有找到cache，value为空，或者正在加载，或者过期了
                //如果value不是正在加载，则需要设置新的entry，将valueReference设置为loading
                if (createNewEntry) {
                    loadingValueReference = new LoadingValueReference<>();
                    //如果在链表的最后也没能找到，则创建一个新的entry
                    if (e == null) {
                        e = newEntry(key, hash, first);
                        e.setValueReference(loadingValueReference);
                        table.set(index, e);
                    } else {
                        e.setValueReference(loadingValueReference);
                    }
                }

            }
            //为什么这里解锁了，正如前面所说，编辑entry是一个锁方法，但是等待加载loadingValue并不是
            finally {
                unlock();
                postWriteCleanup();
            }
            //同步等待value创建完成
            if (createNewEntry) {
                try {
                    synchronized (e) {  //锁住entry,只允许自己修改entry
                        return loadSync(key, hash, loadingValueReference, loader);
                    }
                } finally {
                    statsCounter.recordMisses(1);
                }
            }
            //该value正在加载中，直接等待就好
            else {
                // The entry already exists. Wait for loading.
                return waitForLoadingValue(e, key, valueReference);
            }
        }

        private V waitForLoadingValue(ReferenceEntry<K, V> e, K key, ValueReference<K, V> valueReference) throws Exception {
            if (!valueReference.isLoading()) {
                throw new AssertionError();
            }
            //别的线程正在加载该entry，本线程只需要等待就好
            checkState(!Thread.holdsLock(e));
            try {
                V value = valueReference.waitForValue();
                if (value == null) {
                    throw new Exception("CacheLoader returned null for key " + key + ".");
                }
                long now = map.ticker.read();
                recordRead(e, now);
                return value;
            } finally {
                statsCounter.recordMisses(1);
            }
        }

        private V loadSync(K key, int hash, LoadingValueReference<K, V> loadingValueReference, CacheLoader<? super K, V> loader) throws ExecutionException {
            ListenableFuture<V> loadFuture = loadingValueReference.loadFuture(key, loader);
            return getAndRecordStats(key, hash, loadingValueReference, loadFuture);
        }

        private ListenableFuture<V> loadAsync(K key, int hash,
                                              LoadingValueReference<K, V> loadingValueReference,
                                              CacheLoader<? super K, V> cacheLoader) {
            ListenableFuture<V> loadingFuture = loadingValueReference.loadFuture(key, cacheLoader);
            loadingFuture.addListener(new Runnable() {
                @Override
                public void run() {
                    try {
                        getAndRecordStats(key, hash, loadingValueReference, loadingFuture);
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Exception thrown during refresh", t);
                        loadingValueReference.setException(t);
                    }
                }
            }, directExecutor());
            return loadingFuture;
        }


        /**
         * 不被中断的等待新value被加载，然后record stats
         */
        private V getAndRecordStats(K key, int hash,
                                    LoadingValueReference<K, V> loadingValueReference,
                                    ListenableFuture<V> newValue) throws ExecutionException {
            V value = null;
            try {
                value = getUninterruptibly(newValue);
                if (value == null) {
                    throw new CacheLoader.InvalidCacheLoadException("CacheLoader returned null for key " + key + ".");
                }
                statsCounter.recordLoadSuccess(loadingValueReference.elapsedNanos());
                storeLoadedValue(key, hash, loadingValueReference, value);
                return value;
            } finally {
                if (value == null) {
                    statsCounter.recordLoadException(loadingValueReference.elapsedNanos());
                    removeLoadingValue(key, hash, loadingValueReference);
                }
            }
        }

        private V scheduleRefresh(ReferenceEntry<K, V> entry, K key, int hash, V oldValue, long now, CacheLoader<? super K, V> loader) {
            if (map.refreshes() && (now - entry.getWriteTime() > map.refreshNanos)) {
                V newValue = refresh(key, hash, loader, true);
                if (newValue != null) {
                    return newValue;
                }
            }
            return oldValue;
        }

        /**
         * 刷新value，除非另一个线程也在刷新。
         * 返回刷新后的值，或者空，如果另一个线程也在刷新或者异常发生
         */
        private V refresh(K key, int hash, CacheLoader<? super K, V> loader, boolean checkTime) {
            LoadingValueReference<K, V> loadingValueReference =
                    insertLoadingReference(key, hash, checkTime);
            if (loadingValueReference == null) {
                return null;
            }
            ListenableFuture<V> result = loadAsync(key, hash, loadingValueReference, loader);
            if (result.isDone()) {
                try {
                    return getUninterruptibly(result);
                } catch (Throwable e) {
                }
            }
            return null;
        }


        /**
         * 返回一个新的loadingValueReference, 或者null，如果这个reference已经在loading或者刷新间隔太短
         */
        private LoadingValueReference<K, V> insertLoadingReference(K key, int hash, boolean checkTime) {
            ReferenceEntry<K, V> e = null;
            lock();
            try {
                long now = map.ticker.read();
                preWriteCleanup(now);
                AtomicReferenceArray<ReferenceEntry<K, V>> table = this.table;
                int index = hash & (table.length() - 1);
                ReferenceEntry<K, V> first = table.get(index);
                for (e = first; e != null; e = e.getNext()) {
                    K entryKey = e.getKey();
                    if (equalsKey(key, entryKey)) {
                        ValueReference<K, V> valueReference = e.getValueReference();
                        if (valueReference.isLoading()
                                || (checkTime && (now - e.getWriteTime() < map.refreshNanos))) {
                            return null;
                        }
                        modCount++;
                        LoadingValueReference<K, V> loadingValueReference = new LoadingValueReference<>(valueReference);
                        e.setValueReference(loadingValueReference);
                        return loadingValueReference;
                    }
                }
                modCount++;
                LoadingValueReference<K, V> loadingValueReference = new LoadingValueReference<>();
                e = newEntry(key, hash, first);
                e.setValueReference(loadingValueReference);
                table.set(index, e);
                return loadingValueReference;
            } finally {
                unlock();
                postWriteCleanup();
            }
        }

        private void tryDrainReferenceQueues() {
            if (tryLock()) {
                try {
                    drainReferenceQueues();
                } finally {
                    unlock();
                }
            }
        }

        @GuardedBy("this")
        private void drainReferenceQueues() {
            if (map.usesKeyReferences()) {
                drainKeyReferenceQueue();
            }
            if (map.usesValueReferences()) {
                drainValueReferenceQueue();
            }
        }

        @GuardedBy("this")
        void drainKeyReferenceQueue() {
            Reference<? extends K> ref;
            int i = 0;
            while ((ref = keyReferenceQueue.poll()) != null) {
                @SuppressWarnings("unchecked")
                ReferenceEntry<K, V> entry = (ReferenceEntry<K, V>) ref;
                map.reclaimKey(entry);
                if (++i == DRAIN_MAX) {
                    break;
                }
            }
        }

        @GuardedBy("this")
        void drainValueReferenceQueue() {
            Reference<? extends V> ref;
            int i = 0;
            while ((ref = valueReferenceQueue.poll()) != null) {
                ValueReference<K, V> valueReference = (ValueReference<K, V>) ref;
                map.reclaimValue(valueReference);
                if (++i == DRAIN_MAX) {
                    break;
                }
            }
        }

        void clearReferenceQueues() {
            if (map.usesKeyReferences()) {
                clearKeyReferenceQueue();
            }
            if (map.usesValueReferences()) {
                clearValueReferenceQueue();
            }
        }

        void clearKeyReferenceQueue() {
            while (keyReferenceQueue.poll() != null) {}
        }

        void clearValueReferenceQueue() {
            while (valueReferenceQueue.poll() != null) {}
        }


        private void recordRead(ReferenceEntry<K, V> entry, long now) {
            if (map.recordsAccess()) {
                entry.setAccessTime(now);
            }
            recencyQueue.add(entry);
        }


        @GuardedBy("this")
        private void recordLockedRead(ReferenceEntry<K, V> e, long now) {
            if (map.recordsAccess()) {
                e.setAccessTime(now);
            }
            accessQueue.add(e);
        }


        private void recordWrite(ReferenceEntry<K, V> e, int weight, long now) {
            drainRecencyQueue();
            totalWeight += weight;
            if (map.recordsAccess()) {
                e.setAccessTime(now);
            }
            if (map.recordsWrite()) {
                e.setWriteTime(now);
            }
            accessQueue.add(e);
            writeQueue.add(e);
        }


        /**
         * 首先recency是一个并发的容器，get后数据首先来到这里
         * accessQueue是一个队列，在put的时候会加入元素，先入先出记录最老的缓存，过期的时候从头遍历进行清理
         */
        @GuardedBy("this")
        private void drainRecencyQueue() {
            ReferenceEntry<K, V> e;
            while ((e = recencyQueue.poll()) != null) {
                if (accessQueue.contains(e)) {
                    accessQueue.add(e);
                }
            }
        }

        private void tryExpireEntries(long now) {
            if (tryLock()) {
                try {
                    expireEntries(now);
                } finally {
                    unlock();
                }
            }
        }

        /**
         * 将recency中的数据添加到access中
         * 然后将writeQueue和accessQueue的内容过期部分清楚
         * 考虑到所有的缓存是公用一份读超时或者写超时，所以队头的超时时间一定是长的
         * todo 为每个缓存设计过期时间，为一组缓存设计过期时间
         */
        @GuardedBy("this")
        private void expireEntries(long now) {
            //将recency的缓存追加到queue中,这是一个锁方法
            drainRecencyQueue();
            ReferenceEntry<K, V> e;
            while ((e = writeQueue.peek()) != null && map.isExpired(e, now)) {
                if (!removeEntry(e, e.getHash(), RemovalCause.EXPIRED)) {
                    throw new AssertionError();
                }
            }
            while ((e = accessQueue.peek()) != null && map.isExpired(e, now)) {
                if (!removeEntry(e, e.getHash(), RemovalCause.EXPIRED)) {
                    throw new AssertionError();
                }
            }
        }


        @GuardedBy("this")
        private void enqueueNotification(K key, V value, int weight, RemovalCause cause) {
            totalWeight -= weight;
            if (cause.wasEvicted()) {
                statsCounter.recordEviction();
            }
            if (map.removalNotificationQueue != DISCARDING_QUEUE) {
                RemovalNotification<K, V> notification = RemovalNotification.create(key, value, cause);
                map.removalNotificationQueue.offer(notification);
            }
        }

        @GuardedBy("this")
        private void evictEntries(ReferenceEntry<K, V> e) {
            drainRecencyQueue();
            if (e.getValueReference().getWeight() > maxSegmentWeight) {
                if (!removeEntry(e, e.getHash(), RemovalCause.SIZE)) {
                    throw new AssertionError();
                }
            }
            while (totalWeight > maxSegmentWeight) {
                ReferenceEntry<K, V> next = getNextEvictable();
                if (!removeEntry(next, next.getHash(), RemovalCause.SIZE)) {
                    throw new AssertionError();
                }
            }
        }

        @GuardedBy("this")
        private ReferenceEntry<K, V> getNextEvictable() {
            for (ReferenceEntry<K, V> e : accessQueue) {
                int weight = e.getValueReference().getWeight();
                if (weight > 0) {
                    return e;
                }
            }
            throw new AssertionError();
        }

        ReferenceEntry<K, V> getFirst(int hash) {
            AtomicReferenceArray<ReferenceEntry<K, V>> table = this.table;
            return table.get(hash & (table.length() - 1));
        }


        /**
         * 根据key和hash到map中获取entry
         */
        ReferenceEntry<K, V> getEntry(Object key, int hash) {
            for (ReferenceEntry<K, V> e = getFirst(hash); e != null; e = e.getNext()) {
                if (e.getHash() != hash) {
                    continue;
                }
                K entryKey = e.getKey();
                if (entryKey == null) {
                    tryDrainReferenceQueues();
                    continue;
                }
                if (equalsKey(key, entryKey)) {
                    return e;
                }
            }
            return null;
        }

        private ReferenceEntry<K, V> getLiveEntry(Object key, int hash, long now) {
            ReferenceEntry<K, V> entry = getEntry(key, hash);
            if (entry == null) {
                return null;
            } else if (map.isExpired(entry, now)) {
                tryExpireEntries(now);
                return null;
            }
            return entry;
        }


        V getLiveValue(ReferenceEntry<K, V> entry, long now) {
            //如果key被清理掉，则清理引用
            if (entry.getKey() == null) {
                tryDrainReferenceQueues();
                return null;
            }
            //如果valueReference中的value被清理掉，则清理引用
            V value = entry.getValueReference().get();
            if (value == null) {
                tryDrainReferenceQueues();
                return null;
            }
            //如果只是超时了，则清理过期entry
            if (map.isExpired(entry, now)) {
                tryExpireEntries(now);
                return null;
            }
            return value;
        }


        public boolean containsKey(Object key, int hash) {
            try {
                if (count == 0) {
                    return false;
                }
                long now = map.ticker.read();
                ReferenceEntry<K, V> entry = getLiveEntry(key, hash, now);
                if (entry == null) {
                    return false;
                }
                return entry.getValueReference().get() != null;

            } finally {
                postReadCleanup();
            }
        }


        public V put(K key, int hash, V value, boolean onlyIfAbsent) {
            lock();
            try {

                long now = map.ticker.read();
                preWriteCleanup(now);
                int newCount = this.count + 1;
                if (newCount > this.threshold) {
                    expand();
                    newCount = this.count + 1;
                }

                AtomicReferenceArray<ReferenceEntry<K, V>> table = this.table;
                int index = hash & (table.length() - 1);
                ReferenceEntry<K, V> first = table.get(index);

                for (ReferenceEntry<K, V> e = first; e != null; e = e.getNext()) {
                    K entryKey = e.getKey();
                    //find an existing value
                    if (e.getHash() == hash && equalsKey(key, entryKey)) {
                        ValueReference<K, V> valueReference = e.getValueReference();
                        V entryValue = valueReference.get();
                        if (entryValue == null) {
                            modCount++;
                            if (valueReference.isActive()) {
                                enqueueNotification(key, null, valueReference.getWeight(), RemovalCause.COLLECTED);
                                setValue(e, key, value, now);
                                newCount = this.count;
                            } else {
                                setValue(e, key, value, now);
                                newCount = this.count + 1;
                            }
                            this.count = newCount;
                            evictEntries(e);
                            return null;
                        } else if (onlyIfAbsent) {
                            recordLockedRead(e, now);
                            return entryValue;
                        } else {
                            modCount++;
                            enqueueNotification(key, entryValue, valueReference.getWeight(), RemovalCause.REPLACED);
                            setValue(e, key, value, now);
                            evictEntries(e);
                            return entryValue;
                        }
                    }
                }
                modCount++;
                ReferenceEntry<K, V> newEntry = newEntry(key, hash, first);
                setValue(newEntry, key, value, now);
                table.set(index, newEntry);
                newCount = this.count + 1;
                this.count = newCount;
                evictEntries(newEntry);
                return null;
            } finally {
                unlock();
                postWriteCleanup();
            }
        }

        @GuardedBy("this")
        void expand() {
            AtomicReferenceArray<ReferenceEntry<K, V>> oldTable = this.table;
            int oldCapacity = oldTable.length();
            if (oldCapacity >= MAXIMUM_CAPACITY) {
                return;
            }
            int newCount = count;
            AtomicReferenceArray<ReferenceEntry<K, V>> newTable = newEntryArray(oldCapacity << 1);
            threshold = newTable.length() * 3 / 4;
            int newMask = newTable.length() - 1;
            for (int oldIndex = 0; oldIndex < oldCapacity; ++oldIndex) {
                ReferenceEntry<K, V> head = oldTable.get(oldIndex);
                if (head != null) {
                    ReferenceEntry<K, V> next = head.getNext();
                    int headIndex = head.getHash() & newMask;
                    if (next == null) {
                        newTable.set(headIndex, head);
                    } else {
                        //这里的想法是可能会有一串子在扩容后相同的index，挂在链的尾部
                        //算是一个小小的优化吧，感觉不是很明显
                        ReferenceEntry<K, V> tail = head;
                        int tailIndex = headIndex;
                        for (ReferenceEntry<K, V> e = next; e != null; e = e.getNext()) {
                            int newIndex = e.getHash() & newMask;
                            if (newIndex != tailIndex) {
                                tailIndex = newIndex;
                                tail = e;
                            }
                        }
                        newTable.set(tailIndex, tail);
                        for (ReferenceEntry<K, V> e = head; e != tail; e = e.getNext()) {
                            int newIndex = e.getHash() & newMask;
                            ReferenceEntry<K, V> newNext = newTable.get(newIndex);
                            ReferenceEntry<K, V> newFirst = copyEntry(e, newNext);
                            newTable.set(newIndex, newFirst);
                        }
                    }
                }
            }
            table = newTable;
            this.count = newCount;
        }


        public V replace(K key, int hash, V newValue) {
            lock();
            try {
                long now = map.ticker.read();
                preWriteCleanup(now);
                AtomicReferenceArray<ReferenceEntry<K, V>> table = this.table;
                int index = hash & (table.length() - 1);
                ReferenceEntry<K, V> first = table.get(index);
                for (ReferenceEntry<K, V> e = first; e != null; e = e.getNext()) {
                    K entryKey = e.getKey();
                    if (equalsKey(key, entryKey)) {
                        ValueReference<K, V> valueReference = e.getValueReference();
                        V entryValue = valueReference.get();
                        if (entryValue == null) {
                            int newCount = this.count - 1;
                            modCount++;
                            ReferenceEntry<K, V> newFirst = removeValueFromChain(first, e, entryKey, null, valueReference
                                    , RemovalCause.COLLECTED);
                            newCount = this.count - 1;
                            table.set(index, newFirst);
                            this.count = newCount;
                            return null;
                        } else {
                            modCount++;
                            enqueueNotification(key, entryValue, valueReference.getWeight(), RemovalCause.REPLACED);
                            setValue(e, key, newValue, now);
                            evictEntries(e);
                            return entryValue;
                        }
                    }
                }
                return null;
            } finally {
                postWriteCleanup();
                unlock();
            }
        }


        /**
         * 首先找到这个entry
         * 如果entry非空，则设置cause为explicit，即用户自行删除
         * 否则，如果isActive为true，则说明不是一个loading状态的entry，被垃圾收集来，设置cause为collected
         * 其他情况直接return
         * modCount自增
         * count自减
         * 删除该value
         */
        public V remove(Object key, int hash) {
            lock();
            try {

                long now = map.ticker.read();
                preWriteCleanup(now);

                int newCount = this.count - 1;
                AtomicReferenceArray<ReferenceEntry<K, V>> table = this.table;
                int index = hash & (table.length() - 1);
                ReferenceEntry<K, V> first = table.get(index);

                for (ReferenceEntry<K, V> e = first; e != null; e = e.getNext()) {
                    K entryKey = e.getKey();
                    if (e.getHash() == hash && equalsKey(key, entryKey)) {
                        ValueReference<K, V> valueReference = e.getValueReference();
                        V entryValue = valueReference.get();
                        RemovalCause cause;
                        if (entryValue != null) {
                            cause = RemovalCause.EXPLICIT;
                        } else if (valueReference.isActive()) {  //entryValue==null
                            cause = RemovalCause.COLLECTED;
                        } else {
                            //current loading
                            return null;
                        }
                        modCount++;
                        ReferenceEntry<K, V> newFirst = removeValueFromChain(first, e, entryKey, entryValue, valueReference, cause);
                        newCount = this.count - 1;
                        table.set(index, newFirst);
                        this.count = newCount;
                        return entryValue;
                    }
                }
                return null;
            } finally {
                unlock();
                postWriteCleanup();
            }
        }

        private boolean storeLoadedValue(K key,
                                         int hash,
                                         LoadingValueReference<K, V> oldValueReference,
                                         V newValue) {
            lock();
            try {
                long now = map.ticker.read();
                preWriteCleanup(now);
                int newCount = this.count + 1;
                if (newCount > this.threshold) {
                    expand();
                    newCount = this.count + 1;
                }
                AtomicReferenceArray<ReferenceEntry<K, V>> table = this.table;
                int index = hash & (table.length() - 1);
                ReferenceEntry<K, V> first = table.get(index);
                for (ReferenceEntry<K, V> e = first; e != null; e = e.getNext()) {
                    K entryKey = e.getKey();
                    if (e.getHash() == hash && equalsKey(key, entryKey)) {
                        ValueReference<K, V> valueReference = e.getValueReference();
                        V entryValue = valueReference.get();
                        if (oldValueReference == valueReference || (
                                entryValue == null && valueReference != Unset.unset())) {
                            modCount++;
                            if (oldValueReference.isActive()) {
                                RemovalCause cause =
                                        (entryValue == null) ? RemovalCause.COLLECTED : RemovalCause.REPLACED;
                                enqueueNotification(key, entryValue, valueReference.getWeight(), cause);
                                newCount--;
                            }
                            setValue(e, key, newValue, now);
                            this.count = newCount;
                            evictEntries(e);
                            return true;
                        }
                        // the loaded value was already clobbered
                        enqueueNotification(key, newValue, valueReference.getWeight(), RemovalCause.REPLACED);
                        return false;
                    }
                }

                modCount++;
                ReferenceEntry<K, V> newEntry = newEntry(key, hash, first);
                setValue(newEntry, key, newValue, now);
                table.set(index, newEntry);
                this.count = newCount;
                evictEntries(newEntry);
                return true;
            } finally {
                unlock();
                postWriteCleanup();
            }
        }


        public void clear() {
            if (count == 0) {
                return;
            }
            lock();
            try {
                long now = map.ticker.read();
                preWriteCleanup(now);
                AtomicReferenceArray<ReferenceEntry<K, V>> table = this.table;
                for (int i = 0; i < table.length(); i++) {
                    for (ReferenceEntry<K, V> e = table.get(i); e != null; e = e.getNext()) {
                        K key = e.getKey();
                        V value = e.getValueReference().get();
                        RemovalCause cause = (key == null || value == null) ? RemovalCause.COLLECTED : RemovalCause.EXPLICIT;
                        enqueueNotification(key, value, e.getValueReference().getWeight(), cause);
                    }
                }
                for (int i = 0; i < table.length(); i++) {
                    table.set(i, null);
                }
                clearReferenceQueues();
                writeQueue.clear();
                accessQueue.clear();
                readCount.set(0);
                modCount++;
                count = 0;
            } finally {
                unlock();
                postWriteCleanup();
            }
        }

        /**
         * 首先将remove通知消息入队
         * 然后将entry从accessQueue和writeQueue中删除
         * 如果valueReference正在加载，则直接返回
         * 否则将entry从chain中删除
         */
        @GuardedBy("this")
        private ReferenceEntry<K, V> removeValueFromChain(
                ReferenceEntry<K, V> first,
                ReferenceEntry<K, V> entry,
                K key,
                V value,
                ValueReference<K, V> valueReference,
                RemovalCause cause) {
            enqueueNotification(key, value, valueReference.getWeight(), cause);
            writeQueue.remove(entry);
            accessQueue.remove(entry);
            if (valueReference.isLoading()) {
                valueReference.notifyNewValue(null);
                return first;
            } else {
                return removeEntryFromChain(first, entry);
            }
        }

        /**
         * 遍历链，不断根据当前节点和entry.next创建新的entry
         * todo 这里是遍历了first和entry之间的节点，然后复制一份，可能会产生较大的损失
         * todo 最新的数据在链头，最老的链尾，删除老数据会check整条链，从这个角度来看可能是合理的
         */
        private ReferenceEntry<K, V> removeEntryFromChain(ReferenceEntry<K, V> first,
                                                          ReferenceEntry<K, V> entry) {
            int newCount = count;
            ReferenceEntry<K, V> newFirst = entry.getNext();
            for (ReferenceEntry<K, V> e = first; e != entry; e = e.getNext()) {
                ReferenceEntry<K, V> next = copyEntry(e, newFirst);
                if (next != null) {
                    newFirst = next;
                } else {
                    removeCollectedEntry(e);
                    newCount--;
                }
            }
            this.count = newCount;
            return newFirst;
        }


        private boolean removeLoadingValue(K key, int hash, LoadingValueReference<K, V> loadingValueReference) {
            lock();
            try {
                AtomicReferenceArray<ReferenceEntry<K, V>> table = this.table;
                int index = hash & (table.length() - 1);
                ReferenceEntry<K, V> first = table.get(index);

                for (ReferenceEntry<K, V> e = first; e != null; e = e.getNext()) {
                    K entry = e.getKey();
                    if (equalsKey(key, entry)) {
                        ValueReference<K, V> v = e.getValueReference();
                        if (v == loadingValueReference) {
                            if (loadingValueReference.isActive()) {
                                e.setValueReference(loadingValueReference.oldValue);
                            } else {
                                ReferenceEntry<K, V> newFirst = removeEntryFromChain(first, e);
                                table.set(index, newFirst);
                            }
                            return true;
                        } else {
                            return false;
                        }
                    }
                }
                return false;
            } finally {
                unlock();
                postWriteCleanup();
            }
        }


        /**
         * 首先找到该entry，使用的是地址比较
         * 调用removeValueFromChain，拿到新的链头
         * 设置新的count
         * 返回ture如果找到了该entry
         */
        @GuardedBy("this")
        private boolean removeEntry(ReferenceEntry<K, V> entry, int hash, RemovalCause cause) {
            int newCount;
            AtomicReferenceArray<ReferenceEntry<K, V>> table = this.table;
            int index = hash & (table.length() - 1);
            ReferenceEntry<K, V> first = table.get(index);
            for (ReferenceEntry<K, V> e = first; e != null; e = e.getNext()) {
                if (e == entry) {
                    modCount++;
                    ReferenceEntry<K, V> newFirst = removeValueFromChain(
                            first,
                            e,
                            e.getKey(),
                            e.getValueReference().get(),
                            e.getValueReference(),
                            cause
                    );
                    newCount = this.count - 1;
                    table.set(index, newFirst);
                    this.count = newCount;
                    return true;
                }
            }
            return false;
        }


        /**
         * 通知，从writeQueue和accessQueue中删除e
         */
        @GuardedBy("this")
        private void removeCollectedEntry(ReferenceEntry<K, V> e) {
            enqueueNotification(e.getKey(), e.getValueReference().get(), e.getValueReference().getWeight(), RemovalCause.COLLECTED);
            writeQueue.remove(e);
            accessQueue.remove(e);
        }


        //---------------------------------post方法------------------------------

        /**
         * 在读次数达到阈值的时候cleanUp
         */
        private void postReadCleanup() {
            if ((readCount.incrementAndGet() & map.DRAIN_THRESHOLD) == 0) {
                cleanUp();
            }
        }

        /**
         * 非锁方法，处理一下listeners
         */
        private void postWriteCleanup() {
            runUnlockedCleanup();
        }

        /**
         * 锁方法，过期entry, 清理referenceQueue，重设readCount
         */
        @GuardedBy("this")
        private void preWriteCleanup(long now) {
            runLockedCleanup(now);
        }


        /**
         * 锁方法：过期entry, 清理referenceQueue，重设readCount
         * 非锁方法：处理一下清理后的监听
         */
        private void cleanUp() {
            long now = map.ticker.read();
            runLockedCleanup(now);
            runUnlockedCleanup();
        }

        /**
         * 过期entry, 清理referenceQueue，重设readCount
         */
        private void runLockedCleanup(long now) {
            if (tryLock()) {
                try {
                    drainReferenceQueues();
                    expireEntries(now);
                    readCount.set(0);
                } finally {
                    unlock();
                }
            }
        }

        /**
         * 非锁方法：处理一下清理后的监听
         */
        void runUnlockedCleanup() {
            if (!isHeldByCurrentThread()) {
                map.processPendingNotifications();
            }
        }


        private boolean equalsKey(Object key, K entryKey) {
            return entryKey != null && map.keyEquivalence.equivalent(key, entryKey);
        }


        public boolean reclaimKey(ReferenceEntry<K, V> entry, int hash) {
            lock();
            try {
                int newCount = count-1;
                AtomicReferenceArray<ReferenceEntry<K,V>> table = this.table;
                int index = hash & (table.length()-1);
                ReferenceEntry<K,V> first = table.get(index);

                for (ReferenceEntry<K,V> e = first;e!=null;e = e.getNext()){
                    if(e==entry){
                        modCount++;
                        ReferenceEntry<K,V> newFirst = removeValueFromChain(
                                first,
                                e,
                                e.getKey(),
                                e.getValueReference().get(),
                                e.getValueReference(),
                                RemovalCause.COLLECTED
                        );
                        newCount = this.count-1;
                        table.set(index,newFirst);
                        this.count = newCount;
                        return true;
                    }
                }
                return false;
            }finally {
                unlock();
                postWriteCleanup();
            }
        }

        public boolean reclaimValue(K key, int hash, ValueReference<K, V> valueReference) {
            lock();
            try {
                int newCount = this.count-1;
                AtomicReferenceArray<ReferenceEntry<K,V>> table = this.table;
                int index = hash & (table.length()-1);
                ReferenceEntry<K,V> first = table.get(index);
                for (ReferenceEntry<K,V> e = first;e!=null;e = e.getNext()){
                    K entryKey = e.getKey();
                    if(e.getHash()==hash && equalsKey(key,entryKey)){
                        ValueReference<K,V> v = e.getValueReference();
                        if(v==valueReference){
                            modCount++;
                            ReferenceEntry<K,V> newFirst = removeValueFromChain(
                                    first,
                                    e,
                                    entryKey,
                                    valueReference.get(),
                                    valueReference,
                                    RemovalCause.COLLECTED
                            );
                            newCount = this.count-1;
                            table.set(index,newFirst);
                            this.count = newCount;
                            return true;
                        }
                        return false;
                    }
                }
                return false;
            }finally {
                unlock();
                if(!isHeldByCurrentThread()){// don't cleanup inside of put
                    postWriteCleanup();
                }
            }
        }
    }


    public void cleanUp() {
        for (Segment<K, V> segment : segments) {
            segment.cleanUp();
        }
    }

    /**
     * double check
     *
     * @return
     */
    boolean isEmpty() {
        long sum = 0;
        Segment<K, V>[] segments = this.segments;
        for (Segment<K, V> segment : segments) {
            if (segment.count != 0) {
                return false;
            }
            sum += segment.modCount;
        }
        if (sum != 0) {
            for (Segment<K, V> segment : segments) {
                if (segment.count != 0) {
                    return false;
                }
                sum -= segment.modCount;
            }
            return sum == 0L;
        }
        return true;
    }


    public long longSize() {
        Segment<K, V>[] segments = this.segments;
        long sum = 0;
        for (Segment<K, V> segment : segments) {
            sum += segment.count;
        }
        return sum;
    }

    public int size() {
        return Ints.saturatedCast(longSize());
    }


    public V get(Object key) {
        if (key == null) {
            return null;
        }
        int hash = hash(key);
        return segmentFor(hash).get(key, hash);
    }

    V get(K key, CacheLoader<? super K, V> cacheLoader) throws Exception {
        int hash = hash(checkNotNull(key));
        return segmentFor(hash).get(key, hash, cacheLoader);
    }

    public V getIfPresent(Object key) {
        int hash = hash(checkNotNull(key));
        V value = segmentFor(hash).get(key, hash);
        if (value == null) {
            globalStatsCounter.recordMisses(1);
        } else {
            globalStatsCounter.recordHits(1);
        }
        return value;
    }


    public V getOrLoad(K key) throws Exception {
        return get(key, defaultLoader);
    }


    ReferenceEntry<K, V> getEntry(Object key) {
        if (key == null) {
            return null;
        }
        int hash = hash(key);
        return segmentFor(hash).getEntry(key, hash);
    }

    void refresh(K key) {
        int hash = hash(checkNotNull(key));
        segmentFor(hash).refresh(key, hash, defaultLoader, false);
    }

    public boolean containsKey(Object key) {
        if (key == null) {
            return false;
        }
        int hash = hash(key);
        return segmentFor(hash).containsKey(key, hash);
    }


    public boolean containsValue(Object value) {
        if (value == null) {
            return false;
        }
        long now = ticker.read();
        final Segment<K, V>[] segments = this.segments;
        for (Segment<K, V> segment : segments) {
            AtomicReferenceArray<ReferenceEntry<K, V>> table = segment.table;
            for (int j = 0; j < table.length(); j++) {
                for (ReferenceEntry<K, V> e = table.get(j); e != null; e = e.getNext()) {
                    V v = segment.getLiveValue(e, now);
                    if (v != null && valueEquivalence.equivalent(value, v)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    public V put(K key, V value) {
        checkNotNull(key);
        checkNotNull(value);
        int hash = hash(key);
        return segmentFor(hash).put(key, hash, value, false);
    }

    public V putIfAbsent(K key, V value) {
        checkNotNull(key);
        checkNotNull(value);
        int hash = hash(key);
        return segmentFor(hash).put(key, hash, value, true);
    }

    public V remove(Object key) {
        if (key == null) {
            return null;
        }
        int hash = hash(key);
        return segmentFor(hash).remove(key, hash);
    }

    public V replace(K key, V value) {
        checkNotNull(key);
        checkNotNull(value);
        int hash = hash(key);
        return segmentFor(hash).replace(key, hash, value);
    }

    public void clear() {
        for (Segment<K, V> segment : segments) {
            segment.clear();
        }
    }


    boolean equalsValue(Object v1, V v2) {
        return true;
    }

    ImmutableMap<K, V> getAll(Iterable<? extends K> keys) throws Exception {
        int hits = 0;
        int misses = 0;

        Map<K, V> result = Maps.newLinkedHashMap();
        Set<K> keysToLoad = Sets.newLinkedHashSet();
        for (K key : keys) {
            V value = get(key);
            if (!result.containsKey(key)) {
                result.put(key, value);
                if (value == null) {
                    misses++;
                    keysToLoad.add(key);
                } else {
                    hits++;
                }
            }
        }

        try {
            if (!keysToLoad.isEmpty()) {
                try {
                    Map<K, V> newEntries = loadAll(unmodifiableSet(keysToLoad), defaultLoader);
                    for (K key : keysToLoad) {
                        V value = newEntries.get(key);
                        if (value == null) {
                            throw new Exception("loadAll failed to return a value for " + key);
                        }
                        result.put(key, value);
                    }
                } catch (Exception e) {
                    // loadAll not implemented, fallback to load
                    for (K key : keysToLoad) {
                        misses--; // get will count this miss
                        result.put(key, get(key, defaultLoader));
                    }
                }
            }
            return ImmutableMap.copyOf(result);
        } finally {
            globalStatsCounter.recordHits(hits);
            globalStatsCounter.recordMisses(misses);
        }
    }

    Map<K,V> loadAll(Set<? extends K> keys,CacheLoader<? super K,V> loader) throws Exception{
        checkNotNull(loader);
        checkNotNull(keys);
        Stopwatch stopwatch = Stopwatch.createStarted();
        Map<K,V> result = null;
        boolean success = false;
        try {
            Map<K,V> map = (Map<K, V>) loader.loadAll(keys);
            result = map;
            success = true;
        }catch (Exception e){
            if(e instanceof InterruptedException){
                Thread.currentThread().interrupt();
            }else {
                throw new Exception(e);
            }
        }finally {
            if(!success){
                globalStatsCounter.recordLoadException(stopwatch.elapsed(TimeUnit.NANOSECONDS));
            }
        }
        if(result==null){
            globalStatsCounter.recordLoadException(stopwatch.elapsed(TimeUnit.NANOSECONDS));
            throw new Exception();
        }
        stopwatch.stop();
        boolean nullsPresent = false;
        //todo batch by segment
//        for (Map.Entry<K, V> entry : result.entrySet()) {
//            K key = entry.getKey();
//            V value = entry.getValue();
//            if(key==null || value==null){
//                nullsPresent = true;
//            }else {
//                put(key,value);
//            }
//        }
        Map<Segment<K,V>,List<Map.Entry<K,V>>> indexMap = new HashMap<>();
        for (Map.Entry<K, V> entry : result.entrySet()) {
            if(entry.getKey()==null || entry.getValue()==null){
                nullsPresent = true;
            }else {
                Segment<K, V> segment = segmentFor(hash(entry.getKey()));
                List<Map.Entry<K, V>> entryList = indexMap.get(segment);
                if(entryList==null){
                    entryList = new ArrayList<>();
                }
                entryList.add(entry);
            }
        }
        for (Map.Entry<Segment<K, V>, List<Map.Entry<K, V>>> segmentListEntry : indexMap.entrySet()) {
            Segment<K, V> segment = segmentListEntry.getKey();
            List<Map.Entry<K, V>> entries = segmentListEntry.getValue();
            for (Map.Entry<K, V> entry : entries) {
                segment.put(entry.getKey(),hash(entry.getKey()),entry.getValue(),false);
            }
        }

        if(nullsPresent){
            globalStatsCounter.recordLoadException(stopwatch.elapsed(TimeUnit.NANOSECONDS));
            throw new Exception();
        }
        return result;
    }


    public CacheStats stats() {
        AbstractCache.SimpleStatsCounter counter = new AbstractCache.SimpleStatsCounter();
        counter.incrementBy(this.globalStatsCounter);
        for (Segment<K, V> segment : this.segments) {
            counter.incrementBy(segment.statsCounter);
        }
        return counter.snapshot();
    }

    static final Logger logger = Logger.getLogger(LocalCache.class.getName());


    static final Queue<?> DISCARDING_QUEUE =
            new AbstractQueue<Object>() {
                @Override
                public boolean offer(Object o) {
                    return true;
                }

                @Override
                public Object peek() {
                    return null;
                }

                @Override
                public Object poll() {
                    return null;
                }

                @Override
                public int size() {
                    return 0;
                }

                @Override
                public Iterator<Object> iterator() {
                    return ImmutableSet.of().iterator();
                }
            };

    static <E> Queue<E> discardingQueue() {
        return (Queue) DISCARDING_QUEUE;
    }


}
