package io.github.medtahiri.launcher.home

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Quick math typed into search, like `12*7` or `(3+4)/2`. Kept free of Android so it can be unit tested. */
object Calculator {
    /** The answer to [text], or null if it isn't a sum with at least one operator. */
    fun evaluate(text: String): String? {
        val input = text.replace(" ", "").replace('×', '*').replace('÷', '/').replace(',', '.')
        if (input.isEmpty() || input.none { it in "+-*/%^" } || input.none(Char::isDigit)) return null
        if (input.any { !it.isDigit() && it !in ".+-*/%^()" }) return null
        return try {
            val parser = Parser(input)
            val value = parser.expression()
            if (!parser.done()) return null
            format(value)
        } catch (e: ArithmeticException) {
            null
        } catch (e: IllegalStateException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun format(value: BigDecimal): String {
        val rounded = value.round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
        return if (rounded.scale() < 0) rounded.setScale(0).toPlainString() else rounded.toPlainString()
    }

    private class Parser(val s: String) {
        var i = 0
        fun done() = i == s.length
        private fun peek() = s.getOrNull(i)

        fun expression(): BigDecimal {
            var value = term()
            while (peek() == '+' || peek() == '-') {
                val op = s[i++]
                value = if (op == '+') value + term() else value - term()
            }
            return value
        }

        private fun term(): BigDecimal {
            var value = power()
            while (peek() == '*' || peek() == '/' || peek() == '%') {
                val op = s[i++]
                val right = power()
                value = when (op) {
                    '*' -> value * right
                    '/' -> value.divide(right, MathContext.DECIMAL64)
                    else -> value.remainder(right)
                }
            }
            return value
        }

        private fun power(): BigDecimal {
            val base = unary()
            if (peek() != '^') return base
            i++
            val exponent = power() // right to left: 2^3^2 is 2^9
            check(exponent.stripTrailingZeros().scale() <= 0 && exponent.abs() <= BigDecimal(1000)) { "unsupported power" }
            val n = exponent.toInt()
            return if (n >= 0) base.pow(n, MathContext.DECIMAL64) else BigDecimal.ONE.divide(base.pow(-n, MathContext.DECIMAL64), MathContext.DECIMAL64)
        }

        private fun unary(): BigDecimal = when (peek()) {
            '-' -> { i++; unary().negate() }
            '+' -> { i++; unary() }
            else -> atom()
        }

        private fun atom(): BigDecimal {
            if (peek() == '(') {
                i++
                val value = expression()
                check(peek() == ')') { "missing )" }
                i++
                return value
            }
            val start = i
            while (peek()?.let { it.isDigit() || it == '.' } == true) i++
            check(i > start) { "number expected" }
            return BigDecimal(s.substring(start, i))
        }
    }
}
