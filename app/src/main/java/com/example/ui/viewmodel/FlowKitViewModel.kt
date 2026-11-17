package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AiCoreRepository
import com.example.data.ConstitutionSuggestion
import com.example.data.ExtractedStyleProfile
import com.example.data.FlowKitRepository
import com.example.model.AiCapability
import com.example.model.ApprovalProposalDoc
import com.example.model.AssetDoc
import com.example.model.AuditLogDoc
import com.example.model.CaslParser
import com.example.model.ChannelDoc
import com.example.model.GlobalPolicyDoc
import com.example.model.PolicyDecision
import com.example.model.PolicyResolver
import com.example.model.SimulationResult
import com.example.model.SkillDoc
import com.example.model.UiState
import com.example.model.WorkflowDoc
import com.example.model.WorkflowLifecycle
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "FlowKitVM"
private const val STOP_TIMEOUT_MS = 5000L

class FlowKitViewModel(
    private val repository: FlowKitRepository,
    private val aiCore: AiCoreRepository,
    private val currentUserId: String
) : ViewModel() {

    val globalPolicyState: StateFlow<UiState<GlobalPolicyDoc?>> =
        observeAsUiState(repository.observeGlobalPolicy(currentUserId), "Failed to observe Global Policy")

    val channelsState: StateFlow<UiState<List<ChannelDoc>>> =
        observeAsUiState(repository.observeChannels(currentUserId), "Failed to observe Channels")

    val workflowsState: StateFlow<UiState<List<WorkflowDoc>>> =
        observeAsUiState(repository.observeWorkflows(currentUserId), "Failed to observe Workflows")

    val skillsState: StateFlow<UiState<List<SkillDoc>>> =
        observeAsUiState(repository.observeSkills(currentUserId), "Failed to observe Skills")

    val assetsState: StateFlow<UiState<List<AssetDoc>>> =
        observeAsUiState(repository.observeAssets(currentUserId), "Failed to observe Assets")

    val proposalsState: StateFlow<UiState<List<ApprovalProposalDoc>>> =
        observeAsUiState(repository.observeProposals(currentUserId), "Failed to observe Approval Center")

    val auditLogsState: StateFlow<UiState<List<AuditLogDoc>>> =
        observeAsUiState(repository.observeAuditLogs(currentUserId), "Failed to observe Audit Logs")

    private val _selectedChannelId = MutableStateFlow<String?>(null)
    val selectedChannelId: StateFlow<String?> = _selectedChannelId.asStateFlow()

    private val _selectedWorkflowId = MutableStateFlow<String?>(null)
    val selectedWorkflowId: StateFlow<String?> = _selectedWorkflowId.asStateFlow()

    private val _simulationResult = MutableStateFlow<SimulationResult?>(null)
    val simulationResult: StateFlow<SimulationResult?> = _simulationResult.asStateFlow()

    private val _aiAdvisorState = MutableStateFlow<UiState<ConstitutionSuggestion>?>(null)
    val aiAdvisorState: StateFlow<UiState<ConstitutionSuggestion>?> = _aiAdvisorState.asStateFlow()

    private val _styleExtractionState = MutableStateFlow<UiState<ExtractedStyleProfile>?>(null)
    val styleExtractionState: StateFlow<UiState<ExtractedStyleProfile>?> = _styleExtractionState.asStateFlow()

    private val _hookGenerationState = MutableStateFlow<UiState<String>?>(null)
    val hookGenerationState: StateFlow<UiState<String>?> = _hookGenerationState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedWorkspaceIfEmpty(currentUserId)
                .onFailure { err ->
                    Log.w(TAG, "Initial seed check failed: ${err.message}")
                }
        }
    }

    private fun <T> observeAsUiState(
        flow: Flow<T>,
        fallbackMessage: String
    ): StateFlow<UiState<T>> = flow
        .map<T, UiState<T>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, fallbackMessage, rootError)
            emit(UiState.Error(rootError.message ?: fallbackMessage))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun selectChannel(channelId: String?) {
        _selectedChannelId.value = channelId
        _aiAdvisorState.value = null
        _hookGenerationState.value = null
    }

    fun selectWorkflow(workflowId: String?) {
        _selectedWorkflowId.value = workflowId
        _simulationResult.value = null
    }

    fun updateGlobalPolicy(policy: GlobalPolicyDoc) {
        viewModelScope.launch {
            repository.updateGlobalPolicy(policy)
                .onSuccess { _snackbarMessage.value = "Global Policy ${policy.version} published." }
                .onFailure { _snackbarMessage.value = it.message ?: "Failed to update Global Policy." }
        }
    }

    fun createChannel(
        name: String,
        category: String,
        description: String,
        systemPrompt: String,
        minResolution: String,
        editingPriority: String
    ) {
        viewModelScope.launch {
            repository.createChannel(name, category, description, systemPrompt, minResolution, editingPriority)
                .onSuccess { _snackbarMessage.value = "Channel '$name' created." }
                .onFailure { _snackbarMessage.value = it.message ?: "Failed to create channel." }
        }
    }

    fun updateChannelPolicy(channel: ChannelDoc) {
        viewModelScope.launch {
            repository.updateChannelPoliciesAndStyle(channel)
                .onSuccess { _snackbarMessage.value = "Saved policy & style for ${channel.name}." }
                .onFailure { _snackbarMessage.value = it.message ?: "Failed to update channel." }
        }
    }

    fun rollbackChannelConstitution(channel: ChannelDoc) {
        viewModelScope.launch {
            repository.rollbackChannelConstitution(channel)
                .onSuccess { _snackbarMessage.value = "Constitution rolled back for ${channel.name}." }
                .onFailure { _snackbarMessage.value = it.message ?: "Rollback failed." }
        }
    }

    fun proposeChannelPromptChange(channel: ChannelDoc, newPrompt: String, reason: String, versionTag: String) {
        viewModelScope.launch {
            repository.submitProposal(
                channelId = channel.id,
                targetType = "SYSTEM_PROMPT",
                targetId = channel.id,
                title = "Constitution Update: ${channel.name}",
                currentValue = channel.systemPrompt,
                proposedValue = newPrompt,
                reason = reason.ifBlank { "Operator requested Constitution revision." },
                impactScope = "${channel.name} + dependent workflows",
                riskLevel = "LOW",
                proposer = "Owner / Studio",
                versionTag = versionTag
            ).onSuccess {
                _snackbarMessage.value = "Submitted to Approval Center (Draft -> Review -> Approval)."
            }.onFailure {
                _snackbarMessage.value = it.message ?: "Failed to submit proposal."
            }
        }
    }

    fun runAiConstitutionAdvisor(channel: ChannelDoc, goal: String) {
        viewModelScope.launch {
            _aiAdvisorState.value = UiState.Loading
            aiCore.proposeConstitutionImprovement(channel, goal)
                .onSuccess { suggestion ->
                    _aiAdvisorState.value = UiState.Success(suggestion)
                }
                .onFailure { err ->
                    _aiAdvisorState.value = UiState.Error(err.message ?: "AI Core Advisor failed.")
                }
        }
    }

    fun submitAiSuggestionToApprovalCenter(channel: ChannelDoc, suggestion: ConstitutionSuggestion) {
        viewModelScope.launch {
            repository.submitProposal(
                channelId = channel.id,
                targetType = "SYSTEM_PROMPT",
                targetId = channel.id,
                title = "AI Proposal: ${channel.name} Constitution",
                currentValue = channel.systemPrompt,
                proposedValue = suggestion.proposedPrompt,
                reason = suggestion.reason,
                impactScope = suggestion.impactScope,
                riskLevel = suggestion.riskLevel,
                proposer = "AI Core Advisor (Gemini)",
                versionTag = suggestion.versionTag
            ).onSuccess {
                _aiAdvisorState.value = null
                _snackbarMessage.value = "AI proposal queued in Approval Center. Owner approval required to publish."
            }.onFailure {
                _snackbarMessage.value = it.message ?: "Failed to queue proposal."
            }
        }
    }

    /**
     * Executes AIGG-governed TEXT_GENERATION. Enforces PolicyResolver before touching AI Core.
     */
    fun runGovernedHookGeneration(channel: ChannelDoc, globalPolicy: GlobalPolicyDoc, topic: String) {
        viewModelScope.launch {
            val resolved = PolicyResolver.resolve(globalPolicy, channel, AiCapability.TEXT_GENERATION)
            when (resolved.effectiveDecision) {
                PolicyDecision.DENY -> {
                    _hookGenerationState.value = UiState.Success(
                        "[AIGG DENY -> AUTOMATIC FALLBACK]\n" +
                            "Policy Source: ${resolved.source}\n" +
                            "Fallback Executed: ${AiCapability.TEXT_GENERATION.defaultFallback}\n\n" +
                            "Template Hook for '$topic':\n" +
                            "00:00 - Peak footage cold open (verified asset)\n" +
                            "00:03 - Title card overlay\n" +
                            "00:08 - Main narrative sequence"
                    )
                    repository.logAudit(
                        uid = currentUserId,
                        eventType = "GenerationDenied",
                        actor = "AIGG Gateway",
                        channelId = channel.id,
                        summary = "Blocked TEXT_GENERATION on ${channel.name} (${resolved.source}); executed fallback template.",
                        severity = "WARNING"
                    )
                }

                PolicyDecision.REQUIRE_APPROVAL -> {
                    _hookGenerationState.value = UiState.Error(
                        "AIGG Strict Gate (${resolved.source}): TEXT_GENERATION requires Owner Approval before invoking AI Core."
                    )
                }

                PolicyDecision.ALLOW, PolicyDecision.INHERIT -> {
                    _hookGenerationState.value = UiState.Loading
                    aiCore.generateProductionHook(channel, topic)
                        .onSuccess { hook ->
                            _hookGenerationState.value = UiState.Success(hook)
                            repository.logAudit(
                                uid = currentUserId,
                                eventType = "GenerationExecuted",
                                actor = "AI Core (gemini-3.8-flash)",
                                channelId = channel.id,
                                summary = "Generated governed production hook for '${topic.take(60)}'.",
                                severity = "INFO"
                            )
                        }
                        .onFailure { err ->
                            _hookGenerationState.value = UiState.Error(err.message ?: "AI generation failed.")
                        }
                }
            }
        }
    }

    /**
     * Runs CASL Simulation through PolicyResolver & AIGG Gateway, saving durable Workflow State in Firestore.
     */
    fun runWorkflowSimulation(
        workflow: WorkflowDoc,
        channel: ChannelDoc,
        globalPolicy: GlobalPolicyDoc,
        assets: List<AssetDoc>
    ) {
        viewModelScope.launch {
            val sim = CaslParser.simulate(
                workflow = workflow,
                channel = channel,
                globalPolicy = globalPolicy,
                assets = assets,
                approvalGrantedForResume = false
            )
            _simulationResult.value = sim

            repository.saveWorkflowExecutionState(
                workflowId = workflow.id,
                channelId = channel.id,
                lifecycleState = sim.finalLifecycle,
                currentStepIndex = sim.blockedStepIndex,
                qualityGate = sim.qualityGate,
                traceSummary = sim.summaryText
            )

            if (sim.finalLifecycle == WorkflowLifecycle.AWAITING_APPROVAL && sim.blockedCapability != null) {
                repository.submitProposal(
                    channelId = channel.id,
                    targetType = "WORKFLOW_RESUME",
                    targetId = workflow.id,
                    title = "AIGG Gate: Approve ${sim.blockedCapability.name} for ${workflow.name}",
                    currentValue = "Paused at '${sim.blockedStepName}' (${sim.blockedStepIndex + 1}/${workflow.totalSteps})",
                    proposedValue = "Grant strict AIGG execution approval for '${sim.blockedStepName}' and resume ${workflow.name}.",
                    reason = "Channel/Global AIGG policy requires strict approval for ${sim.blockedCapability.label}.",
                    impactScope = "${workflow.name} (${channel.name})",
                    riskLevel = "MEDIUM",
                    proposer = "Workflow Engine (CASL)",
                    versionTag = workflow.version
                )
                _snackbarMessage.value = "Workflow paused at AIGG gate -> Queued in Approval Center."
            } else {
                _snackbarMessage.value = "Simulation finished: ${sim.finalLifecycle.label}."
            }
        }
    }

    fun createWorkflow(
        channel: ChannelDoc,
        name: String,
        triggerType: String,
        branchMode: String
    ) {
        viewModelScope.launch {
            val defaultCasl = """
                {
                  "caslVersion": "1.0",
                  "workflow": "${name.lowercase().replace(" ", "_")}",
                  "steps": [
                    {"id":"s1","label":"Metadata & Rights Probe","type":"SKILL_CALL","branch":"EDITING","skill":"Metadata & Rights Probe","capability":"NONE","fallback":"Reject invalid asset"},
                    {"id":"s2","label":"AI Scene & Hook Analysis","type":"SKILL_CALL","branch":"ANALYSIS","skill":"Scene & Motion Analyzer","capability":"AI_ANALYSIS","fallback":"Audio peak detection"},
                    {"id":"s3","label":"Generative Visual Thumbnail","type":"IF_ELSE","branch":"GENERATIVE","skill":"Keyframe Visual Generator","capability":"IMAGE_GENERATION","fallback":"Use approved channel template"},
                    {"id":"s4","label":"Automated Quality Gate","type":"QUALITY_GATE","branch":"QUALITY","skill":"Final Render & Quality Gate","capability":"NONE","fallback":"Flag NEEDS_REVIEW"}
                  ]
                }
            """.trimIndent()
            repository.createWorkflow(channel, name, triggerType, branchMode, defaultCasl, 4)
                .onSuccess { _snackbarMessage.value = "Workflow '$name' created." }
                .onFailure { _snackbarMessage.value = it.message ?: "Failed to create workflow." }
        }
    }

    fun toggleSkill(skill: SkillDoc) {
        viewModelScope.launch {
            repository.toggleSkillEnabled(skill)
                .onFailure { _snackbarMessage.value = it.message ?: "Failed to toggle skill." }
        }
    }

    fun addAsset(
        channelId: String,
        title: String,
        assetType: String,
        resolution: String,
        qualityScore: Int,
        rightsStatus: String
    ) {
        viewModelScope.launch {
            repository.addAsset(
                channelId = channelId,
                title = title,
                assetType = assetType,
                resolution = resolution,
                qualityScore = qualityScore,
                rightsStatus = rightsStatus,
                provenanceChain = "Manual Intake -> Asset Registry -> Quality Score $qualityScore",
                tags = listOf(assetType.lowercase(), resolution.lowercase())
            ).onSuccess {
                _snackbarMessage.value = "Asset '$title' registered."
            }.onFailure {
                _snackbarMessage.value = it.message ?: "Failed to register asset."
            }
        }
    }

    fun verifyAssetRights(asset: AssetDoc) {
        viewModelScope.launch {
            repository.verifyAssetRights(asset, "VERIFIED")
                .onSuccess { _snackbarMessage.value = "Rights verified for '${asset.title}'." }
                .onFailure { _snackbarMessage.value = it.message ?: "Rights verification failed." }
        }
    }

    fun analyzeReferenceNotes(channel: ChannelDoc, referenceNotes: String) {
        viewModelScope.launch {
            _styleExtractionState.value = UiState.Loading
            aiCore.extractStyleProfile(channel.name, referenceNotes)
                .onSuccess { profile ->
                    _styleExtractionState.value = UiState.Success(profile)
                }
                .onFailure { err ->
                    _styleExtractionState.value = UiState.Error(err.message ?: "Reference analysis failed.")
                }
        }
    }

    fun applyExtractedStyle(channel: ChannelDoc, profile: ExtractedStyleProfile) {
        viewModelScope.launch {
            val updated = channel.copy(
                styleVisual = profile.visualProfile,
                styleTempo = profile.tempoProfile,
                styleAudio = profile.audioProfile,
                styleConfidence = profile.confidenceScore.toLong()
            )
            repository.updateChannelPoliciesAndStyle(updated)
                .onSuccess {
                    _styleExtractionState.value = null
                    _snackbarMessage.value = "Style Profile updated for ${channel.name}."
                }
                .onFailure {
                    _snackbarMessage.value = it.message ?: "Failed to save Style Profile."
                }
        }
    }

    fun resolveProposal(
        proposal: ApprovalProposalDoc,
        approve: Boolean,
        channels: List<ChannelDoc>,
        workflows: List<WorkflowDoc>,
        globalPolicy: GlobalPolicyDoc?,
        assets: List<AssetDoc>
    ) {
        viewModelScope.launch {
            repository.resolveProposal(proposal, approve, channels, workflows, globalPolicy, assets)
                .onSuccess {
                    _snackbarMessage.value = if (approve) {
                        "Approved & published: ${proposal.title}"
                    } else {
                        "Rejected: ${proposal.title}"
                    }
                }
                .onFailure {
                    _snackbarMessage.value = it.message ?: "Failed to resolve proposal."
                }
        }
    }
}
