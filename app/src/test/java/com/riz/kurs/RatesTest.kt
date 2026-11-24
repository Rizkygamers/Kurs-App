package com.riz.kurs

import org.junit.Assert.assertEquals
import org.junit.Test

class RatesTest {
    private val r = 16_000.0

    @Test
    fun usdToIdr() = assertEquals(16_000.0, MODES[0].calc(r, 1.0), 1e-9)

    @Test
    fun idrToUsd() = assertEquals(1.0, MODES[1].calc(r, r), 1e-9)

    @Test
    fun idrToUsc() = assertEquals(100.0, MODES[2].calc(r, r), 1e-9)

    @Test
    fun uscToIdr() = assertEquals(16_000.0, MODES[3].calc(r, 100.0), 1e-9)
}
