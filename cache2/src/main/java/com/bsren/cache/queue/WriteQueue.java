package com.bsren.cache.queue;


import com.bsren.cache.AbstractReferenceEntry;
import com.bsren.cache.ReferenceEntry;
import com.bsren.cache.entry.NullEntry;
import com.google.common.collect.AbstractSequentialIterator;
import com.google.j2objc.annotations.Weak;

import java.util.AbstractQueue;
import java.util.Iterator;

import static com.bsren.cache.entry.NullEntry.nullEntry;


public final class WriteQueue<K, V> extends AbstractQueue<ReferenceEntry<K, V>> {
    final ReferenceEntry<K, V> head =
            new AbstractReferenceEntry<K, V>() {

                @Override
                public long getWriteTime() {
                    return Long.MAX_VALUE;
                }

                @Override
                public void setWriteTime(long time) {
                }

                @Weak
                ReferenceEntry<K, V> nextWrite = this;

                @Override
                public ReferenceEntry<K, V> getNextInWriteQueue() {
                    return nextWrite;
                }

                @Override
                public void setNextInWriteQueue(ReferenceEntry<K, V> next) {
                    this.nextWrite = next;
                }

                @Weak
                ReferenceEntry<K, V> previousWrite = this;

                @Override
                public ReferenceEntry<K, V> getPreviousInWriteQueue() {
                    return previousWrite;
                }

                @Override
                public void setPreviousInWriteQueue(ReferenceEntry<K, V> previous) {
                    this.previousWrite = previous;
                }
            };

    // implements Queue

    @Override
    public boolean offer(ReferenceEntry<K, V> entry) {
        // unlink
        connectWriteOrder(entry.getPreviousInWriteQueue(), entry.getNextInWriteQueue());

        // add to tail
        connectWriteOrder(head.getPreviousInWriteQueue(), entry);
        connectWriteOrder(entry, head);

        return true;
    }

    @Override
    public ReferenceEntry<K, V> peek() {
        ReferenceEntry<K, V> next = head.getNextInWriteQueue();
        return (next == head) ? null : next;
    }

    @Override
    public ReferenceEntry<K, V> poll() {
        ReferenceEntry<K, V> next = head.getNextInWriteQueue();
        if (next == head) {
            return null;
        }

        remove(next);
        return next;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean remove(Object o) {
        ReferenceEntry<K, V> e = (ReferenceEntry<K, V>) o;
        ReferenceEntry<K, V> previous = e.getPreviousInWriteQueue();
        ReferenceEntry<K, V> next = e.getNextInWriteQueue();
        connectWriteOrder(previous, next);
        nullifyWriteOrder(e);

        return next != NullEntry.INSTANCE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean contains(Object o) {
        ReferenceEntry<K, V> e = (ReferenceEntry<K, V>) o;
        return e.getNextInWriteQueue() != NullEntry.INSTANCE;
    }

    @Override
    public boolean isEmpty() {
        return head.getNextInWriteQueue() == head;
    }

    @Override
    public int size() {
        int size = 0;
        for (ReferenceEntry<K, V> e = head.getNextInWriteQueue();
             e != head;
             e = e.getNextInWriteQueue()) {
            size++;
        }
        return size;
    }

    @Override
    public void clear() {
        ReferenceEntry<K, V> e = head.getNextInWriteQueue();
        while (e != head) {
            ReferenceEntry<K, V> next = e.getNextInWriteQueue();
            nullifyWriteOrder(e);
            e = next;
        }

        head.setNextInWriteQueue(head);
        head.setPreviousInWriteQueue(head);
    }

    @Override
    public Iterator<ReferenceEntry<K, V>> iterator() {
        return new AbstractSequentialIterator<ReferenceEntry<K, V>>(peek()) {
            @Override
            protected ReferenceEntry<K, V> computeNext(ReferenceEntry<K, V> previous) {
                ReferenceEntry<K, V> next = previous.getNextInWriteQueue();
                return (next == head) ? null : next;
            }
        };
    }

    public static <K, V> void connectWriteOrder(ReferenceEntry<K, V> previous, ReferenceEntry<K, V> next) {
        previous.setNextInWriteQueue(next);
        next.setPreviousInWriteQueue(previous);
    }

    // Guarded By Segment.this
    public static <K, V> void nullifyWriteOrder(ReferenceEntry<K, V> nulled) {
        ReferenceEntry<K, V> nullEntry = nullEntry();
        nulled.setNextInWriteQueue(nullEntry);
        nulled.setPreviousInWriteQueue(nullEntry);
    }
}