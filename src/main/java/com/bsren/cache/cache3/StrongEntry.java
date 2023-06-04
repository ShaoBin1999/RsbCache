package com.bsren.cache.cache3;

public  class StrongEntry<K,V> implements Entry<K,V> {

        final K key;

        int hash;

        Entry<K,V> next;

        volatile Value<K,V> value;

        long accessTime;

        long writeTime;

        StrongEntry(K key, int hash, Entry<K,V> next){
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

    @Override
    public long getAccessTime() {
        return accessTime;
    }

    @Override
    public void setAccessTime(long time) {
            this.accessTime = time;
    }

    @Override
    public long getWriteTime() {
        return writeTime;
    }

    @Override
    public void setWriteTime(long time) {
            this.writeTime = time;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StrongEntry<?, ?> that = (StrongEntry<?, ?>) o;

        if (!key.equals(that.key)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + value.hashCode();
        return result;
    }
}
//}
