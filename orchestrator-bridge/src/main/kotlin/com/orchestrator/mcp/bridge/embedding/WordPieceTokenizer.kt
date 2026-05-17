package com.orchestrator.mcp.bridge.embedding

/**
 * Simple WordPiece tokenizer using tokenizer.json vocab.
 * Handles CLS/SEP tokens, truncation, and padding.
 */
class WordPieceTokenizer(private val vocab: Map<String, Int>) {
    private val clsId = vocab["[CLS]"] ?: 101
    private val sepId = vocab["[SEP]"] ?: 102
    private val padId = vocab["[PAD]"] ?: 0
    private val unkId = vocab["[UNK]"] ?: 100

    /** Tokenize text into input_ids with CLS/SEP, truncate/pad to maxLen. */
    fun tokenize(text: String, maxLen: Int): IntArray {
        val tokens = wordPieceTokenize(text.lowercase())
        val truncated = tokens.take(maxLen - 2)
        val ids = mutableListOf(clsId)
        ids.addAll(truncated)
        ids.add(sepId)
        while (ids.size < maxLen) ids.add(padId)
        return ids.toIntArray()
    }

    private fun wordPieceTokenize(text: String): List<Int> {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val ids = mutableListOf<Int>()
        for (word in words) {
            ids.addAll(tokenizeWord(word))
            if (ids.size >= OnnxInference.MAX_SEQ_LEN - 2) break
        }
        return ids
    }

    private fun tokenizeWord(word: String): List<Int> {
        val ids = mutableListOf<Int>()
        var start = 0
        while (start < word.length) {
            var found = false
            for (end in word.length downTo start + 1) {
                val sub = if (start == 0) word.substring(start, end) else "##${word.substring(start, end)}"
                val id = vocab[sub]
                if (id != null) {
                    ids.add(id)
                    start = end
                    found = true
                    break
                }
            }
            if (!found) {
                ids.add(unkId)
                start++
            }
        }
        return ids
    }
}
