package com.leetcode.string;

public class ReverseWordsInString {
    public String reverseWords(String s) {
        char[] arr = s.toCharArray();
        StringBuilder sb_tem = new StringBuilder();
        String tem = new String();
        StringBuilder sb = new StringBuilder();
        for (int i = arr.length-1; i >=0; i--) {
            if(arr[i]==' '){
                if(i!=arr.length-1&&arr[i+1]!=' '){
                    sb_tem.reverse();
                    tem = sb_tem.toString();
                    sb.append(tem);
                    sb.append(' ');
                    sb_tem.setLength(0);
                    //tem = "";
                }else{
                    continue;
                }
            }else{
                sb_tem.append(arr[i]);
                if(i==0){
                    sb_tem.reverse();
                    tem = sb_tem.toString();
                    sb.append(tem);
                    sb.append(' ');
                    sb_tem.setLength(0);
                    //tem = "";
                }else{
                    continue;
                }
            }
        }
        if(sb.length()>0){
            sb.setLength(sb.length()-1);
        }
        String res = sb.toString();
        return res;
    }

    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder();
        sb.append("aaa");
        sb.append(" ");
        sb.append("bbb");
        sb.reverse();
        String res = sb.toString();
        System.out.println(res);

        ReverseWordsInString reverseWordsInString = new ReverseWordsInString();
        System.out.println("”"+reverseWordsInString.reverseWords("  the   sky is blue ")+"”");
    }
}
