package com.leetcode.HashTable;

import java.util.HashMap;

public class FourSumII {
    public int fourSumCount(int[] nums1, int[] nums2, int[] nums3, int[] nums4) {
        //妙法：分成两组，a+b，c+d，复杂度n方
        int count = 0;
        HashMap<Integer,Integer>hashMap = new HashMap<>();
        for (int i = 0; i < nums1.length; i++) {
            for (int j = 0; j < nums2.length; j++) {
                if(hashMap.containsKey(nums1[i]+nums2[j])){
                    int val = hashMap.get(nums1[i]+nums2[j]);
                    val++;
                    hashMap.put(nums1[i]+nums2[j],val);
                }else{
                    hashMap.put(nums1[i]+nums2[j],1);
                }
            }
        }
        for (int i = 0; i < nums3.length; i++) {
            for (int j = 0; j < nums4.length; j++) {
                if(hashMap.containsKey(-nums3[i] - nums4[j])){
                    count += hashMap.get(-nums3[i] - nums4[j]);
                }
            }
        }
        return count;
    }
}
