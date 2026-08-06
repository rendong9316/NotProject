package com.leetcode.stackAndQueue;

public class ImplQueueByStack_Test {
    public static void main(String[] args) {
        // 对应题目示例输入：["MyQueue","push","push","peek","pop","empty"]
        MyQueue myQueue = new MyQueue();

        myQueue.push(1);
        System.out.println("执行push(1)");

        myQueue.push(2);
        System.out.println("执行push(2)");

        int peekRes = myQueue.peek();
        System.out.println("peek()预期1，实际输出：" + peekRes);

        int popRes = myQueue.pop();
        System.out.println("pop()预期1，实际输出：" + popRes);

        boolean emptyRes = myQueue.empty();
        System.out.println("empty()预期false，实际输出：" + emptyRes);


        // 下面再加一组测试，直接触发你的报错！
        System.out.println("\n=====复现空栈报错场景====");
        MyQueue q2 = new MyQueue();
        q2.push(10);
        int r1 = q2.pop();
        System.out.println("第一次pop得到:"+r1);
        // 此时栈已经空，再pop就直接抛NoSuchElementException
//        int r2 = q2.pop();
//        System.out.println("第二次pop得到:"+r2);
    }
}
