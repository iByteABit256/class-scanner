package org.class_scanner

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class ClassScanner(
    private val maxThreads: Int = Runtime.getRuntime().availableProcessors(),
    private val verbose: Boolean = false
) {

    private val visited = ConcurrentHashMap.newKeySet<KClass<*>>()
    private val tree = ConcurrentHashMap<String, Map<String, String>>()
    private val flat = ConcurrentHashMap<String, MutableList<Map<String, String>>>()
    private val executor = Executors.newFixedThreadPool(maxThreads)
    private val processedClasses = AtomicInteger(0)
    private val pendingFutures = ConcurrentHashMap.newKeySet<CompletableFuture<Void>>()

    fun scan(clazz: KClass<*>) {
        if (verbose) {
            println("Starting multithreaded analysis with $maxThreads threads")
        }

        val startTime = System.currentTimeMillis()

        // Start the initial scan
        scanClass(clazz)

        // Wait for all futures to complete
        if (verbose) {
            var lastProcessed = 0
            while (pendingFutures.isNotEmpty()) {
                val current = processedClasses.get()
                val pending = pendingFutures.size

                if (current != lastProcessed && current > 0) {
                    println("Progress: $current classes processed, $pending pending tasks")
                    lastProcessed = current
                }

                // Remove completed futures
                pendingFutures.removeIf { it.isDone }
                Thread.sleep(100)
            }
        } else {
            // Simple wait
            while (pendingFutures.isNotEmpty()) {
                pendingFutures.removeIf { it.isDone }
                Thread.sleep(50)
            }
        }

        executor.shutdown()

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        if (verbose) {
            println("Analysis completed in ${duration}ms")
            println("Processed ${processedClasses.get()} classes total")
            println("Used $maxThreads threads")
        }
    }

    private fun scanClass(clazz: KClass<*>) {
        if (!visited.add(clazz)) {
            return // Already processed or being processed
        }

        val future = CompletableFuture.runAsync({
            try {
                processClass(clazz)
            } catch (e: Exception) {
                if (verbose) {
                    println("Error processing ${clazz.simpleName}: ${e.message}")
                }
            }
        }, executor)

        pendingFutures.add(future)
    }

    private fun processClass(clazz: KClass<*>) {
        val className = clazz.simpleName ?: clazz.toString()
        val fields = linkedMapOf<String, String>()
        val classFlat = mutableListOf<Map<String, String>>()

        if (verbose && processedClasses.get() % 10 == 0) {
            println("Analyzing: $className")
        }

        clazz.declaredMemberProperties.forEach { prop ->
            val type = extractType(prop)
            val typeName = type.simpleName ?: "Unknown"

            fields[prop.name] = typeName

            classFlat += mapOf(
                "class" to className,
                "field" to prop.name,
                "type" to typeName
            )

            // Schedule recursive scanning for non-ignored types
            if (!ignore(type)) {
                scanClass(type) // This will check if already visited
            }
        }

        tree[className] = fields
        flat[className] = classFlat
        processedClasses.incrementAndGet()
    }

    private fun extractType(prop: KProperty1<*, *>): KClass<*> {
        val returnType = prop.returnType
        val classifier = returnType.classifier

        // Direct type
        if (classifier is KClass<*>) return classifier

        // Generic container: List<T>, Set<T>
        val arg = returnType.arguments.firstOrNull()?.type?.classifier
        return arg as? KClass<*> ?: Any::class
    }

    private fun ignore(type: KClass<*>): Boolean =
        type.java.isPrimitive ||
                type.qualifiedName?.startsWith("java.") == true ||
                type.qualifiedName?.startsWith("javax.") == true ||
                type == String::class ||
                type == Any::class

    fun toJson(): String {
        val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

        // Convert concurrent collections to regular collections for JSON serialization
        val flatList = flat.values.flatten().sortedBy { "${it["class"]}.${it["field"]}" }
        val sortedTree = tree.toSortedMap()

        return mapper.writeValueAsString(mapOf(
            "tree" to sortedTree,
            "flat" to flatList,
            "metadata" to mapOf(
                "totalClasses" to processedClasses.get(),
                "threadsUsed" to maxThreads,
                "timestamp" to System.currentTimeMillis()
            )
        ))
    }
}
