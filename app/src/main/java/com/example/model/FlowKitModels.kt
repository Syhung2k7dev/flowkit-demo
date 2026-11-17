package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONArray
import org.json.JSONObject

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class PolicyDecision(val label: String) {
    ALLOW("ALLOW"),
    DENY("DENY"),
    REQUIRE_APPROVAL("REQUIRE_APPROVAL"),
    INHERIT("INHERIT");

    companion object {
        fun fromString(raw: String, fallback: PolicyDecision = DENY): PolicyDecision =
            entries.firstOrNull { it.name == raw } ?: fallback
    }
}

enum class AiCapability(val label: String, val group: String, val defaultFallback: String) {
    TEXT_GENERATION("Text Generation", "GENERATION", "Use verified script template"),
    IMAGE_GENERATION("Image Generation", "GENERATION", "Use approved thumbnail template"),
    VIDEO_GENERATION("Video Generation", "GENERATION", "Pull verified library footage"),
    AUDIO_GENERATION("Audio / Voice Gen", "GENERATION", "Use pre-recorded voiceover / BGM"),
    AI_ANALYSIS("AI Vision & Analysis", "ANALYSIS", "Rule-based scene metadata parser"),
    AI_ENHANCEMENT("AI Enhancement", "TRANSFORMATION", "FFmpeg standard color/denoise filter"),
    NONE("No AI Required", "EDITING", "Standard Media Engine execution");

