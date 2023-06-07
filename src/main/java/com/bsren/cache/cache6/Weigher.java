package com.bsren.cache.cache6;


@FunctionalInterface
public interface Weigher<K,V> {

    int weigh(K key, V value);

}
