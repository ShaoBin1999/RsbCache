package com.bsren.cache.cache3;

import com.google.common.base.Ticker;
import org.junit.Test;

public class TestCache {

    public static void main(String[] args) {
        LocalCache<Integer,String> ls = new LocalCache<>(10,4);
        ls.put(2,"11");
        ls.put(1,"fa");
        System.out.println(ls.get(2));
        System.out.println(ls.get(1));
    }

    @Test
    public void test1(){
        System.out.println(Ticker.systemTicker().read());
    }

    @Test
    public void test2() throws InterruptedException {
        LocalCache<Integer,String> ls = new LocalCache<>(10,4);
        ls.setExpireAfterAccessNanos(1000L * 1000 * 1000 * 15); //30s;
        ls.setExpireAfterWriteNanos(1000L * 1000 * 1000 * 10); //10s
        for (int i=1;i<=10;i++){
            ls.put(i,"lalala"+i);
        }
        System.out.println(ls.getSize());
        Thread.sleep(20*1000);
        for (int i=1;i<=10;i++){
            System.out.println(ls.get(i));
        }
        System.out.println(ls.getSize());
        ls.put(2,"fafafa");
        System.out.println(ls.get(2));
        Thread.sleep(12*1000);
        System.out.println(ls.get(2));
        System.out.println(ls.getSize());
    }

    @Test
    public void test3(){
        LocalCache<Integer,String> ls = new LocalCache<>(10,4);
        for (int i=1;i<=100;i++){
            ls.put(i,"lalala"+i);
        }
        System.out.println(ls.getSize());
    }


    @Test
    public void test4(){
        Node node = new Node();
        node.val = 0;
        Node node1 = new Node();
        node1.val = 1;
        Node node2 = new Node();
        node2.val = 2;
        Node node3 = new Node();
        node3.val = 3;
        Node node4 = new Node();
        node4.val = 4;
        node.next = node1;
        node1.next = node2;
        node2.next = node3;
        node3.next = node4;
        Node newNode = remove(node, node3);
        while (newNode!=null){
            System.out.println(newNode.val);
            newNode = newNode.next;
        }
    }

    @Test
    public void test5() throws InterruptedException {
        long l = System.nanoTime();
        Thread.sleep(1000);
        System.out.println(System.nanoTime()-l);
    }

    private Node remove(Node node, Node remove) {
        Node next = remove.next;
        for (Node c = node;c!=remove;c = c.next){
            Node n = new Node();
            n.val = c.val;
            n.next = next;
            next = n;
        }
        return next;
    }

    static class Node{
        int val;
        Node next;
    }

}
