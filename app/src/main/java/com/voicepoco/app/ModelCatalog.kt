package com.voicepoco.app

import android.content.Context
import java.io.File

object ModelCatalog {

    data class Model(
        val key: String,
        val fileName: String,
        val label: String,
        val approxMb: Int,
        val minRamMb: Int
    )

    const val SMALL_FILE = "functiongemma-270m-it-Q4_K_M.gguf"
    const val BIG_FILE = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"

    val small = Model("small", SMALL_FILE, "FunctionGemma 270M", 241, 3500)
    val big = Model("big", BIG_FILE, "Qwen 1.5B", 940, 5500)
    val all = listOf(small, big)

    fun dir(context: Context): File = File(context.filesDir, "models")

    fun file(context: Context, model: Model): File = File(dir(context), model.fileName)

    fun installed(context: Context, model: Model): Boolean = file(context, model).isFile

    fun byKey(key: String): Model? = all.firstOrNull { it.key == key }
}
