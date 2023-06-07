package com.bsren.cache.cache6;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import static com.google.common.base.Preconditions.checkNotNull;
public abstract class CacheLoader<K,V> {


    protected CacheLoader(){

    }

    public abstract V load(K key) throws Exception;

    public Map<K,V> loadALL(Iterable<K> keys) throws Exception{
        throw new UnsupportedLoadingOperationException();
    }

    public static <K,V> CacheLoader<K,V> from(Function<K,V> function){
        return new FunctionToCacheLoader<>(function);
    }

    /**
     * 仅仅在调用reload方法时使用executor,作为同步方法的补充
     */
    public static <K,V> CacheLoader<K,V> asyncReloading(CacheLoader<K,V> loader, Executor executor){
        checkNotNull(loader);
        checkNotNull(executor);
        return new CacheLoader<K, V>() {
            @Override
            public V load(K key) throws Exception {
                return loader.load(key);
            }

            @Override
            public ListenableFuture<V> reload(K key, V oldValue) throws Exception {
                ListenableFutureTask<V> task = ListenableFutureTask.create(new Callable<V>() {
                    @Override
                    public V call() throws Exception {
                        return loader.reload(key,oldValue).get();
                    }
                });
                executor.execute(task);
                return task;
            }

            @Override
            public Map<K, V> loadALL(Iterable<K> keys) throws Exception {
                return loader.loadALL(keys);
            }
        };
    }

    private static final class FunctionToCacheLoader<K, V> extends CacheLoader<K,V> implements Serializable{

        private final Function<K,V> computingFunction;

        public FunctionToCacheLoader(Function<K,V> computingFunction){
            this.computingFunction = checkNotNull(computingFunction);
        }

        @Override
        public V load(K key) throws Exception {
            return computingFunction.apply(key);
        }

        private static final long serialVersionID = 0;
    }





    public ListenableFuture<V> reload(K key, V oldValue) throws Exception{
        checkNotNull(key);
        checkNotNull(oldValue);
        return Futures.immediateFuture(load(key));
    }

    public static final class UnsupportedLoadingOperationException
            extends UnsupportedOperationException {
        // Package-private because this should only be thrown by loadAll() when it is not overridden.
        // Cache implementors may want to catch it but should not need to be able to throw it.
        UnsupportedLoadingOperationException() {}
    }

    public static final class InvalidCacheLoadException extends RuntimeException {
        public InvalidCacheLoadException(String message) {
            super(message);
        }
    }
}
