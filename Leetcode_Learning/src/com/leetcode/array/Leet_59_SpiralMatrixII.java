package com.leetcode.array;

public class Leet_59_SpiralMatrixII {
    public int[][] solution(int n){
        int[][] result = new int [n][n];
        //一共要画n/2圈，每圈四条边，也就是四个for/使用左闭右开逻辑
        int num = 1;
        for (int i = 0; i < n/2; i++) {
            //执行n/2圈的绘制
            for (int j = i ; j < n-i-1; j++) {
                //第一条横边
                //第一圈画n-1个点（左闭右开），第二圈画n-3个点
                result[i][j]=num;
                num ++;
            }
            for (int k = i  ; k < n-i-1 ; k++) {
                //右边竖边
                result[k][n-i-1]=num;
                num ++;
            }
            for (int l = n-i-1; l >i; l--) {
                //底边
                result[n-i-1][l]=num;
                num ++;
            }
            for (int m = n-i-1; m >i ; m--) {
                result[m][i]=num;
                num ++;
            }
        }
        if(n%2==1){
            result[n/2][n/2]=n*n;
        }
        return result;
    }
}
