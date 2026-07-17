package com.leetcode.array;


import static java.lang.Math.min;

public class Leet_209_MinimumSizeSubarraySum {
    public static void main(String[] args) {
        int nums[] = {1,2,3,4,5,6,7};
        Leet_209_MinimumSizeSubarraySum leet209MinimumSizeSubarraySum = new Leet_209_MinimumSizeSubarraySum();
        System.out.println(leet209MinimumSizeSubarraySum.solution_split_window(nums,22));
    }

    public int solution(int nums[],int s){
        //暴力解法，无法AC

        int min = 100001;
        for (int i = 0; i < nums.length; i++) {
            int sum = 0;
            int count = 0;
            for (int j = i; j < nums.length ; j++) {
                sum = sum + nums[j];
                count++;
                if(sum>=s){
                    if(count<min){
                        min = count;
                    }
                    break;

                }
            }
        }
        if(min!=100001){
            return min;
        }else{
            return 0;
        }
    }


    public int solution_split_window(int nums[],int s){
        //滑动窗口法
        //关键：j是终点。一轮循环移动终点，起点用来缩紧范围
        int min = 1000001;
        int i = 0;
        int sum = 0;
        for (int j = 0; j < nums.length; j++) {
            sum = sum + nums[j];
            while (sum >= s){
                min = min(min,j-i+1);
                sum-=nums[i];
                i++;
            }
        }
        if(min == 1000001){
            return 0;
        }else{
            return min;
        }
    }
}
