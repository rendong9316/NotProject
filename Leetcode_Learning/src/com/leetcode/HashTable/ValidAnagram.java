package com.leetcode.HashTable;

public class ValidAnagram {
    public boolean isAnagram(String s, String t) {
        if(s.length()!=t.length()){
            return false;
        }
        char[] ss = s.toCharArray();
        char[] tt = t.toCharArray();
        //先来试试纯数组实现（哈希表数组版）
        //创建两个长度为26的数组
        int[] arrs = new int[26];
        int[] arrt = new int[26];
        //分别记录s和t中a到z字母出现的次数，数组下标为字母索引，数值为次数
        for (int i = 0; i < s.length(); i++) {
            arrs[ss[i]-'a']++;
            arrt[tt[i]-'a']++;
        }

        for (int i = 0; i < 26; i++) {
            if(arrs[i]!=arrt[i]){
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        String s = "niger";
        String t = "niegr";
        ValidAnagram validAnagram = new ValidAnagram();
        System.out.println(validAnagram.isAnagram(s,t));

    }
}
