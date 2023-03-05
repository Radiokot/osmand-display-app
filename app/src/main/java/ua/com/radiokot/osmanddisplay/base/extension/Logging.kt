package ua.com.radiokot.osmanddisplay.base.extension

import mu.KLogger
import mu.KotlinLogging

fun Any.kLogger(name: String): KLogger = KotlinLogging.logger("$name@${hashCode()}")