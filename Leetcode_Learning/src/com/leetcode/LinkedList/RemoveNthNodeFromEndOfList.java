package com.leetcode.LinkedList;

import java.util.List;

public class RemoveNthNodeFromEndOfList {
    public ListNode removeNthFromEnd(ListNode head, int n) {
        ListNode dummy_cur = new ListNode(0,head);
        ListNode dummy_pre = new ListNode(1,head);
        ListNode tem = head;
        ListNode pre_tem = new ListNode(2,dummy_pre);


        int count = 0;

        while (dummy_cur.next!=null){
            dummy_cur = dummy_cur.next;
            count++;
            if(count>=n){
                pre_tem = pre_tem.next;
                dummy_pre = dummy_pre.next;
                //需要删掉pre节点
            }
        }
        pre_tem.next = dummy_pre.next;
        if(count == n){
            return pre_tem.next;
        }else{
            return tem;
        }


    }
}
