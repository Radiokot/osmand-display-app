package ua.com.radiokot.osmanddisplay

import java.io.InputStream
import java.nio.charset.Charset

object TestAssets {
    fun getInputStream(name: String): InputStream =
        javaClass
            .classLoader!!
            .getResourceAsStream(name)!!

    fun readText(name: String, charset: Charset = Charsets.UTF_8): String =
        getInputStream(name)
            .bufferedReader(charset)
            .readText()
}