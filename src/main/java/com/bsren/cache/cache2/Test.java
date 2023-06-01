package com.bsren.cache.cache2;

public class Test {

    public static void main(String[] args) {
        LocalCache<Integer,String> ls = new LocalCache<>(10,4);
        ls.put(2,"11");
        ls.put(1,"fa");
        System.out.println(ls.get(2));
        System.out.println(ls.get(1));

    }
}
