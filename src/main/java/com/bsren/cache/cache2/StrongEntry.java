package com.bsren.cache.cache2;

//public enum EntryFactory {
//
//
public  class StrongEntry<K,V> implements Entry<K,V>{

        final K key;

        int hash;

        Entry<K,V> next;

        volatile Value<K,V> value;

        StrongEntry(K key,int hash,Entry<K,V> next){
            this.key = key;
            this.hash = hash;
            this.next = next;
        }

        @Override
        public Value<K, V> getValue() {
            return value;
        }

        @Override
        public void setValue(Value<K, V> value) {
            this.value = value;
        }

        @Override
        public Entry<K, V> getNext() {
            return next;
        }

        @Override
        public int getHash() {
            return hash;
        }

        @Override
        public K getKey() {
            return key;
        }
    }
//}
