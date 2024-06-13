package com.bsren.cache.cache7;


import com.bsren.cache.abstractCache.*;
import com.bsren.cache.abstractCache.entry.StrongEntry;
import com.bsren.cache.abstractCache.loading.Unset;
import com.bsren.cache.abstractCache.queue.AccessQueue;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.concurrent.GuardedBy;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.*;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.common.util.concurrent.Uninterruptibles.getUninterruptibly;


/**
 * 1.put
 * 2.get
 * 3.扩容
 * 4.设置读超时和写超时
 * 5.过期回收
 */
public class LocalCache<K, V> {

    static final int MAXIMUM_CAPACITY = 1 << 30;

    static final String val = "112";

    final Segment<K, V>[] segments;

    int segmentMask;

    long expireAfterAccessNanos;

    AbstractCache.StatsCounter globalStatsCounter;

    long expireAfterWriteNanos;

    CacheLoader<K, V> defaultLoader;

    long getSize() {
        Segment<K, V>[] segments = this.segments;
        long sum = 0;
        for (Segment<K, V> segment : segments) {
            sum += segment.count;
        }
        return sum;
    }

    public void setExpireAfterAccessNanos(long expireAfterAccessNanos) {
        this.expireAfterAccessNanos = expireAfterAccessNanos;
    }

    public void setExpireAfterWriteNanos(long expireAfterWriteNanos) {
        this.expireAfterWriteNanos = expireAfterWriteNanos;
    }

    final int DRAIN_THRESHOLD = 0x3F;

    long refreshNanos;

    boolean expiresAfterWrite() {
        return expireAfterWriteNanos > 0;
    }

    boolean expiresAfterAccess() {
        return expireAfterAccessNanos > 0;
    }

    boolean refreshes() {
        return refreshNanos > 0;
    }

    public long getRefreshNanos() {
        return refreshNanos;
    }

    public void setRefreshNanos(long refreshNanos) {
        this.refreshNanos = refreshNanos;
    }

    Ticker ticker;

    LocalCache(int initialCapacity, int segmentCount, CacheLoader<K, V> cacheLoader) {
        this(initialCapacity, segmentCount);
        this.defaultLoader = cacheLoader;
    }

    public LocalCache(int initialCapacity, int segmentCount) {
        this.ticker = Ticker.systemTicker();
        this.segments = newSegmentArray(segmentCount);
        this.globalStatsCounter = new AbstractCache.SimpleStatsCounter();
        int segmentCapacity = initialCapacity / segmentCount;
        if (segmentCapacity * segmentCount < initialCapacity) {
            segmentCapacity++;
        }
        segmentMask = segmentCount - 1;
        int segmentSize = 1;
        while (segmentSize < segmentCapacity) {
            segmentSize <<= 1;
        }
        for (int i = 0; i < this.segments.length; i++) {
            segments[i] = createSegment(segmentSize);
        }
    }

    Segment<K, V> createSegment(int initialCapacity) {
        return new Segment<>(this, initialCapacity);
    }

    final Segment<K, V>[] newSegmentArray(int size) {
        return new Segment[size];
    }

    public V get(Object key) {
        if (key == null) {
            return null;
        }
        int hash = hash(key);
        return segmentFor(hash).get(key, hash);
    }

