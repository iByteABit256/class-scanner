package org.class_scanner.analyzer

sealed class AnalysisResult {
    data class Success(val jsonOutput: String) : AnalysisResult()

    data class Error(
        val message: String,
        val throwable: Throwable? = null,  // Changed from Exception? to Throwable?
        val suggestions: List<String> = emptyList()
    ) : AnalysisResult()
}
