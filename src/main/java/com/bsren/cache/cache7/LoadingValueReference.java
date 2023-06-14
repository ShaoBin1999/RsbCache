package com.bsren.cache.cache7;

import com.bsren.cache.abstractCache.ReferenceEntry;
import com.bsren.cache.abstractCache.ValueReference;
import com.bsren.cache.abstractCache.loading.Unset;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.SettableFuture;

import java.lang.ref.ReferenceQueue;

public class LoadingValueReference <K,V> implements ValueReference<K,V> {

    volatile ValueReference<K,V> oldValue;

    SettableFuture<V> futureValue = SettableFuture.create();

    Stopwatch stopwatch = Stopwatch.createUnstarted();

    public LoadingValueReference(ValueReference<K,V> oldValue){
        this.oldValue = (oldValue==null)? Unset.unset():oldValue;
    }


    @Override
    public V get() {
        return null;
    }

    @Override
    public ReferenceEntry<K, V> getEntry() {
        return null;
    }

    @Override
    public ValueReference<K, V> copyFor(ReferenceQueue<V> queue, V value, ReferenceEntry<K, V> entry) {
        return null;
    }

    @Override
    public boolean isLoading() {
        return false;
    }
}
