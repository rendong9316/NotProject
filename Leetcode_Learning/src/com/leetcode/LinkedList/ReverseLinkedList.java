package com.leetcode.LinkedList;

public class ReverseLinkedList {
    public ListNode reverseList(ListNode head) {

        //双指针法

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


        //下面是carl的参考答案，逻辑更简洁
//        ListNode prev = null;
//        ListNode cur = head;
//        ListNode temp = null;
//        while (cur != null) {
//            temp = cur.next;// 保存下一个节点
//            cur.next = prev;
//            prev = cur;
//            cur = temp;
//        }
//        return prev;
    }


    public ListNode reverseList_digui(ListNode head) {
        return reverse(head,null);
        //递归法
    }
    private ListNode reverse(ListNode cur,ListNode pre){
        if(cur==null){
            return pre;
        }else{
            ListNode tem = cur.next;
            cur.next=pre;
            return reverse(tem,cur);
        }
    }

//    完整执行流程演示
//    链表：1 → 2 → 3 → null
//    reverse(1,null)
//    tem=2，1.next=null，递归 reverse (2,1)
//    reverse(2,1)
//    tem=3，2.next=1，递归 reverse (3,2)
//    reverse(3,2)
//    tem=null，3.next=2，递归 reverse (null,3)
//    reverse (null,3)
//    cur==null，return 3（新头结点）
//    最终链表：3 → 2 → 1 → null

}
