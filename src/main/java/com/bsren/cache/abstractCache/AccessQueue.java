package com.bsren.cache.abstractCache;

import java.util.AbstractQueue;
import java.util.Iterator;

public final class AccessQueue<K,V> extends AbstractQueue<ReferenceEntry<K,V>> {

    final ReferenceEntry<K,V> head = new AbstractReferenceEntry<K, V>() {

        @Override
        public long getAccessTime() {
            return super.getAccessTime();
        }

        @Override
        public void setAccessTime(long time) {
            super.setAccessTime(time);
        }


    }
    @Override
    public Iterator<ReferenceEntry<K, V>> iterator() {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean offer(ReferenceEntry<K, V> kvReferenceEntry) {
        return false;
    }

    @Override
    public ReferenceEntry<K, V> poll() {
        return null;
    }

    @Override
    public ReferenceEntry<K, V> peek() {
        return null;
    }
}
