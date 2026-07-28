package com.leetcode.HashTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ThreeSum {
    public List<List<Integer>> threeSum(int[] nums) {
        Arrays.sort(nums);
        List<List<Integer>> result = new ArrayList<>();
        int count =0;
        for (int i = 0; i < nums.length-2; i++) {
            int left = i+1;
            int right = nums.length-1;
            while(left<right){
                //这个循环终止条件很妙，受豆包启发了。原来写的while（true）
                if(nums[i]+nums[left]+nums[right]>0){
                    right--;
                    continue;
                }
                if(nums[i]+nums[left]+nums[right]<0){
                    left++;
                    continue;
                }
                if(nums[i]+nums[left]+nums[right]==0){
                    int a = 0;
                    for (int j = 0; j < count; j++) {
                        if(nums[i]== result.get(j).get(0) &&nums[left]== result.get(j).get(1) &&nums[right]== result.get(j).get(2)){
                            left++;
                            a=1;
                            break;
                        }
                    }
                    if(a==1){
                        continue;
                    }else{
                        // 找到一组 [-1,0,1] 直接塞入
                        result.add(Arrays.asList(nums[i], nums[left], nums[right]));
                        count++;
                        left++;
                    }
                }
            }

        }
        return result;
    }

    public static void main(String[] args) {
        ThreeSum threeSum = new ThreeSum();
        int [] arr = {-1,3,-2,4,-5,1};
        System.out.println(threeSum.threeSum(arr));
    }
}
