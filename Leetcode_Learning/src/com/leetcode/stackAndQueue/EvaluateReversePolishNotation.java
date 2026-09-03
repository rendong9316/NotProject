package com.leetcode.stackAndQueue;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

public class EvaluateReversePolishNotation {
    public int evalRPN(String[] tokens) {
        Deque<String> stack = new ArrayDeque<>();
        int a = 0;
        int b = 0;
        int c = 0;
        for(String s : tokens){
            if(s!="+"&&s!="-"&&s!="*"&&s!="/"){
                stack.push(s);
            } else if (s=="+") {
                a = Integer.valueOf(stack.pop());
                b = Integer.valueOf(stack.pop());
                c = b+a;
                stack.push(String.valueOf(c));
            }else if (s=="-") {
                a = Integer.valueOf(stack.pop());
                b = Integer.valueOf(stack.pop());
                c = b-a;
                stack.push(String.valueOf(c));
            }else if (s=="*") {
                a = Integer.valueOf(stack.pop());
                b = Integer.valueOf(stack.pop());
                c = b*a;
                stack.push(String.valueOf(c));
            }else if (s=="/") {
                a = Integer.valueOf(stack.pop());
                b = Integer.valueOf(stack.pop());
                c = b/a;
                stack.push(String.valueOf(c));
            }
        }
        int res =  Integer.valueOf(stack.peek());
        return res;

    }

    public static void main(String[] args) {
        EvaluateReversePolishNotation evaluateReversePolishNotation = new EvaluateReversePolishNotation();
        String[] tokens ={"2","1","+","3","*"};
        int res = evaluateReversePolishNotation.evalRPN(tokens);
        System.out.println(res);
    }
}
