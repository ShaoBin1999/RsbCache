package com.bsren.cache.cache2;

public interface Entry<K,V> {

    Value<K,V> getValue();

    void setValue(Value<K,V> value);

    Entry<K,V> getNext();

    int getHash();

    K getKey();
}
