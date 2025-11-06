package org.class_scanner.maven

import org.class_scanner.utils.SystemUtils
import java.io.File

data class MavenProjectInfo(
    val mavenRoot: File,
    val className: String
)

class MavenProjectAnalyzer(private val verbose: Boolean = false) {

    fun extractMavenInfo(javaFile: File): MavenProjectInfo {
        if (!isMavenAvailable()) {
            throw RuntimeException("Maven (mvn) is not available in PATH. Please install Maven and add it to your PATH.")
        }

        val absolutePath = javaFile.absolutePath
        val className = javaFile.nameWithoutExtension

        // Find the Maven root by looking for the src/main/java pattern
        val srcMainJavaPattern = Regex("(.+?)[\\\\/]src[\\\\/]main[\\\\/]java[\\\\/](.+)")
        val match = srcMainJavaPattern.find(absolutePath.replace('\\', '/'))

        if (match != null) {
            val mavenRootPath = match.groupValues[1]
            val packagePath = match.groupValues[2]

            val packageName = packagePath
                .substringBeforeLast("/")
                .replace('/', '.')
                .replace('\\', '.')

            val fullyQualifiedClassName = if (packageName.isNotEmpty()) {
                "$packageName.$className"
            } else {
                className
            }

            return MavenProjectInfo(File(mavenRootPath), fullyQualifiedClassName)
        }

        // Fallback: try to find Maven root by walking up the directory tree
        return findMavenRootFallback(javaFile, className)
    }

    fun getClasspath(mavenRoot: File): List<File> {
        val results = mutableListOf<File>()

        // First, add compiled classes from all modules
        results.addAll(findCompiledClasses(mavenRoot))

        // Then, get Maven dependencies using Maven itself
        mavenRoot.walkTopDown().forEach { file ->
            if (file.name == "pom.xml" && file.parentFile.resolve("src").exists()) {
                try {
                    val classpath = executeMavenClasspath(file.parentFile)
                    classpath.forEach { path ->
                        val classpathFile = File(path)
                        if (classpathFile.exists()) {
                            results.add(classpathFile)
                        }
                    }
                } catch (e: Exception) {
                    if (verbose) {
                        println("Warning: Could not get Maven classpath for ${file.parentFile.name}: ${e.message}")
                    }
                }
            }
        }

        return results.distinct()
    }

    private fun findMavenRootFallback(javaFile: File, className: String): MavenProjectInfo {
        var currentDir = javaFile.parentFile
        val packageParts = mutableListOf<String>()

        while (currentDir != null) {
            if (currentDir.resolve("pom.xml").exists()) {
                val fullyQualifiedClassName = if (packageParts.isNotEmpty()) {
                    "${packageParts.reversed().joinToString(".")}.$className"
                } else {
                    className
                }
                return MavenProjectInfo(currentDir, fullyQualifiedClassName)
            }

            if (currentDir.name == "java" &&
                currentDir.parentFile?.name == "main" &&
                currentDir.parentFile?.parentFile?.name == "src"
            ) {
                break
            }

            if (currentDir.name.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*"))) {
                packageParts.add(currentDir.name)
            }

            currentDir = currentDir.parentFile
        }

        // Continue walking up to find Maven root
        while (currentDir != null) {
            if (currentDir.resolve("pom.xml").exists()) {
                val fullyQualifiedClassName = if (packageParts.isNotEmpty()) {
                    "${packageParts.reversed().joinToString(".")}.$className"
                } else {
                    className
                }
                return MavenProjectInfo(currentDir, fullyQualifiedClassName)
            }
            currentDir = currentDir.parentFile
        }

        // If we can't find Maven root, use the parent directory of the Java file
        val fullyQualifiedClassName = if (packageParts.isNotEmpty()) {
            "${packageParts.reversed().joinToString(".")}.$className"
        } else {
            className
        }

        return MavenProjectInfo(javaFile.parentFile, fullyQualifiedClassName)
    }

    private fun isMavenAvailable(): Boolean {
        return try {
            val processBuilder = ProcessBuilder()
            val command = if (SystemUtils.isWindows()) "mvn.cmd" else "mvn"
            processBuilder.command(command, "--version")
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()
            val exitCode = process.waitFor()

            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun findCompiledClasses(root: File): List<File> {
        val results = mutableListOf<File>()

        root.walkTopDown().forEach { file ->
            if (file.isDirectory && file.name == "target") {
                val classesDir = File(file, "classes")
                if (classesDir.exists() && classesDir.isDirectory) {
                    results.add(classesDir)
                    if (verbose) {
                        println("Found classes directory: ${classesDir.absolutePath}")
                    }
                }

                val testClassesDir = File(file, "test-classes")
                if (testClassesDir.exists() && testClassesDir.isDirectory) {
                    results.add(testClassesDir)
                    if (verbose) {
                        println("Found test classes directory: ${testClassesDir.absolutePath}")
                    }
                }
            }
        }

        return results
    }

    private fun executeMavenClasspath(moduleDir: File): List<String> {
        val processBuilder = ProcessBuilder()
        processBuilder.directory(moduleDir)

        val mvnCommand = if (SystemUtils.isWindows()) "mvn.cmd" else "mvn"

        val command = listOf(
            mvnCommand,
            "dependency:build-classpath",
            "-Dmdep.outputFile=target/classpath.txt",
            "-Dmdep.includeScope=compile",
            "-q"
        )

        processBuilder.command(command)
        processBuilder.redirectErrorStream(true)

        if (verbose) {
            println("Executing Maven command in ${moduleDir.name}: ${command.joinToString(" ")}")
        }

        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("Maven command failed with exit code $exitCode. Output: $output")
        }

        val classpathFile = File(moduleDir, "target/classpath.txt")
        if (classpathFile.exists()) {
            val classpath = classpathFile.readText().trim()
            classpathFile.delete()

            if (classpath.isNotEmpty()) {
                return classpath.split(File.pathSeparator).filter { it.isNotBlank() }
            }
        }

        return emptyList()
    }
}
