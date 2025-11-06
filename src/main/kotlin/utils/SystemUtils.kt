package org.example.utils

object SystemUtils {
    fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("windows")
    }
}
