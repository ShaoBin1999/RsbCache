package com.bsren.cache.cache7;

import com.bsren.cache.abstractCache.CacheLoader;
import com.bsren.cache.abstractCache.ReferenceEntry;
import com.bsren.cache.abstractCache.ValueReference;
import com.bsren.cache.abstractCache.loading.Unset;
import com.google.common.base.Function;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.ref.ReferenceQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.google.common.util.concurrent.Futures.transform;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.common.util.concurrent.Uninterruptibles.getUninterruptibly;

public class LoadingValueReference <K,V> implements ValueReference<K,V> {

    volatile ValueReference<K,V> oldValue;

    SettableFuture<V> futureValue = SettableFuture.create();

    Stopwatch stopwatch = Stopwatch.createUnstarted();

    public LoadingValueReference(){
        this(null);
    }

    public LoadingValueReference(ValueReference<K,V> oldValue){
        this.oldValue = (oldValue==null)? Unset.unset():oldValue;
    }


    @Override
    public V get() {
        return null;
    }

    public boolean set(@Nullable V newValue) {
        return futureValue.set(newValue);
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

    @Override
    public boolean isActive() {
        return oldValue.isActive();
    }

    @Override
    public V waitForValue() throws ExecutionException {
        return getUninterruptibly(futureValue);
    }

    public ListenableFuture<V> loadFuture(K key, CacheLoader<K,V> loader){
        try {
            stopwatch.start();
            V previousValue = oldValue.get();
            if(previousValue==null){
                V newValue = loader.load(key);
                return set(newValue)?futureValue: Futures.immediateFuture(newValue);
            }
            ListenableFuture<V> newValue = loader.reload(key,previousValue);
            if(newValue==null){
                return Futures.immediateFuture(null);
            }
            return transform(newValue, new Function<V, V>() {
                @Override
                public V apply(V newValue) {
                    LoadingValueReference.this.set(newValue);
                    return newValue;

                }
            },directExecutor());

        }catch (Exception t) {
            ListenableFuture<V> result = setException(t) ? futureValue : fullyFailedFuture(t);
            if (t instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return result;
        }
    }
    public boolean setException(Throwable t) {
        return futureValue.setException(t);
    }

    private ListenableFuture<V> fullyFailedFuture(Throwable t) {
        return Futures.immediateFailedFuture(t);
    }

    public long elapsedNanos() {
        return stopwatch.elapsed(TimeUnit.NANOSECONDS);
    }
}
