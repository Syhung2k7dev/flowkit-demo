package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ConstitutionSuggestion
import com.example.model.AiCapability
import com.example.model.ChannelDoc
import com.example.model.GlobalPolicyDoc
import com.example.model.PolicyDecision
import com.example.model.PolicyResolver
import com.example.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBanner
import com.example.ui.components.LoadingCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioCard
import com.example.ui.components.branchColor
import com.example.ui.components.decisionColor
import com.example.ui.theme.AmberGovernance
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldPass
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.VioletGenerative

@Composable
fun ChannelsScreen(
    channelsState: UiState<List<ChannelDoc>>,
    globalPolicy: GlobalPolicyDoc?,
    selectedChannelId: String?,
    aiAdvisorState: UiState<ConstitutionSuggestion>?,
    hookGenerationState: UiState<String>?,
    onSelectChannel: (String?) -> Unit,
    onCreateChannel: (String, String, String, String, String, String) -> Unit,
    onUpdateChannelPolicy: (ChannelDoc) -> Unit,
    onProposeConstitutionChange: (ChannelDoc, String, String, String) -> Unit,
    onRollbackConstitution: (ChannelDoc) -> Unit,
    onRunAiAdvisor: (ChannelDoc, String) -> Unit,
    onQueueAiSuggestion: (ChannelDoc, ConstitutionSuggestion) -> Unit,
    onRunGovernedHook: (ChannelDoc, GlobalPolicyDoc, String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    val channels = (channelsState as? UiState.Success)?.data.orEmpty()
    val selectedChannel = channels.firstOrNull { it.id == selectedChannelId }

    if (selectedChannel != null) {
        BackHandler { onSelectChannel(null) }
        ChannelDetailView(
            channel = selectedChannel,
            globalPolicy = globalPolicy ?: GlobalPolicyDoc(),
            aiAdvisorState = aiAdvisorState,
            hookGenerationState = hookGenerationState,
            onBack = { onSelectChannel(null) },
            onUpdateChannelPolicy = onUpdateChannelPolicy,
            onProposeConstitutionChange = onProposeConstitutionChange,
            onRollbackConstitution = onRollbackConstitution,
            onRunAiAdvisor = onRunAiAdvisor,
            onQueueAiSuggestion = onQueueAiSuggestion,
            onRunGovernedHook = onRunGovernedHook
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    SectionHeader(
                        title = "Channel Manager",
                        subtitle = "Independent production lines with isolated Constitutions, Policies & Style Profiles",
                        trailing = {
                            Button(
                                onClick = { showCreateDialog = true },
                                modifier = Modifier.testTag("create_channel_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                Text("New Channel")
                            }
                        }
                    )
                }

                when (channelsState) {
                    is UiState.Loading -> item { LoadingCard("Loading Channels...") }
                    is UiState.Error -> item { ErrorBanner(channelsState.message) }
                    is UiState.Success -> {
                        if (channels.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "No Channels Yet",
                                    subtitle = "Create a channel with its own Constitution and AIGG policy."
                                )
                            }
                        } else {
                            items(channels, key = { it.id }) { channel ->
                                ChannelListCard(
                                    channel = channel,
                                    globalPolicy = globalPolicy ?: GlobalPolicyDoc(),
                                    onClick = { onSelectChannel(channel.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateChannelDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, cat, desc, prompt, res, priority ->
                onCreateChannel(name, cat, desc, prompt, res, priority)
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelListCard(
    channel: ChannelDoc,
    globalPolicy: GlobalPolicyDoc,
    onClick: () -> Unit
) {
    val videoPolicy = PolicyResolver.resolve(globalPolicy, channel, AiCapability.VIDEO_GENERATION)
    val imagePolicy = PolicyResolver.resolve(globalPolicy, channel, AiCapability.IMAGE_GENERATION)
    val textPolicy = PolicyResolver.resolve(globalPolicy, channel, AiCapability.TEXT_GENERATION)

    StudioCard(
        borderColor = ObsidianBorder,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("channel_card_${channel.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(channel.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${channel.category} · Constitution ${channel.promptVersion} · ${channel.productionsCount} runs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(channel.minResolution, EmeraldPass)
            }

            Text(
                text = channel.description.ifBlank { channel.systemPrompt },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPill(channel.editingPriority, branchColor(channel.editingPriority))
                StatusPill("Text: ${textPolicy.effectiveDecision.name}", decisionColor(textPolicy.effectiveDecision))
                StatusPill("Image: ${imagePolicy.effectiveDecision.name}", decisionColor(imagePolicy.effectiveDecision))
                StatusPill("Video: ${videoPolicy.effectiveDecision.name}", decisionColor(videoPolicy.effectiveDecision))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelDetailView(
    channel: ChannelDoc,
    globalPolicy: GlobalPolicyDoc,
    aiAdvisorState: UiState<ConstitutionSuggestion>?,
    hookGenerationState: UiState<String>?,
    onBack: () -> Unit,
    onUpdateChannelPolicy: (ChannelDoc) -> Unit,
    onProposeConstitutionChange: (ChannelDoc, String, String, String) -> Unit,
    onRollbackConstitution: (ChannelDoc) -> Unit,
    onRunAiAdvisor: (ChannelDoc, String) -> Unit,
    onQueueAiSuggestion: (ChannelDoc, ConstitutionSuggestion) -> Unit,
    onRunGovernedHook: (ChannelDoc, GlobalPolicyDoc, String) -> Unit
) {
    var activeTab by remember { mutableStateOf(0) }
    var draftPrompt by remember(channel.id, channel.systemPrompt) { mutableStateOf(channel.systemPrompt) }
    var changeReason by remember { mutableStateOf("") }
    var advisorGoal by remember { mutableStateOf("Improve opening hook retention while preserving strict asset rights.") }
    var hookTopic by remember { mutableStateOf("Highlight reel of a comeback victory") }
    var policyDraft by remember(channel) { mutableStateOf(channel) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("channel_detail_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Channels")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(channel.name, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Constitution ${channel.promptVersion} · Style Confidence ${channel.styleConfidence}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanPulse
                        )
                    }
                    StatusPill(channel.minResolution, EmeraldPass)
                }
            }

            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Constitution & AI", "AIGG Permissions", "Style & AIGG Test").forEachIndexed { idx, label ->
                        FilterChip(
                            selected = activeTab == idx,
                            onClick = { activeTab = idx },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanPulse.copy(alpha = 0.2f),
                                selectedLabelColor = CyanPulse
                            )
                        )
                    }
                }
            }

            when (activeTab) {
                0 -> {
                    item {
                        StudioCard(borderColor = CyanPulse.copy(alpha = 0.4f)) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Channel System Prompt (Constitution)", style = MaterialTheme.typography.titleMedium)
                                    if (channel.previousSystemPrompt.isNotBlank()) {
                                        TextButton(
                                            onClick = { onRollbackConstitution(channel) },
                                            modifier = Modifier.testTag("rollback_constitution_button")
                                        ) {
                                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Text(" Rollback", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = draftPrompt,
                                    onValueChange = { draftPrompt = it },
                                    label = { Text("Draft Constitution") },
                                    minLines = 4,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = changeReason,
                                    onValueChange = { changeReason = it },
                                    label = { Text("Proposal Reason / Evidence") },
                                    placeholder = { Text("Why does this channel need this prompt update?") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    onClick = {
                                        onProposeConstitutionChange(
                                            channel,
                                            draftPrompt,
                                            changeReason,
                                            "${channel.promptVersion}-rev"
                                        )
                                    },
                                    enabled = draftPrompt.isNotBlank() && draftPrompt != channel.systemPrompt,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("propose_constitution_button")
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                    Text("Submit Proposal for Owner Approval")
                                }
                            }
                        }
                    }

                    item {
                        StudioCard(borderColor = VioletGenerative.copy(alpha = 0.45f)) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = VioletGenerative)
                                    Text("AI Core Constitution Advisor", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    "Gemini analyzes this channel's Constitution and drafts a Change Proposal. AI cannot self-publish.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = advisorGoal,
                                    onValueChange = { advisorGoal = it },
                                    label = { Text("Optimization Goal") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = { onRunAiAdvisor(channel, advisorGoal) },
                                    enabled = aiAdvisorState !is UiState.Loading && advisorGoal.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = VioletGenerative,
                                        contentColor = Color(0xFF1E1042)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("run_ai_advisor_button")
                                ) {
                                    if (aiAdvisorState is UiState.Loading) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("Analyze & Propose via Gemini")
                                    }
                                }

                                when (aiAdvisorState) {
                                    is UiState.Error -> ErrorBanner(aiAdvisorState.message)
                                    is UiState.Success -> {
                                        val s = aiAdvisorState.data
                                        Surface(
                                            color = ObsidianSurfaceElevated,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, VioletGenerative.copy(alpha = 0.5f))
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    StatusPill("Draft ${s.versionTag}", VioletGenerative)
                                                    StatusPill("Risk: ${s.riskLevel}", AmberGovernance)
                                                }
                                                Text(s.proposedPrompt, style = MaterialTheme.typography.bodyMedium)
                                                Text("Reason: ${s.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("Impact: ${s.impactScope}", style = MaterialTheme.typography.bodySmall, color = CyanPulse)
                                                Button(
                                                    onClick = { onQueueAiSuggestion(channel, s) },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("queue_ai_proposal_button")
                                                ) {
                                                    Text("Queue in Approval Center")
                                                }
                                            }
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }
                }

                1 -> {
                    item {
                        StudioCard {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Granular AIGG Permission Matrix", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Global DENY or Strict Approval cannot be relaxed by Channel Policy.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AmberGovernance
                                )

                                ChannelCapEditor(
                                    capability = AiCapability.TEXT_GENERATION,
                                    rawValue = policyDraft.textGen,
                                    globalPolicy = globalPolicy,
                                    channel = policyDraft,
                                    onSelect = { policyDraft = policyDraft.copy(textGen = it) }
                                )
                                ChannelCapEditor(
                                    capability = AiCapability.IMAGE_GENERATION,
                                    rawValue = policyDraft.imageGen,
                                    globalPolicy = globalPolicy,
                                    channel = policyDraft,
                                    onSelect = { policyDraft = policyDraft.copy(imageGen = it) }
                                )
                                ChannelCapEditor(
                                    capability = AiCapability.VIDEO_GENERATION,
                                    rawValue = policyDraft.videoGen,
                                    globalPolicy = globalPolicy,
                                    channel = policyDraft,
                                    onSelect = { policyDraft = policyDraft.copy(videoGen = it) }
                                )
                                ChannelCapEditor(
                                    capability = AiCapability.AUDIO_GENERATION,
                                    rawValue = policyDraft.audioGen,
                                    globalPolicy = globalPolicy,
                                    channel = policyDraft,
                                    onSelect = { policyDraft = policyDraft.copy(audioGen = it) }
                                )
                                ChannelCapEditor(
                                    capability = AiCapability.AI_ANALYSIS,
                                    rawValue = policyDraft.aiAnalysis,
                                    globalPolicy = globalPolicy,
                                    channel = policyDraft,
                                    onSelect = { policyDraft = policyDraft.copy(aiAnalysis = it) }
                                )

                                Button(
                                    onClick = { onUpdateChannelPolicy(policyDraft) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("save_channel_policy_button")
                                ) {
                                    Text("Save Channel Policy Matrix")
                                }
                            }
                        }
                    }
                }

                2 -> {
                    item {
                        StudioCard {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Channel Style Profile", style = MaterialTheme.typography.titleMedium)
                                Text("Visual: ${channel.styleVisual}", style = MaterialTheme.typography.bodyMedium)
                                Text("Tempo: ${channel.styleTempo}", style = MaterialTheme.typography.bodyMedium)
                                Text("Audio: ${channel.styleAudio}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    item {
                        StudioCard(borderColor = CyanPulse.copy(alpha = 0.4f)) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Test AIGG Execution Gateway (TEXT_GENERATION)", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Demonstrates live PolicyResolver check before invoking Gemini AI Core, or Automatic Fallback if denied.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = hookTopic,
                                    onValueChange = { hookTopic = it },
                                    label = { Text("Production Topic") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = { onRunGovernedHook(channel, globalPolicy, hookTopic) },
                                    enabled = hookGenerationState !is UiState.Loading && hookTopic.isNotBlank(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("test_aigg_hook_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                    Text("Execute via AIGG Gateway")
                                }

                                when (hookGenerationState) {
                                    is UiState.Loading -> LoadingCard("AI Execution Gateway checking policy & invoking Gemini...")
                                    is UiState.Error -> ErrorBanner(hookGenerationState.message)
                                    is UiState.Success -> {
                                        Surface(
                                            color = ObsidianSurfaceElevated,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, CyanPulse.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = hookGenerationState.data,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(14.dp)
                                            )
                                        }
                                    }
                                    null -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelCapEditor(
    capability: AiCapability,
    rawValue: String,
    globalPolicy: GlobalPolicyDoc,
    channel: ChannelDoc,
    onSelect: (String) -> Unit
) {
    val resolved = PolicyResolver.resolve(globalPolicy, channel, capability)
    Surface(
        color = ObsidianSurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(capability.label, style = MaterialTheme.typography.titleSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (resolved.overriddenAttempt) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked by Global Policy",
                            tint = AmberGovernance,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    StatusPill("Effective: ${resolved.effectiveDecision.name}", decisionColor(resolved.effectiveDecision))
                }
            }
            Text(
                text = "Resolved via ${resolved.source}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PolicyDecision.entries.forEach { opt ->
                    FilterChip(
                        selected = rawValue == opt.name,
                        onClick = { onSelect(opt.name) },
                        label = { Text(opt.name, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = decisionColor(opt).copy(alpha = 0.2f),
                            selectedLabelColor = decisionColor(opt)
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateChannelDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Documentary") }
    var description by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var minRes by remember { mutableStateOf("1080p") }
    var priority by remember { mutableStateOf("HYBRID_BALANCED") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Channel Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("Channel Constitution (System Prompt)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("720p", "1080p", "4K").forEach { res ->
                        FilterChip(
                            selected = minRes == res,
                            onClick = { minRes = res },
                            label = { Text(res) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, category, description, systemPrompt, minRes, priority) },
                enabled = name.isNotBlank() && systemPrompt.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
