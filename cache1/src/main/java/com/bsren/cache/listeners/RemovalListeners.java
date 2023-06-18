package com.bsren.cache.listeners;

import java.util.concurrent.Executor;

import static com.google.common.base.Preconditions.checkNotNull;

public class RemovalListeners {
    
    
    private RemovalListeners() {}

    public static <K, V> RemovalListener<K, V> asynchronous(
            RemovalListener<K, V> listener, Executor executor) {
        checkNotNull(listener);
        checkNotNull(executor);
        return (RemovalNotification<K, V> notification) ->
                executor.execute(() -> listener.onRemoval(notification));
    }
}
