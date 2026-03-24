package com.forge.admin.common.permission;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 数据权限上下文持有者
 *
 * 使用 ThreadLocal 栈管理上下文，支持嵌套调用
 * 基于 shi9-boot 设计
 *
 * @author standadmin
 * @since 2026-03-04
 */
public class DataPermissionContextHolder {

    private static final ThreadLocal<Deque<DataPermissionContext>> STACK =
        ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * 获取当前栈顶的上下文
     *
     * @return 当前上下文，不存在返回 null
     */
    public static DataPermissionContext peek() {
        return STACK.get().peek();
    }

    /**
     * 推入新的上下文到栈顶
     *
     * @return 新创建的上下文
     */
    public static DataPermissionContext push() {
        var context = new DataPermissionContext();
        STACK.get().push(context);
        return context;
    }

    /**
     * 弹出栈顶的上下文
     */
    public static void pop() {
        var stack = STACK.get();
        stack.pop();
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    /**
     * 清空当前线程的栈
     */
    public static void clear() {
        STACK.remove();
    }

    /**
     * 获取栈的大小
     *
     * @return 栈的大小
     */
    public static int size() {
        return STACK.get().size();
    }
}
