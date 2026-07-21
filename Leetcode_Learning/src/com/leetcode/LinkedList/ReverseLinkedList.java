package com.leetcode.LinkedList;

public class ReverseLinkedList {
    public ListNode reverseList(ListNode head) {
        ListNode cur = new ListNode();
        ListNode pre = new ListNode();
        ListNode tem = new ListNode();
        cur = head;
        pre.next = cur;
        pre = null;

        while(cur!=null){
            tem = cur.next;
            cur.next = pre;
            //cur = tem;
            //这里一定是先移动pre
            pre = cur;
            cur = tem;

        }
        return pre;



    }
}
