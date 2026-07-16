package com.leetcode.array;

import java.sql.SQLOutput;

public class Leet_704_BinarySearch {
    public static void main(String[] args) {
        int[] nums = {-1,0,3,5,9,12};
        int target = 0;
        Leet_704_BinarySearch leet704BinarySearch = new Leet_704_BinarySearch();

        System.out.println(leet704BinarySearch.search(nums,target));


    }
    public int search(int[] nums , int target){
        int left = 0;
        int right = nums.length-1;

        while (left<=right){           //第一个关键点：循环终止条件
            int middle = left + (right-left)/2;   //防止溢出的写法

            if(nums[middle]==target){
                return middle;
            }else{
                if (nums[middle]<target){
                        left = middle + 1;
                }else{
                    right = middle - 1;
                }
            }
        }
        return -1;
    }
}
