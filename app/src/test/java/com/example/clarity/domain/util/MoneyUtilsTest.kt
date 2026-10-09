package com.example.clarity.domain.util

import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class MoneyUtilsTest {
    @Test fun parsesExactMinorUnitsAndOneDecimal() {
        assertEquals(12345L, MoneyUtils.parseMinor("123.45").getOrThrow())
        assertEquals(12340L, MoneyUtils.parseMinor("123.4").getOrThrow())
        assertEquals(1200L, MoneyUtils.parseMinor(" 12 ").getOrThrow())
        assertEquals(1L, MoneyUtils.parseMinor("0.01").getOrThrow())
        assertEquals(0L, MoneyUtils.parseMinor("0").getOrThrow())
        assertEquals(-125L, MoneyUtils.parseMinor("-1.25").getOrThrow())
    }
    @Test fun rejectsInvalidAndOverPreciseAmounts() {
        listOf("", " ", "abc", "1.001", "1.000", "1,20", "1e2", "NaN", "1.").forEach {
            assertTrue("Should reject: $it", MoneyUtils.parseMinor(it).isFailure)
        }
    }
    @Test fun handlesLargeAmountsWithoutRoundingOrOverflow() {
        assertEquals(Long.MAX_VALUE, MoneyUtils.parseMinor("92233720368547758.07").getOrThrow())
        assertTrue(MoneyUtils.parseMinor("92233720368547758.08").isFailure)
        assertTrue(MoneyUtils.parseMinor("999999999999999999999999").isFailure)
        assertEquals("92233720368547758.07", MoneyUtils.formatMinor(Long.MAX_VALUE))
    }
    @Test fun alwaysFormatsTwoDecimalsAndUnboundedTotals() {
        assertEquals("0.00", MoneyUtils.formatMinor(0L))
        assertEquals("1.20", MoneyUtils.formatMinor(120L))
        assertEquals("-1.20", MoneyUtils.formatMinor(-120L))
        assertEquals("184467440737095516.14", MoneyUtils.formatMinor(BigInteger.valueOf(Long.MAX_VALUE) * BigInteger.TWO))
    }
}
