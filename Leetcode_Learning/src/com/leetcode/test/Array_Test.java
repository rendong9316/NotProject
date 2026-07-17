package com.leetcode.test;

import com.leetcode.array.Leet_59_SpiralMatrixII;

public class Array_Test {
    public static void main(String[] args) {
        Leet_59_SpiralMatrixII leet59SpiralMatrixII = new Leet_59_SpiralMatrixII();
        int[][] a = leet59SpiralMatrixII.solution(5);
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                System.out.println(a[i][j]);

            }

        }
    }




}
