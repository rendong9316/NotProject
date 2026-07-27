package com.leetcode.HashTable;

public class RansomNote {
    public boolean canConstruct(String ransomNote, String magazine) {
        char[] ran = ransomNote.toCharArray();
        char[] mag = magazine.toCharArray();
        int[] ranNum = new int[26];
        int[] magNum = new int[26];

        for (char c : ran) {
            ranNum[c - 'a']++;
        }
        for (char c : mag){
            magNum[c - 'a']++;
        }

        for (int i = 0; i < 26; i++) {
            if(magNum[i]<ranNum[i]){
                return false;
            }
        }
        return true;

    }
}
