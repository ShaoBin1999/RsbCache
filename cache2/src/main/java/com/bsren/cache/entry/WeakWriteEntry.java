package com.bsren.cache.entry;


import com.bsren.cache.ReferenceEntry;
import com.google.j2objc.annotations.Weak;

import java.lang.ref.ReferenceQueue;

import static com.bsren.cache.entry.NullEntry.nullEntry;


public class WeakWriteEntry<K, V> extends WeakEntry<K, V> {

    public WeakWriteEntry(ReferenceQueue<K> queue, K key, int hash, ReferenceEntry<K, V> next) {
        super(queue, key, hash, next);
    }

    volatile long writeTime = Long.MAX_VALUE;

    // Guarded By Segment.this
    @Weak ReferenceEntry<K, V> nextWrite = nullEntry();


    // Guarded By Segment.this
    @Weak ReferenceEntry<K, V> previousWrite = nullEntry();


    @Override
    public long getWriteTime() {
        return writeTime;
    }

    @Override
    public void setWriteTime(long time) {
        this.writeTime = time;
    }

    @Override
    public ReferenceEntry<K, V> getNextInWriteQueue() {
        return nextWrite;
    }

    @Override
    public void setNextInWriteQueue(ReferenceEntry<K, V> next) {
        this.nextWrite = next;
    }

    @Override
    public ReferenceEntry<K, V> getPreviousInWriteQueue() {
        return previousWrite;
    }

    @Override
    public void setPreviousInWriteQueue(ReferenceEntry<K, V> previous) {
        this.previousWrite = previous;
    }
}
