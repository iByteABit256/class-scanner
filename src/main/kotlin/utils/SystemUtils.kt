package org.class_scanner.utils

object SystemUtils {
    fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("windows")
    }
}
