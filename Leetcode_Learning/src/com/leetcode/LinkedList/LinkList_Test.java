package com.leetcode.LinkedList;

public class LinkList_Test {
    public static void main(String[] args) {
        // 1. 创建链表: 1 -> 2 -> 6 -> 3 -> 4 -> 5 -> 6
        ListNode head = new ListNode(1);
        head.next = new ListNode(2);
/*        head.next.next = new ListNode(6);
        head.next.next.next = new ListNode(3);
        head.next.next.next.next = new ListNode(4);
        head.next.next.next.next.next = new ListNode(5);
        head.next.next.next.next.next.next = new ListNode(6);*/

        // 2. 打印原链表
        System.out.print("原链表: ");
        printList(head);

/*        // 3. 调用方法删除 val=6
        RemoveLinkedListElements solution = new RemoveLinkedListElements();
        ListNode result = solution.removeElements_dummy(head, 1);

        // 4. 打印结果
        System.out.print("删除后: ");
        printList(result);

        // 反转链表
        ReverseLinkedList reverseLinkedList = new ReverseLinkedList();
        ListNode resullt2 = reverseLinkedList.reverseList_digui(head);
        System.out.println("反转链表后：");
        printList(resullt2);*/

        //删除链表倒数第n个元素
        RemoveNthNodeFromEndOfList removeNthNodeFromEndOfList = new RemoveNthNodeFromEndOfList();
        ListNode result3 = removeNthNodeFromEndOfList.removeNthFromEnd(head,2);
        System.out.println("删除链表倒数第n个元素后：");
        printList(result3);



    }

    // 辅助方法：打印链表
    public static void printList(ListNode head) {
        ListNode curr = head;
        while (curr != null) {
            System.out.print(curr.val);
            if (curr.next != null) {
                System.out.print(" -> ");
            }
            curr = curr.next;
        }
        System.out.println();
    }


}
