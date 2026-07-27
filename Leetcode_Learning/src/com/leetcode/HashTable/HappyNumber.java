package com.leetcode.HashTable;

import java.util.ArrayList;
import java.util.HashSet;

public class HappyNumber {
    public boolean isHappy(int n) {
        //首先是做提取各个位数的操作
        //我先全程手搓，再看模板
        //原理：先%10，提取个位
        //再/10，再%10，提取十位
        //直到/10==0，循环结束
        //循环里应该是先%10提取位数，再/10为下一把循环做准备
        //所以循环终止条件应该是n==0
        //使用可变长度的arraylist装拆开的各个数字
        HashSet<Integer>hashSet = new HashSet<>();
        ArrayList<Integer>arrayList = new ArrayList<>();
        int tem = 0;
        while(true){
            arrayList = get_each_num(n);
            for (int i = 0; i < arrayList.size(); i++) {
                tem += arrayList.get(i)*arrayList.get(i);
            }
            n = tem;
            if(n==1){
                return true;
            }
            if(hashSet.contains(n)){
                return false;
            }
            hashSet.add(n);
            tem = 0;//第一遍写完这个忘记了，导致死循环。加上就好了！！！！
        }

    }

    public ArrayList<Integer> get_each_num(int m){
        ArrayList<Integer>arrayList = new ArrayList<>();
        int tem = 0;
        while(m!=0){
            tem = m %10;
            arrayList.add(tem);
            m=m/10;
        }
        return arrayList;
    }

    public static void main(String[] args) {
        HappyNumber happyNumber = new HappyNumber();
        System.out.println(happyNumber.isHappy(2));
    }
}
