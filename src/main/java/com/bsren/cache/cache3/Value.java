package com.bsren.cache.cache3;

public interface Value<K,V> {

    V get();

    Entry<K,V> getEntry();

}
