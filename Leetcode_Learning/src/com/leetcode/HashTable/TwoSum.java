package com.leetcode.HashTable;

import java.util.HashMap;
import java.util.HashSet;

public class TwoSum {
    public int[] twoSum(int[] nums, int target) {
        //个人思路：一趟循环用n遍历nums，然后把除了n的所有元素放到hashset里
        //最后看是否contain（target-n）

        //但是！！！本题不是看存不存在两数之和，而是一定存在，需要返回具体下标
        //所以要用hashmap！！！有键有值
        HashMap<Integer,Integer> hashMap =new HashMap<>();
        int[] arr = new int[2];
        for (int i = 0; i < nums.length; i++) {
            for (int j = 0; j!=i; j++) {
                hashMap.put(nums[j],j);
            }
            if(hashMap.containsKey(target-nums[i])){
                arr[0]=i;
                arr[1]=hashMap.get(target-nums[i]);
                return arr;
            }
        }
        return arr;//防报错
    }



    //显然，上面的两层循环复杂度和暴力解法都没区别了,是n^2。一次循环就可以解决问题。自行重写：
    //自己没想出来，还是旧思想，想先一趟循环把所有东西放进map里。这样复杂度是2n。但是题解的绝妙思想可以到n
    public int[] twoSum_singleLoop(int[] nums, int target) {
        //题解真的是太妙了
        //先查后放
        HashMap<Integer,Integer> hashMap =new HashMap<>();
        int[] arr = new int[2];
        for (int i = 0; i < nums.length; i++) {
            if(hashMap.containsKey(target-nums[i])){
                arr[0]=hashMap.get(target-nums[i]);
                arr[1]=i;
                return arr;
            }
            else{
                hashMap.put(nums[i],i);
            }
        }
        return arr;

    }
}
