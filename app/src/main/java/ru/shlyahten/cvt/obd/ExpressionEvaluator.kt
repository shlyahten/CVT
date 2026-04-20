package ru.shlyahten.cvt.obd

import kotlin.math.pow

/**
 * Tiny expression evaluator for Torque-like equations.
 * Supports: + - * / ^ ( ) and variables (A, AA, N, AB, AC, AD, ...)
 */
object ExpressionEvaluator {
    fun eval(expression: String, variables: Map<String, Double>): Double {
        val tokens = tokenize(expression)
        val rpn = toRpn(tokens)
        return evalRpn(rpn, variables)
    }

    private sealed interface Token {
        data class Number(val value: Double) : Token
        data class Ident(val name: String) : Token
        data class Op(val op: Char) : Token
        data object LParen : Token
        data object RParen : Token
    }

    private fun tokenize(expr: String): List<Token> {
        val s = expr.trim()
        val out = ArrayList<Token>(s.length / 2)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c == '(' -> {
                    out += Token.LParen; i++
                }
                c == ')' -> {
                    out += Token.RParen; i++
                }
                c in charArrayOf('+', '*', '/', '^') -> {
                    out += Token.Op(c); i++
                }
                c == '-' -> {
                    // unary minus if at start or preceded by op or '('
                    val prev = out.lastOrNull()
                    val unary = prev == null || prev is Token.Op || prev is Token.LParen
                    if (unary) {
                        // parse as part of number when possible
                        val (num, next) = readNumber(s, i)
                        if (num != null) {
                            out += Token.Number(num)
                            i = next
                        } else {
                            out += Token.Op('-'); i++
                        }
                    } else {
                        out += Token.Op('-'); i++
                    }
                }
                c.isDigit() || c == '.' -> {
                    val (num, next) = readNumber(s, i)
                    out += Token.Number(num ?: error("Bad number at $i"))
                    i = next
                }
                c.isLetter() -> {
                    val start = i
                    i++
                    while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
                    out += Token.Ident(s.substring(start, i))
                }
                else -> error("Unexpected char '$c' in expression")
            }
        }
        return out
    }

    private fun readNumber(s: String, startIdx: Int): Pair<Double?, Int> {
        var i = startIdx
        if (s[i] == '-') i++
        var seenDigit = false
        while (i < s.length && s[i].isDigit()) {
            seenDigit = true; i++
        }
        if (i < s.length && s[i] == '.') {
            i++
            while (i < s.length && s[i].isDigit()) {
                seenDigit = true; i++
            }
        }
        if (!seenDigit) return null to startIdx
        val num = s.substring(startIdx, i).toDouble()
        return num to i
    }

    private fun precedence(op: Char): Int = when (op) {
        '^' -> 4
        '*', '/' -> 3
        '+', '-' -> 2
        else -> error("unknown op")
    }

    private fun isRightAssociative(op: Char): Boolean = op == '^'

    private fun toRpn(tokens: List<Token>): List<Token> {
        val out = ArrayList<Token>(tokens.size)
        val stack = ArrayDeque<Token>()
        for (t in tokens) {
            when (t) {
                is Token.Number, is Token.Ident -> out += t
                is Token.Op -> {
                    while (true) {
                        val top = stack.lastOrNull()
                        if (top is Token.Op) {
                            val p1 = precedence(t.op)
                            val p2 = precedence(top.op)
                            val pop = if (isRightAssociative(t.op)) p1 < p2 else p1 <= p2
                            if (pop) {
                                out += stack.removeLast()
                                continue
                            }
                        }
                        break
                    }
                    stack.addLast(t)
                }
                Token.LParen -> stack.addLast(t)
                Token.RParen -> {
                    while (stack.isNotEmpty() && stack.last() !is Token.LParen) {
                        out += stack.removeLast()
                    }
                    if (stack.isEmpty() || stack.last() !is Token.LParen) error("Mismatched parens")
                    stack.removeLast()
                }
            }
        }
        while (stack.isNotEmpty()) {
            val t = stack.removeLast()
            if (t is Token.LParen || t is Token.RParen) error("Mismatched parens")
            out += t
        }
        return out
    }

    private fun evalRpn(rpn: List<Token>, vars: Map<String, Double>): Double {
        val st = ArrayDeque<Double>()
        for (t in rpn) {
            when (t) {
                is Token.Number -> st.addLast(t.value)
                is Token.Ident -> st.addLast(vars[t.name] ?: vars[t.name.uppercase()] ?: error("Unknown var ${t.name}"))
                is Token.Op -> {
                    val b = st.removeLastOrNull() ?: error("Stack underflow")
                    val a = st.removeLastOrNull() ?: error("Stack underflow")
                    val v = when (t.op) {
                        '+' -> a + b
                        '-' -> a - b
                        '*' -> a * b
                        '/' -> a / b
                        '^' -> a.pow(b)
                        else -> error("Unknown op ${t.op}")
                    }
                    st.addLast(v)
                }
                Token.LParen, Token.RParen -> error("Paren in RPN")
            }
        }
        return st.singleOrNull() ?: error("Bad expression")
    }

    private fun <T> ArrayDeque<T>.removeLastOrNull(): T? = if (isEmpty()) null else removeLast()
}

