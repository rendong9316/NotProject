package com.leetcode.stackAndQueue;

import java.util.*;

public class TopKFrequentElements {
    public int[] topKFrequent(int[] nums, int k) {
        Map<Integer,Integer> map = new HashMap<>();
        for (int num : nums) {
            map.put(num,map.getOrDefault(num,0)+1);
            //有 key 拿原值，没 key 拿你给的默认值
        }
        PriorityQueue<int[]> priorityQueue = new PriorityQueue<>((a,b)->a[1]-b[1]);
        for(Map.Entry<Integer,Integer> entry : map.entrySet()){
            if(priorityQueue.size()<k){
                priorityQueue.add(new int[]{entry.getKey(),entry.getValue()});
            }else {
                if(entry.getValue()>priorityQueue.peek()[1]){
                    priorityQueue.poll();
                    priorityQueue.add(new int[]{entry.getKey(),entry.getValue()});
                }
            }
        }

        int [] res = new int[k];
        for (int i = 0; i < k; i++) {
            res[i] = priorityQueue.poll()[0];
        }
        return res;
    }
}
