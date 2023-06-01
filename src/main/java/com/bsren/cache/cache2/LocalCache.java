package com.bsren.cache.cache2;


import java.util.concurrent.locks.ReentrantLock;


/**
 * 1.put
 * 2.get
 * 3.扩容
 */
public class LocalCache<K,V> {

    static final int MAXIMUM_CAPACITY = 1 << 30;

    final Segment<K,V>[] segments;

    int segmentMask;

    LocalCache(int initialCapacity,int segmentCount){
        this.segments = newSegmentArray(segmentCount);
        int segmentCapacity = initialCapacity/segmentCount;
        if(segmentCapacity*segmentCount<initialCapacity){
            segmentCapacity++;
        }
        segmentMask = segmentCount-1;
        int segmentSize = 1;
        while (segmentSize<segmentCapacity){
            segmentSize<<=1;
        }
        for (int i=0;i<this.segments.length;i++){
            segments[i] = createSegment(segmentSize);
        }
    }

    Segment<K,V> createSegment(int initialCapacity) {
        return new Segment<>(this,initialCapacity);
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



    static class Segment<K,V> extends ReentrantLock {

        final LocalCache<K,V> map;

        volatile Object[] table;

        int threshold;

        volatile int count;

        Segment(LocalCache<K,V> map,
                int initialCapacity){
            this.map = map;
            initTable(newEntryArray(initialCapacity));
        }

        private void initTable(Object[] newEntryArray) {
            this.threshold = newEntryArray.length*3/4;
            this.table = newEntryArray;
        }

        Entry<K,V>[] newEntryArray(int size){
            return new Entry[size];
        }


        V get(Object key,int hash){
            if(count!=0){
                Entry<K,V> e = getLiveEntry(key,hash);
                if(e == null){
                    return null;
                }
                Value<K,V> value = e.getValue();
                if(value!=null){
                    return value.get();
                }
            }
            return null;
        }

        private Entry<K,V> getLiveEntry(Object key, int hash) {
            Entry<K, V> entry = getEntry(key, hash);
            if(entry==null){
                return null;
            }
            return entry;
        }

        /**
         * 根据key和hash到map中获取entry
         */
        Entry<K,V> getEntry(Object key, int hash){
            for (Entry<K,V> e = getFirst(hash);e!=null;e = e.getNext()){
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

        Entry<K,V> getFirst(int hash){
            Object[] table = this.table;
            return (Entry<K, V>) table[hash & (table.length-1)];
        }

        public V put(K key, int hash, V value) {
            int newCount = this.count+1;
            if(newCount>this.threshold){
                expand();
            }
            Entry<K,V>[] table = (Entry<K, V>[]) this.table;
            int index = hash & (table.length-1);
            Entry<K,V> first = table[index];
            for (Entry<K,V> e = first;e!=null;e = e.getNext()){
                K entryKey = e.getKey();
                if(e.getHash() == hash && entryKey!=null && entryKey.equals(key)){
                    Value<K, V> preValue = e.getValue();
                    V v = preValue.get();
                    e.setValue(new StrongValue<>(value));
                    this.count = newCount;
                    return v;
                }
            }
            Entry<K, V> newEntry = newEntry(key, hash, first);
            newEntry.setValue(new StrongValue<>(value));
            table[index] = newEntry;
            this.count = newCount;
            return null;
        }

        Entry<K,V> newEntry(K key,int hash,Entry<K,V> next){
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
                Entry<K,V> head = (Entry<K,V>)oldTable[oldIndex];
                if(head!=null){
                    Entry<K, V> next = head.getNext();
                    int headIndex = head.getHash() & newMask;
                    if(next==null){
                        newTable[headIndex] = head;
                    }else {
                        //这里的想法是可能会有一串子在扩容后相同的index，挂在链的尾部
                        //算是一个小小的优化吧，感觉不是很明显
                        Entry<K,V> tail = head;
                        int tailIndex = headIndex;
                        for (Entry<K,V> e = next;e!=null;e = e.getNext()){
                            int newIndex = e.getHash() & newMask;
                            if(newIndex!=tailIndex){
                                tailIndex = newIndex;
                                tail = e;
                            }
                        }
                        newTable[tailIndex] = tail;
                        for (Entry<K,V> e = head;e!=tail;e = e.getNext()){
                            int newIndex = e.getHash() & newMask;
                            Entry<K,V> newNext = (Entry<K, V>) newTable[newIndex];
                            Entry<K,V> newFirst = copyEntry(e,newNext);
                            newTable[newIndex] = newFirst;
                        }
                    }
                }
            }
            table = newTable;
            this.count = newCount;
        }

        private Entry<K,V> copyEntry(Entry<K,V> original, Entry<K,V> newNext) {
            Entry<K,V> newEntry = new StrongEntry<>(original.getKey(),original.getHash(),newNext);
            newEntry.setValue(original.getValue());
            return newEntry;
        }
    }


}
