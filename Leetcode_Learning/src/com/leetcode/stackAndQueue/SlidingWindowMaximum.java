package com.leetcode.stackAndQueue;

import java.util.ArrayDeque;
import java.util.Deque;

public class SlidingWindowMaximum {
    public int[] maxSlidingWindow(int[] nums, int k) {
        MyQueue myQueue = new MyQueue();
        int lengthRes = nums.length-k+1;
        int[] res = new int[lengthRes];
        for (int i = 0; i < k; i++) {
            myQueue.push(nums[i]);
        }
        res[0] = myQueue.peek();
        for (int i = k; i < nums.length; i++) {
            myQueue.pop(nums[i-k]);
            myQueue.push(nums[i]);
            res[i-k+1] = myQueue.peek();
        }
        return res;
    }

    private class MyQueue{
        Deque<Integer> deque = new ArrayDeque<>();
        public void push(int val){
            while(!deque.isEmpty()&&deque.getLast()<val){
                deque.removeLast();
            }
            deque.addLast(val);
        }

        public void pop(int val){
            if(!deque.isEmpty()&&deque.getFirst()==val){
                deque.removeFirst();
            }
        }

        public int peek(){
            return deque.peek();
        }
    }

    public static void main(String[] args) {
        SlidingWindowMaximum slidingWindowMaximum = new SlidingWindowMaximum();
        int[] arr = slidingWindowMaximum.maxSlidingWindow(new int[]{-7,-8,7,5,7,1,6,0},4);
        for (int i = 0; i < arr.length; i++) {
            System.out.println(arr[i]);
        }

    }
}
