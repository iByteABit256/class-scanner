package org.class_scanner.analyzer

import org.class_scanner.ClassScanner
import org.class_scanner.maven.MavenProjectAnalyzer
import org.class_scanner.classfile.ClassFileAnalyzer
import java.io.File
import java.net.URLClassLoader

class ClassAnalyzer(
    private val verbose: Boolean = false,
    private val threadCount: Int = Runtime.getRuntime().availableProcessors()
) {
    private val mavenAnalyzer = MavenProjectAnalyzer(verbose)
    private val classFileAnalyzer = ClassFileAnalyzer(verbose)

    fun analyzeJavaFile(javaFile: File): AnalysisResult {
        if (verbose) {
            println("Analyzing Java source file...")
        }

        return try {
            val mavenInfo = mavenAnalyzer.extractMavenInfo(javaFile)
            val classpathFiles = mavenAnalyzer.getClasspath(mavenInfo.mavenRoot)

            if (classpathFiles.isEmpty()) {
                return AnalysisResult.Error(
                    message = "No compiled classes or JARs found under ${mavenInfo.mavenRoot}",
                    suggestions = listOf("Try running: mvn compile")
                )
            }

            if (verbose) {
                println("Found ${classpathFiles.size} classpath entries")
            }

            scanClass(mavenInfo.className, classpathFiles)

        } catch (e: Exception) {
            AnalysisResult.Error(
                message = "Error processing Java file: ${e.message}",
                throwable = e
            )
        }
    }

    fun analyzeClassFile(classFile: File): AnalysisResult {
        if (verbose) {
            println("Analyzing compiled class file...")
        }

        return try {
            val classInfo = classFileAnalyzer.extractClassInfo(classFile)

            if (verbose) {
                println("Detected class name: ${classInfo.className}")
                println("Using ${classInfo.classpathFiles.size} classpath entries")
            }

            scanClass(classInfo.className, classInfo.classpathFiles)

        } catch (e: Exception) {
            AnalysisResult.Error(
                message = "Error processing class file: ${e.message}",
                throwable = e
            )
        }
    }

    private fun scanClass(className: String, classpathFiles: List<File>): AnalysisResult {
        val urls = classpathFiles.map { it.toURI().toURL() }.toTypedArray()
        val loader = URLClassLoader(urls, Thread.currentThread().contextClassLoader)

        return try {
            if (verbose) {
                println("Loading class: $className")
            }

            val clazz = Class.forName(className, true, loader).kotlin

            if (verbose) {
                println("Class loaded successfully")
                println("Starting multithreaded analysis with $threadCount threads...")
            }

            val scanner = ClassScanner(maxThreads = threadCount, verbose = verbose)
            scanner.scan(clazz)

            AnalysisResult.Success(scanner.toJson())

        } catch (e: ClassNotFoundException) {
            AnalysisResult.Error(
                message = "Class '$className' not found in the provided classpath",
                throwable = e,
                suggestions = listOf(
                    "Make sure the project is compiled: mvn compile",
                    "Check if the package structure matches the file path",
                    "Verify the class file is in the correct location"
                )
            )
        } catch (e: NoClassDefFoundError) {
            AnalysisResult.Error(
                message = "Missing dependency class: ${e.message}",
                throwable = e,
                suggestions = listOf(
                    "Run: mvn dependency:resolve",
                    "Check if all dependencies are available",
                    "Try: mvn clean compile"
                )
            )
        } catch (e: Exception) {
            AnalysisResult.Error(
                message = e.message ?: "Unknown error occurred",
                throwable = e
            )
        }
    }
}
