package com.bsren.cache.abstractCache.entry;


import com.bsren.cache.cache5.ReferenceEntry;
import com.bsren.cache.cache5.entry.StrongEntry;
import com.google.j2objc.annotations.Weak;

import static com.bsren.cache.cache5.entry.NullReferenceEntry.nullEntry;


public class StrongAccessWriteEntry<K, V> extends StrongEntry<K, V> {

    volatile long writeTime = Long.MAX_VALUE;

    volatile long accessTime = Long.MAX_VALUE;

    // Guarded By Segment.this
    @Weak ReferenceEntry<K, V> nextAccess = nullEntry();

    // Guarded By Segment.this
    @Weak ReferenceEntry<K, V> nextWrite = nullEntry();

    // Guarded By Segment.this
    @Weak ReferenceEntry<K, V> previousWrite = nullEntry();

    // Guarded By Segment.this
    @Weak ReferenceEntry<K, V> previousAccess = nullEntry();


    public StrongAccessWriteEntry(K key, int hash, ReferenceEntry<K, V> next) {
        super(key, hash, next);
    }

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