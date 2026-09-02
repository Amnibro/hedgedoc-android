package org.hedgedoc.android.data

/**
 * ShareJS / HedgeDoc 1.x text operations.
 * Positive Int = retain, negative Int = delete, String = insert.
 */
class TextOperation {
    private val ops = ArrayList<Any>()

    fun retain(n: Int): TextOperation {
        require(n >= 0) { "retain must be >= 0" }
        if (n == 0) return this
        val last = ops.lastOrNull()
        if (last is Int && last > 0) {
            ops[ops.lastIndex] = last + n
        } else {
            ops.add(n)
        }
        return this
    }

    fun delete(n: Int): TextOperation {
        require(n >= 0) { "delete must be >= 0" }
        if (n == 0) return this
        val last = ops.lastOrNull()
        if (last is Int && last < 0) {
            ops[ops.lastIndex] = last - n
        } else {
            ops.add(-n)
        }
        return this
    }

    fun insert(text: String): TextOperation {
        if (text.isEmpty()) return this
        val last = ops.lastOrNull()
        if (last is String) {
            ops[ops.lastIndex] = last + text
        } else {
            ops.add(text)
        }
        return this
    }

    fun baseLength(): Int {
        var length = 0
        for (op in ops) {
            when (op) {
                is Int -> length += kotlin.math.abs(op)
                is String -> Unit
            }
        }
        return length
    }

    fun targetLength(): Int {
        var length = 0
        for (op in ops) {
            when (op) {
                is Int -> if (op > 0) length += op
                is String -> length += op.length
            }
        }
        return length
    }

    fun apply(document: String): String {
        if (baseLength() != document.length) {
            throw IllegalArgumentException(
                "Operation base length ${baseLength()} does not match document ${document.length}",
            )
        }
        val out = StringBuilder()
        var index = 0
        for (op in ops) {
            when (op) {
                is Int -> {
                    if (op > 0) {
                        out.append(document, index, index + op)
                        index += op
                    } else {
                        index += -op
                    }
                }
                is String -> out.append(op)
            }
        }
        return out.toString()
    }

    fun toJsonList(): List<Any> = ops.toList()

    companion object {
        fun replaceAll(old: String, new: String): TextOperation {
            return TextOperation().delete(old.length).insert(new)
        }
    }
}
