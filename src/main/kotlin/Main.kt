package org.class_scanner

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import org.class_scanner.analyzer.ClassAnalyzer
import org.class_scanner.analyzer.AnalysisResult

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
  
  class-scanner MyClass.java -t 8 -v -f analysis.json
    """.trimIndent(),
    name = "class-scanner"
) {
    private val inputFile by argument(help = "Java source file (.java) or compiled class file (.class)").file(mustExist = true)
    private val verbose by option("-v", "--verbose").flag(defaultForHelp = "Enable verbose output")
    private val outputFile by option("-f", "--file", help = "Save output to file instead of stdout").file()
    private val threads by option(
        "-t",
        "--threads",
        help = "Number of threads to use for analysis (default: number of CPU cores)"
    )
        .int()
        .default(Runtime.getRuntime().availableProcessors())

    override fun run() {
        validateInput()

        val analyzer = ClassAnalyzer(
            verbose = verbose,
            threadCount = threads
        )

        val result = when {
            inputFile.name.endsWith(".java") -> {
                logIfVerbose("Detected Java source file")
                analyzer.analyzeJavaFile(inputFile)
            }

            inputFile.name.endsWith(".class") -> {
                logIfVerbose("Detected compiled class file")
                analyzer.analyzeClassFile(inputFile)
            }

            else -> {
                echo("Error: File must be a Java source file (.java) or compiled class file (.class)", err = true)
                echo("Received: ${inputFile.name}")
                return
            }
        }

        handleResult(result)
    }

    private fun validateInput() {
        if (threads < 1) {
            echo("Error: Thread count must be at least 1", err = true)
            throw RuntimeException("Invalid thread count")
        }
    }

    private fun logIfVerbose(message: String) {
        if (verbose) {
            echo(message)
            echo("Processing: ${inputFile.absolutePath}")
            echo("Using $threads threads for analysis")
            outputFile?.let { echo("Output will be saved to: ${it.absolutePath}") }
        }
    }

    private fun handleResult(result: AnalysisResult) {
        when (result) {
            is AnalysisResult.Success -> {
                handleOutput(result.jsonOutput)
            }

            is AnalysisResult.Error -> {
                echo("Error: ${result.message}", err = true)
                if (verbose && result.throwable != null) {
                    result.throwable.printStackTrace()
                }
                if (result.suggestions.isNotEmpty()) {
                    echo("")
                    echo("Suggestions:")
                    result.suggestions.forEach { echo("  • $it") }
                }
            }
        }
    }

    private fun handleOutput(jsonOutput: String) {
        if (outputFile != null) {
            try {
                outputFile!!.parentFile?.mkdirs()
                outputFile!!.writeText(jsonOutput)

                if (verbose) {
                    echo("Analysis completed successfully!")
                    echo("Output saved to: ${outputFile!!.absolutePath}")
                    echo("File size: ${outputFile!!.length()} bytes")
                } else {
                    echo("Analysis complete. Output saved to: ${outputFile!!.absolutePath}")
                }
            } catch (e: Exception) {
                echo("Error writing to file ${outputFile!!.absolutePath}: ${e.message}", err = true)
                if (verbose) {
                    e.printStackTrace()
                }
                echo("Falling back to stdout:")
                println(jsonOutput)
            }
        } else {
            if (verbose) {
                echo("Analysis completed successfully!")
                echo("Output:")
            }
            println(jsonOutput)
        }
    }
}

fun main(args: Array<String>) = ClassScannerCli().main(args)
