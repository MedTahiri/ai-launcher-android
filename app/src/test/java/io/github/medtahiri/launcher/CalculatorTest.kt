package io.github.medtahiri.launcher

import io.github.medtahiri.launcher.home.Calculator
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorTest {
    @Test fun doesSums() {
        assertEquals("84", Calculator.evaluate("12*7"))
        assertEquals("3.5", Calculator.evaluate("(3 + 4) / 2"))
        assertEquals("14", Calculator.evaluate("2+3*4"))
        assertEquals("-1", Calculator.evaluate("-3+2"))
        assertEquals("512", Calculator.evaluate("2^3^2"))
        assertEquals("0.333333333333", Calculator.evaluate("1/3"))
        assertEquals("1", Calculator.evaluate("10 % 3"))
        assertEquals("2.5", Calculator.evaluate("1,5+1"))
    }

    @Test fun ignoresWhatIsntASum() {
        assertEquals(null, Calculator.evaluate("chrome"))
        assertEquals(null, Calculator.evaluate("42"))
        assertEquals(null, Calculator.evaluate("1/0"))
        assertEquals(null, Calculator.evaluate("(1+2"))
        assertEquals(null, Calculator.evaluate("3+"))
        assertEquals(null, Calculator.evaluate("2^100000"))
        assertEquals(null, Calculator.evaluate("covid-19"))
    }
}
