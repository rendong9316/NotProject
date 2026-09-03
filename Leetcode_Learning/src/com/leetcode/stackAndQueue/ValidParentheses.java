package com.leetcode.stackAndQueue;

import java.util.ArrayDeque;
import java.util.Deque;

public class ValidParentheses {
    public boolean ValidParentheses(String s) {
        char[] string = s.toCharArray();
        Deque<Character> stack = new ArrayDeque<>();
        for (int i = 0; i < string.length; i++) {
            if(string[i]=='('){
                stack.push(')');
            } else if (string[i]=='[') {
                stack.push(']');
            } else if (string[i]=='{') {
                stack.push('}');
                //下面是第一个要注意的点：先判断是否为空，避免空指针异常
            } else if (stack.isEmpty()||string[i]!=stack.pop()) {
                return false;
            }
        }
        if(!stack.isEmpty()){
            return false;
        }
        return true;

    }

    public static void main(String[] args) {
        String s = "()";
        ValidParentheses validParentheses = new ValidParentheses();
        System.out.println(validParentheses.ValidParentheses(s));
    }
}
