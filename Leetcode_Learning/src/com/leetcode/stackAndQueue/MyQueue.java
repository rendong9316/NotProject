package com.leetcode.stackAndQueue;

import java.util.ArrayDeque;
import java.util.Deque;

public class MyQueue {

    //先来两个私有成员变量

    private Deque<Integer> stackin;
    private Deque<Integer> stackout;
    public MyQueue() {
        stackin = new ArrayDeque<>();
        stackout = new ArrayDeque<>();
    }

    public void push(int x) {
        stackin.push(x);
    }

    public int pop() {
        updown(stackin,stackout);
        return stackout.pop();

    }

    public int peek() {
        updown(stackin,stackout);
        return stackout.peek();
    }

    public boolean empty() {
        return stackin.isEmpty()&&stackout.isEmpty();
    }
    //自定义颠倒stack中所有元素的 函数
    public void updown(Deque<Integer> stackin,Deque<Integer> stackout){

        //使用惰性转移策略，可使均摊时间复杂度降到o1
        if(!stackout.isEmpty()){return;}
        while(!stackin.isEmpty()){
            stackout.push(stackin.pop());
        }
    }
}

/**
 * Your MyQueue object will be instantiated and called as such:
 * MyQueue obj = new MyQueue();
 * obj.push(x);
 * int param_2 = obj.pop();
 * int param_3 = obj.peek();
 * boolean param_4 = obj.empty();
 */
