package com.example.data

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.model.ApprovalProposalDoc
import com.example.model.AssetDoc
import com.example.model.AuditLogDoc
import com.example.model.ChannelDoc
import com.example.model.GlobalPolicyDoc
import com.example.model.QualityGateResult
import com.example.model.SkillDoc
import com.example.model.WorkflowDoc
import com.example.model.WorkflowLifecycle
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FlowKitRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing FlowKit Firestore.")

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    fun observeGlobalPolicy(userId: String): Flow<GlobalPolicyDoc?> {
        val path = "users/$userId/global_policy/default"
        return userDoc(userId).collection("global_policy").document("default")
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(GlobalPolicyDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.GET, path, userId)
                throw error
            }
    }

    fun observeChannels(userId: String): Flow<List<ChannelDoc>> {
        val path = "users/$userId/channels"
        return userDoc(userId).collection("channels")
            .snapshots()
            .map { snap ->
                snap.toObjects(ChannelDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.name }
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, path, userId)
                throw error
            }
    }

    fun observeWorkflows(userId: String): Flow<List<WorkflowDoc>> {
        val path = "users/$userId/workflows"
        return userDoc(userId).collection("workflows")
            .snapshots()
            .map { snap ->
                snap.toObjects(WorkflowDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.name }
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, path, userId)
                throw error
            }
    }

    fun observeSkills(userId: String): Flow<List<SkillDoc>> {
        val path = "users/$userId/skills"
        return userDoc(userId).collection("skills")
            .snapshots()
            .map { snap ->
                snap.toObjects(SkillDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.level }
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, path, userId)
                throw error
            }
    }

    fun observeAssets(userId: String): Flow<List<AssetDoc>> {
        val path = "users/$userId/assets"
        return userDoc(userId).collection("assets")
            .snapshots()
            .map { snap ->
                snap.toObjects(AssetDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.qualityScore }
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, path, userId)
                throw error
            }
    }

    fun observeProposals(userId: String): Flow<List<ApprovalProposalDoc>> {
        val path = "users/$userId/proposals"
        return userDoc(userId).collection("proposals")
            .snapshots()
            .map { snap ->
                snap.toObjects(ApprovalProposalDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareBy<ApprovalProposalDoc> { if (it.status == "PENDING") 0 else 1 }
                            .thenByDescending { it.createdAt?.seconds ?: 0L }
                    )
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, path, userId)
                throw error
            }
    }

    fun observeAuditLogs(userId: String): Flow<List<AuditLogDoc>> {
        val path = "users/$userId/audit_logs"
        return userDoc(userId).collection("audit_logs")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(40)
            .snapshots()
            .map { snap ->
                snap.toObjects(AuditLogDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, path, userId)
                throw error
            }
    }

    private fun unwrapAndLog(error: Throwable, op: OperationType, path: String, userId: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }

    suspend fun seedWorkspaceIfEmpty(userId: String): Result<Boolean> {
        val path = "users/$userId/channels"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }

            val existingChannels = userDoc(uid).collection("channels").limit(1).get().await()
            if (!existingChannels.isEmpty) return Result.success(false)

            val batch = db.batch()

            // 1. Global Policy
            batch.set(
                userDoc(uid).collection("global_policy").document("default"),
                mapOf(
                    "userId" to uid,
                    "version" to "v1.2",
                    "minResolution" to "720p",
                    "requireRightsCheck" to true,
                    "requireOwnerApprovalForPrompts" to true,
                    "textGen" to "ALLOW",
                    "imageGen" to "REQUIRE_APPROVAL",
                    "videoGen" to "DENY",
                    "audioGen" to "REQUIRE_APPROVAL",
                    "aiAnalysis" to "ALLOW",
                    "aiEnhancement" to "ALLOW",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            // 2. Channels
            batch.set(
                userDoc(uid).collection("channels").document("ch_gameplay"),
                mapOf(
                    "userId" to uid,
                    "name" to "Gameplay Channel",
                    "category" to "Gaming",
                    "description" to "Fast-paced 1080p gameplay highlights prioritizing authentic footage & Editing Branch.",
                    "systemPrompt" to "Channel Constitution: Prioritize real captured gameplay footage (1080p+). AI may analyze scenes, draft commentary scripts, and propose thumbnail concepts. Generating synthetic gameplay video is strictly prohibited.",
                    "promptVersion" to "v2.0",
                    "previousSystemPrompt" to "Channel Constitution v1.0: Prioritize gameplay footage 720p+ and automatic cuts.",
                    "minResolution" to "1080p",
                    "editingPriority" to "EDITING_FIRST",
                    "textGen" to "ALLOW",
                    "imageGen" to "REQUIRE_APPROVAL",
                    "videoGen" to "DENY",
                    "audioGen" to "DENY",
                    "aiAnalysis" to "ALLOW",
                    "aiEnhancement" to "ALLOW",
                    "styleVisual" to "High-contrast HUD framing, zero motion blur on peak kills",
                    "styleTempo" to "2.2s average cut interval, J-cut audio lead 0.3s",
                    "styleAudio" to "Duck BGM -14dB on voiceover, punchy SFX risers",
                    "styleConfidence" to 94L,
                    "productionsCount" to 12L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            batch.set(
                userDoc(uid).collection("channels").document("ch_aistory"),
                mapOf(
                    "userId" to uid,
                    "name" to "AI Story Channel",
                    "category" to "AI Story",
                    "description" to "Narrative storytelling combining generative key visuals, AI voiceover, and timeline assembly.",
                    "systemPrompt" to "Channel Constitution: Craft atmospheric visual narratives with consistent character framing, dramatic pacing, and curated voiceovers. Every generated asset requires AIGG provenance logging and strict quality gate inspection.",
                    "promptVersion" to "v1.4",
                    "previousSystemPrompt" to "Channel Constitution v1.0: Generate story visuals and narrations.",
                    "minResolution" to "1080p",
                    "editingPriority" to "HYBRID_BALANCED",
                    "textGen" to "ALLOW",
                    "imageGen" to "REQUIRE_APPROVAL",
                    "videoGen" to "INHERIT",
                    "audioGen" to "REQUIRE_APPROVAL",
                    "aiAnalysis" to "ALLOW",
                    "aiEnhancement" to "ALLOW",
                    "styleVisual" to "Cinematic 2.39:1 matte, warm amber & slate grading",
                    "styleTempo" to "4.5s contemplative beats with cross-dissolve transitions",
                    "styleAudio" to "Warm baritone narration -12 LUFS, ambient synth bed",
                    "styleConfidence" to 89L,
                    "productionsCount" to 7L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            // 3. Workflows with CASL JSON DSL
            val gameplayCasl = """
                {
                  "caslVersion": "1.0",
                  "workflow": "gameplay_highlight_pipeline",
                  "steps": [
                    {"id":"s1","label":"Ingest & Rights Verify","type":"SKILL_CALL","branch":"EDITING","skill":"Metadata & Rights Probe","capability":"NONE","fallback":"Reject asset"},
                    {"id":"s2","label":"AI Vision Highlight Detection","type":"SKILL_CALL","branch":"ANALYSIS","skill":"Scene & Motion Analyzer","capability":"AI_ANALYSIS","fallback":"Threshold audio peak detector"},
                    {"id":"s3","label":"Script Hook & Callouts","type":"SKILL_CALL","branch":"GENERATIVE","skill":"Narrative Hook Writer","capability":"TEXT_GENERATION","fallback":"Use verified intro template"},
                    {"id":"s4","label":"Synthetic B-Roll Check","type":"IF_ELSE","branch":"GENERATIVE","skill":"Synthetic Gameplay Gen","capability":"VIDEO_GENERATION","fallback":"Pull verified 1080p library clip"},
                    {"id":"s5","label":"Timeline Cut & Audio Sync","type":"PARALLEL","branch":"EDITING","skill":"Auto Cut & Audio Sync","capability":"NONE","fallback":"Standard linear cut"},
                    {"id":"s6","label":"Automated Quality Gate","type":"QUALITY_GATE","branch":"QUALITY","skill":"Video Quality Inspection","capability":"NONE","fallback":"Flag NEEDS_REVIEW"}
                  ]
                }
            """.trimIndent()

            val storyCasl = """
                {
                  "caslVersion": "1.0",
                  "workflow": "ai_story_production",
                  "steps": [
                    {"id":"s1","label":"Narrative Script Synthesis","type":"SKILL_CALL","branch":"GENERATIVE","skill":"Story Script Architect","capability":"TEXT_GENERATION","fallback":"Use curated script outline"},
                    {"id":"s2","label":"Key Scene Visual Generation","type":"SKILL_CALL","branch":"GENERATIVE","skill":"Keyframe Visual Generator","capability":"IMAGE_GENERATION","fallback":"Use approved illustrated storyboard"},
                    {"id":"s3","label":"Voiceover Narration Gate","type":"SKILL_CALL","branch":"GENERATIVE","skill":"AI Voice Narrator","capability":"AUDIO_GENERATION","fallback":"Use pre-recorded studio VO"},
                    {"id":"s4","label":"Ken Burns & Subtitle Mix","type":"SKILL_CALL","branch":"EDITING","skill":"Timeline & Subtitle Assembler","capability":"NONE","fallback":"Static frame assembly"},
                    {"id":"s5","label":"Automated Quality Gate","type":"QUALITY_GATE","branch":"QUALITY","skill":"Video Quality Inspection","capability":"NONE","fallback":"Flag NEEDS_REVIEW"}
                  ]
                }
            """.trimIndent()

            batch.set(
                userDoc(uid).collection("workflows").document("wf_gameplay"),
                mapOf(
                    "userId" to uid,
                    "channelId" to "ch_gameplay",
                    "channelName" to "Gameplay Channel",
                    "name" to "Gameplay Highlight Pipeline",
                    "triggerType" to "EVENT",
                    "lifecycleState" to "VALIDATED",
                    "branchMode" to "HYBRID",
                    "caslJson" to gameplayCasl,
                    "currentStepIndex" to 0L,
                    "totalSteps" to 6L,
                    "lastQualityGate" to "PASS",
                    "lastTraceSummary" to "Ready. AIGG enforces Automatic Fallback if VIDEO_GENERATION is invoked.",
                    "version" to "v2.1",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            batch.set(
                userDoc(uid).collection("workflows").document("wf_story"),
                mapOf(
                    "userId" to uid,
                    "channelId" to "ch_aistory",
                    "channelName" to "AI Story Channel",
                    "name" to "AI Story Narrative Pipeline",
                    "triggerType" to "MANUAL",
                    "lifecycleState" to "AWAITING_APPROVAL",
                    "branchMode" to "HYBRID",
                    "caslJson" to storyCasl,
                    "currentStepIndex" to 1L,
                    "totalSteps" to 5L,
                    "lastQualityGate" to "NEEDS_REVIEW",
                    "lastTraceSummary" to "Paused at 'Key Scene Visual Generation' (2/5) awaiting Owner Approval for IMAGE_GENERATION.",
                    "version" to "v1.3",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            // 4. Skills (Levels 0-3)
            val skills = listOf(
                "sk_probe" to mapOf(
                    "userId" to uid, "name" to "Metadata & Rights Probe",
                    "level" to "LEVEL_0_PRIMITIVE", "branch" to "EDITING",
                    "requiredCapability" to "NONE",
                    "fallbackStrategy" to "Reject asset missing license tag",
                    "inputSchemaSummary" to "{ assetUri: String, minRes: String }",
                    "outputSchemaSummary" to "{ width: Int, fps: Int, rightsVerified: Bool }",
                    "version" to "v1.0", "enabled" to true
                ),
                "sk_vision" to mapOf(
                    "userId" to uid, "name" to "Scene & Motion Analyzer",
                    "level" to "LEVEL_1_FUNCTIONAL", "branch" to "ANALYSIS",
                    "requiredCapability" to "AI_ANALYSIS",
                    "fallbackStrategy" to "Audio RMS peak & histogram scene cut",
                    "inputSchemaSummary" to "{ footageId: String, styleProfileId: String }",
                    "outputSchemaSummary" to "{ highlights: List<TimeRange>, confidence: Float }",
                    "version" to "v1.4", "enabled" to true
                ),
                "sk_autocut" to mapOf(
                    "userId" to uid, "name" to "Auto Cut & Audio Sync",
                    "level" to "LEVEL_2_COMPOSITE", "branch" to "EDITING",
                    "requiredCapability" to "NONE",
                    "fallbackStrategy" to "Standard FFmpeg linear splice",
                    "inputSchemaSummary" to "{ highlights: List, audioTrackId: String }",
                    "outputSchemaSummary" to "{ timelineProjectId: String, durationSec: Int }",
                    "version" to "v2.0", "enabled" to true
                ),
                "sk_thumb" to mapOf(
                    "userId" to uid, "name" to "Keyframe Visual Generator",
                    "level" to "LEVEL_1_FUNCTIONAL", "branch" to "GENERATIVE",
                    "requiredCapability" to "IMAGE_GENERATION",
                    "fallbackStrategy" to "Use approved channel storyboard template",
                    "inputSchemaSummary" to "{ prompt: String, aspectRatio: String }",
                    "outputSchemaSummary" to "{ generatedAssetId: String, provenanceId: String }",
                    "version" to "v1.2", "enabled" to true
                ),
                "sk_script" to mapOf(
                    "userId" to uid, "name" to "Narrative Hook Writer",
                    "level" to "LEVEL_1_FUNCTIONAL", "branch" to "GENERATIVE",
                    "requiredCapability" to "TEXT_GENERATION",
                    "fallbackStrategy" to "Use verified static intro script template",
                    "inputSchemaSummary" to "{ channelConstitution: String, sceneSummary: String }",
                    "outputSchemaSummary" to "{ hookScript: String, beatTimestamps: List }",
                    "version" to "v1.5", "enabled" to true
                ),
                "sk_qc" to mapOf(
                    "userId" to uid, "name" to "Final Render & Quality Gate",
                    "level" to "LEVEL_3_PRODUCTION", "branch" to "QUALITY",
                    "requiredCapability" to "NONE",
                    "fallbackStrategy" to "Hold output and mark FAIL in Quality Gate",
                    "inputSchemaSummary" to "{ projectAssetId: String, policySnapshot: Json }",
                    "outputSchemaSummary" to "{ gateStatus: PASS|FAIL, finalAssetId: String }",
                    "version" to "v2.2", "enabled" to true
                )
            )
            for ((docId, fields) in skills) {
                batch.set(
                    userDoc(uid).collection("skills").document(docId),
                    fields + mapOf(
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            // 5. Assets
            val assets = listOf(
                "as_footage_01" to mapOf(
                    "userId" to uid, "channelId" to "ch_gameplay",
                    "title" to "Apex Ranked Clutch Raw.mp4", "assetType" to "VIDEO",
                    "resolution" to "1080p60", "qualityScore" to 96L,
                    "rightsStatus" to "VERIFIED",
                    "provenanceChain" to "Original Capture -> Metadata Probe -> Ready for Scene Analysis",
                    "tags" to listOf("gameplay", "1080p", "verified")
                ),
                "as_ref_01" to mapOf(
                    "userId" to uid, "channelId" to "ch_gameplay",
                    "title" to "Esports Pacing Reference Cut", "assetType" to "REFERENCE",
                    "resolution" to "1080p60", "qualityScore" to 94L,
                    "rightsStatus" to "VERIFIED",
                    "provenanceChain" to "Reference Intake -> Style Extractor -> Style Profile v2",
                    "tags" to listOf("reference", "pacing", "j-cut")
                ),
                "as_thumb_tpl" to mapOf(
                    "userId" to uid, "channelId" to "ch_gameplay",
                    "title" to "Approved High-Contrast Thumb Template", "assetType" to "TEMPLATE",
                    "resolution" to "1280x720", "qualityScore" to 91L,
                    "rightsStatus" to "VERIFIED",
                    "provenanceChain" to "Design Library -> Fallback Registry (IMAGE_GENERATION)",
                    "tags" to listOf("fallback", "template", "thumbnail")
                ),
                "as_bgm_unknown" to mapOf(
                    "userId" to uid, "channelId" to "ch_gameplay",
                    "title" to "Unlicensed Synthwave Track.wav", "assetType" to "AUDIO",
                    "resolution" to "48kHz", "qualityScore" to 88L,
                    "rightsStatus" to "UNKNOWN",
                    "provenanceChain" to "Quarantined by Asset Rights Management (Auto-excluded)",
                    "tags" to listOf("audio", "quarantined")
                ),
                "as_story_frame" to mapOf(
                    "userId" to uid, "channelId" to "ch_aistory",
                    "title" to "Noir Station Keyframe Concept", "assetType" to "GENERATED",
                    "resolution" to "1920x1080", "qualityScore" to 92L,
                    "rightsStatus" to "VERIFIED",
                    "provenanceChain" to "AIGG Gateway -> Gemini Visual -> Provenance #PRV-402",
                    "tags" to listOf("story", "ai-generated", "keyframe")
                )
            )
            for ((docId, fields) in assets) {
                batch.set(
                    userDoc(uid).collection("assets").document(docId),
                    fields + mapOf(
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            // 6. Pending Approval Proposals
            batch.set(
                userDoc(uid).collection("proposals").document("prop_prompt_01"),
                mapOf(
                    "userId" to uid,
                    "channelId" to "ch_gameplay",
                    "targetType" to "SYSTEM_PROMPT",
                    "targetId" to "ch_gameplay",
                    "title" to "Tighten Gameplay Intro Hook Constitution",
                    "currentValue" to "Channel Constitution: Prioritize real captured gameplay footage (1080p+). AI may analyze scenes, draft commentary scripts, and propose thumbnail concepts. Generating synthetic gameplay video is strictly prohibited.",
                    "proposedValue" to "Channel Constitution v2.1: Prioritize real captured gameplay footage (1080p60). Open every video within 2.5 seconds using peak-action footage before context. AI may analyze scenes, draft commentary scripts, and propose thumbnail concepts. Generating synthetic gameplay video remains strictly prohibited.",
                    "reason" to "Production telemetry across 12 runs shows 18% higher retention when peak-action clips lead before context.",
                    "impactScope" to "Gameplay Channel Constitution + Gameplay Highlight Pipeline",
                    "riskLevel" to "LOW",
                    "proposer" to "AI Core Advisor",
                    "status" to "PENDING",
                    "versionTag" to "v2.1",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            batch.set(
                userDoc(uid).collection("proposals").document("prop_wf_story_02"),
                mapOf(
                    "userId" to uid,
                    "channelId" to "ch_aistory",
                    "targetType" to "WORKFLOW_RESUME",
                    "targetId" to "wf_story",
                    "title" to "AIGG Gate: Approve IMAGE_GENERATION for AI Story",
                    "currentValue" to "Workflow paused at Step 2/5: Key Scene Visual Generation (AWAITING_APPROVAL)",
                    "proposedValue" to "Grant AIGG execution approval for Keyframe Visual Generator (gemini-nano-banana-2.1) and resume AI Story Narrative Pipeline.",
                    "reason" to "Strict Approval is active on IMAGE_GENERATION under Global Policy v1.2.",
                    "impactScope" to "AI Story Narrative Pipeline (Step 2/5 -> Completion)",
                    "riskLevel" to "MEDIUM",
                    "proposer" to "Workflow Engine (AIGG)",
                    "status" to "PENDING",
                    "versionTag" to "v1.3",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            // 7. Initial Audit Log
            batch.set(
                userDoc(uid).collection("audit_logs").document("log_seed_01"),
                mapOf(
                    "userId" to uid,
                    "eventType" to "PolicyPublished",
                    "actor" to "Governance Core",
                    "channelId" to "ch_gameplay",
                    "summary" to "Initialized Global Policy v1.2 with strict AIGG gates & multi-channel inheritance.",
                    "severity" to "INFO",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            batch.commit().await()
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateGlobalPolicy(policy: GlobalPolicyDoc): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/global_policy/default"
        return try {
            userDoc(uid).collection("global_policy").document("default").update(
                mapOf(
                    "version" to policy.version.take(20),
                    "minResolution" to policy.minResolution,
                    "requireRightsCheck" to policy.requireRightsCheck,
                    "requireOwnerApprovalForPrompts" to policy.requireOwnerApprovalForPrompts,
                    "textGen" to policy.textGen,
                    "imageGen" to policy.imageGen,
                    "videoGen" to policy.videoGen,
                    "audioGen" to policy.audioGen,
                    "aiAnalysis" to policy.aiAnalysis,
                    "aiEnhancement" to policy.aiEnhancement,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(uid, "PolicyPublished", "Owner", "", "Updated Global Policy ${policy.version} (minRes=${policy.minResolution})", "INFO")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createChannel(
        name: String,
        category: String,
        description: String,
        systemPrompt: String,
        minResolution: String,
        editingPriority: String
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("channels").document()
        return try {
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Channel name cannot be empty" }
            val cleanPrompt = systemPrompt.trim().take(4000)
            require(cleanPrompt.isNotEmpty()) { "Channel Constitution cannot be empty" }

            docRef.set(
                mapOf(
                    "userId" to uid,
                    "name" to cleanName,
                    "category" to category.trim().ifBlank { "Custom" }.take(40),
                    "description" to description.trim().take(500),
                    "systemPrompt" to cleanPrompt,
                    "promptVersion" to "v1.0",
                    "previousSystemPrompt" to "",
                    "minResolution" to minResolution,
                    "editingPriority" to editingPriority,
                    "textGen" to "INHERIT",
                    "imageGen" to "INHERIT",
                    "videoGen" to "INHERIT",
                    "audioGen" to "INHERIT",
                    "aiAnalysis" to "INHERIT",
                    "aiEnhancement" to "INHERIT",
                    "styleVisual" to "Clean studio framing",
                    "styleTempo" to "Balanced narrative cuts",
                    "styleAudio" to "Normalized -14 LUFS",
                    "styleConfidence" to 80L,
                    "productionsCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(uid, "ChannelCreated", "Owner", docRef.id, "Created channel '$cleanName' ($minResolution)", "INFO")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateChannelPoliciesAndStyle(channel: ChannelDoc): Result<Unit> {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("channels").document(channel.id)
        return try {
            require(channel.id.isNotBlank()) { "Channel ID required" }
            docRef.update(
                mapOf(
                    "name" to channel.name.take(80),
                    "category" to channel.category.take(40),
                    "description" to channel.description.take(500),
                    "minResolution" to channel.minResolution,
                    "editingPriority" to channel.editingPriority,
                    "textGen" to channel.textGen,
                    "imageGen" to channel.imageGen,
                    "videoGen" to channel.videoGen,
                    "audioGen" to channel.audioGen,
                    "aiAnalysis" to channel.aiAnalysis,
                    "aiEnhancement" to channel.aiEnhancement,
                    "styleVisual" to channel.styleVisual.take(400),
                    "styleTempo" to channel.styleTempo.take(300),
                    "styleAudio" to channel.styleAudio.take(300),
                    "styleConfidence" to channel.styleConfidence.coerceIn(0L, 100L),
                    "productionsCount" to channel.productionsCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(uid, "ChannelPolicyUpdated", "Owner", channel.id, "Updated AIGG & Style Profile for ${channel.name}", "INFO")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun rollbackChannelConstitution(channel: ChannelDoc): Result<Unit> {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("channels").document(channel.id)
        return try {
            require(channel.previousSystemPrompt.isNotBlank()) { "No previous Constitution version available to rollback." }
            docRef.update(
                mapOf(
                    "systemPrompt" to channel.previousSystemPrompt.take(4000),
                    "previousSystemPrompt" to channel.systemPrompt.take(4000),
                    "promptVersion" to "${channel.promptVersion}-rb",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(uid, "VersionRolledBack", "Owner", channel.id, "Rolled back Constitution for ${channel.name}", "WARNING")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveWorkflowExecutionState(
        workflowId: String,
        channelId: String,
        lifecycleState: WorkflowLifecycle,
        currentStepIndex: Int,
        qualityGate: QualityGateResult,
        traceSummary: String
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("workflows").document(workflowId)
        return try {
            docRef.update(
                mapOf(
                    "lifecycleState" to lifecycleState.name,
                    "currentStepIndex" to currentStepIndex.toLong(),
                    "lastQualityGate" to qualityGate.name,
                    "lastTraceSummary" to traceSummary.take(2000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val severity = when (lifecycleState) {
                WorkflowLifecycle.FAILED -> "CRITICAL"
                WorkflowLifecycle.AWAITING_APPROVAL -> "WARNING"
                else -> "INFO"
            }
            logAudit(uid, "Workflow${lifecycleState.name}", "Workflow Engine", channelId, traceSummary.take(450), severity)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createWorkflow(
        channel: ChannelDoc,
        name: String,
        triggerType: String,
        branchMode: String,
        caslJson: String,
        totalSteps: Int
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("workflows").document()
        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "channelId" to channel.id,
                    "channelName" to channel.name.take(80),
                    "name" to name.trim().take(100),
                    "triggerType" to triggerType,
                    "lifecycleState" to "VALIDATED",
                    "branchMode" to branchMode,
                    "caslJson" to caslJson.take(8000),
                    "currentStepIndex" to 0L,
                    "totalSteps" to totalSteps.coerceIn(1, 50).toLong(),
                    "lastQualityGate" to "NONE",
                    "lastTraceSummary" to "Validated CASL DSL schema. Ready for execution.",
                    "version" to "v1.0",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(uid, "WorkflowCreated", "Owner", channel.id, "Created workflow '${name.trim()}'", "INFO")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleSkillEnabled(skill: SkillDoc): Result<Unit> {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("skills").document(skill.id)
        return try {
            docRef.update(
                mapOf(
                    "enabled" to !skill.enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(uid, "SkillToggled", "Owner", "", "Skill '${skill.name}' set enabled=${!skill.enabled}", "INFO")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun addAsset(
        channelId: String,
        title: String,
        assetType: String,
        resolution: String,
        qualityScore: Int,
        rightsStatus: String,
        provenanceChain: String,
        tags: List<String>
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("assets").document()
        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "channelId" to channelId,
                    "title" to title.trim().take(100),
                    "assetType" to assetType,
                    "resolution" to resolution.take(30),
                    "qualityScore" to qualityScore.coerceIn(0, 100).toLong(),
                    "rightsStatus" to rightsStatus,
                    "provenanceChain" to provenanceChain.take(600),
                    "tags" to tags.take(10),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(uid, "AssetRegistered", "Asset Manager", channelId, "Registered $assetType '${title.trim()}' (Rights: $rightsStatus)", "INFO")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun verifyAssetRights(asset: AssetDoc, newRightsStatus: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("assets").document(asset.id)
        return try {
            docRef.update(
                mapOf(
                    "rightsStatus" to newRightsStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(uid, "AssetValidated", "Owner", asset.channelId, "Updated rights of '${asset.title}' -> $newRightsStatus", "INFO")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun submitProposal(
        channelId: String,
        targetType: String,
        targetId: String,
        title: String,
        currentValue: String,
        proposedValue: String,
        reason: String,
        impactScope: String,
        riskLevel: String,
        proposer: String,
        versionTag: String
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("proposals").document()
        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "channelId" to channelId,
                    "targetType" to targetType,
                    "targetId" to targetId,
                    "title" to title.trim().take(120),
                    "currentValue" to currentValue.take(4000),
                    "proposedValue" to proposedValue.take(4000),
                    "reason" to reason.take(1000),
                    "impactScope" to impactScope.take(400),
                    "riskLevel" to riskLevel,
                    "proposer" to proposer.take(80),
                    "status" to "PENDING",
                    "versionTag" to versionTag.take(20),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(uid, "ApprovalRequested", proposer, channelId, "Submitted proposal '${title.trim()}' ($targetType)", "WARNING")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun resolveProposal(
        proposal: ApprovalProposalDoc,
        approve: Boolean,
        channels: List<ChannelDoc>,
        workflows: List<WorkflowDoc>,
        globalPolicy: GlobalPolicyDoc?,
        assets: List<AssetDoc>
    ): Result<Unit> {
        val uid = requireUserId()
        val propRef = userDoc(uid).collection("proposals").document(proposal.id)
        return try {
            require(proposal.status == "PENDING") { "Proposal already resolved" }
            val newStatus = if (approve) "APPROVED" else "REJECTED"

            propRef.update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (approve) {
                when (proposal.targetType) {
                    "SYSTEM_PROMPT" -> {
                        val ch = channels.firstOrNull { it.id == proposal.targetId }
                        if (ch != null) {
                            userDoc(uid).collection("channels").document(ch.id).update(
                                mapOf(
                                    "previousSystemPrompt" to ch.systemPrompt.take(4000),
                                    "systemPrompt" to proposal.proposedValue.take(4000),
                                    "promptVersion" to proposal.versionTag.take(20),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                    }

                    "WORKFLOW_RESUME" -> {
                        val wf = workflows.firstOrNull { it.id == proposal.targetId }
                        val ch = channels.firstOrNull { it.id == proposal.channelId }
                        if (wf != null && ch != null && globalPolicy != null) {
                            val sim = com.example.model.CaslParser.simulate(
                                workflow = wf,
                                channel = ch,
                                globalPolicy = globalPolicy,
                                assets = assets,
                                approvalGrantedForResume = true
                            )
                            userDoc(uid).collection("workflows").document(wf.id).update(
                                mapOf(
                                    "lifecycleState" to sim.finalLifecycle.name,
                                    "currentStepIndex" to sim.blockedStepIndex.toLong(),
                                    "lastQualityGate" to sim.qualityGate.name,
                                    "lastTraceSummary" to "Resumed after Owner Approval -> ${sim.summaryText}".take(2000),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                    }
                }
            }

            logAudit(
                uid = uid,
                eventType = if (approve) "ApprovalGranted" else "ApprovalRejected",
                actor = "Owner",
                channelId = proposal.channelId,
                summary = "${if (approve) "Approved & published" else "Rejected"} '${proposal.title}' (${proposal.versionTag})",
                severity = if (approve) "INFO" else "WARNING"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, propRef.path)
            Result.failure(e)
        }
    }

    suspend fun logAudit(
        uid: String,
        eventType: String,
        actor: String,
        channelId: String,
        summary: String,
        severity: String = "INFO"
    ) {
        try {
            userDoc(uid).collection("audit_logs").document().set(
                mapOf(
                    "userId" to uid,
                    "eventType" to eventType.take(60),
                    "actor" to actor.take(80),
                    "channelId" to channelId.take(128),
                    "summary" to summary.take(500),
                    "severity" to severity,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$uid/audit_logs")
        }
    }
}
