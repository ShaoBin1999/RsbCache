package com.bsren.cache;

import com.google.common.collect.ForwardingObject;

public abstract class ForwardingCache<K,V> extends ForwardingObject implements Cache<K,V> {

    protected ForwardingCache() {}

    @Override
    protected abstract Cache<K, V> delegate();


}
