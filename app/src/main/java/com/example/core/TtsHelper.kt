package com.example.core

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class TtsHelper(context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        _speakingMessageId.value = null
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _speakingMessageId.value = null
                    }
                })
            }
        }
    }

    fun toggleSpeak(messageId: String, markdownText: String) {
        if (_speakingMessageId.value == messageId) {
            stop()
            return
        }
        if (!isReady) return
        val cleanText = stripMarkdownForSpeech(markdownText)
        if (cleanText.isBlank()) return
        _speakingMessageId.value = messageId
        tts?.speak(
            cleanText.take(3900),
            TextToSpeech.QUEUE_FLUSH,
            null,
            UUID.randomUUID().toString()
        )
    }

    fun stop() {
        tts?.stop()
        _speakingMessageId.value = null
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _speakingMessageId.value = null
    }

    private fun stripMarkdownForSpeech(input: String): String {
        return input
            .replace(Regex("```[\\s\\S]*?```"), " code block omitted. ")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("[#*>_~\\-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
