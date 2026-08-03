package com.leetcode.string;

public class RepeatedSubstringPattern {
    public boolean reapeatedSubstring(String s){
        char[] arrr = s.toCharArray();
        if(arrr.length==1){
            return false;
        }
        for (int i = 0; i <= arrr.length/2-1; i++) {
            if(arrr.length%(i+1)!=0){
                continue;
            }else{
                int count =0;
                for (int j = 0; j < arrr.length/(i+1)-1; j++) {
                    for (int k = 0; k <=i; k++) {
                        if(arrr[k]!=arrr[k+i+1+j*(i+1)]){
                            count++;
                        }else{
                            continue;
                        }
                    }
                }
                if(count==0){
                    return true;
                }else{
                    continue;
                }
            }
        }

        return false;
    }

    public static void main(String[] args) {
        String s ="abcabcabc";
        RepeatedSubstringPattern repeatedSubstringPattern=new RepeatedSubstringPattern();
        System.out.println(repeatedSubstringPattern.reapeatedSubstring(s));
    }
}
