package com.bsren.cache.queue;

import com.bsren.cache.AbstractReferenceEntry;
import com.bsren.cache.ReferenceEntry;
import com.bsren.cache.entry.NullEntry;
import com.google.common.collect.AbstractSequentialIterator;
import com.google.j2objc.annotations.Weak;

import java.util.AbstractQueue;
import java.util.Iterator;

import static com.bsren.cache.entry.NullEntry.nullEntry;

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

        @Weak
        ReferenceEntry<K, V> nextAccess = this;

        @Weak
        ReferenceEntry<K, V> previousAccess = this;
        @Override
        public ReferenceEntry<K, V> getNextInAccessQueue() {
            return nextAccess;
        }

        @Override
        public void setNextInAccessQueue(ReferenceEntry<K, V> next) {
            this.nextAccess = next;
        }

        @Override
        public ReferenceEntry<K, V> getPreviousInAccessQueue() {
            return previousAccess;
        }

        @Override
        public void setPreviousInAccessQueue(ReferenceEntry<K, V> previous) {
            this.previousAccess = previous;
        }
    };



    @Override
    public Iterator<ReferenceEntry<K, V>> iterator() {
        return new AbstractSequentialIterator<ReferenceEntry<K, V>>(peek()) {
            @Override
            protected ReferenceEntry<K, V> computeNext(ReferenceEntry<K, V> previous) {
                ReferenceEntry<K, V> next = previous.getNextInAccessQueue();
                return (next == head) ? null : next;
            }
        };
    }

    @Override
    public int size() {
        int size = 0;
        for (ReferenceEntry<K,V> e = head.getNextInAccessQueue(); e!=head; e = e.getNextInAccessQueue()){
            size++;
        }
        return size;
    }

    @Override
    public void clear() {
        ReferenceEntry<K,V> e = head.getNextInAccessQueue();
        while (e!=null){
            ReferenceEntry<K,V> next = e.getNextInAccessQueue();
            nullifyAccessOrder(e);
            e = next;
        }
        head.setNextInAccessQueue(head);
        head.setPreviousInAccessQueue(head);
    }

    @Override
    public boolean contains(Object o) {
        ReferenceEntry<K,V> e = (ReferenceEntry<K, V>) o;
        return e.getNextInAccessQueue()!=NullEntry.INSTANCE;
    }

    @Override
    public boolean isEmpty() {
        return head.getNextInAccessQueue()==head;
    }



    //头插法,每次都插到头的前面
    @Override
    public boolean offer(ReferenceEntry<K, V> entry) {
        connectAccessOrder(entry.getPreviousInAccessQueue(),entry.getNextInAccessQueue());
        connectAccessOrder(head.getPreviousInAccessQueue(),entry);
        connectAccessOrder(entry,head);
        return true;
    }

    @Override
    public ReferenceEntry<K, V> poll() {
        ReferenceEntry<K,V> next = head.getNextInAccessQueue();
        if(next==head){
            return null;
        }
        remove(next);
        return next;
    }

    @Override
    public ReferenceEntry<K, V> peek() {
        ReferenceEntry<K,V> next = head.getNextInAccessQueue();
        return (next==head)?null:next;
    }

    @Override
    public boolean remove(Object o) {
        ReferenceEntry<K,V> e = (ReferenceEntry<K, V>) o;
        ReferenceEntry<K, V> previous = e.getPreviousInAccessQueue();
        ReferenceEntry<K, V> next = e.getNextInAccessQueue();
        connectAccessOrder(previous,next);
        nullifyAccessOrder(e);
        return next!= NullEntry.INSTANCE;
    }

    static <K, V> void connectAccessOrder(ReferenceEntry<K, V> previous, ReferenceEntry<K, V> next) {
        previous.setNextInAccessQueue(next);
        next.setPreviousInAccessQueue(previous);
    }

    static <K, V> void nullifyAccessOrder(ReferenceEntry<K, V> nulled) {
        ReferenceEntry<K, V> nullEntry = nullEntry();
        nulled.setNextInAccessQueue(nullEntry);
        nulled.setPreviousInAccessQueue(nullEntry);
    }
}
