package com.leetcode.LinkedList;

public class LinkedListCycleII {
    public ListNode detectCycle(ListNode head) {

        //快慢指针
        //涉及数学推到，很有意思，就像记二级结论一样
        //这个必须定期复习
        ListNode fast = head;
        ListNode slow = head;
        ListNode index2 = null;
        ListNode index1 = head;
        while(fast!=null&&fast.next!=null){
            fast=fast.next.next;
            slow=slow.next;
            if(fast==slow){
                index2 = fast;
                break;
            }
        }
        if(index2==null){
            return null;
        }else{
            while(true){
                if(index1==index2){
                    return index1;
                }
                index1=index1.next;
                index2=index2.next;
            }
        }
    }
}
