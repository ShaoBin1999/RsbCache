package com.bsren.cache.abstractCache.entry;


import com.bsren.cache.cache5.ReferenceEntry;
import com.bsren.cache.cache5.entry.StrongEntry;
import com.google.j2objc.annotations.Weak;

import static com.bsren.cache.cache5.entry.NullReferenceEntry.nullEntry;

public class StrongWriteEntry<K, V> extends StrongEntry<K, V> {
    public StrongWriteEntry(K key, int hash, ReferenceEntry<K, V> next) {
        super(key, hash, next);
    }

    // The code below is exactly the same for each write entry type.

    volatile long writeTime = Long.MAX_VALUE;

    @Override
    public long getWriteTime() {
        return writeTime;
    }

    @Override
    public void setWriteTime(long time) {
        this.writeTime = time;
    }

    // Guarded By Segment.this
    @Weak
    ReferenceEntry<K, V> nextWrite = nullEntry();

    @Override
    public ReferenceEntry<K, V> getNextInWriteQueue() {
        return nextWrite;
    }

    @Override
    public void setNextInWriteQueue(ReferenceEntry<K, V> next) {
        this.nextWrite = next;
    }

    // Guarded By Segment.this
    @Weak ReferenceEntry<K, V> previousWrite = nullEntry();

    @Override
    public ReferenceEntry<K, V> getPreviousInWriteQueue() {
        return previousWrite;
    }

    @Override
    public void setPreviousInWriteQueue(ReferenceEntry<K, V> previous) {
        this.previousWrite = previous;
    }
}