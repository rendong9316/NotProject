package com.leetcode.LinkedList;

public class IntersectionOfTwoLinkedLists {
    public ListNode getIntersectionNode(ListNode headA, ListNode headB) {
        //最好想的肯定是暴力解法
        //A依次往后走，每到一个，遍历每一个B的节点，看看二者指向的next是否完全相同（同一个地址）
        ListNode cur_a = new ListNode(0,headA);
        //还是得用虚拟头节点法处理极限情况（只有一个共同节点单蹦搁那）

        while(cur_a!=null){
            ListNode cur_b = new ListNode(1,headB);
            while(cur_b!=null){
                if(cur_b.next==cur_a.next){
                    return cur_b.next;
                }
                cur_b=cur_b.next;
            }
            cur_a = cur_a.next;
        }
        return null;



    }
}
