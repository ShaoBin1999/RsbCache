package com.bsren.cache.abstractCache.entry;


import com.bsren.cache.cache5.ReferenceEntry;
import com.bsren.cache.cache5.entry.StrongEntry;
import com.google.j2objc.annotations.Weak;

import static com.bsren.cache.cache5.entry.NullReferenceEntry.nullEntry;

public class StrongAccessEntry<K, V> extends StrongEntry<K, V> {
    public StrongAccessEntry(K key, int hash, ReferenceEntry<K, V> next) {
        super(key, hash, next);
    }

    // The code below is exactly the same for each access entry type.

    volatile long accessTime = Long.MAX_VALUE;

    @Override
    public long getAccessTime() {
        return accessTime;
    }

    @Override
    public void setAccessTime(long time) {
        this.accessTime = time;
    }

    // Guarded By Segment.this
    @Weak
    ReferenceEntry<K, V> nextAccess = nullEntry();

    @Override
    public ReferenceEntry<K, V> getNextInAccessQueue() {
        return nextAccess;
    }

    @Override
    public void setNextInAccessQueue(ReferenceEntry<K, V> next) {
        this.nextAccess = next;
    }

    // Guarded By Segment.this
    @Weak ReferenceEntry<K, V> previousAccess = nullEntry();

    @Override
    public ReferenceEntry<K, V> getPreviousInAccessQueue() {
        return previousAccess;
    }

    @Override
    public void setPreviousInAccessQueue(ReferenceEntry<K, V> previous) {
        this.previousAccess = previous;
    }
}
