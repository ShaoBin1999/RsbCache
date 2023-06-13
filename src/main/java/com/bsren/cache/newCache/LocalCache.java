package com.bsren.cache.newCache;


import com.bsren.cache.abstractCache.AbstractCache;
import com.bsren.cache.abstractCache.AbstractReferenceEntry;
import com.bsren.cache.abstractCache.StrongEntry;
import com.bsren.cache.abstractCache.StrongValueReference;
import com.google.common.base.Ticker;

import java.security.PublicKey;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkNotNull;


/**
 * 1.put
 * 2.get
 * 3.扩容
 * 4.设置读超时和写超时
 * 5.过期回收
 */
public class LocalCache<K, V> {

    static final int MAXIMUM_CAPACITY = 1 << 30;

    final Segment<K, V>[] segments;

    int segmentMask;

    long expireAfterAccessNanos;

    AbstractCache.StatsCounter globalStatsCounter;

    long expireAfterWriteNanos;

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

    boolean expiresAfterWrite() {
        return expireAfterWriteNanos > 0;
    }

    boolean expiresAfterAccess() {
        return expireAfterAccessNanos > 0;
    }

    Ticker ticker;

    LocalCache(int initialCapacity, int segmentCount) {
        this.ticker = Ticker.systemTicker();
        this.segments = newSegmentArray(segmentCount);
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
        return segmentFor(hash).put(key, hash, value);
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
            accessQueue = new LinkedList<>();
            writeQueue = new LinkedList<>();
            recencyQueue = new LinkedList<>();
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
                        return value;
                    }
                }
                return null;
            } finally {
                postReadCleanup();
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

        private void expireEntries(long now) {
            drainRecencyQueue();
            ReferenceEntry<K, V> e;
            while ((e = writeQueue.peek()) != null && map.isExpired(e, now)) {
                if (!removeEntry(e, e.getHash())) {
                    throw new AssertionError();
                }
                ;
            }
            while ((e = accessQueue.peek()) != null && map.isExpired(e, now)) {
                if (!removeEntry(e, e.getHash())) {
                    throw new AssertionError();
                }
                ;
            }
        }

        private boolean removeEntry(ReferenceEntry<K, V> entry, int hash) {
            int newCount = this.count - 1;
            AtomicReferenceArray<ReferenceEntry<K, V>> table = this.table;
            int index = hash & (table.length() - 1);
            ReferenceEntry<K, V> first = table.get(index);
            for (ReferenceEntry<K, V> e = first; e != null; e = e.getNext()) {
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

        //todo 非常不理解
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
                    continue;
                }
                if (key.equals(entryKey)) {
                    return e;
                }
            }
            return null;
        }

        ReferenceEntry<K, V> getFirst(int hash) {
            AtomicReferenceArray<ReferenceEntry<K, V>> table = this.table;
            return table.get(hash & (table.length() - 1));
        }

        public V put(K key, int hash, V value) {
            lock();
            try {
                long now = map.ticker.read();
                int newCount = this.count + 1;
                if (newCount > this.threshold) {
                    expand();
                    newCount = this.count + 1;
                }


                ReferenceEntry<K, V>[] table = (ReferenceEntry<K, V>[]) this.table;
                int index = hash & (table.length - 1);
                ReferenceEntry<K, V> first = table[index];
                for (ReferenceEntry<K, V> e = first; e != null; e = e.getNext()) {
                    K entryKey = e.getKey();
                    //find an existing value
                    if (e.getHash() == hash && entryKey != null && entryKey.equals(key)) {
                        Value<K, V> preValue = e.getValue();
                        V v = preValue.get();
                        e.setValue(new com.bsren.cache.cache3.StrongValue<>(value));
                        e.setAccessTime(now);
                        e.setAccessTime(now);
                        writeQueue.add(e);
                        accessQueue.add(e);
                        return v;
                    }
                }
                ReferenceEntry<K, V> newEntry = newEntry(key, hash, first);
                newEntry.setValue(new StrongValue<>(value));
                newEntry.setWriteTime(now);
                newEntry.setAccessTime(now);
                writeQueue.add(newEntry);
                accessQueue.add(newEntry);
                table[index] = newEntry;
                this.count = newCount;
                return null;
            } finally {
                unlock();
                postWriteCleanup();
            }
        }

        private void postWriteCleanup() {

        }

        ReferenceEntry<K, V> newEntry(K key, int hash, ReferenceEntry<K, V> next) {
            return new StrongEntry<>(key, hash, next);
        }

        void expand() {
            Object[] oldTable = table;
            int oldCapacity = oldTable.length;
            if (oldCapacity >= MAXIMUM_CAPACITY) {
                return;
            }
            int newCount = count;
            Object[] newTable = newEntryArray(oldCapacity << 1);
            threshold = newTable.length * 3 / 4;
            int newMask = newTable.length - 1;
            for (int oldIndex = 0; oldIndex < oldCapacity; ++oldIndex) {
                ReferenceEntry<K, V> head = (ReferenceEntry<K, V>) oldTable[oldIndex];
                if (head != null) {
                    ReferenceEntry<K, V> next = head.getNext();
                    int headIndex = head.getHash() & newMask;
                    if (next == null) {
                        newTable[headIndex] = head;
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
                        newTable[tailIndex] = tail;
                        for (ReferenceEntry<K, V> e = head; e != tail; e = e.getNext()) {
                            int newIndex = e.getHash() & newMask;
                            ReferenceEntry<K, V> newNext = (ReferenceEntry<K, V>) newTable[newIndex];
                            ReferenceEntry<K, V> newFirst = copyEntry(e, newNext);
                            newTable[newIndex] = newFirst;
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
                    if(equalsKey(key,entryKey)){
                        ValueReference<K,V> valueReference = e.getValueReference();
                        V entryValue = valueReference.get();
                        if(entryValue!=null){
                            modCount++;
                            ReferenceEntry<K, V> newFirst = removeValueFromChain(first, e, entryKey, hash, entryValue, valueReference);
                            newCount = this.count-1;
                            table.set(index,newFirst);
                            this.count = newCount;
                            return entryValue;
                        }else {
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
            return true;
        }

        private void preWriteCleanup(long now) {


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
                    if(equalsKey(key,entryKey)){
                        ValueReference<K, V> valueReference = e.getValueReference();
                        V entryValue = valueReference.get();
                        if(entryValue==null){
                            int newCount = this.count - 1;
                            modCount++;
                            ReferenceEntry<K, V> newFirst = removeValueFromChain(first, e, entryKey, hash, entryValue, valueReference);
                            newCount = this.count-1;
                            table.set(index,newFirst);
                            this.count = newCount;
                            return null;
                        }

                        else {
                            modCount++;
                            setValue(e,key,newValue,now);
                            evictEntries(e);
                            return entryValue;
                        }
                    }
                }
                return null;
            }finally {
                postWriteCleanup();
                unlock();
            }
        }

        private void setValue(ReferenceEntry<K,V> e, K key, V newValue, long now) {
            ValueReference<K,V> valueReference = new StrongValueReference<>(newValue);
            e.setValueReference(valueReference);
            recordWrite(e,now);
        }

        private void recordWrite(ReferenceEntry<K,V> e, long now) {
            drainRecencyQueue();
            if(map.recordsAccess()){
                e.setAccessTime(now);
            }
            if(map.recordsWrite()){
                e.setWriteTime(now);
            }
            accessQueue.add(e);
            writeQueue.add(e);
        }
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

    public V replace(K key, V value){
        checkNotNull(key);
        checkNotNull(value);
        int hash = hash(key);
        return segmentFor(hash).replace(key,hash,value);
    }


}
