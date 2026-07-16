package com.leetcode.array;

import java.sql.SQLOutput;
//移除元素
public class Leet_27_RemoveElement {
    public static void main(String[] args) {
        int[] nums = {3,2,2,3};
        int target = 3;
        Leet_27_RemoveElement Leet_27_RemoveElement = new Leet_27_RemoveElement();

        System.out.println(Leet_27_RemoveElement.solution_fast_slow_point(nums,target));
        for (int i = 0; i < nums.length; i++) {
            System.out.println(nums[i]);

        }


    }
    public int search(int[] nums , int val){
        //两层循环暴力操作
        //关键在于数组可用长度要实时变更

        int size = nums.length;
        for (int i = 0 ; i<=size-1; i++ ){
            if(nums[i]==val){
                for(int j = i;j<= nums.length-2;j++){
                    nums[j]=nums[j+1];
                }
                i--;
                size--;
            }
        }
        return size;
    }

    public int solution_fast_slow_point(int[] nums , int val){
        int slow = 0;
        for (int fast = 0; fast < nums.length; fast++) {
            //快指针始终指向需要保留的元素
            //慢指针为下标索引
            if(nums[fast]!=val){    //用fast来逐个侦察
                nums[slow]=nums[fast];
                slow++;
            }
        }
        return slow;
    }
}
