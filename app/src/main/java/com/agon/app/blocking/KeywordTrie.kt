package com.agon.app.blocking

/**
 * Aho-Corasick-style keyword trie with failure links, plus a
 * word-boundary check on the matched span. Used by
 * [ContentFilterEngine] to test whether a window's concatenated
 * text contains any blacklisted keyword.
 *
 * The trie is built from a list of keywords (case-insensitive);
 * [hasMatch] returns `true` the first time any keyword is found
 * bounded by non-letter, non-digit characters on both sides —
 * so "pornographic" doesn't match "porn" but "porn video" does.
 *
 * Both methods are `@Synchronized` so the trie can be safely
 * rebuilt from a settings flow while [hasMatch] is being called
 * from the accessibility event thread.
 */
class KeywordTrie {

    private class Node {
        val children = mutableMapOf<Char, Node>()
        var failure: Node? = null
        var isLeaf = false
        val outputs = mutableListOf<String>()
    }

    private var root = Node()

    @Synchronized
    fun build(keywords: List<String>) {
        val newRoot = Node()
        for (kw in keywords) {
            val word = kw.lowercase().trim()
            if (word.isEmpty()) continue
            var current = newRoot
            for (char in word) current = current.children.getOrPut(char) { Node() }
            current.isLeaf = true
            current.outputs.add(word)
        }

        val queue = java.util.ArrayDeque<Node>()
        for (child in newRoot.children.values) {
            child.failure = newRoot
            queue.add(child)
        }
        while (queue.isNotEmpty()) {
            val current = queue.poll() ?: continue
            for ((char, child) in current.children) {
                var f = current.failure
                while (f != null && !f.children.containsKey(char)) f = f.failure
                child.failure = if (f == null) newRoot else f.children[char]
                child.outputs.addAll(child.failure?.outputs ?: emptyList())
                queue.add(child)
            }
        }
        root = newRoot
    }

    @Synchronized
    fun hasMatch(text: String): Boolean {
        val lower = text.lowercase()
        var current = root
        for (i in lower.indices) {
            while (current != root && !current.children.containsKey(lower[i])) {
                current = current.failure ?: root
            }
            current = current.children[lower[i]] ?: root
            if (current.isLeaf || current.outputs.isNotEmpty()) {
                val longestMatch = current.outputs.maxByOrNull { it.length } ?: ""
                val start = i - longestMatch.length + 1
                val before = if (start > 0) lower[start - 1] else ' '
                val after = if (i + 1 < lower.length) lower[i + 1] else ' '
                if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            }
        }
        return false
    }
}
