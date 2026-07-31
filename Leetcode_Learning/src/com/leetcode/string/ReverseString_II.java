package com.leetcode.string;

public class ReverseString_II {
    public String reverseStr(String s, int k) {

        //自己想的模拟方法非常复杂，甚至还考虑用字符列表，时间空间复杂度都将爆炸
        //Carl的思路很妙，步进不再是1，而是2k，直接非常简洁

        char[] arr = s.toCharArray();
        for (int i = 0; i < arr.length; i+=2*k) {
            //使用双指针，反转i到i+k（索引）这几个值
            //反转之前就要进行逻辑判断，看长度够不够

            //一定要抽离出reverse函数，不然太容易错了，我最后也没写对
            //reverse传参：起始、终止索引，完整字符数组
            if(i+k>=arr.length){
/*                for (int j = i; j < i+(arr.length-i)/2; j++) {
                    char tem = arr[j];
                    arr[j]=arr[i+arr.length-j-1];
                    arr[i+arr.length-j-1]=tem;
                }*/
                continue;
            }else{
/*                for (int j = i; j < i+k/2; j++) {
                    char tem = arr[j];
                    arr[j]=arr[i+k+i-j-1];
                    arr[i+k+i-j-1]=tem;
                }*/
                continue;
            }
        }
        String result = new String(arr);
        return result;


    }

    public static void main(String[] args) {
        //char[] s = {'h','e','l','l','O'};
        String s = "abcd";
        int k = 4;
        System.out.println(s);
        ReverseString_II reverseStringIi = new ReverseString_II();
        String result = reverseStringIi.reverseStr(s,k);
        System.out.println(result);
    }
}
