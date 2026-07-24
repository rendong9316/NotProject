package com.leetcode.recursion;

import com.sun.jdi.PathSearchingVirtualMachine;

import java.security.PublicKey;

public class Hanoi {
    //form，assistant，to
    public void hanoiSolve(int n, char F, char A, char T){
        //看b站这个视频豁然开朗
        //https://www.bilibili.com/video/BV1LiS1YSEgF/?spm_id_from=333.337.search-card.all.click&vd_source=10162be6199a50650da857a958bee013
        //核心思想：
        //最小结构为操作最后一个大盘，这是最小问题
        //所有步骤都是为了最后这个大盘设立的
        //一切输出也都是为了描述大盘的移动
        //但是当大盘上面有东西时，就把上面整体视为一个新的小汉诺塔进行操作，但是！
        //对于上面小塔的操作，目的不是移动到第三根T柱子，而是目的移到中间的A柱子

        //hanoi函数全过程就叫做：通过A作为中转，将塔从F移动到T。中间细节一定无法人脑模拟
        if(n==1){
            System.out.println("将"+n+"号圆盘从"+F+"移到"+T);//执行对大盘的操作（全文目的就是干大盘）
        }else{
            //把大盘上面小塔最终目的移到A
            hanoiSolve(n-1,F,T,A);
            System.out.println("将"+n+"号圆盘从"+F+"移到"+T);//同样操作大盘到目的地
            //再把A中转柱上的小塔移过来
            hanoiSolve(n-1,A,F,T);

        }


    }

    public static void main(String[] args) {
        Hanoi hanoi = new Hanoi();
        char a ='F';
        char b = 'A';
        char c = 'T';
        hanoi.hanoiSolve(3,a,b,c);
    }

}
