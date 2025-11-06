package org.example

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import java.io.File
import java.net.URL
import java.net.URLClassLoader

class ClassScannerCli : CliktCommand(
    help = """
Class Scanner - Analyze Java class field names and types recursively

Simply provide a Java source file (.java) or compiled class file (.class)
and all fields will be added in a JSON file from the file given all the way
to the last classes that only contain primitive types or types contained in the standard java packages

Examples:

  class-scanner /path/to/project/src/main/java/com/example/MyClass.java
    
  class-scanner /path/to/classes/com/example/MyClass.class
  
  class-scanner /foo/MyClass.java -f output_file.json
    """.trimIndent(),
    name = "class-scanner"
) {
    private val inputFile by argument(help = "Java source file (.java) or compiled class file (.class)").file(mustExist = true)
    private val verbose by option("-v", "--verbose").flag(defaultForHelp = "Enable verbose output")
    private val outputFile by option("-f", "--file", help = "Save output to file instead of stdout").file()

    override fun run() {
        when {
            inputFile.name.endsWith(".java") -> {
                if (verbose) {
                    echo("Detected Java source file")
                    echo("Processing: ${inputFile.absolutePath}")
                    outputFile?.let { echo("Output will be saved to: ${it.absolutePath}") }
                }
                handleJavaFile()
            }
            inputFile.name.endsWith(".class") -> {
                if (verbose) {
                    echo("Detected compiled class file")
                    echo("Processing: ${inputFile.absolutePath}")
                    outputFile?.let { echo("Output will be saved to: ${it.absolutePath}") }
                }
                handleClassFile()
            }
            else -> {
                echo("Error: File must be a Java source file (.java) or compiled class file (.class)", err = true)
                echo("Received: ${inputFile.name}")
                return
            }
        }
    }

    private fun handleJavaFile() {
        if (verbose) {
            echo("Analyzing Java source file...")
        }

        // Check if Maven is available in PATH
        if (!isMavenAvailable()) {
            echo("Error: Maven (mvn) is not available in PATH.", err = true)
            echo("Please install Maven and add it to your PATH to analyze Java source files.")
            return
        }

        try {
            // Extract Maven root and class name from Java file path
            val (mavenRoot, className) = extractMavenInfoFromJavaFile(inputFile)

            if (verbose) {
                echo("Detected Maven root: ${mavenRoot.absolutePath}")
                echo("Detected class name: $className")
            }

            // Use the enhanced classpath discovery
            val classpathFiles = getMavenClasspath(mavenRoot, verbose)
            if (classpathFiles.isEmpty()) {
                echo("Error: No compiled classes or JARs found under $mavenRoot", err = true)
                echo("Try running: mvn compile")
                return
            }

            if (verbose) {
                echo("Found ${classpathFiles.size} classpath entries")
            }

            scanClass(className, classpathFiles, verbose, outputFile)

        } catch (e: Exception) {
            echo("Error processing Java file: ${e.message}", err = true)
            if (verbose) {
                e.printStackTrace()
            }
        }
    }

    private fun handleClassFile() {
        if (verbose) {
            echo("Analyzing compiled class file...")
        }

        try {
            // Extract class name and build classpath
            val (className, classpathFiles) = extractClassInfoFromFile(inputFile, emptyList())

            if (verbose) {
                echo("Detected class name: $className")
                echo("Using ${classpathFiles.size} classpath entries")
            }

            scanClass(className, classpathFiles, verbose, outputFile)

        } catch (e: Exception) {
            echo("Error processing class file: ${e.message}", err = true)
            if (verbose) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Common method to scan a class with given classpath
     */
    private fun scanClass(className: String, classpathFiles: List<File>, verbose: Boolean = false, outputFile: File? = null) {
        val urls: Array<URL> = classpathFiles.map { it.toURI().toURL() }.toTypedArray()
        val loader = URLClassLoader(urls, Thread.currentThread().contextClassLoader)

        try {
            if (verbose) {
                echo("Loading class: $className")
            }

            val clazz = Class.forName(className, true, loader).kotlin

            if (verbose) {
                echo("Class loaded successfully")
                echo("Starting analysis...")
            }

            val scanner = ClassScanner()
            scanner.scan(clazz)

            val jsonOutput = scanner.toJson()

            // Handle output
            if (outputFile != null) {
                try {
                    // Create parent directories if they don't exist
                    outputFile.parentFile?.mkdirs()

                    // Write to file
                    outputFile.writeText(jsonOutput)

                    if (verbose) {
                        echo("Analysis completed successfully!")
                        echo("Output saved to: ${outputFile.absolutePath}")
                        echo("File size: ${outputFile.length()} bytes")
                    } else {
                        echo("Analysis complete. Output saved to: ${outputFile.absolutePath}")
                    }
                } catch (e: Exception) {
                    echo("Error writing to file ${outputFile.absolutePath}: ${e.message}", err = true)
                    if (verbose) {
                        e.printStackTrace()
                    }
                    // Fall back to stdout
                    echo("Falling back to stdout:")
                    println(jsonOutput)
                }
            } else {
                // Output to stdout
                if (verbose) {
                    echo("Analysis completed successfully!")
                    echo("Output:")
                }
                println(jsonOutput)
            }

        } catch (e: ClassNotFoundException) {
            echo("Error: Class '$className' not found in the provided classpath.", err = true)
            echo("")
            echo("Suggestions:")
            if (className.contains(".")) {
                echo("  Make sure the project is compiled: mvn compile")
                echo("  • Check if the package structure matches the file path")
            } else {
                echo("  • Verify the class file is in the correct location")
                echo("  • Check if dependencies are available")
            }

            if (verbose) {
                echo("")
                echo("🔍 Searched in ${classpathFiles.size} locations:")
                classpathFiles.take(5).forEach { echo("  - ${it.absolutePath}") }
                if (classpathFiles.size > 5) {
                    echo("  ... and ${classpathFiles.size - 5} more locations")
                }
            }
        } catch (e: NoClassDefFoundError) {
            echo("Error: Missing dependency class: ${e.message}", err = true)
            echo("")
            echo("Suggestions:")
            echo("  • Run: mvn dependency:resolve")
            echo("  • Check if all dependencies are available")
            echo("  • Try: mvn clean compile")
        } catch (e: Exception) {
            echo("Error: ${e.message}", err = true)

            if (verbose) {
                echo("")
                echo("Stack trace:")
                e.printStackTrace()
            }
        }
    }
}

fun main(args: Array<String>) = ClassScannerCli().main(args)

/**
 * Extract Maven root directory and fully qualified class name from a Java source file
 */
private fun extractMavenInfoFromJavaFile(javaFile: File): Pair<File, String> {
    val absolutePath = javaFile.absolutePath
    val className = javaFile.nameWithoutExtension

    // Find the Maven root by looking for the src/main/java pattern
    val srcMainJavaPattern = Regex("(.+?)[\\\\/]src[\\\\/]main[\\\\/]java[\\\\/](.+)")
    val match = srcMainJavaPattern.find(absolutePath.replace('\\', '/'))

    if (match != null) {
        val mavenRootPath = match.groupValues[1]
        val packagePath = match.groupValues[2]

        // Convert file path to package name
        val packageName = packagePath
            .substringBeforeLast("/") // Remove the filename
            .replace('/', '.')
            .replace('\\', '.')

        val fullyQualifiedClassName = if (packageName.isNotEmpty()) {
            "$packageName.$className"
        } else {
            className
        }

        return Pair(File(mavenRootPath), fullyQualifiedClassName)
    }

    // Fallback: try to find Maven root by walking up the directory tree
    var currentDir = javaFile.parentFile
    val packageParts = mutableListOf<String>()

    while (currentDir != null) {
        // Check if this directory contains a pom.xml (Maven root)
        if (currentDir.resolve("pom.xml").exists()) {
            // Found Maven root
            val fullyQualifiedClassName = if (packageParts.isNotEmpty()) {
                "${packageParts.reversed().joinToString(".")}.$className"
            } else {
                className
            }
            return Pair(currentDir, fullyQualifiedClassName)
        }

        // If we hit src/main/java, stop collecting package parts
        if (currentDir.name == "java" &&
            currentDir.parentFile?.name == "main" &&
            currentDir.parentFile?.parentFile?.name == "src") {
            break
        }

        // Collect package parts
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
            return Pair(currentDir, fullyQualifiedClassName)
        }
        currentDir = currentDir.parentFile
    }

    // If we can't find Maven root, use the parent directory of the Java file
    val fullyQualifiedClassName = if (packageParts.isNotEmpty()) {
        "${packageParts.reversed().joinToString(".")}.$className"
    } else {
        className
    }

    return Pair(javaFile.parentFile, fullyQualifiedClassName)
}

/**
 * Extract fully qualified class name from .class file and build classpath
 */
private fun extractClassInfoFromFile(classFile: File, additionalClasspathArgs: List<File>): Pair<String, List<File>> {
    val classpathFiles = mutableListOf<File>()

    // Determine the root directory and class name
    val (className, rootDir) = findClassNameAndRoot(classFile)

    // Add the root directory to classpath
    classpathFiles.add(rootDir)

    // Add additional classpath entries (JARs, directories, etc.)
    additionalClasspathArgs.forEach { file ->
        if (file.exists()) {
            classpathFiles.add(file)
        } else {
            println("Warning: Classpath entry not found: ${file.absolutePath}")
        }
    }

    return Pair(className, classpathFiles)
}

/**
 * Find the fully qualified class name and root directory from a .class file
 */
private fun findClassNameAndRoot(classFile: File): Pair<String, File> {
    val classFileName = classFile.nameWithoutExtension

    // Try to find the root by looking for common patterns
    var currentDir = classFile.parentFile
    val packageParts = mutableListOf<String>()

    // Walk up the directory tree to find the root
    while (currentDir != null) {
        val dirName = currentDir.name

        // Check if this looks like a classes root directory
        if (dirName == "classes" ||
            dirName == "target" ||
            dirName == "build" ||
            currentDir.resolve("META-INF").exists()) {
            break
        }

        // Add directory name to package parts (we'll reverse this later)
        if (dirName.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*"))) {
            packageParts.add(0, dirName)
        }

        currentDir = currentDir.parentFile
    }

    // If we found a classes directory, use it as root
    val rootDir = if (currentDir?.name == "classes") {
        currentDir
    } else {
        // Otherwise, calculate root based on package structure
        var root = classFile.parentFile
        repeat(packageParts.size) {
            root = root?.parentFile
        }
        root ?: classFile.parentFile
    }

    // Build fully qualified class name
    val className = if (packageParts.isNotEmpty()) {
        "${packageParts.joinToString(".")}.$classFileName"
    } else {
        classFileName
    }

    return Pair(className, rootDir)
}

/**
 * Check if Maven is available in the system PATH
 */
private fun isMavenAvailable(): Boolean {
    return try {
        val processBuilder = ProcessBuilder()
        val command = if (isWindows()) "mvn.cmd" else "mvn"
        processBuilder.command(command, "--version")
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()
        val exitCode = process.waitFor()

        exitCode == 0
    } catch (e: Exception) {
        false
    }
}

/**
 * Get Maven classpath using Maven's dependency:build-classpath goal
 */
fun getMavenClasspath(mavenRoot: File, verbose: Boolean = false): List<File> {
    val results = mutableListOf<File>()

    // First, add compiled classes from all modules
    results.addAll(findCompiledClasses(mavenRoot, verbose))

    // Then, get Maven dependencies using Maven itself
    mavenRoot.walkTopDown().forEach { file ->
        if (file.name == "pom.xml" && file.parentFile.resolve("src").exists()) {
            try {
                val classpath = executeMavenClasspath(file.parentFile, verbose)
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

/**
 * Find compiled classes directories in all Maven modules
 */
private fun findCompiledClasses(root: File, verbose: Boolean = false): List<File> {
    val results = mutableListOf<File>()

    root.walkTopDown().forEach { file ->
        if (file.isDirectory && file.name == "target") {
            // Add compiled classes
            val classesDir = File(file, "classes")
            if (classesDir.exists() && classesDir.isDirectory) {
                results.add(classesDir)
                if (verbose) {
                    println("Found classes directory: ${classesDir.absolutePath}")
                }
            }

            // Add test classes (might be needed for some analysis)
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

/**
 * Execute Maven dependency:build-classpath to get the full classpath using Maven from PATH
 */
private fun executeMavenClasspath(moduleDir: File, verbose: Boolean = false): List<String> {
    val processBuilder = ProcessBuilder()
    processBuilder.directory(moduleDir)

    // Use Maven from PATH
    val mvnCommand = if (isWindows()) "mvn.cmd" else "mvn"

    // Build the Maven command
    val command = listOf(
        mvnCommand,
        "dependency:build-classpath",
        "-Dmdep.outputFile=target/classpath.txt",
        "-Dmdep.includeScope=compile",
        "-q" // Quiet mode to reduce output
    )

    processBuilder.command(command)

    // Redirect error stream to capture any issues
    processBuilder.redirectErrorStream(true)

    if (verbose) {
        println("Executing Maven command in ${moduleDir.name}: ${command.joinToString(" ")}")
    }

    val process = processBuilder.start()

    // Read output in case there are issues
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()

    if (exitCode != 0) {
        throw RuntimeException("Maven command failed with exit code $exitCode. Output: $output")
    }

    val classpathFile = File(moduleDir, "target/classpath.txt")
    if (classpathFile.exists()) {
        val classpath = classpathFile.readText().trim()
        classpathFile.delete() // Clean up temporary file

        if (classpath.isNotEmpty()) {
            return classpath.split(File.pathSeparator).filter { it.isNotBlank() }
        }
    }

    // If no classpath file was created, but Maven succeeded, return empty list
    // This can happen if there are no dependencies
    return emptyList()
}

/**
 * Check if running on Windows
 */
private fun isWindows(): Boolean {
    return System.getProperty("os.name").lowercase().contains("windows")
}
