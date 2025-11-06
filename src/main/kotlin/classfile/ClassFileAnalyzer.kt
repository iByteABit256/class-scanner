package org.class_scanner.classfile

import java.io.File

data class ClassFileInfo(
    val className: String,
    val classpathFiles: List<File>
)

class ClassFileAnalyzer(private val verbose: Boolean = false) {

    fun extractClassInfo(classFile: File, additionalClasspathArgs: List<File> = emptyList()): ClassFileInfo {
        val classpathFiles = mutableListOf<File>()

        val (className, rootDir) = findClassNameAndRoot(classFile)

        classpathFiles.add(rootDir)

        additionalClasspathArgs.forEach { file ->
            if (file.exists()) {
                classpathFiles.add(file)
            } else {
                if (verbose) {
                    println("Warning: Classpath entry not found: ${file.absolutePath}")
                }
            }
        }

        return ClassFileInfo(className, classpathFiles)
    }

    private fun findClassNameAndRoot(classFile: File): Pair<String, File> {
        val classFileName = classFile.nameWithoutExtension

        var currentDir = classFile.parentFile
        val packageParts = mutableListOf<String>()

        while (currentDir != null) {
            val dirName = currentDir.name

            if (dirName == "classes" ||
                dirName == "target" ||
                dirName == "build" ||
                currentDir.resolve("META-INF").exists()
            ) {
                break
            }

            if (dirName.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*"))) {
                packageParts.add(0, dirName)
            }

            currentDir = currentDir.parentFile
        }

        val rootDir = if (currentDir?.name == "classes") {
            currentDir
        } else {
            var root = classFile.parentFile
            repeat(packageParts.size) {
                root = root?.parentFile
            }
            root ?: classFile.parentFile
        }

        val className = if (packageParts.isNotEmpty()) {
            "${packageParts.joinToString(".")}.$classFileName"
        } else {
            classFileName
        }

        return Pair(className, rootDir)
    }
}
