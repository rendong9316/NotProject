package com.leetcode.LinkedList;

//这道题很好
//对于类和方法有了进一步的认识

public class DesignLinkedList {

    //先要定义私有化的成员变量

    //先来一个私有类，定义链表
    private class ListNode {
        int val;
        ListNode next;
        ListNode(){}
        ListNode(int val){this.val=val;}
        ListNode(int val,ListNode next){this.val=val;this.next=next;}
    }

    //再来几个好用的私有成员变量
    private int size;

    //同样引入虚拟头节点概念，便于后续增删改查操作
    private ListNode dummy_head;

    public DesignLinkedList() {
        //这叫构造方法
        this.size=0;
        this.dummy_head=new ListNode(0);
    }

    public int get(int index) {
        if(index>=this.size||index<0){
            return -1;
        }else{
            ListNode cur = this.dummy_head;
            for (int i = 0; i <= index; i++) {
                cur = cur.next;
            }
            return cur.val;
        }
    }

    public void addAtHead(int val) {
        size++;
        ListNode head=new ListNode(val);
       //这俩注意顺序
        head.next=dummy_head.next;
        dummy_head.next=head;
    }

    public void addAtTail(int val) {
        ListNode tail = new ListNode(val);
        ListNode cur = this.dummy_head;

        //警惕空指针异常
/*        for (int i = 0; i <=size; i++) {
            cur=cur.next;
        }*/
        while(cur.next!=null){
            cur=cur.next;
        }
        cur.next= tail;
        size++;
    }

    public void addAtIndex(int index, int val) {
        if(index>size||index<0){
            return;
        }
        if(index==size){
            this.addAtTail(val);//这里就有size++
        }else{
            ListNode cur = this.dummy_head;
            ListNode newnode = new ListNode(val);
            for (int i = 0; i < index; i++) {
                cur=cur.next;
            }
            newnode.next=cur.next;
            cur.next=newnode;

            size++;
        }
        //size++;
        //上面调用自身方法里面就有size++了

    }

    public void deleteAtIndex(int index) {
        if(index>=size||index<0){
            return;
        }else{
            ListNode cur = this.dummy_head;
            for (int i = 0; i < index; i++) {
                cur=cur.next;
            }
            cur.next=cur.next.next;
        }
        size--;

    }
}
