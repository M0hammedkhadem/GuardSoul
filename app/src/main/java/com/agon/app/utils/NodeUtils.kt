package com.agon.app.utils

import android.view.accessibility.AccessibilityNodeInfo
import java.util.Stack

enum class TraversalAction {
    STOP,
    CONTINUE,
    SKIP_CHILDREN
}

object NodeUtils {

    fun safeRecycle(node: AccessibilityNodeInfo?) {
        if (node != null) {
            try {
                node.recycle()
            } catch (_: IllegalStateException) { }
        }
    }

    fun recycleAll(nodes: Collection<AccessibilityNodeInfo?>) {
        for (node in nodes) safeRecycle(node)
    }

    fun recycleAll(vararg nodes: AccessibilityNodeInfo?) {
        for (node in nodes) safeRecycle(node)
    }

    /**
     * BFS traversal with auto-recycling.
     * Root is recycled by the traversal; do NOT recycle it in the caller.
     * Returns true if visitor returned STOP.
     */
    fun bfs(
        root: AccessibilityNodeInfo,
        maxNodes: Int = 200,
        visitor: (AccessibilityNodeInfo) -> TraversalAction
    ): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var count = 0

        try {
            while (queue.isNotEmpty() && count < maxNodes) {
                val current = queue.removeFirst()
                count++
                try {
                    when (visitor(current)) {
                        TraversalAction.STOP -> return true
                        TraversalAction.SKIP_CHILDREN -> { }
                        TraversalAction.CONTINUE -> {
                            val childCount = current.childCount
                            for (i in 0 until childCount) {
                                if (count >= maxNodes) break
                                val child = current.getChild(i) ?: continue
                                queue.addLast(child)
                            }
                        }
                    }
                } finally {
                    safeRecycle(current)
                }
            }
            return false
        } finally {
            for (node in queue) safeRecycle(node)
            queue.clear()
        }
    }

    /**
     * DFS traversal with auto-recycling.
     * Root is recycled by the traversal; do NOT recycle it in the caller.
     * Returns true if visitor returned STOP.
     */
    fun dfs(
        root: AccessibilityNodeInfo,
        maxNodes: Int = 400,
        visitor: (AccessibilityNodeInfo) -> TraversalAction
    ): Boolean {
        val stack = Stack<AccessibilityNodeInfo>()
        stack.push(root)
        var count = 0

        try {
            while (stack.isNotEmpty() && count < maxNodes) {
                val node = stack.pop()
                count++
                try {
                    when (visitor(node)) {
                        TraversalAction.STOP -> return true
                        TraversalAction.SKIP_CHILDREN -> { }
                        TraversalAction.CONTINUE -> {
                            val childCount = node.childCount
                            for (i in 0 until childCount) {
                                if (count >= maxNodes) break
                                val child = node.getChild(i) ?: continue
                                stack.push(child)
                            }
                        }
                    }
                } finally {
                    safeRecycle(node)
                }
            }
            return false
        } finally {
            while (stack.isNotEmpty()) safeRecycle(stack.pop())
        }
    }
}
