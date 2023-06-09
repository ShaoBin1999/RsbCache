package com.bsren.cache.cache5;


import com.bsren.cache.cache5.entry.StrongEntry;
import com.google.common.base.Ticker;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.ref.ReferenceQueue;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static com.bsren.cache.cache5.entry.NullReferenceEntry.nullEntry;
import static com.google.common.base.Preconditions.checkNotNull;


/**
 * 1.put
 * 2.get
 * 3.扩容
 * 4.设置读超时和写超时
 * 5.过期回收
 * 6.限制缓存数量
 * 7.remove
 */
public class LocalCache<K,V> {

    static final int MAXIMUM_CAPACITY = 1 << 30;

    final Segment<K,V>[] segments;

    int segmentMask;

    long expireAfterAccessNanos;

    long expireAfterWriteNanos;

    static final int UNSET_INT = -1;

    Weigher<K,V> weigher;

    long maxWeight;

    boolean evictsBySize() {
        return maxWeight >= 0;
    }


    long getSize(){
        Segment<K,V>[] segment = this.segments;
        long sum = 0;
        for (Segment<K, V> kvSegment : segment) {
            sum+=kvSegment.count;
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


    enum OneWeigher implements Weigher<Object, Object> {
        INSTANCE;

        @Override
        public int weigh(Object key, Object value) {
            return 1;
        }
    }

    LocalCache(int initialCapacity,int segmentCount,int maxWeight){
        this.weigher = (Weigher<K, V>) OneWeigher.INSTANCE;
        this.ticker = Ticker.systemTicker();
        this.segments = newSegmentArray(segmentCount);
        this.maxWeight = maxWeight;
        if(evictsBySize()){
            initialCapacity = Math.min(initialCapacity,maxWeight);
        }
        int segmentCapacity = initialCapacity/segmentCount;
        if(segmentCapacity*segmentCount<initialCapacity){
            segmentCapacity++;
        }
        segmentMask = segmentCount-1;
        int segmentSize = 1;
        while (segmentSize<segmentCapacity){
            segmentSize<<=1;
        }
        if(evictsBySize()){
            long maxSegmentWeight = maxWeight/segmentCount+1;
            long remainder = maxWeight % segmentCount;
            for (int i=0;i<this.segments.length;i++){
                if(i==remainder){
                    maxSegmentWeight--;
                }
                this.segments[i] = createSegment(segmentSize,maxSegmentWeight);
            }
        }
        for (int i=0;i<this.segments.length;i++){
            segments[i] = createSegment(segmentSize,UNSET_INT);
        }
    }

    Segment<K,V> createSegment(int initialCapacity,long maxSegmentWeight ) {
        return new Segment<>(this,initialCapacity,maxSegmentWeight);
    }

    final Segment<K,V>[] newSegmentArray(int size){
        return new Segment[size];
    }

    public V get(Object key){
        if(key==null){
            return null;
        }
        int hash = hash(key);
        return segmentFor(hash).get(key,hash);
    }

    public V put(K key,V value){
        int hash = hash(key);
        return segmentFor(hash).put(key,hash,value);
    }




    Segment<K,V> segmentFor(int hash){
        return segments[hash & segmentMask];
    }

    int hash(Object key){
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

    public V remove(Object key){
        if(key==null){
            return null;
        }
        int hash = hash(key);
        return segmentFor(hash).remove(key,hash);
    }

    public boolean remove(Object key,Object value){
        if(key==null || value==null){
            return false;
        }
        int hash = hash(key);
        return segmentFor(hash).remove(key,hash,value);
    }

    static <K, V> void connectAccessOrder(ReferenceEntry<K, V> previous, ReferenceEntry<K, V> next) {
        previous.setNextInAccessQueue(next);
        next.setPreviousInAccessQueue(previous);
    }

    static <K, V> void connectWriteOrder(ReferenceEntry<K, V> previous,ReferenceEntry<K, V> next) {
        previous.setNextInWriteQueue(next);
        next.setPreviousInWriteQueue(previous);
    }

    static <K, V> void nullifyWriteOrder(ReferenceEntry<K, V> nulled) {
        ReferenceEntry<K, V> nullEntry = nullEntry();
        nulled.setNextInWriteQueue(nullEntry);
        nulled.setPreviousInWriteQueue(nullEntry);
    }

    static <K, V> void nullifyAccessOrder(ReferenceEntry<K, V> nulled) {
        ReferenceEntry<K, V> nullEntry = nullEntry();
        nulled.setNextInAccessQueue(nullEntry);
        nulled.setPreviousInAccessQueue(nullEntry);
    }

    static class Segment<K,V> extends ReentrantLock {


        ReferenceQueue<K> keyReferenceQueue;

        ReferenceQueue<V> valueReferenceQueue;

        final LocalCache<K,V> map;

        volatile Object[] table;

        int threshold;

        volatile int count;

        Queue<ReferenceEntry<K,V>> recencyQueue;

        AtomicInteger readCount = new AtomicInteger();

        Queue<ReferenceEntry<K, V>> accessQueue;

        Queue<ReferenceEntry<K,V>> writeQueue;

        long maxSegmentWeight;

        long totalWeight;

        int modCount;

        Segment(LocalCache<K,V> map,
                int initialCapacity,
                long maxSegmentWeight){
            this.map = map;
            this.maxSegmentWeight = maxSegmentWeight;
            initTable(newEntryArray(initialCapacity));
            accessQueue = new LinkedList<>();
            writeQueue = new LinkedList<>();
            recencyQueue = new LinkedList<>();
        }

        private void initTable(Object[] newEntryArray) {
            this.threshold = newEntryArray.length*3/4;
            this.table = newEntryArray;
        }

        ReferenceEntry<K,V>[] newEntryArray(int size){
            return new ReferenceEntry[size];
        }


        V get(Object key,int hash){
            try {
                if(count!=0){
                    long now = map.ticker.read();
                    ReferenceEntry<K,V> e = getLiveEntry(key,hash,now);
                    if(e == null){
                        return null;
                    }
                    ValueReference<K,V> value = e.getValueReference();
                    if(value!=null){
                        recordRead(e,now);
                        return value.get();
                    }
//                    tryDrainReferenceQueues();
                }
                return null;
            }finally {
                postReadCleanup();
            }
        }

        private void postReadCleanup() {
            if((readCount.incrementAndGet() & map.DRAIN_THRESHOLD)==0){
                cleanUp();
            }
        }

        private void cleanUp() {
            long now = map.ticker.read();
            runCleanup(now);
        }

        private void runCleanup(long now) {
            if(tryLock()){
                try {
//                    drainReferenceQueues();
                    expireEntries(now);
                    readCount.set(0);
                }finally {
                    unlock();
                }
            }
        }

        private void expireEntries(long now) {
            drainRecencyQueue();
            ReferenceEntry<K,V> e;
            while ((e = writeQueue.peek())!=null && map.isExpired(e,now)){
                if(!removeEntry(e,e.getHash(), RemovalCause.EXPIRED)){
                    break;
                };
            }
            while ((e = accessQueue.peek())!=null && map.isExpired(e,now)){
                if(!removeEntry(e,e.getHash(), RemovalCause.EXPIRED)){
                    break;
                };
            }
        }

        private boolean removeEntry(ReferenceEntry<K,V> referenceEntry, int hash, RemovalCause cause) {
            int newCount = this.count - 1;
            ReferenceEntry<K,V>[] table = (ReferenceEntry<K, V>[]) this.table;
            int index = hash & (table.length-1);
            ReferenceEntry<K,V> first = table[index];
            for (ReferenceEntry<K,V> e = first; e!=null; e = e.getNext()){
                if(e.getKey()== referenceEntry.getKey()){
                    ReferenceEntry<K,V> newFirst = removeValueFromChain(
                            first,
                            e,
                            e.getKey(),
                            hash,
                            e.getValueReference().get(),
                            e.getValueReference(),
                            cause
                    );
                    newCount = this.count-1;
                    table[index] = newFirst;
                    this.count = newCount;
                    return true;
                }
            }
            return false;
        }

        private ReferenceEntry<K,V> removeValueFromChain(
                ReferenceEntry<K,V> first,
                ReferenceEntry<K,V> referenceEntry,
                K key,
                int hash,
                V v,
                ValueReference<K,V> value,
                RemovalCause removalCause
        ) {
            writeQueue.remove(referenceEntry);
            accessQueue.remove(referenceEntry);
            return removeEntryFromChain(first, referenceEntry);
        }

        private ReferenceEntry<K,V> removeEntryFromChain(ReferenceEntry<K,V> first, ReferenceEntry<K,V> referenceEntry) {
            int newCount = count;
            ReferenceEntry<K,V> newFirst = referenceEntry.getNext();
            for (ReferenceEntry<K,V> e = first; e!= referenceEntry; e=e.getNext()){
                ReferenceEntry<K,V> next = copyEntry(e,newFirst);
                newFirst = next;
                //todo
            }
            this.count = newCount;
            return newFirst;
        }

        //todo 非常不理解
        private void drainRecencyQueue() {
            ReferenceEntry<K,V> e;
            while ((e = recencyQueue.poll())!=null){
                if(accessQueue.contains(e)){
                    accessQueue.add(e);
                }
            }
        }

        private void drainReferenceQueues() {

        }

        private void recordRead(ReferenceEntry<K,V> referenceEntry, long now) {
            if(map.recordsAccess()){
                referenceEntry.setAccessTime(now);
            }
            recencyQueue.add(referenceEntry);
        }

        private ReferenceEntry<K,V> getLiveEntry(Object key, int hash, long now) {
            ReferenceEntry<K, V> referenceEntry = getEntry(key, hash);
            if(referenceEntry ==null){
                return null;
            }else if(map.isExpired(referenceEntry,now)){
                tryExpireEntries(now);
                return null;
            }
            return referenceEntry;
        }

        private void tryExpireEntries(long now) {
            if(tryLock()){
                try {
                    expireEntries(now);
                }finally {
                    unlock();
                }
            }
        }

        /**
         * 根据key和hash到map中获取entry
         */
        ReferenceEntry<K,V> getEntry(Object key, int hash){
            for (ReferenceEntry<K,V> e = getFirst(hash); e!=null; e = e.getNext()){
                if(e.getHash()!=hash){
                    continue;
                }
                K entryKey = e.getKey();
                if(entryKey==null){
                    continue;
                }
                if(key.equals(entryKey)){
                    return e;
                }
            }
            return null;
        }

        ReferenceEntry<K,V> getFirst(int hash){
            Object[] table = this.table;
            return (ReferenceEntry<K, V>) table[hash & (table.length-1)];
        }

        public V put(K key, int hash, V value) {
            lock();
            try {
                long now = map.ticker.read();
                int newCount = this.count+1;
                if(newCount>this.threshold){
                    expand();
                    newCount = this.count+1;
                }


                ReferenceEntry<K,V>[] table = (ReferenceEntry<K, V>[]) this.table;
                int index = hash & (table.length-1);
                ReferenceEntry<K,V> first = table[index];
                for (ReferenceEntry<K,V> e = first; e!=null; e = e.getNext()){
                    K entryKey = e.getKey();
                    //find an existing value
                    if(e.getHash() == hash && entryKey!=null && entryKey.equals(key)){
                        ValueReference<K, V> preValue = e.getValueReference();
                        V v = preValue.get();
                        e.setValueReference(new StrongValueReference<>(value));
                        e.setAccessTime(now);
                        e.setAccessTime(now);
                        totalWeight += this .map.weigher.weigh(key,value);
                        writeQueue.add(e);
                        accessQueue.add(e);
                        return v;
                    }
                }
                ReferenceEntry<K, V> newReferenceEntry = newEntry(key, hash, first);
                newReferenceEntry.setValueReference(new StrongValueReference<>(value));
                newReferenceEntry.setWriteTime(now);
                newReferenceEntry.setAccessTime(now);
                writeQueue.add(newReferenceEntry);
                accessQueue.add(newReferenceEntry);
                table[index] = newReferenceEntry;
                this.count = newCount;
                evictEntries(newReferenceEntry);
                return null;
            }finally {
                unlock();
                postWriteCleanup();
            }
        }

        private void evictEntries(ReferenceEntry<K,V> newReferenceEntry) {
            if(!map.evictsBySize()){
                return;
            }
            drainRecencyQueue();
            if(newReferenceEntry.getValueReference().getWeight()>maxSegmentWeight){
                if(!removeEntry(newReferenceEntry, newReferenceEntry.getHash(),
                        RemovalCause.SIZE)){
                    throw new AssertionError();
                }
            }
            while (totalWeight>maxSegmentWeight){
                ReferenceEntry<K,V> e = getNextEvictable();
                if(!removeEntry(e,e.getHash(), RemovalCause.SIZE)){
                    throw new AssertionError();
                }
            }

        }

        ReferenceEntry<K,V> getNextEvictable(){
            for (ReferenceEntry<K, V> e : accessQueue) {
                int weight = e.getValueReference().getWeight();
                if(weight>0){
                    return e;
                }
            }
            throw new AssertionError();
        }

        private void postWriteCleanup() {

        }

        ReferenceEntry<K,V> newEntry(K key, int hash, ReferenceEntry<K,V> next){
            return new StrongEntry<>(key,hash,next);
        }

        void expand(){
            Object[] oldTable = table;
            int oldCapacity = oldTable.length;
            if(oldCapacity>=MAXIMUM_CAPACITY){
                return;
            }
            int newCount = count;
            Object[] newTable = newEntryArray(oldCapacity << 1);
            threshold = newTable.length * 3/4;
            int newMask = newTable.length-1;
            for (int oldIndex = 0;oldIndex<oldCapacity;++oldIndex){
                ReferenceEntry<K,V> head = (ReferenceEntry<K,V>)oldTable[oldIndex];
                if(head!=null){
                    ReferenceEntry<K, V> next = head.getNext();
                    int headIndex = head.getHash() & newMask;
                    if(next==null){
                        newTable[headIndex] = head;
                    }else {
                        //这里的想法是可能会有一串子在扩容后相同的index，挂在链的尾部
                        //算是一个小小的优化吧，感觉不是很明显
                        ReferenceEntry<K,V> tail = head;
                        int tailIndex = headIndex;
                        for (ReferenceEntry<K,V> e = next; e!=null; e = e.getNext()){
                            int newIndex = e.getHash() & newMask;
                            if(newIndex!=tailIndex){
                                tailIndex = newIndex;
                                tail = e;
                            }
                        }
                        newTable[tailIndex] = tail;
                        for (ReferenceEntry<K,V> e = head; e!=tail; e = e.getNext()){
                            int newIndex = e.getHash() & newMask;
                            ReferenceEntry<K,V> newNext = (ReferenceEntry<K, V>) newTable[newIndex];
                            ReferenceEntry<K,V> newFirst = copyEntry(e,newNext);
                            newTable[newIndex] = newFirst;
                        }
                    }
                }
            }
            table = newTable;
            this.count = newCount;
        }

        private ReferenceEntry<K,V> copyEntry(ReferenceEntry<K,V> original, ReferenceEntry<K,V> newNext) {
            ReferenceEntry<K,V> newReferenceEntry = new StrongEntry<>(original.getKey(),original.getHash(),newNext);
            newReferenceEntry.setValueReference(original.getValueReference());
            return newReferenceEntry;
        }

        public V remove(Object key, int hash) {
            lock();
            try {
                long now = map.ticker.read();
                preWriteCleanup(now);
                int newCount = this.count-1;
                ReferenceEntry<K,V>[] table = (ReferenceEntry<K, V>[]) this.table;
                int index = hash & (table.length-1);
                ReferenceEntry<K,V> first = table[index];
                for (ReferenceEntry<K,V> e = first; e!=null; e = e.getNext()){
                    K entryKey = e.getKey();
                    if (e.getHash() == hash
                            && key.equals(entryKey)){
                        ValueReference<K, V> value = e.getValueReference();
                        V entryValue = value.get();
                        RemovalCause removalCause;
                        if(entryValue!=null){
                            removalCause = RemovalCause.EXPLICIT;
                        }else{
                            return null;
                        }
                        modCount++;
                        ReferenceEntry<K,V> newFirst = removeValueFromChain(first,e,entryKey,hash,
                               entryValue,value,removalCause);
                        newCount = this.count-1;
                        table[index] = newFirst;
                        this.count = newCount;
                        return entryValue;
                    }
                }
                return null;

            }finally {
                unlock();
            }
        }

        boolean remove(Object key, int hash, Object value){
            lock();
            try {
                long now = map.ticker.read();
                preWriteCleanup(now);
                int newCount = this.count-1;
                ReferenceEntry<K,V>[] table = (ReferenceEntry<K, V>[]) this.table;
                int index = hash & (table.length-1);
                ReferenceEntry<K,V> first = table[index];
                for (ReferenceEntry<K,V> e = first; e!=null; e = e.getNext()){
                    K entryKey = e.getKey();
                    if(e.getHash() == hash
                    && entryKey!=null
                    && key.equals(entryKey)){
                        ValueReference<K, V> eValueReference = e.getValueReference();
                        V v = eValueReference.get();
                        RemovalCause cause = RemovalCause.EXPLICIT;
                        ++modCount;
                        ReferenceEntry<K, V> newFirst = removeValueFromChain(first, e, entryKey, hash, v, eValueReference, cause);
                        newCount = this.count-1;
                        table[index] = newFirst;
                        this.count = newCount;
                        return cause== RemovalCause.EXPLICIT;
                    }
                }
                return false;
            }finally {
                unlock();
                postWriteCleanup();
            }
        }

        private void preWriteCleanup(long now) {

        }

        void clear(){
            if(count==0){
                return;
            }
            lock();
            try {
                long now = map.ticker.read();
                preWriteCleanup(now);
                ReferenceEntry<K,V>[] table = (ReferenceEntry<K, V>[]) this.table;
                for (int i=0;i<table.length;i++){
                    table[i] = null;
                }

                writeQueue.clear();;
                accessQueue.clear();
                readCount.set(0);
                ++modCount;
                count=0;
            }finally {
                unlock();
                postWriteCleanup();
            }
        }
    }

    private boolean recordsAccess() {
        return expiresAfterAccess();
    }

    private boolean isExpired(ReferenceEntry<K, V> referenceEntry, long now) {
        checkNotNull(referenceEntry);
        if(expiresAfterAccess() && (now - referenceEntry.getAccessTime())>expireAfterAccessNanos){
            return true;
        }
        if(expiresAfterWrite() && (now - referenceEntry.getWriteTime()>expireAfterWriteNanos)){
            return true;
        }
        return false;
    }


}
