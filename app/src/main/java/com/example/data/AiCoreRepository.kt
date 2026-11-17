package com.example.data

import android.util.Log
import com.example.model.ChannelDoc
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiCoreRepository"

data class ConstitutionSuggestion(
    val proposedPrompt: String,
    val reason: String,
    val impactScope: String,
    val riskLevel: String,
    val versionTag: String
)

data class ExtractedStyleProfile(
    val visualProfile: String,
    val tempoProfile: String,
    val audioProfile: String,
    val confidenceScore: Int
)

class AiCoreRepository {

    private val constitutionModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "proposedPrompt" to Schema.string("Refined Channel System Prompt / Constitution"),
                "reason" to Schema.string("Evidence-based rationale for the change"),
                "impactScope" to Schema.string("Affected workflows, skills, and quality gates"),
                "riskLevel" to Schema.enumeration(listOf("LOW", "MEDIUM", "HIGH"), "Risk assessment"),
                "versionTag" to Schema.string("Suggested next version tag e.g. v2.2")
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val styleModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "visualProfile" to Schema.string("Color grading, framing, visual composition rules"),
                "tempoProfile" to Schema.string("Cut cadence, transition style, pacing rules"),
                "audioProfile" to Schema.string("BGM ducking, SFX rhythm, loudness target"),
                "confidenceScore" to Schema.integer("Confidence score between 60 and 99")
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val generalModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    /**
     * Section 4.4: AI analyzes Channel System Prompt and proposes a change for Owner Approval.
     * AI never auto-publishes the change.
     */
    suspend fun proposeConstitutionImprovement(
        channel: ChannelDoc,
        operatorGoal: String
    ): Result<ConstitutionSuggestion> {
        return try {
            val prompt = """
                You are FlowKit AI Core Advisor operating under Governance Core.
                Analyze the following Channel Constitution (System Prompt) and propose a concise, actionable improvement.
                Remember: you are creating a Change Proposal for human owner review; do NOT loosen safety or rights policies.
                
                Channel: ${channel.name} (${channel.category})
                Current Version: ${channel.promptVersion}
                Min Resolution: ${channel.minResolution}
                Editing Priority: ${channel.editingPriority}
                Current Constitution:
                "${channel.systemPrompt}"
                
                Operator Optimization Goal:
                "$operatorGoal"
            """.trimIndent()

            val response = withTimeout(60_000L) {
                constitutionModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("AI Core returned empty response.")
            val json = JSONObject(rawJson)
            Result.success(
                ConstitutionSuggestion(
                    proposedPrompt = json.getString("proposedPrompt"),
                    reason = json.getString("reason"),
                    impactScope = json.getString("impactScope"),
                    riskLevel = json.optString("riskLevel", "LOW"),
                    versionTag = json.optString("versionTag", "${channel.promptVersion}-draft")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Constitution proposal timed out", e)
            Result.failure(Exception("AI Core timed out after 60s. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Constitution proposal failed", e)
            Result.failure(e)
        }
    }

    /**
     * Section 13: Reference-to-Editing Intelligence.
     * Converts reference video notes into a structured StyleProfile.
     */
    suspend fun extractStyleProfile(
        channelName: String,
        referenceNotes: String
    ): Result<ExtractedStyleProfile> {
        return try {
            val prompt = """
                You are FlowKit Reference-to-Editing Intelligence.
                Convert the following reference video observations for channel "$channelName" into a concise, structured Style Profile.
                
                Reference Observations:
                "$referenceNotes"
            """.trimIndent()

            val response = withTimeout(60_000L) {
                styleModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("AI Core returned empty response.")
            val json = JSONObject(rawJson)
            Result.success(
                ExtractedStyleProfile(
                    visualProfile = json.getString("visualProfile"),
                    tempoProfile = json.getString("tempoProfile"),
                    audioProfile = json.getString("audioProfile"),
                    confidenceScore = json.optInt("confidenceScore", 88).coerceIn(0, 100)
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Style extraction timed out", e)
            Result.failure(Exception("Style extraction timed out after 60s."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Style extraction failed", e)
            Result.failure(e)
        }
    }

    /**
     * Section 6.3 / 8.2: Governed TEXT_GENERATION executed only after AIGG Gateway approves.
     */
    suspend fun generateProductionHook(
        channel: ChannelDoc,
        topic: String
    ): Result<String> {
        return try {
            val prompt = """
                Follow this Channel Constitution strictly:
                "${channel.systemPrompt}"
                
                Style Tempo: ${channel.styleTempo}
                Style Audio: ${channel.styleAudio}
                
                Draft a crisp 3-beat production hook + editing cue sheet (under 110 words) for:
                "$topic"
            """.trimIndent()

            val response = withTimeout(60_000L) {
                generalModel.generateContent(prompt)
            }
            val text = response.text
            if (text.isNullOrBlank()) {
                Result.failure(IllegalStateException("AI Core returned empty text."))
            } else {
                Result.success(text.trim())
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Hook generation timed out", e)
            Result.failure(Exception("AI generation timed out after 60s."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Hook generation failed", e)
            Result.failure(e)
        }
    }
}
