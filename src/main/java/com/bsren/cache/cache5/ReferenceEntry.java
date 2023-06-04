package com.bsren.cache.cache5;

public interface ReferenceEntry<K,V> {

    ValueReference<K,V> getValueReference();

    void setValueReference(ValueReference<K,V> value);

    ReferenceEntry<K,V> getNext();

    int getHash();

    K getKey();

    long getAccessTime();

    void setAccessTime(long time);

    ReferenceEntry<K,V> getNextInAccessQueue();

    void setNextInAccessQueue(ReferenceEntry<K,V> next);

    ReferenceEntry<K,V> getPreviousInAccessQueue();

    void setPreviousInAccessQueue(ReferenceEntry<K,V> previous);

    long getWriteTime();

    void setWriteTime(long time);

    ReferenceEntry<K, V> getNextInWriteQueue();

    void setNextInWriteQueue(ReferenceEntry<K, V> next);

    ReferenceEntry<K, V> getPreviousInWriteQueue();

    void setPreviousInWriteQueue(ReferenceEntry<K, V> previous);
}
