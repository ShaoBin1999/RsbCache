package com.bsren.cache;

import java.util.*;

public class Test {

    public static void main(String[] args) {
        String s = "abcda";
        int[][] l = new int[5][];
        l[0] = new int[]{3,3,0};
        l[1] = new int[]{1,2,0};
        l[2] = new int[]{0,3,1};
        l[3] = new int[]{0,3,2};
        l[4] = new int[]{0,4,1};
        System.out.println(new Test().canMakePaliQueries(s,l));
    }

    public List<Boolean> canMakePaliQueries(String s, int[][] queries) {
        Set<Integer> set = new HashSet<>();
        for (int[] query : queries) {
            set.add(query[0]);
            set.add(query[0]-1);
            set.add(query[1]);
            set.add(query[1]-1);
        }
        HashMap<Integer,int[]> map = new HashMap<>();
        int[] ls = new int[26];
        for (int i = 0; i < s.toCharArray().length; i++) {
            ls[s.charAt(i)-'a']++;
            if(set.contains(i)){
                map.put(i, Arrays.copyOf(ls,ls.length));
            }
        }
        List<Boolean> l = new ArrayList<>(queries.length);
        for (int[] query : queries) {
            int[] ls1 = map.get(query[0]-1);
            int[] ls2 = map.get(query[1]);
            int size = query[1]-query[0]+1;
            l.add(fun(ls1,ls2,query[2],size));
        }
        return l;
    }

    private Boolean fun(int[] ls1, int[] ls2, int i,int size) {
        int[] l = new int[ls2.length];
        if(ls1==null){
            l = ls2;
        }else {
            for (int i1 = 0; i1 < ls2.length; i1++) {
                l[i1] = ls2[i1]-ls1[i1];
            }
        }

        int odd = 0;
        for (int i1 : l) {
            if(i1%2==1){
                odd++;
            }
        }
        if(size%2==0){
            return odd <= i * 2;
        }
        else {
            return odd <= i*2+1;
        }
    }


}
