package com.bsren.cache.entry;


import com.bsren.cache.ReferenceEntry;
import com.google.j2objc.annotations.Weak;

import java.lang.ref.ReferenceQueue;

import static com.bsren.cache.entry.NullEntry.nullEntry;


public class WeakAccessEntry<K, V> extends WeakEntry<K, V> {

    public WeakAccessEntry(ReferenceQueue<K> queue, K key, int hash, ReferenceEntry<K, V> next) {
        super(queue, key, hash, next);
    }
    volatile long accessTime = Long.MAX_VALUE;

    // Guarded By Segment.this
    @Weak ReferenceEntry<K, V> nextAccess = nullEntry();


    // Guarded By Segment.this
    @Weak ReferenceEntry<K, V> previousAccess = nullEntry();


    @Override
    public long getAccessTime() {
        return accessTime;
    }

    @Override
    public void setAccessTime(long time) {
        this.accessTime = time;
    }

    @Override
    public ReferenceEntry<K, V> getNextInAccessQueue() {
        return nextAccess;
    }

    @Override
    public void setNextInAccessQueue(ReferenceEntry<K, V> next) {
        this.nextAccess = next;
    }

    @Override
    public ReferenceEntry<K, V> getPreviousInAccessQueue() {
        return previousAccess;
    }

    @Override
    public void setPreviousInAccessQueue(ReferenceEntry<K, V> previous) {
        this.previousAccess = previous;
    }
}