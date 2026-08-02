package com.leetcode.string;

public class FindTheIndexOfTheFirstOccurrenceInAString {
    public int strStr(String haystack, String needle) {
        //一下子想到双指针思想
        char[] hay = haystack.toCharArray();
        char[] nee = needle.toCharArray();
        for (int i = 0; i < hay.length; i++) {
            if(hay[i]==nee[0]){
                int j =i;
                int count = 0;
                for (int k = 0; k < nee.length; k++) {
                    if(hay[j]!=nee[k]){
                        count++;
                        if(j==hay.length-1&&k!=nee.length-1){
                            return -1;
                        } else if (j==hay.length-1&&k==nee.length-1) {
                            continue;
                        }else{
                            j++;
                        }
                    }else{
                        if(j==hay.length-1&&k!=nee.length-1){
                            return -1;
                        } else if (j==hay.length-1&&k==nee.length-1) {
                            continue;
                        }else{
                            j++;
                        }
                    }
                }
                if(count!=0){
                    continue;
                }else{
                    return i;
                }
            }else{
                continue;
            }
        }
        return -1;
    }
}
