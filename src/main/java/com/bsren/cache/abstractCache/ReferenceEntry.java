package com.bsren.cache.abstractCache;

public interface ReferenceEntry<K,V> {

    ValueReference<K,V> getValueReference();

    void setValueReference(ValueReference<K,V> valueReference);

    ReferenceEntry<K,V> getNext();

    int getHash();

    K getKey();

    long getAccessTime();

    void setAccessTime(long now);

    long getWriteTime();

    void setWriteTime(long time);

}
