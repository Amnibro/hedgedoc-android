package org.hedgedoc.android.data

/**
 * ShareJS / HedgeDoc 1.x text operations.
 * Positive Int = retain, negative Int = delete, String = insert.
 */
class TextOperation {
    private val ops = ArrayList<Any>()

    val parts: List<Any> get() = ops

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

    /** Accepts either a count or an already-negative delete op. */
    fun delete(n: Int): TextOperation {
        val amount = kotlin.math.abs(n)
        if (amount == 0) return this
        val last = ops.lastOrNull()
        if (last is Int && last < 0) {
            ops[ops.lastIndex] = last - amount
        } else {
            ops.add(-amount)
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

    fun isNoop(): Boolean = ops.none { it is String || (it is Int && it < 0) }

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

    /**
     * Sequential composition: `a.compose(b).apply(doc) == b.apply(a.apply(doc))`.
     * Port of ot.js TextOperation.compose.
     */
    fun compose(other: TextOperation): TextOperation {
        if (targetLength() != other.baseLength()) {
            throw IllegalArgumentException("Cannot compose: target ${targetLength()} != base ${other.baseLength()}")
        }
        val result = TextOperation()
        val a = ops
        val b = other.ops
        var i = 0
        var j = 0
        var op1: Any? = a.getOrNull(i++)
        var op2: Any? = b.getOrNull(j++)
        while (true) {
            if (op1 == null && op2 == null) break
            if (op1 is Int && op1 < 0) {
                result.delete(op1)
                op1 = a.getOrNull(i++)
                continue
            }
            if (op2 is String) {
                result.insert(op2)
                op2 = b.getOrNull(j++)
                continue
            }
            if (op1 == null) throw IllegalArgumentException("Cannot compose: first operation is too short.")
            if (op2 == null) throw IllegalArgumentException("Cannot compose: first operation is too long.")
            if (op1 is Int && op2 is Int && op2 > 0) {
                when {
                    op1 > op2 -> {
                        result.retain(op2)
                        op1 -= op2
                        op2 = b.getOrNull(j++)
                    }
                    op1 == op2 -> {
                        result.retain(op1)
                        op1 = a.getOrNull(i++)
                        op2 = b.getOrNull(j++)
                    }
                    else -> {
                        result.retain(op1)
                        op2 -= op1
                        op1 = a.getOrNull(i++)
                    }
                }
            } else if (op1 is String && op2 is Int && op2 < 0) {
                when {
                    op1.length > -op2 -> {
                        op1 = op1.substring(-op2)
                        op2 = b.getOrNull(j++)
                    }
                    op1.length == -op2 -> {
                        op1 = a.getOrNull(i++)
                        op2 = b.getOrNull(j++)
                    }
                    else -> {
                        op2 += op1.length
                        op1 = a.getOrNull(i++)
                    }
                }
            } else if (op1 is String && op2 is Int) {
                when {
                    op1.length > op2 -> {
                        result.insert(op1.substring(0, op2))
                        op1 = op1.substring(op2)
                        op2 = b.getOrNull(j++)
                    }
                    op1.length == op2 -> {
                        result.insert(op1)
                        op1 = a.getOrNull(i++)
                        op2 = b.getOrNull(j++)
                    }
                    else -> {
                        result.insert(op1)
                        op2 -= op1.length
                        op1 = a.getOrNull(i++)
                    }
                }
            } else if (op1 is Int && op2 is Int) {
                when {
                    op1 > -op2 -> {
                        result.delete(op2)
                        op1 += op2
                        op2 = b.getOrNull(j++)
                    }
                    op1 == -op2 -> {
                        result.delete(op2)
                        op1 = a.getOrNull(i++)
                        op2 = b.getOrNull(j++)
                    }
                    else -> {
                        result.delete(op1)
                        op2 += op1
                        op1 = a.getOrNull(i++)
                    }
                }
            } else {
                throw IllegalArgumentException("Cannot compose: unexpected op pair $op1 / $op2")
            }
        }
        return result
    }

    fun toJsonList(): List<Any> = ops.toList()

    companion object {
        fun replaceAll(old: String, new: String): TextOperation {
            return TextOperation().delete(old.length).insert(new)
        }

        /** Smallest sensible retain/delete/insert triple for an edit to [old]. */
        fun diff(old: String, new: String): TextOperation {
            var prefix = 0
            val max = minOf(old.length, new.length)
            while (prefix < max && old[prefix] == new[prefix]) prefix++
            var suffix = 0
            while (suffix < max - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
            return TextOperation()
                .retain(prefix)
                .delete(old.length - prefix - suffix)
                .insert(new.substring(prefix, new.length - suffix))
                .retain(suffix)
        }

        fun fromJsonList(values: List<Any?>): TextOperation {
            val op = TextOperation()
            for (value in values) {
                when (value) {
                    is String -> op.insert(value)
                    is Number -> {
                        val n = value.toInt()
                        if (n >= 0) op.retain(n) else op.delete(n)
                    }
                    else -> throw IllegalArgumentException("Unknown operation part: $value")
                }
            }
            return op
        }

        /**
         * Concurrent composition. Both operations start from the same document;
         * `a.compose(bPrime) == b.compose(aPrime)`. Port of ot.js TextOperation.transform.
         */
        fun transform(first: TextOperation, second: TextOperation): Pair<TextOperation, TextOperation> {
            if (first.baseLength() != second.baseLength()) {
                throw IllegalArgumentException(
                    "Cannot transform: base ${first.baseLength()} != ${second.baseLength()}",
                )
            }
            val firstPrime = TextOperation()
            val secondPrime = TextOperation()
            val a = first.ops
            val b = second.ops
            var i = 0
            var j = 0
            var op1: Any? = a.getOrNull(i++)
            var op2: Any? = b.getOrNull(j++)
            while (true) {
                if (op1 == null && op2 == null) break
                if (op1 is String) {
                    firstPrime.insert(op1)
                    secondPrime.retain(op1.length)
                    op1 = a.getOrNull(i++)
                    continue
                }
                if (op2 is String) {
                    firstPrime.retain(op2.length)
                    secondPrime.insert(op2)
                    op2 = b.getOrNull(j++)
                    continue
                }
                if (op1 == null) throw IllegalArgumentException("Cannot transform: first operation is too short.")
                if (op2 == null) throw IllegalArgumentException("Cannot transform: first operation is too long.")
                if (op1 !is Int || op2 !is Int) {
                    throw IllegalArgumentException("Cannot transform: unexpected op pair $op1 / $op2")
                }
                if (op1 > 0 && op2 > 0) {
                    val shared: Int
                    when {
                        op1 > op2 -> {
                            shared = op2
                            op1 -= op2
                            op2 = b.getOrNull(j++)
                        }
                        op1 == op2 -> {
                            shared = op2
                            op1 = a.getOrNull(i++)
                            op2 = b.getOrNull(j++)
                        }
                        else -> {
                            shared = op1
                            op2 -= op1
                            op1 = a.getOrNull(i++)
                        }
                    }
                    firstPrime.retain(shared)
                    secondPrime.retain(shared)
                } else if (op1 < 0 && op2 < 0) {
                    when {
                        -op1 > -op2 -> {
                            op1 -= op2
                            op2 = b.getOrNull(j++)
                        }
                        op1 == op2 -> {
                            op1 = a.getOrNull(i++)
                            op2 = b.getOrNull(j++)
                        }
                        else -> {
                            op2 -= op1
                            op1 = a.getOrNull(i++)
                        }
                    }
                } else if (op1 < 0 && op2 > 0) {
                    val shared: Int
                    when {
                        -op1 > op2 -> {
                            shared = op2
                            op1 += op2
                            op2 = b.getOrNull(j++)
                        }
                        -op1 == op2 -> {
                            shared = op2
                            op1 = a.getOrNull(i++)
                            op2 = b.getOrNull(j++)
                        }
                        else -> {
                            shared = -op1
                            op2 += op1
                            op1 = a.getOrNull(i++)
                        }
                    }
                    firstPrime.delete(shared)
                } else {
                    val shared: Int
                    when {
                        op1 > -op2 -> {
                            shared = -op2
                            op1 += op2
                            op2 = b.getOrNull(j++)
                        }
                        op1 == -op2 -> {
                            shared = op1
                            op1 = a.getOrNull(i++)
                            op2 = b.getOrNull(j++)
                        }
                        else -> {
                            shared = op1
                            op2 += op1
                            op1 = a.getOrNull(i++)
                        }
                    }
                    secondPrime.delete(shared)
                }
            }
            return firstPrime to secondPrime
        }
    }
}
