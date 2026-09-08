package com.leetcode.binaryTree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class BinaryTreeInorderTraversal {

/**
 * Definition for a binary tree node.
 * public class TreeNode {
 *     int val;
 *     TreeNode left;
 *     TreeNode right;
 *     TreeNode() {}
 *     TreeNode(int val) { this.val = val; }
 *     TreeNode(int val, TreeNode left, TreeNode right) {
 *         this.val = val;
 *         this.left = left;
 *         this.right = right;
 *     }
 * }
 **/
    public List<Integer> inorderTraversal(TreeNode root) {
        //中序遍历
        List<Integer> list = new ArrayList<>();
        diguiF(root,list);
        return list;
        }

    private void diguiF(TreeNode cur,List list){

        //关于递归的一点心得体会
        //整体就三件事：先输出当前树的根节点，然后对左子树执行遍历，然后对右子树进行遍历
        //相信diguiF能实现。相信的原理是有一个list.add(cur.val)步骤，有这个就能保证不断递归每个节点都有机会
        //成为root，从而依次被写入list
        if(cur==null){
            return;
        }

        diguiF(cur.left,list);
        list.add(cur.val);
        diguiF(cur.right,list);

    }


    public List<Integer> inorderTraversal_diedai(TreeNode root) {
        //中序遍历
        List<Integer> list = new ArrayList<>();
        Deque<TreeNode> deque = new ArrayDeque<>();
        if(root==null){
            return list;
        }
        TreeNode cur = root;

        while (!deque.isEmpty()||cur!=null){
            if(cur!=null){
                deque.push(cur);
                cur = cur.left;
            }else {
                TreeNode top = deque.pop();    // 只弹出一次
                list.add(top.val);              // 用保存的引用取值
                cur = top.right;                // 然后处理右子树
            }
        }

        return list;


    }




    public static void main(String[] args) {
        // 1. 手动构建一棵二叉树
        // 例如构建：    1
        //             / \
        //            2   3
        //           / \
        //          4   5

        TreeNode node4 = new TreeNode(4);
        TreeNode node5 = new TreeNode(5);
        TreeNode node2 = new TreeNode(2, node4, node5);
        TreeNode node3 = new TreeNode(3);
        TreeNode root = new TreeNode(1, node2, node3);

        // 2. 创建测试对象，调用方法
        BinaryTreeInorderTraversal solution = new BinaryTreeInorderTraversal();
        List<Integer> result = solution.inorderTraversal_diedai(root);

        // 3. 打印结果
        System.out.println("前序遍历结果：" + result);
        // 期望输出：[1, 2, 4, 5, 3]
    }



    }





