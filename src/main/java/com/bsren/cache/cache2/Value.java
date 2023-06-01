package com.bsren.cache.cache2;

public interface Value<K,V> {

    V get();

    Entry<K,V> getEntry();

}
