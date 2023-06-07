package com.bsren.cache.cache6;

public interface Entry<K,V> {

    Value<K,V> getValue();

    void setValue(Value<K,V> value);

    Entry<K,V> getNext();

    int getHash();

    K getKey();

    long getAccessTime();

    void setAccessTime(long time);

    long getWriteTime();

    void setWriteTime(long time);
}
