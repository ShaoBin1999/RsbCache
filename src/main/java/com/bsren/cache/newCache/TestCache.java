package com.bsren.cache.newCache;

import com.bsren.cache.abstractCache.CacheStats;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class TestCache {

    LocalCache<Integer,String> localCache = new LocalCache<>(10,4);

    public static String[]  getDataSource(int size){
        String[] strings = new String[size];
        for(int i=0;i<size;i++){
            strings[i] =  "cacheValue"+i;
        }
        return strings;
    }

    @Test
    public void testPutAndGet(){
        String[] dataSource = getDataSource(10);
        for (int i = 0; i < dataSource.length; i++) {
            localCache.put(i,dataSource[i]);
        }
        for (int i=0;i<dataSource.length;i++){
            System.out.println("key:"+i+","+"value:"+localCache.get(i));
        }
    }

    @Test
    public void testRemove(){
        String[] dataSource = getDataSource(10);
        for (int i = 0; i < dataSource.length; i++) {
            localCache.put(i,dataSource[i]);
        }
        localCache.remove(5);
        System.out.println(localCache.get(5));
    }

    @Test
    public void testReplace(){
        String[] dataSource = getDataSource(10);
        for (int i = 0; i < dataSource.length; i++) {
            localCache.put(i,dataSource[i]);
        }
        localCache.replace(5,"55");
        System.out.println(localCache.get(5));
    }

    @Test
    public void testClear(){
        String[] dataSource = getDataSource(10);
        for (int i = 0; i < dataSource.length; i++) {
            localCache.put(i,dataSource[i]);
        }
        localCache.clear();
        for (int i=0;i<dataSource.length;i++){
            System.out.println("key:"+i+","+"value:"+localCache.get(i));
        }
    }


    @Test
    public void testExpand(){
        String[] dataSource = getDataSource(100);
        for (int i = 0; i < dataSource.length; i++) {
            localCache.put(i,dataSource[i]);
        }
        for (int i=0;i<dataSource.length;i++){
            System.out.println("key:"+i+","+"value:"+localCache.get(i));
        }
        System.out.println(localCache.getSize());
        for (LocalCache.Segment<Integer, String> segment : localCache.segments) {
            System.out.println(segment.count);
        }
    }

    @Test
    public void testExpireRead() throws InterruptedException {
        String[] dataSource = getDataSource(5);
        localCache.setExpireAfterAccessNanos(2*1000*1000*1000L);  //2s
        for (int i = 0; i < dataSource.length; i++) {
            localCache.put(i,dataSource[i]);
        }
        Thread.sleep(1000*1);
        System.out.println(localCache.get(1));
        Thread.sleep(1500);
        System.out.println(localCache.get(1));
        System.out.println(localCache.get(0));
    }

    @Test
    public void testExpireWrite() throws InterruptedException {
        String[] dataSource = getDataSource(5);
        localCache.setExpireAfterAccessNanos(2*1000*1000*1000L);  //10s
        for (int i = 0; i < dataSource.length; i++) {
            localCache.put(i,dataSource[i]);
        }
        Thread.sleep(1000*3);
        System.out.println(localCache.get(0));
    }

    @Test
    public void testStats(){
        String[] dataSource = getDataSource(5);
        for (int i = 0; i < dataSource.length; i++) {
            localCache.put(i,dataSource[i]);
        }
        for (int i=0;i<10;i++){
            System.out.println(localCache.getIfPresent(i));
        }
        CacheStats stats = localCache.stats();
        System.out.println(stats);
    }



}
