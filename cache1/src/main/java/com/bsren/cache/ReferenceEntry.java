package com.bsren.cache;

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

    ReferenceEntry<K, V> getNextInAccessQueue();

    /** Sets the next entry in the access queue. */
    void setNextInAccessQueue(ReferenceEntry<K, V> next);

    /** Returns the previous entry in the access queue. */
    ReferenceEntry<K, V> getPreviousInAccessQueue();

    /** Sets the previous entry in the access queue. */
    void setPreviousInAccessQueue(ReferenceEntry<K, V> previous);

    ReferenceEntry<K, V> getNextInWriteQueue();

    /** Sets the next entry in the write queue. */
    void setNextInWriteQueue(ReferenceEntry<K, V> next);

    /** Returns the previous entry in the write queue. */
    ReferenceEntry<K, V> getPreviousInWriteQueue();

    /** Sets the previous entry in the write queue. */
    void setPreviousInWriteQueue(ReferenceEntry<K, V> previous);


}
