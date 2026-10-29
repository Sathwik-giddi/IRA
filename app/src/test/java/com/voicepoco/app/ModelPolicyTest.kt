package com.voicepoco.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelPolicyTest {

    @Test
    fun recommendsSmallModelOnLowRamOr32Bit() {
        assertEquals(ModelCatalog.small, ModelPolicy.recommended(3900, true))
        assertEquals(ModelCatalog.small, ModelPolicy.recommended(8000, false))
    }

    @Test
    fun recommendsBigModelOn64BitWithEnoughRam() {
        assertEquals(ModelCatalog.big, ModelPolicy.recommended(6000, true))
        assertEquals(ModelCatalog.big, ModelPolicy.recommended(8192, true))
    }

    @Test
    fun keepsThreadsWithinSafeRange() {
        assertEquals(2, ModelPolicy.threads(1))
        assertEquals(2, ModelPolicy.threads(2))
        assertEquals(4, ModelPolicy.threads(4))
        assertEquals(4, ModelPolicy.threads(8))
    }
}