    companion object {
        fun fromString(raw: String): AiCapability =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

enum class WorkflowLifecycle(val label: String) {
    CREATED("Created"),
    VALIDATED("Validated"),
    RUNNING("Running"),
    AWAITING_APPROVAL("Awaiting Approval"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    FAILED("Failed");

    companion object {
        fun fromString(raw: String): WorkflowLifecycle =
            entries.firstOrNull { it.name == raw } ?: CREATED
    }
}

enum class BranchMode(val label: String) {
    EDITING_ONLY("Editing Branch Only"),
    GENERATIVE_ONLY("Generative Branch Only"),
    HYBRID("Hybrid (Editing + Generative)");

    companion object {
        fun fromString(raw: String): BranchMode =
            entries.firstOrNull { it.name == raw } ?: HYBRID
    }
}

enum class SkillLevel(val badge: String, val title: String) {
    LEVEL_0_PRIMITIVE("L0", "Primitive"),
    LEVEL_1_FUNCTIONAL("L1", "Functional"),
    LEVEL_2_COMPOSITE("L2", "Composite"),
    LEVEL_3_PRODUCTION("L3", "Production");

    companion object {
        fun fromString(raw: String): SkillLevel =
            entries.firstOrNull { it.name == raw } ?: LEVEL_1_FUNCTIONAL
    }
}

enum class QualityGateResult(val label: String) {
    NONE("Not Run"),
    PASS("PASS"),
    PASS_WITH_WARNINGS("PASS_WITH_WARNINGS"),
    NEEDS_REVIEW("NEEDS_REVIEW"),
    FAIL("FAIL");

    companion object {
        fun fromString(raw: String): QualityGateResult =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

enum class ProposalStatus(val label: String) {
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected");

    companion object {
        fun fromString(raw: String): ProposalStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class RiskLevel(val label: String) {
    LOW("Low Risk"),
    MEDIUM("Medium Risk"),
    HIGH("High Risk");

    companion object {
        fun fromString(raw: String): RiskLevel =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

@Keep
@IgnoreExtraProperties
data class GlobalPolicyDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val version: String = "v1.0",
    val minResolution: String = "720p",
    val requireRightsCheck: Boolean = true,
    val requireOwnerApprovalForPrompts: Boolean = true,
    val textGen: String = "ALLOW",
    val imageGen: String = "REQUIRE_APPROVAL",
    val videoGen: String = "DENY",
    val audioGen: String = "REQUIRE_APPROVAL",
    val aiAnalysis: String = "ALLOW",
    val aiEnhancement: String = "ALLOW",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun decisionFor(capability: AiCapability): PolicyDecision = when (capability) {
        AiCapability.TEXT_GENERATION -> PolicyDecision.fromString(textGen)
        AiCapability.IMAGE_GENERATION -> PolicyDecision.fromString(imageGen)
        AiCapability.VIDEO_GENERATION -> PolicyDecision.fromString(videoGen)
        AiCapability.AUDIO_GENERATION -> PolicyDecision.fromString(audioGen)
        AiCapability.AI_ANALYSIS -> PolicyDecision.fromString(aiAnalysis)
        AiCapability.AI_ENHANCEMENT -> PolicyDecision.fromString(aiEnhancement)
        AiCapability.NONE -> PolicyDecision.ALLOW
    }
}

@Keep
@IgnoreExtraProperties
data class ChannelDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = "",
    val description: String = "",
    val systemPrompt: String = "",
    val promptVersion: String = "v1.0",
    val previousSystemPrompt: String = "",
    val minResolution: String = "1080p",
    val editingPriority: String = "HYBRID_BALANCED",
    val textGen: String = "INHERIT",
    val imageGen: String = "INHERIT",
    val videoGen: String = "INHERIT",
    val audioGen: String = "INHERIT",
    val aiAnalysis: String = "INHERIT",
    val aiEnhancement: String = "INHERIT",
    val styleVisual: String = "",
    val styleTempo: String = "",
    val styleAudio: String = "",
    val styleConfidence: Long = 85L,
    val productionsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun rawDecisionFor(capability: AiCapability): PolicyDecision = when (capability) {
        AiCapability.TEXT_GENERATION -> PolicyDecision.fromString(textGen, PolicyDecision.INHERIT)
        AiCapability.IMAGE_GENERATION -> PolicyDecision.fromString(imageGen, PolicyDecision.INHERIT)
        AiCapability.VIDEO_GENERATION -> PolicyDecision.fromString(videoGen, PolicyDecision.INHERIT)
        AiCapability.AUDIO_GENERATION -> PolicyDecision.fromString(audioGen, PolicyDecision.INHERIT)
        AiCapability.AI_ANALYSIS -> PolicyDecision.fromString(aiAnalysis, PolicyDecision.INHERIT)
        AiCapability.AI_ENHANCEMENT -> PolicyDecision.fromString(aiEnhancement, PolicyDecision.INHERIT)
        AiCapability.NONE -> PolicyDecision.ALLOW
    }
}

@Keep
@IgnoreExtraProperties
data class WorkflowDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val name: String = "",
    val triggerType: String = "MANUAL",
    val lifecycleState: String = "CREATED",
    val branchMode: String = "HYBRID",
    val caslJson: String = "",
    val currentStepIndex: Long = 0L,
    val totalSteps: Long = 1L,
    val lastQualityGate: String = "NONE",
    val lastTraceSummary: String = "",
    val version: String = "v1.0",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class SkillDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val level: String = "LEVEL_1_FUNCTIONAL",
    val branch: String = "EDITING",
    val requiredCapability: String = "NONE",
    val fallbackStrategy: String = "",
    val inputSchemaSummary: String = "",
    val outputSchemaSummary: String = "",
    val version: String = "v1.0",
    val enabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class AssetDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val channelId: String = "",
    val title: String = "",
    val assetType: String = "VIDEO",
    val resolution: String = "1080p",
    val qualityScore: Long = 90L,
    val rightsStatus: String = "VERIFIED",
    val provenanceChain: String = "",
    val tags: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ApprovalProposalDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val channelId: String = "",
    val targetType: String = "SYSTEM_PROMPT",
    val targetId: String = "",
    val title: String = "",
    val currentValue: String = "",
    val proposedValue: String = "",
    val reason: String = "",
    val impactScope: String = "",
    val riskLevel: String = "LOW",
    val proposer: String = "AI Core Advisor",
    val status: String = "PENDING",
    val versionTag: String = "v1.1",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class AuditLogDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val eventType: String = "",
    val actor: String = "",
    val channelId: String = "",
    val summary: String = "",
    val severity: String = "INFO",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

data class ResolvedPermission(
    val capability: AiCapability,
    val effectiveDecision: PolicyDecision,
    val source: String,
    val overriddenAttempt: Boolean = false
)

/**
 * Governance Core Policy Resolver (Section 5.1):
 * 1. DENY cannot be overridden by lower levels.
 * 2. REQUIRE_APPROVAL must be processed before execution unless tightened to DENY.
 * 3. INHERIT resolves from Global Policy.
 * 4. Unknown permission defaults to DENY.
 */
object PolicyResolver {
    fun resolve(global: GlobalPolicyDoc, channel: ChannelDoc, capability: AiCapability): ResolvedPermission {
        if (capability == AiCapability.NONE) {
            return ResolvedPermission(capability, PolicyDecision.ALLOW, "Media Engine (No AI)")
        }
        val globalDecision = global.decisionFor(capability)
        val channelDecision = channel.rawDecisionFor(capability)

        if (globalDecision == PolicyDecision.DENY) {
            val attemptedRelax = channelDecision == PolicyDecision.ALLOW || channelDecision == PolicyDecision.REQUIRE_APPROVAL
            return ResolvedPermission(
                capability = capability,
                effectiveDecision = PolicyDecision.DENY,
                source = "Global Policy (DENY locked)",
                overriddenAttempt = attemptedRelax
            )
        }

        if (channelDecision == PolicyDecision.INHERIT) {
            return ResolvedPermission(
                capability = capability,
                effectiveDecision = globalDecision,
                source = "Inherited from Global (${global.version})"
            )
        }

        if (globalDecision == PolicyDecision.REQUIRE_APPROVAL && channelDecision == PolicyDecision.ALLOW) {
            return ResolvedPermission(
                capability = capability,
                effectiveDecision = PolicyDecision.REQUIRE_APPROVAL,
                source = "Global Strict Approval Gate (cannot relax)",
                overriddenAttempt = true
            )
        }

        return ResolvedPermission(
            capability = capability,
            effectiveDecision = channelDecision,
            source = "Channel Policy (${channel.name})"
        )
    }
}

data class CaslStepNode(
    val stepId: String,
    val label: String,
    val nodeType: String, // SEQUENCE, IF_ELSE, PARALLEL, SKILL_CALL, QUALITY_GATE
    val branch: String,   // EDITING, GENERATIVE, GOVERNANCE, QUALITY
    val skillName: String,
    val capability: AiCapability,
    val fallbackLabel: String,
    val conditionExpr: String = ""
)

data class StepTraceItem(
    val stepIndex: Int,
    val stepId: String,
    val label: String,
    val branch: String,
    val outcome: String, // EXECUTED, FALLBACK_EXECUTED, AWAITING_APPROVAL, BLOCKED_NO_FALLBACK, PASS
    val detail: String,
    val policyDecision: PolicyDecision
)

data class SimulationResult(
    val finalLifecycle: WorkflowLifecycle,
    val qualityGate: QualityGateResult,
    val blockedStepIndex: Int,
    val blockedStepName: String?,
    val blockedCapability: AiCapability?,
    val traces: List<StepTraceItem>,
    val summaryText: String
)

object CaslParser {
    fun parseSteps(caslJson: String): List<CaslStepNode> {
        return try {
            val root = JSONObject(caslJson)
            val arr = root.optJSONArray("steps") ?: JSONArray()
            (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                CaslStepNode(
                    stepId = obj.optString("id", "step_${idx + 1}"),
                    label = obj.optString("label", "Step ${idx + 1}"),
                    nodeType = obj.optString("type", "SKILL_CALL"),
                    branch = obj.optString("branch", "EDITING"),
                    skillName = obj.optString("skill", "Generic Skill"),
                    capability = AiCapability.fromString(obj.optString("capability", "NONE")),
                    fallbackLabel = obj.optString("fallback", ""),
                    conditionExpr = obj.optString("condition", "")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun simulate(
        workflow: WorkflowDoc,
        channel: ChannelDoc,
        globalPolicy: GlobalPolicyDoc,
        assets: List<AssetDoc>,
        approvalGrantedForResume: Boolean = false
    ): SimulationResult {
        val steps = parseSteps(workflow.caslJson)
        if (steps.isEmpty()) {
            return SimulationResult(
                finalLifecycle = WorkflowLifecycle.FAILED,
                qualityGate = QualityGateResult.FAIL,
                blockedStepIndex = 0,
                blockedStepName = null,
                blockedCapability = null,
                traces = emptyList(),
                summaryText = "CASL Schema Validation Failed: No executable steps found."
            )
        }

        val traces = mutableListOf<StepTraceItem>()
        val channelAssets = assets.filter { it.channelId == channel.id }
        val hasUnknownRights = globalPolicy.requireRightsCheck &&
            channelAssets.any { it.rightsStatus == "UNKNOWN" }

        for ((index, step) in steps.withIndex()) {
            val resolved = PolicyResolver.resolve(globalPolicy, channel, step.capability)
            when {
                step.nodeType == "QUALITY_GATE" -> {
                    val gateResult = if (hasUnknownRights) QualityGateResult.PASS_WITH_WARNINGS else QualityGateResult.PASS
                    traces += StepTraceItem(
                        stepIndex = index,
                        stepId = step.stepId,
                        label = step.label,
                        branch = step.branch,
                        outcome = gateResult.name,
                        detail = if (hasUnknownRights) {
                            "Quality Gate warning: 1 channel asset has UNKNOWN rights excluded from final mix; minRes=${channel.minResolution} verified."
                        } else {
                            "Automated Quality Gate PASS: ${channel.minResolution} compliant, provenance chain verified."
                        },
                        policyDecision = PolicyDecision.ALLOW
                    )
                }

                resolved.effectiveDecision == PolicyDecision.ALLOW -> {
                    traces += StepTraceItem(
                        stepIndex = index,
                        stepId = step.stepId,
                        label = step.label,
                        branch = step.branch,
                        outcome = "EXECUTED",
                        detail = "Executed '${step.skillName}' (${resolved.source}).",
                        policyDecision = PolicyDecision.ALLOW
                    )
                }

                resolved.effectiveDecision == PolicyDecision.DENY -> {
                    val fb = step.fallbackLabel.ifBlank { step.capability.defaultFallback }
                    if (fb.isNotBlank()) {
                        traces += StepTraceItem(
                            stepIndex = index,
                            stepId = step.stepId,
                            label = step.label,
                            branch = "EDITING",
                            outcome = "FALLBACK_EXECUTED",
                            detail = "AIGG DENY (${resolved.source}) -> Automatic Fallback: $fb.",
                            policyDecision = PolicyDecision.DENY
                        )
                    } else {
                        traces += StepTraceItem(
                            stepIndex = index,
                            stepId = step.stepId,
                            label = step.label,
                            branch = step.branch,
                            outcome = "BLOCKED_NO_FALLBACK",
                            detail = "AIGG DENY with no registered fallback. Halted.",
                            policyDecision = PolicyDecision.DENY
                        )
                        return SimulationResult(
                            finalLifecycle = WorkflowLifecycle.FAILED,
                            qualityGate = QualityGateResult.FAIL,
                            blockedStepIndex = index,
                            blockedStepName = step.label,
                            blockedCapability = step.capability,
                            traces = traces,
                            summaryText = "Halted at '${step.label}': AIGG denied ${step.capability.name} with no fallback."
                        )
                    }
                }

                resolved.effectiveDecision == PolicyDecision.REQUIRE_APPROVAL -> {
                    if (approvalGrantedForResume && index <= workflow.currentStepIndex.toInt()) {
                        traces += StepTraceItem(
                            stepIndex = index,
                            stepId = step.stepId,
                            label = step.label,
                            branch = step.branch,
                            outcome = "EXECUTED",
                            detail = "Owner Approval verified -> Executed '${step.skillName}' via AIGG Gateway.",
                            policyDecision = PolicyDecision.ALLOW
                        )
                    } else {
                        traces += StepTraceItem(
                            stepIndex = index,
                            stepId = step.stepId,
                            label = step.label,
                            branch = step.branch,
                            outcome = "AWAITING_APPROVAL",
                            detail = "AIGG Strict Gate (${resolved.source}): paused at step ${index + 1}/${steps.size}, queued in Approval Center.",
                            policyDecision = PolicyDecision.REQUIRE_APPROVAL
                        )
                        return SimulationResult(
                            finalLifecycle = WorkflowLifecycle.AWAITING_APPROVAL,
                            qualityGate = QualityGateResult.NEEDS_REVIEW,
                            blockedStepIndex = index,
                            blockedStepName = step.label,
                            blockedCapability = step.capability,
                            traces = traces,
                            summaryText = "Paused at '${step.label}' (${index + 1}/${steps.size}) awaiting Owner Approval for ${step.capability.label}."
                        )
                    }
                }

                else -> Unit
            }
        }

        val finalGate = if (hasUnknownRights) QualityGateResult.PASS_WITH_WARNINGS else QualityGateResult.PASS
        return SimulationResult(
            finalLifecycle = WorkflowLifecycle.COMPLETED,
            qualityGate = finalGate,
            blockedStepIndex = steps.size,
            blockedStepName = null,
            blockedCapability = null,
            traces = traces,
            summaryText = "Completed ${steps.size}/${steps.size} steps (${finalGate.label}). Provenance & Audit recorded."
        )
    }
}
