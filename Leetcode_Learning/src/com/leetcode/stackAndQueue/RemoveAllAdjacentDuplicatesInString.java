package com.leetcode.stackAndQueue;

import java.util.ArrayDeque;
import java.util.Deque;

public class RemoveAllAdjacentDuplicatesInString {
    public String removeDuplicates(String s) {
        char[] string = s.toCharArray();
        Deque<Character> deque = new ArrayDeque<>();
        for (int i = 0; i < string.length; i++) {
            if(i==0){
                deque.push(string[i]);
            }else {
                if(deque.isEmpty()||deque.peek()!=string[i]){
                    deque.push(string[i]);
                }else {
                    deque.pop();
                }
            }
        }
        StringBuilder stringBuilder = new StringBuilder();
        while(!deque.isEmpty()){
            stringBuilder.append(deque.pollLast());
        }
        return stringBuilder.toString();

    }

    public static void main(String[] args) {
        RemoveAllAdjacentDuplicatesInString removeAllAdjacentDuplicatesInString = new RemoveAllAdjacentDuplicatesInString();
        System.out.println(removeAllAdjacentDuplicatesInString.removeDuplicates("abbaca"));
    }
}
