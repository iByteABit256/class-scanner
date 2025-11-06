package org.example

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties

class ClassScanner {

    private val visited = mutableSetOf<KClass<*>>()
    private val tree = linkedMapOf<String, Map<String, String>>()
    private val flat = mutableListOf<Map<String, String>>()

    fun scan(clazz: KClass<*>) {
        if (!visited.add(clazz)) return

        val fields = linkedMapOf<String, String>()

        clazz.declaredMemberProperties.forEach { prop ->
            val type = extractType(prop)
            val typeName = type.simpleName ?: "Unknown"

            fields[prop.name] = typeName

            flat += mapOf(
                "class" to (clazz.simpleName ?: clazz.toString()),
                "field" to prop.name,
                "type" to typeName
            )

            if (!ignore(type)) {
                scan(type)
            }
        }

        tree[clazz.simpleName ?: clazz.toString()] = fields
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
        return mapper.writeValueAsString(mapOf("tree" to tree, "flat" to flat))
    }
}
