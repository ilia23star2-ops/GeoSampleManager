package com.example.geosamplemanager.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.9-settings-scale-2:
 * Разделены textFactor и densityFactor. textFactor ≥ densityFactor
 * всегда — иначе текст и отступы растут не пропорционально, и
 * вёрстка ломается.
 */
class UiScaleTest {

    @Test
    fun normal_hasBothFactorsOne() {
        assertEquals(1.00f, UiScale.NORMAL.textFactor, 0.0001f)
        assertEquals(1.00f, UiScale.NORMAL.densityFactor, 0.0001f)
    }

    @Test
    fun large_textFactorGreaterThanDensity() {
        assertEquals(1.15f, UiScale.LARGE.textFactor, 0.0001f)
        assertEquals(1.05f, UiScale.LARGE.densityFactor, 0.0001f)
        assert(UiScale.LARGE.textFactor > UiScale.LARGE.densityFactor) {
            "textFactor должен быть больше densityFactor"
        }
    }

    @Test
    fun huge_textFactorGreaterThanDensity() {
        assertEquals(1.30f, UiScale.HUGE.textFactor, 0.0001f)
        assertEquals(1.10f, UiScale.HUGE.densityFactor, 0.0001f)
        assert(UiScale.HUGE.textFactor > UiScale.HUGE.densityFactor) {
            "textFactor должен быть больше densityFactor"
        }
    }

    @Test
    fun factorsIncreaseMonotonically() {
        assert(UiScale.LARGE.textFactor > UiScale.NORMAL.textFactor)
        assert(UiScale.HUGE.textFactor > UiScale.LARGE.textFactor)
        assert(UiScale.LARGE.densityFactor > UiScale.NORMAL.densityFactor)
        assert(UiScale.HUGE.densityFactor > UiScale.LARGE.densityFactor)
    }

    @Test
    fun fromName_validName_returnsEnum() {
        assertEquals(UiScale.LARGE, UiScale.fromName("LARGE"))
        assertEquals(UiScale.HUGE, UiScale.fromName("HUGE"))
        assertEquals(UiScale.NORMAL, UiScale.fromName("NORMAL"))
    }

    @Test
    fun fromName_unknown_returnsNormal() {
        assertEquals(UiScale.NORMAL, UiScale.fromName("UNKNOWN"))
        assertEquals(UiScale.NORMAL, UiScale.fromName(null))
        assertEquals(UiScale.NORMAL, UiScale.fromName(""))
    }

    @Test
    fun everyScaleHasNonEmptyTitle() {
        UiScale.values().forEach { s ->
            assert(s.title.isNotBlank()) { "UiScale.${s.name} без названия" }
        }
    }
}