    V get(K key, CacheLoader<K, V> cacheLoader) throws Exception {
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

    public V put(K key, V value) {
        int hash = hash(key);
        return segmentFor(hash).put(key, hash, value,false);
    }

    public V putIfAbsent(K key,V value){
        checkNotNull(key);
        checkNotNull(value);
        int hash = hash(key);
        return segmentFor(hash).put(key,hash,value,true);
    }

    public V remove(Object key) {
        if (key == null) {
            return null;
        }
        int hash = hash(key);
        return segmentFor(hash).remove(key, hash);
    }


    Segment<K, V> segmentFor(int hash) {
        return segments[hash & segmentMask];
    }

    int hash(Object key) {
        int h = key.hashCode();
        return rehash(h);
    }

    static int rehash(int h) {
        h += (h << 15) ^ 0xffffcd7d;
        h ^= (h >>> 10);
        h += (h << 3);
        h ^= (h >>> 6);
        h += (h << 2) + (h << 14);
        return h ^ (h >>> 16);
    }


    static class Segment<K, V> extends ReentrantLock {

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

        Segment(LocalCache<K, V> map,
                int initialCapacity) {
            this.map = map;
            initTable(newEntryArray(initialCapacity));
            accessQueue = map.useAccessQueue()?
                    new ConcurrentLinkedQueue<>(): LocalCache.discardingQueue();

            writeQueue = map.useWriteQueue()?
                    new ConcurrentLinkedQueue<>():LocalCache.discardingQueue();
            recencyQueue = map.useAccessQueue()?new AccessQueue<>():LocalCache.discardingQueue();
            this.statsCounter = new AbstractCache.SimpleStatsCounter();
        }

        private void initTable(AtomicReferenceArray<ReferenceEntry<K, V>> newEntryArray) {
            this.threshold = newEntryArray.length() * 3 / 4;
            this.table = newEntryArray;
        }

        AtomicReferenceArray<ReferenceEntry<K, V>> newEntryArray(int size) {
            return new AtomicReferenceArray<>(size);
        }


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
                        return scheduleRefresh(e,e.getKey(),hash,value,now,map.defaultLoader);
                    }
                }
                return null;
            } finally {
                postReadCleanup();
            }
        }

        V get(K key, int hash, CacheLoader<K, V> loader) throws Exception {
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
                        if(valueReference.isLoading()){
                            return waitForLoadingValue(e,key,valueReference);
                        }
                    }
                }
                // at this point e is either null or expired;
                // 这里有可能创建新的value，所以要加锁
                // 但是也可能别的线程已经创建好了或者在创建中，所以可以get返回
                // 也就是说锁住的是进入的过程，loading的过程是不锁的
                return lockedGetOrLoad(key,hash,loader);
            } finally {
                postReadCleanup();
            }
        }

        private V lockedGetOrLoad(K key, int hash, CacheLoader<K,V> loader) throws Exception {
            ReferenceEntry<K,V> e;
            ValueReference<K,V> valueReference = null;
            LoadingValueReference<K,V> loadingValueReference = null;
            boolean createNewEntry =true;
            lock();
            try {
                long now = map.ticker.read();
                preWriteCleanup(now);
                int newCount = this.count-1;
                AtomicReferenceArray<ReferenceEntry<K,V>> table = this.table;
                int index = hash & (table.length()-1);
                ReferenceEntry<K,V> first = table.get(index);
                for (e = first;e!=null;e = e.getNext()){
                    K entryKey = e.getKey();
                    if(equalsKey(key,entryKey)){
                        valueReference = e.getValueReference();
                        if(valueReference.isLoading()){   //正在加载
                            createNewEntry = false;
                        }else {
                            V value = valueReference.get();
                            if(value==null){              //value的值为空
                                enqueueNotification();
                            }else if(map.isExpired(e,now)){  //或者过期了
                                enqueueNotification();
                            }else {
                                recordLockedRead(e,now);    //value有值，并且没有过期，是一次成功的get
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
                if(createNewEntry){
                    loadingValueReference = new LoadingValueReference<>();
                    //如果在链表的最后也没能找到，则创建一个新的entry
                    if(e==null){
                        e = newEntry(key,hash,first);
                        e.setValueReference(loadingValueReference);
                        table.set(index,e);
                    }else {
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
            if(createNewEntry){
                try {
                    synchronized (e){  //锁住entry,只允许自己修改entry
                        return loadSync(key,hash,loadingValueReference,loader);
                    }
                }finally {
                    statsCounter.recordMisses(1);
                }
            }
            //该value正在加载中，直接等待就好
            else {
                // The entry already exists. Wait for loading.
                return waitForLoadingValue(e,key,valueReference);
            }
        }

        private V loadSync(K key, int hash, LoadingValueReference<K, V> loadingValueReference, CacheLoader<K, V> loader) throws ExecutionException {
            ListenableFuture<V> loadFuture = loadingValueReference.loadFuture(key, loader);
            return getAndRecordStats(key,hash,loadingValueReference,loadFuture);
        }

        @GuardedBy("this")
        private void recordLockedRead(ReferenceEntry<K,V> e, long now) {
            if(map.recordsAccess()){
                e.setAccessTime(now);
            }
            accessQueue.add(e);
        }

        private V waitForLoadingValue(ReferenceEntry<K,V> e, K key, ValueReference<K,V> valueReference) throws Exception {
            if(!valueReference.isLoading()){
                throw new AssertionError();
            }
            //别的线程正在加载该entry，本线程只需要等待就好
            checkState(!Thread.holdsLock(e));
            try {
                V value = valueReference.waitForValue();
                if(value==null){
                    throw new Exception("CacheLoader returned null for key " + key + ".");
                }
                long now = map.ticker.read();
                recordRead(e,now);
                return value;
            }finally {
                statsCounter.recordMisses(1);
            }
        }

        private V scheduleRefresh(ReferenceEntry<K, V> entry, K key, int hash, V oldValue, long now, CacheLoader<K, V> loader) {
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
        private V refresh(K key, int hash, CacheLoader<K, V> loader, boolean checkTime) {
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

        private ListenableFuture<V> loadAsync(K key, int hash,
                                              LoadingValueReference<K, V> loadingValueReference,
                                              CacheLoader<K, V> cacheLoader) {
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

        private boolean storeLoadedValue(K key,
                                         int hash,
                                         LoadingValueReference<K,V> oldValueReference,
                                         V newValue) {
            lock();
            try {
                long now = map.ticker.read();
                preWriteCleanup(now);
                int newCount = this.count+1;
                if(newCount>this.threshold){
                    expand();
                    newCount = this.count+1;
                }
                AtomicReferenceArray<ReferenceEntry<K,V>> table = this.table;
                int index = hash & (table.length()-1);
                ReferenceEntry<K,V> first = table.get(index);
                for (ReferenceEntry<K,V> e = first;e!=null;e = e.getNext()){
                    K entryKey = e.getKey();
                    if(equalsKey(key,entryKey)){
                        ValueReference<K,V> valueReference = e.getValueReference();
                        V entryValue = valueReference.get();
                        if(oldValueReference==valueReference || (
                                entryValue==null && valueReference!= Unset.unset())){
                            modCount++;
                            if(oldValueReference.isActive()){
                                RemovalCause cause =
                                        (entryValue == null) ? RemovalCause.COLLECTED : RemovalCause.REPLACED;
                                enqueueNotification();
                                newCount--;
                            }
                            setValue(e,key,newValue,now);
                            this.count = newCount;
                            return true;
                        }
                        // the loaded value was already clobbered
                        enqueueNotification();
                        return false;
                    }
                }

                modCount++;
                ReferenceEntry<K,V> newEntry = newEntry(key,hash,first);
                setValue(newEntry,key,newValue,now);
                table.set(index,newEntry);
                this.count = newCount;
                return true;
            }finally {
                unlock();
                postWriteCleanup();
            }
        }

        private boolean removeLoadingValue(K key, int hash, LoadingValueReference<K, V> loadingValueReference) {
            lock();
            try {
                AtomicReferenceArray<ReferenceEntry<K, V>> table = this.table;
                int index = hash & (table.length() - 1);
                ReferenceEntry<K, V> first = table.get(index);

                for (ReferenceEntry<K, V> e = first; e != null; e = e.getNext()) {
                    K entry = e.getKey();
                    if(equalsKey(key,entry)){
                        ValueReference<K,V> v = e.getValueReference();
                        if(v==loadingValueReference){
                            if(loadingValueReference.isActive()){
                                e.setValueReference(loadingValueReference.oldValue);
                            }else {
                                ReferenceEntry<K,V> newFirst = removeEntryFromChain(first,e);
                                table.set(index,newFirst);
                            }
                            return true;
                        }else {
                            return false;
                        }
                    }
                }
                return false;
            }finally {
                unlock();
                postWriteCleanup();
            }
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

        private void postReadCleanup() {
            if ((readCount.incrementAndGet() & map.DRAIN_THRESHOLD) == 0) {
                cleanUp();
            }
        }

        private void cleanUp() {
            long now = map.ticker.read();
            runCleanup(now);
        }

        private void runCleanup(long now) {
            if (tryLock()) {
                try {
                    expireEntries(now);
                    readCount.set(0);
                } finally {
                    unlock();
                }
            }
        }

        @GuardedBy("this")
        private void expireEntries(long now) {
            //将recency的缓存追加到queue中,这是一个锁方法
            drainRecencyQueue();
            ReferenceEntry<K, V> e;
            while ((e = writeQueue.peek()) != null && map.isExpired(e, now)) {
                if (!removeEntry(e, e.getHash())) {
                    throw new AssertionError();
                }
            }
            while ((e = accessQueue.peek()) != null && map.isExpired(e, now)) {
                if (!removeEntry(e, e.getHash())) {
                    throw new AssertionError();
                }
            }
        }

        @GuardedBy("this")
        private boolean removeEntry(ReferenceEntry<K, V> entry, int hash) {
            int newCount = this.count - 1;
            AtomicReferenceArray<ReferenceEntry<K, V>> table = this.table;
            int index = hash & (table.length() - 1);
            ReferenceEntry<K, V> first = table.get(index);
            for (ReferenceEntry<K, V> e = first; e != null; e = e.getNext()) {
                //todo 原code用的是地址相等
                if (e.getKey().equals(entry.getKey())) {
                    modCount++;
                    ReferenceEntry<K, V> newFirst = removeValueFromChain(
                            first,
                            e,
                            e.getKey(),
                            hash,
                            e.getValueReference().get(),
                            e.getValueReference()
                    );
                    newCount = this.count - 1;
                    table.set(index, newFirst);
                    this.count = newCount;
                    return true;
                }
            }
            return false;
        }

        @GuardedBy("this")
        private ReferenceEntry<K, V> removeValueFromChain(
                ReferenceEntry<K, V> first,
                ReferenceEntry<K, V> entry,
                K key,
                int hash,
                V Value,
                ValueReference<K, V> valueReference) {
            writeQueue.remove(entry);
            accessQueue.remove(entry);
            return removeEntryFromChain(first, entry);
        }

        private ReferenceEntry<K, V> removeEntryFromChain(ReferenceEntry<K, V> first,
                                                          ReferenceEntry<K, V> entry) {
            int newCount = count;
            ReferenceEntry<K, V> newFirst = entry.getNext();
            for (ReferenceEntry<K, V> e = first; e != entry; e = e.getNext()) {
                ReferenceEntry<K, V> next = copyEntry(e, newFirst);
                newFirst = next;
            }
            this.count = newCount;
            return newFirst;
        }

        /**
         * 首先recency是一个并发的容器，get后数据首先来到这里
         * accessQueue是一个队列，在put的时候会加入元素，先入先出记录最老的缓存，过期的时候从头遍历进行清理
         */
        private void drainRecencyQueue() {
            ReferenceEntry<K, V> e;
            while ((e = recencyQueue.poll()) != null) {
                if (accessQueue.contains(e)) {
                    accessQueue.add(e);
                }
            }
        }

        private void drainReferenceQueues() {

        }

        private void recordRead(ReferenceEntry<K, V> entry, long now) {
            if (map.recordsAccess()) {
                entry.setAccessTime(now);
            }
            recencyQueue.add(entry);
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


        private void tryDrainReferenceQueues() {

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
                if (equalsKey(key,entryKey)) {
                    return e;
                }
            }
            return null;
        }

        ReferenceEntry<K, V> getFirst(int hash) {
            AtomicReferenceArray<ReferenceEntry<K, V>> table = this.table;
            return table.get(hash & (table.length() - 1));
        }

        public V put(K key, int hash, V value,boolean onlyIfAbsent) {
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
                    if (e.getHash() == hash && entryKey != null && entryKey.equals(key)) {
                        ValueReference<K, V> valueReference = e.getValueReference();
                        V entryValue = valueReference.get();
                        if(entryValue==null){
                            modCount++;
                            if(valueReference.isActive()){
                                enqueueNotification();
                                setValue(e,key,value,now);
                                newCount = this.count;
                            }else {
                                setValue(e,key,value,now);
                                newCount = this.count+1;
                            }
                            this.count = newCount;
                            evictEntries(e);
                            return null;
                        }else if(onlyIfAbsent){
                            recordLockedRead(e,now);
                            return entryValue;
                        }else {
                            modCount++;
                            enqueueNotification();
                            setValue(e,key,value,now);
                            evictEntries(e);
                            return entryValue;
                        }
                    }
                }
                modCount++;
                ReferenceEntry<K, V> newEntry = newEntry(key, hash, first);
                setValue(newEntry, key, value, now);
                table.set(index, newEntry);
                newCount = this.count+1;
                this.count = newCount;
                evictEntries(newEntry);
                return null;
            } finally {
                unlock();
                postWriteCleanup();
            }
        }

        @GuardedBy("this")
        private void evictEntries(ReferenceEntry<K, V> e) {
            drainRecencyQueue();
        }

        private void postWriteCleanup() {

        }

        ReferenceEntry<K, V> newEntry(K key, int hash, ReferenceEntry<K, V> next) {
            return new StrongEntry<>(key, hash, next);
        }

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

        /**
         * 用强entry代替
         */
        private ReferenceEntry<K, V> copyEntry(ReferenceEntry<K, V> original, ReferenceEntry<K, V> newNext) {
            ReferenceEntry<K, V> newEntry = new StrongEntry<>(original.getKey(), original.getHash(), newNext);
            newEntry.setValueReference(original.getValueReference());
            return newEntry;
        }

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
                    if (equalsKey(key, entryKey)) {
                        ValueReference<K, V> valueReference = e.getValueReference();
                        V entryValue = valueReference.get();
                        if (entryValue != null) {
                            modCount++;
                            ReferenceEntry<K, V> newFirst = removeValueFromChain(first, e, entryKey, hash, entryValue, valueReference);
                            newCount = this.count - 1;
                            table.set(index, newFirst);
                            this.count = newCount;
                            return entryValue;
                        } else {
                            return null;
                        }
                    }
                }
                return null;
            } finally {
                unlock();
                postWriteCleanup();
            }
        }


        private boolean equalsKey(Object key, K entryKey) {
            return key.equals(entryKey);
        }

        @GuardedBy("this")
        private void preWriteCleanup(long now) {
            runLockedCleanup(now);
        }

        private void runLockedCleanup(long now) {
            if(tryLock()){
                try {
                    drainReferenceQueues();
                    expireEntries(now);
                    readCount.set(0);
                }finally {
                    unlock();
                }
            }
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
                            ReferenceEntry<K, V> newFirst = removeValueFromChain(first, e, entryKey, hash, entryValue, valueReference);
                            newCount = this.count - 1;
                            table.set(index, newFirst);
                            this.count = newCount;
                            return null;
                        } else {
                            modCount++;
                            setValue(e, key, newValue, now);
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

        private void setValue(ReferenceEntry<K, V> e, K key, V newValue, long now) {
            ValueReference<K, V> valueReference = new StrongValueReference<>(newValue);
            e.setValueReference(valueReference);
            recordWrite(e, now);
        }

        private void recordWrite(ReferenceEntry<K, V> e, long now) {
            drainRecencyQueue();
            if (map.recordsAccess()) {
                e.setAccessTime(now);
            }
            if (map.recordsWrite()) {
                e.setWriteTime(now);
            }
            accessQueue.add(e);
            writeQueue.add(e);
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
                        enqueueNotification();
                    }
                }
                for (int i = 0; i < table.length(); i++) {
                    table.set(i, null);
                }
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

        private void enqueueNotification() {

        }
    }

    private boolean useAccessQueue() {
        return expiresAfterAccess();
    }

    private boolean useWriteQueue(){
        return expiresAfterWrite();
    }

    private boolean recordsWrite() {
        return expiresAfterWrite();
    }

    private boolean recordsAccess() {
        return expiresAfterAccess();
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
                    if (v != null && equalsValue(value, v)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    boolean equalsValue(Object v1, V v2) {
        return true;
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

    public void cleanUp() {
        for (Segment<K, V> segment : segments) {
            segment.cleanUp();
        }
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
