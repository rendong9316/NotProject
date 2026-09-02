package com.leetcode.stackAndQueue;

import java.util.ArrayDeque;
import java.util.Queue;

//自己写的石山代码

public class MyStack {
    private Queue<Integer> queue1;
    private Queue<Integer> queue2;

    public MyStack() {
        queue1 = new ArrayDeque<>();
        queue2 = new ArrayDeque<>();
    }

    public void push(int x) {
        queue1.offer(x);
    }

    public int pop() {
        int size = queue1.size();
        while (size>1){
            queue2.offer(queue1.poll());
            size--;
        }
        int result = queue1.poll();
        while(!queue2.isEmpty()){
            queue1.offer(queue2.poll());
        }
        return result;

    }

    public int top() {
        int size = queue1.size();
        while (size>1){
            queue2.offer(queue1.poll());
            size--;
        }
        int result = queue1.peek();
        queue2.offer(queue1.poll());
        while(!queue2.isEmpty()){
            queue1.offer(queue2.poll());
        }
        return result;
    }

    public boolean empty() {
        return queue1.isEmpty();
    }
}

/**
 * Your MyStack object will be instantiated and called as such:
 * MyStack obj = new MyStack();
 * obj.push(x);
 * int param_2 = obj.pop();
 * int param_3 = obj.top();
 * boolean param_4 = obj.empty();
 */
