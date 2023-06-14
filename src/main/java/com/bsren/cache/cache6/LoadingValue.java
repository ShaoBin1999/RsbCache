package com.bsren.cache.cache6;

import com.google.common.base.Function;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.lang.ref.ReferenceQueue;

import static com.google.common.util.concurrent.Futures.transform;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

public class LoadingValue <K,V> implements Value<K,V> {


    Value<K, V> oldValue;

    SettableFuture<V> futureValue = SettableFuture.create();

    Stopwatch stopwatch = Stopwatch.createUnstarted();

    public LoadingValue() {
        this(null);
    }

    public LoadingValue(Value<K, V> oldValue) {
        this.oldValue = oldValue;
    }

    @Override
    public V get() {
        return oldValue.get();
    }

    @Override
    public Entry<K, V> getEntry() {
        return null;
    }

    @Override
    public int getWeight() {
        return 0;
    }

    @Override
    public boolean isLoading() {
        return true;
    }

    @Override
    public V waitForValue() {
        return null;
    }

    public ListenableFuture<V> loadFuture(K key, CacheLoader<K, V> loader) {
        try {
            stopwatch.start();
            V previousValue = oldValue.get();
            if (previousValue == null) {
                V newValue = loader.load(key);
                return set(newValue) ? futureValue : Futures.immediateFuture(newValue);
            }
            ListenableFuture<V> newValue = loader.reload(key, previousValue);
            if (newValue == null) {
                return Futures.immediateFuture(null);
            }
            return transform(newValue, input -> {
                LoadingValue.this.set(input);
                return input;
            }, directExecutor());
        } catch (Throwable t) {
            ListenableFuture<V> result = setException(t) ? futureValue : fullyFailedFuture(t);
            if (t instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return result;
        }
    }

    private ListenableFuture<V> fullyFailedFuture(Throwable t) {
        return Futures.immediateFailedFuture(t);
    }

    private boolean setException(Throwable t) {
        return futureValue.setException(t);
    }

    private boolean set(V newValue) {
        return futureValue.set(newValue);
    }
}

