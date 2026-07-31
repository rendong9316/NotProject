package com.leetcode.string;

public class ReverseString {
    public void reverseString(char[] s) {

        //疑似过于简单了
        //就是两两交换，往中间收紧，美其名曰“双指针”，实则我本能就想出来

        for (int i = 0; i < s.length/2; i++) {
            char tem = s[i];
            s[i]=s[s.length-i-1];
            s[s.length-i-1]=tem;
        }
    }

    public static void main(String[] args) {
        char[] s = {'h','e','l','l','O'};
        System.out.println(s);
        ReverseString reverseString = new ReverseString();
        reverseString.reverseString(s);
        System.out.println(s);
    }
}
