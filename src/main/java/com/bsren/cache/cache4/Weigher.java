package com.bsren.cache.cache4;


@FunctionalInterface
public interface Weigher<K,V> {

    int weigh(K key, V value);

}
