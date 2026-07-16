package com.leetcode.array;

public class Leet_977_SquaresOfASortedArray {
    public static void main(String[] args) {
        int[] num2 = {-9,2,2,3};

        Leet_977_SquaresOfASortedArray Leet_27_RemoveElement = new Leet_977_SquaresOfASortedArray();
        int num3[] = Leet_27_RemoveElement.solution_fast_slow_point(num2);
        for (int i = 0; i < num3.length; i++) {
            System.out.println(num3[i]);

        }


    }

    public int[] solution_fast_slow_point(int[] nums ){

        //双指针思想，最大值在两边出现。
        //也可以说是三指针，k用来指示存放位置，从右往左动。

        int num[]=new int[nums.length];
        int left = 0;
        int k = nums.length-1;
        for (int right = nums.length-1; right >= left;) {
            if(nums[right]*nums[right]>=nums[left]*nums[left]){
                num[k]= nums[right]*nums[right];
                right--;
                k--;
            }else{
                num[k]=nums[left]*nums[left];
                left++;
                k--;
            }

        }
        return num;

    }
}
