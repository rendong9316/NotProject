package com.leetcode.stackAndQueue;

public class ImplQueueByStack_Test {
    public static void main(String[] args) {
        // 注意：源码构造函数名为 MyQueue()，而非 ImplQueueByStack()，以下按源码实际写法实例化
        MyQueue queue = new MyQueue();

        // --- 测试 empty() 初始状态 ---
        System.out.println("=== 初始 empty: " + queue.empty() + " (期望 false) ===");

        // --- 测试 push + peek + pop 基本顺序 ---
        queue.push(1);
        System.out.println("push 1 后 empty: " + queue.empty() + " (期望 false)");
        System.out.println("peek: " + queue.peek() + " (期望 1)");

        queue.push(2);
        System.out.println("push 2 后 peek: " + queue.peek() + " (期望 1)");

        queue.push(3);
        System.out.println("push 3 后 peek: " + queue.peek() + " (期望 1)");

        int val1 = queue.pop();
        System.out.println("pop: " + val1 + " (期望 1)");

        int val2 = queue.pop();
        System.out.println("pop: " + val2 + " (期望 2)");

        System.out.println("peek: " + queue.peek() + " (期望 3)");

        int val3 = queue.pop();
        System.out.println("pop: " + val3 + " (期望 3)");

        System.out.println("empty: " + queue.empty() + " (期望 true)");

        // --- 测试空队列 pop/peek 是否会报错 ---
        System.out.println("\n=== 空队列测试 ===");
        try {
            queue.pop();
            System.out.println("空队列 pop: 未报错（但结果可能异常）");
        } catch (Exception e) {
            System.out.println("空队列 pop 抛出异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        try {
            queue.peek();
            System.out.println("空队列 peek: 未报错（但结果可能异常）");
        } catch (Exception e) {
            System.out.println("空队列 peek 抛出异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        // --- 测试大量元素后交替 push/pop ---
        System.out.println("\n=== 交替 push/pop 测试 ===");
        MyQueue queue2 = new MyQueue();
        for (int i = 1; i <= 10; i++) {
            queue2.push(i);
        }
        System.out.println("push 1~10 后 empty: " + queue2.empty() + " (期望 false)");
        for (int i = 1; i <= 10; i++) {
            int v = queue2.pop();
            System.out.print(v + " ");
        }
        System.out.println("\n全部 pop 后 empty: " + queue2.empty() + " (期望 true)");

        // --- 源码注释说明存在的问题 ---
        System.out.println("\n=== 源码已知问题 ===");
        System.out.println("1. 构造函数名写为 MyQueue()，但类名为 ImplQueueByStack，编译会报错");
        System.out.println("2. pop() 方法中调用了两次 pop()，且中间执行了 updown 颠倒操作，逻辑有误");
        System.out.println("3. peek() 调用 updown 后没有还原 stackin，导致 peek 后内部状态被破坏");
    }
}
