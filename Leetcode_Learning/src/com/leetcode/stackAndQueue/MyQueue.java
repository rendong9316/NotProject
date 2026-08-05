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
        stackout=updown(stackin);
        stackout.pop();
        stackin=updown(stackout);
        return stackout.pop();

    }

    public int peek() {
        stackout=updown(stackin);
        return stackout.peek();
    }

    public boolean empty() {
        return stackin.isEmpty();
    }
    //自定义颠倒stack中所有元素的 函数
    public Deque<Integer> updown(Deque<Integer> stackin){
        Deque<Integer> stacktem = new ArrayDeque<>();
        while(stackin.isEmpty()!=true){
            stacktem.push(stackin.pop());
        }
        return stacktem;

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
