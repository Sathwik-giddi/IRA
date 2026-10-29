package com.voicepoco.app

object ModelPolicy {

    fun recommended(ramMb: Int, is64Bit: Boolean): ModelCatalog.Model =
        if (is64Bit && ramMb >= ModelCatalog.big.minRamMb) ModelCatalog.big else ModelCatalog.small

    fun threads(cores: Int): Int = cores.coerceIn(2, 4)
}
