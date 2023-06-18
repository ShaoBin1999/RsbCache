package com.bsren.cache.listeners;



public interface RemovalListener<K,V> {
    void onRemoval(RemovalNotification<K, V> notification);
}
