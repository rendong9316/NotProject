package com.leetcode.HashTable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

public class IntersectionOfTwoArrays {
    public int[] intersection(int[] nums1, int[] nums2) {
        Set<Integer>set1 = new HashSet<>();
        Set<Integer>set2 = new HashSet<>();
        Set<Integer>set3 = new HashSet<>();
        for (int i = 0; i < nums1.length; i++) {
            set1.add(nums1[i]);
        }
        for (int i = 0; i < nums2.length; i++) {
            set2.add(nums2[i]);
        }
        Iterator<Integer> it = set1.iterator();
        while (it.hasNext()){
            Integer tem = it.next();
            if(set2.contains(tem)){
                set3.add(tem);
            }
        }
        int[] arr = new int[set3.size()];
        int i =0;
        for(Integer j : set3){
            arr[i] = j;
            i++;
        }
        return arr;

        //牛逼一刀流
        //return set3.stream().mapToInt(Integer::intValue).toArray();
    }
}
