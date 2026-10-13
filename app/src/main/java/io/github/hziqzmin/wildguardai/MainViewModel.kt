package io.github.hziqzmin.wildguardai

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

data class ChatMessage(
    val id: Int,
    val text: String,
    val author: Author,
    val isStreaming: Boolean = false,
    val sources: List<String> = emptyList()
)

enum class Author { USER, AI }

data class KnowledgeChunk(
    val topic: String? = null,
    val region: String? = null,
    val knot_name: String? = null,
    val description: String? = null,
    val use_cases: List<String>? = null,
    val instructions: String? = null,
    val text: String? = null,
    val embedding: List<Double>? = null
)

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "WildGuardAI"

        // Support both lowercase and uppercase asset filenames
        private val GEMMA_ASSET_CANDIDATES = listOf(
            "gemma3-1b-it-int4.task",
            "gemma3-1B-it-int4.task"
        )

        private const val CONTEXT_CHARS_PER_CHUNK = 500
        private const val TOP_K = 2
        private const val MAX_PROMPT_CHARS = 7000

        // Auto-unload LLM from RAM after 5 minutes of inactivity to save battery
        private const val IDLE_UNLOAD_MS = 5 * 60 * 1000L
    }

    private var textEmbedder: TextEmbedder? = null
    private var kbEmbeddings: List<FloatArray> = emptyList()
    private var library: List<KnowledgeChunk> = emptyList()

    private var brain: LlmInference? = null
    private var brainSession: LlmInferenceSession? = null
    private var idleUnloadJob: Job? = null

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isAITyping = MutableStateFlow(false)
    val isAITyping = _isAITyping.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            initializeRagEngine()
            // Brief splash transition so the Figma Loading Screen is visible cleanly
            delay(600)
            _isLoading.value = false
        }
    }

    /**
     * Fast, zero-battery-drain startup:
     * 1. Loads knowledge_base.json and uses its pre-computed embeddings directly (0ms).
     * 2. Initializes lightweight TextEmbedder for user queries (~5MB).
     * 3. Defers heavy 600MB Gemma LLM loading until the user actually sends a message (Lazy Loading).
     */
    private fun initializeRagEngine() {
        Log.d(TAG, "Initializing RAG Engine...")

        // 1) Load knowledge base + pre-computed embeddings
        try {
            val gson = Gson()
            val jsonStream = app.assets.open("knowledge_base.json").bufferedReader()
            val type = object : TypeToken<List<KnowledgeChunk>>() {}.type
            library = gson.fromJson(jsonStream, type) ?: emptyList()

            // Use pre-computed embeddings from JSON to avoid 45 TFLite passes on startup
            kbEmbeddings = library.map { chunk ->
                val precomputed = chunk.embedding
                if (!precomputed.isNullOrEmpty()) {
                    FloatArray(precomputed.size) { i -> precomputed[i].toFloat() }
                } else {
                    FloatArray(0)
                }
            }
            Log.d(TAG, "Loaded ${library.size} survival chunks with pre-computed vectors.")
        } catch (e: Exception) {
            library = emptyList()
            kbEmbeddings = emptyList()
            Log.e(TAG, "Failed to load knowledge_base.json", e)
        }

        // 2) Init MediaPipe TextEmbedder for user query embedding
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("embedding_model.tflite")
                .build()

            val options = TextEmbedderOptions.builder()
                .setBaseOptions(baseOptions)
                .setL2Normalize(true)
                .setQuantize(false)
                .build()

            textEmbedder = TextEmbedder.createFromOptions(app, options)
            Log.d(TAG, "TextEmbedder initialized")
        } catch (e: Exception) {
            textEmbedder = null
            Log.e(TAG, "TextEmbedder init failed, will use hybrid keyword matching", e)
        }
    }

    /**
     * Lazily loads Gemma 3 1B only when needed, preferring GPU backend for 3x-5x better speed & battery,
     * with automatic fallback to CPU.
     */
    @Synchronized
    private fun ensureLlmLoaded(): LlmInference? {
        resetIdleTimer()
        if (brain != null) return brain

        val assetList = try {
            app.assets.list("")?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }

        val matchedAsset = GEMMA_ASSET_CANDIDATES.firstOrNull { it in assetList }
        if (matchedAsset == null) {
            Log.w(TAG, "Gemma .task file not found in assets. Using Direct RAG Survival Responder.")
            return null
        }

        val modelPath = copyAssetToFile(matchedAsset, matchedAsset)

        // Try GPU first for performance & battery savings, fallback to CPU if unsupported on emulator
        for (backend in listOf(LlmInference.Backend.GPU, LlmInference.Backend.CPU)) {
            try {
                Log.d(TAG, "Attempting to load Gemma with backend: $backend")
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(512)
                    .setMaxTopK(40)
                    .setPreferredBackend(backend)
                    .build()

                brain = LlmInference.createFromOptions(app, options)
                Log.d(TAG, "Gemma loaded successfully on $backend")
                return brain
            } catch (e: Exception) {
                Log.w(TAG, "Backend $backend failed, trying fallback...", e)
            }
        }
        return null
    }

    private fun resetIdleTimer() {
        idleUnloadJob?.cancel()
        idleUnloadJob = viewModelScope.launch(Dispatchers.IO) {
            delay(IDLE_UNLOAD_MS.milliseconds)
            unloadLlmToSaveBattery()
        }
    }

    @Synchronized
    private fun unloadLlmToSaveBattery() {
        if (_isAITyping.value) return
        try {
            brainSession?.close()
            brain?.close()
            Log.d(TAG, "Unloaded Gemma LLM after inactivity to conserve battery & RAM.")
        } catch (e: Exception) {
            Log.w(TAG, "Error unloading LLM", e)
        } finally {
            brainSession = null
            brain = null
        }
    }

    // ----------------- CHAT & STREAMING -----------------

    fun sendMessage(userText: String) {
        if (_isAITyping.value || userText.isBlank()) return

        // 1) Append user message
        val userMsgId = _messages.value.size
        _messages.update { it + ChatMessage(id = userMsgId, text = userText, author = Author.USER) }
        _isAITyping.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 2) Retrieve top survival knowledge chunks
                val topChunks = retrieveTopChunks(userText, TOP_K)
                val sourceNames = topChunks.mapNotNull { it.topic ?: it.knot_name }.distinct()

                // 3) Check if Gemma LLM is available; if not, stream directly from verified RAG knowledge base
                val llm = ensureLlmLoaded()
                if (llm == null) {
                    streamOfflineRagFallback(topChunks, sourceNames)
                    return@launch
                }

                val context = if (topChunks.isEmpty()) {
                    "No specific survival notes were found for this question in the knowledge base."
                } else {
                    topChunks.joinToString("\n\n") { chunk ->
                        "- ${chunk.asContextText().take(CONTEXT_CHARS_PER_CHUNK)}"
                    }
                }

                var prompt = """
                You are WildGuard AI, an offline wilderness survival assistant.
                Use ONLY the Knowledge base below to answer the user's question clearly and safely.

                Knowledge base:
                $context

                Question: $userText

                Answer format:
                - Start with ONE short summary sentence.
                - Give 3–5 clear numbered steps.
                - End with "Caution: " followed by a brief safety warning.
                Answer:
                """.trimIndent()

                if (prompt.length > MAX_PROMPT_CHARS) {
                    prompt = prompt.take(MAX_PROMPT_CHARS)
                }

                val session = createSession(llm)
                if (session == null) {
                    streamOfflineRagFallback(topChunks, sourceNames)
                    return@launch
                }

                // Create placeholder AI message for live token streaming
                val aiMsgId = _messages.value.size
                _messages.update {
                    it + ChatMessage(
                        id = aiMsgId,
                        text = "",
                        author = Author.AI,
                        isStreaming = true,
                        sources = sourceNames
                    )
                }

                val responseBuilder = StringBuilder()
                session.addQueryChunk(prompt)
                session.generateResponseAsync { partialResult, done ->
                    responseBuilder.append(partialResult)
                    val currentText = responseBuilder.toString().trimStart()

                    _messages.update { list ->
                        list.map { msg ->
                            if (msg.id == aiMsgId) {
                                msg.copy(
                                    text = currentText.ifBlank { "..." },
                                    isStreaming = !done
                                )
                            } else msg
                        }
                    }

                    if (done) {
                        val finalClean = responseBuilder.toString().trim()
                        if (finalClean.length < 10) {
                            viewModelScope.launch(Dispatchers.IO) {
                                _messages.update { list -> list.filterNot { it.id == aiMsgId } }
                                streamOfflineRagFallback(topChunks, sourceNames)
                            }
                        } else {
                            _isAITyping.value = false
                            resetIdleTimer()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during sendMessage()", e)
                _messages.update {
                    it + ChatMessage(
                        id = it.size,
                        text = "Unable to process request: ${e.localizedMessage}",
                        author = Author.AI
                    )
                }
                _isAITyping.value = false
            }
        }
    }

    /**
     * Instant RAG responder when the 600MB .task model file is not yet bundled in assets
     * or if LLM output is empty. Formats verified survival guidelines from knowledge_base.json.
     */
    private suspend fun streamOfflineRagFallback(
        chunks: List<KnowledgeChunk>,
        sources: List<String>
    ) {
        val formattedAnswer = if (chunks.isEmpty()) {
            "I couldn't find a matching survival protocol for that topic in the offline knowledge base. Try asking about fire making, knots (like Bowline or Reef knot), hypothermia, bleeding, or shelter.\n\nCaution: Always prioritize shelter, water, and signaling for rescue in an emergency."
        } else {
            val primary = chunks.first()
            val title = primary.topic ?: primary.knot_name ?: "Survival Protocol"
            val body = primary.asContextText().trim()
            buildString {
                append("$title\n\n")
                append(body)
                if (chunks.size > 1) {
                    val secondary = chunks[1]
                    val secTitle = secondary.topic ?: secondary.knot_name
                    if (!secTitle.isNullOrBlank()) {
                        append("\n\nRelated ($secTitle):\n")
                        append(secondary.asContextText().take(240).trim())
                    }
                }
                append("\n\nCaution: Follow each survival step carefully and assess your surroundings for immediate hazards before acting.")
            }
        }

        val aiMsgId = _messages.value.size
        _messages.update {
            it + ChatMessage(
                id = aiMsgId,
                text = "",
                author = Author.AI,
                isStreaming = true,
                sources = sources
            )
        }

        // Smooth token-like streaming experience
        val words = formattedAnswer.split(" ")
        val sb = StringBuilder()
        for ((index, word) in words.withIndex()) {
            if (index > 0) sb.append(" ")
            sb.append(word)
            if (index % 3 == 0 || index == words.lastIndex) {
                val partial = sb.toString()
                _messages.update { list ->
                    list.map { msg ->
                        if (msg.id == aiMsgId) {
                            msg.copy(
                                text = partial,
                                isStreaming = index != words.lastIndex
                            )
                        } else msg
                    }
                }
                delay(18)
            }
        }
        _isAITyping.value = false
    }

    // ----------------- RAG RETRIEVAL -----------------

    private fun retrieveTopChunks(query: String, k: Int): List<KnowledgeChunk> {
        if (library.isEmpty()) return emptyList()

        return try {
            if (textEmbedder != null && kbEmbeddings.isNotEmpty()) {
                findTopMatchesWithEmbedder(query, k)
            } else {
                findTopMatchesByKeyword(query, library, k)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falling back to keyword search", e)
            findTopMatchesByKeyword(query, library, k)
        }
    }

    private fun embedText(text: String): FloatArray {
        val embedder = textEmbedder ?: return FloatArray(0)
        val result = embedder.embed(text)
        val embeddings = result.embeddingResult().embeddings()
        if (embeddings.isEmpty()) return FloatArray(0)
        return embeddings[0].floatEmbedding()
    }

    private fun findTopMatchesWithEmbedder(query: String, k: Int): List<KnowledgeChunk> {
        val qVec = embedText(query)
        val stopwords = setOf(
            "how", "what", "when", "where", "why", "who",
            "to", "a", "an", "the", "is", "are", "do", "i",
            "you", "in", "of", "for", "on", "and", "or", "can", "with"
        )

        val queryWords = query.lowercase()
            .split(" ", "\n", "\t", ",", ".", "?", "!", ":", ";")
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 2 }

        val contentWords = queryWords.filter { it !in stopwords }.ifEmpty { queryWords }.toSet()

        data class Scored(
            val chunk: KnowledgeChunk,
            val sim: Double,
            val titleScore: Int,
            val bodyScore: Int
        )

        val scored = library.mapIndexed { index, chunk ->
            val vec = kbEmbeddings.getOrNull(index)
            val sim = if (vec != null && vec.isNotEmpty() && qVec.isNotEmpty() && vec.size == qVec.size) {
                cosine(qVec, vec)
            } else {
                0.0
            }

            val titleText = "${chunk.topic.orEmpty()} ${chunk.knot_name.orEmpty()}".lowercase()
            val bodyText = "${chunk.region.orEmpty()} ${chunk.text.orEmpty()} ${chunk.description.orEmpty()} ${chunk.instructions.orEmpty()}".lowercase()

            val titleScore = contentWords.count { titleText.contains(it) }
            val bodyScore = contentWords.count { bodyText.contains(it) }

            Scored(chunk, sim, titleScore, bodyScore)
        }

        val withTitleMatches = scored.filter { it.titleScore > 0 }
        val pool = if (withTitleMatches.isNotEmpty()) withTitleMatches else scored

        return pool.sortedWith(
            compareByDescending<Scored> { it.titleScore }
                .thenByDescending { it.bodyScore }
                .thenByDescending { it.sim }
        ).take(k).map { it.chunk }
    }

    private fun findTopMatchesByKeyword(
        query: String,
        kb: List<KnowledgeChunk>,
        k: Int
    ): List<KnowledgeChunk> {
        val queryWords = query.lowercase()
            .split(" ", "\n", "\t", ",", ".", "?", "!", ":", ";")
            .filter { it.length > 2 }

        return kb.map { chunk ->
            val textAll = "${chunk.topic.orEmpty()} ${chunk.knot_name.orEmpty()} ${chunk.text.orEmpty()} ${chunk.description.orEmpty()} ${chunk.instructions.orEmpty()}".lowercase()
            val titleText = "${chunk.topic.orEmpty()} ${chunk.knot_name.orEmpty()}".lowercase()
            val score = queryWords.sumOf { word ->
                (if (titleText.contains(word)) 3 else 0) + (if (textAll.contains(word)) 1 else 0)
            }
            chunk to score
        }
            .sortedByDescending { it.second }
            .take(k)
            .map { it.first }
    }

    private fun KnowledgeChunk.asContextText(): String {
        return when {
            !text.isNullOrBlank() -> text
            !description.isNullOrBlank() -> {
                val title = topic ?: knot_name ?: "Knot"
                val uses = use_cases?.joinToString(", ").orEmpty()
                val instr = instructions.orEmpty()
                "$title.\nDescription: $description\nUses: $uses\nInstructions: $instr"
            }
            else -> ""
        }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        val len = minOf(a.size, b.size)
        var dot = 0.0
        var magA = 0.0
        var magB = 0.0
        for (i in 0 until len) {
            val va = a[i].toDouble()
            val vb = b[i].toDouble()
            dot += va * vb
            magA += va * va
            magB += vb * vb
        }
        if (magA == 0.0 || magB == 0.0) return 0.0
        return dot / (sqrt(magA) * sqrt(magB))
    }

    private fun copyAssetToFile(assetName: String, outFileName: String): String {
        val file = File(app.filesDir, outFileName)
        if (!file.exists()) {
            app.assets.open(assetName).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file.absolutePath
    }

    private fun createSession(engine: LlmInference): LlmInferenceSession? {
        try {
            brainSession?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing old session", e)
        }

        return try {
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(20)
                .setTopP(0.8f)
                .setTemperature(0.2f)
                .build()
            LlmInferenceSession.createFromOptions(engine, sessionOptions).also {
                brainSession = it
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create LlmInferenceSession", e)
            null
        }
    }

    /**
     * Resets the conversation back to the initial Figma "Home Page" centered prompt state.
     */
    fun resetChatSession() {
        brainSession?.let {
            try {
                it.close()
            } catch (_: Exception) {
            }
        }
        brainSession = null
        _isAITyping.value = false
        _messages.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        unloadLlmToSaveBattery()
        try {
            textEmbedder?.close()
        } catch (_: Exception) {
        }
    }
}
