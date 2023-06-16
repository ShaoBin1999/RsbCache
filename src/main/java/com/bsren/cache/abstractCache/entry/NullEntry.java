package com.bsren.cache.abstractCache.entry;

import com.bsren.cache.abstractCache.ReferenceEntry;
import com.bsren.cache.abstractCache.ValueReference;

public enum NullEntry implements ReferenceEntry<Object,Object> {

    INSTANCE;

    @Override
    public ValueReference<Object, Object> getValueReference() {
        return null;
    }

    @Override
    public void setValueReference(ValueReference<Object, Object> value) {

    }

    @Override
    public ReferenceEntry<Object, Object> getNext() {
        return null;
    }

    @Override
    public int getHash() {
        return 0;
    }

    @Override
    public Object getKey() {
        return null;
    }

    @Override
    public long getAccessTime() {
        return 0;
    }

    @Override
    public void setAccessTime(long time) {

    }

    @Override
    public long getWriteTime() {
        return 0;
    }

    @Override
    public void setWriteTime(long time) {

    }


    @Override
    public ReferenceEntry<Object, Object> getNextInWriteQueue() {
        return this;
    }

    @Override
    public void setNextInWriteQueue(ReferenceEntry<Object, Object> next) {}

    @Override
    public ReferenceEntry<Object, Object> getPreviousInWriteQueue() {
        return this;
    }

    @Override
    public void setPreviousInWriteQueue(ReferenceEntry<Object, Object> previous) {}

    @Override
    public ReferenceEntry<Object, Object> getNextInAccessQueue() {
        return this;
    }

    @Override
    public void setNextInAccessQueue(ReferenceEntry<Object, Object> next) {}

    @Override
    public ReferenceEntry<Object, Object> getPreviousInAccessQueue() {
        return this;
    }

    @Override
    public void setPreviousInAccessQueue(ReferenceEntry<Object, Object> previous) {}

    public static <K, V> ReferenceEntry<K, V> nullEntry() {
        return (ReferenceEntry<K, V>) INSTANCE;
    }

}