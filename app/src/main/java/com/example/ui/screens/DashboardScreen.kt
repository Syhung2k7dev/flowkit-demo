package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AiCapability
import com.example.model.ApprovalProposalDoc
import com.example.model.AuditLogDoc
import com.example.model.ChannelDoc
import com.example.model.GlobalPolicyDoc
import com.example.model.PolicyDecision
import com.example.model.UiState
import com.example.model.WorkflowDoc
import com.example.ui.components.ErrorBanner
import com.example.ui.components.LoadingCard
import com.example.ui.components.MetricTile
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioCard
import com.example.ui.components.decisionColor
import com.example.ui.theme.AmberGovernance
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldPass
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.RoseDeny
import com.example.ui.theme.VioletGenerative

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    globalPolicyState: UiState<GlobalPolicyDoc?>,
    channelsState: UiState<List<ChannelDoc>>,
    workflowsState: UiState<List<WorkflowDoc>>,
    proposalsState: UiState<List<ApprovalProposalDoc>>,
    auditLogsState: UiState<List<AuditLogDoc>>,
    onUpdateGlobalPolicy: (GlobalPolicyDoc) -> Unit,
    onNavigateToApprovals: () -> Unit,
    onNavigateToWorkflows: () -> Unit,
    onNavigateToChannels: () -> Unit
) {
    val channels = (channelsState as? UiState.Success)?.data.orEmpty()
    val workflows = (workflowsState as? UiState.Success)?.data.orEmpty()
    val proposals = (proposalsState as? UiState.Success)?.data.orEmpty()
    val auditLogs = (auditLogsState as? UiState.Success)?.data.orEmpty()
    val pendingCount = proposals.count { it.status == "PENDING" }
    val awaitingWfCount = workflows.count { it.lifecycleState == "AWAITING_APPROVAL" }

    var editingGlobalPolicy by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Studio Control Deck Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_studio_hero_1794889830777),
                        contentDescription = "FlowKit orchestration deck",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        ObsidianBg.copy(alpha = 0.94f),
                                        ObsidianBg.copy(alpha = 0.55f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill("HYBRID PRODUCTION", CyanPulse)
                            StatusPill("AIGG STRICT", AmberGovernance)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "FlowKit Control Deck",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Multi-Channel Governance · CASL Runtime · AI Core",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricTile(
                        label = "Channels",
                        value = "${channels.size}",
                        icon = Icons.Default.Tv,
                        accent = CyanPulse,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onNavigateToChannels)
                    )
                    MetricTile(
                        label = "Workflows",
                        value = "${workflows.size}",
                        icon = Icons.Default.AccountTree,
                        accent = VioletGenerative,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onNavigateToWorkflows)
                    )
                    MetricTile(
                        label = "Pending Gates",
                        value = "$pendingCount",
                        icon = Icons.Default.Approval,
                        accent = if (pendingCount > 0) AmberGovernance else EmeraldPass,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onNavigateToApprovals)
                    )
                }
            }

            if (pendingCount > 0 || awaitingWfCount > 0) {
                item {
                    Surface(
                        color = AmberGovernance.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, AmberGovernance.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "$pendingCount Pending Governance Decision(s)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = AmberGovernance
                                )
                                Text(
                                    text = "$awaitingWfCount workflow(s) paused at AWAITING_APPROVAL.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = onNavigateToApprovals,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AmberGovernance,
                                    contentColor = Color(0xFF281700)
                                ),
                                modifier = Modifier.testTag("overview_open_approvals_button")
                            ) {
                                Text("Review")
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Global Policy Root",
                    subtitle = "Top-level invariants inherited by all channels (DENY cannot be overridden)",
                    trailing = {
                        if (globalPolicyState is UiState.Success && globalPolicyState.data != null) {
                            OutlinedButton(
                                onClick = { editingGlobalPolicy = !editingGlobalPolicy },
                                modifier = Modifier.testTag("toggle_global_policy_editor")
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                Text(if (editingGlobalPolicy) "Close" else "Configure")
                            }
                        }
                    }
                )
            }

            item {
                when (globalPolicyState) {
                    is UiState.Loading -> LoadingCard("Loading Global Policy...")
                    is UiState.Error -> ErrorBanner(globalPolicyState.message)
                    is UiState.Success -> {
                        val policy = globalPolicyState.data
                        if (policy == null) {
                            LoadingCard("Provisioning Global Policy v1.2...")
                        } else {
                            GlobalPolicyCard(
                                policy = policy,
                                expanded = editingGlobalPolicy,
                                onSave = { updated ->
                                    onUpdateGlobalPolicy(updated)
                                    editingGlobalPolicy = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Observability & Audit Stream",
                    subtitle = "Traceability across Governance Core, AIGG Gateway, CASL & Assets"
                )
            }

            when (auditLogsState) {
                is UiState.Loading -> item { LoadingCard("Loading audit logs...") }
                is UiState.Error -> item { ErrorBanner(auditLogsState.message) }
                is UiState.Success -> {
                    val logs = auditLogsState.data.take(6)
                    items(logs, key = { it.id }) { log ->
                        val tint = when (log.severity) {
                            "CRITICAL" -> RoseDeny
                            "WARNING" -> AmberGovernance
                            else -> CyanPulse
                        }
                        StudioCard(borderColor = ObsidianBorder) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusPill(log.eventType, tint)
                                    Text(
                                        text = log.actor,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = log.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
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
private fun GlobalPolicyCard(
    policy: GlobalPolicyDoc,
    expanded: Boolean,
    onSave: (GlobalPolicyDoc) -> Unit
) {
    var draft by remember(policy, expanded) { mutableStateOf(policy) }

    StudioCard(borderColor = CyanPulse.copy(alpha = 0.35f)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = CyanPulse)
                    Text("Global Policy ${policy.version}", style = MaterialTheme.typography.titleMedium)
                }
                StatusPill("Min ${policy.minResolution}", EmeraldPass)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    AiCapability.TEXT_GENERATION,
                    AiCapability.IMAGE_GENERATION,
                    AiCapability.VIDEO_GENERATION,
                    AiCapability.AUDIO_GENERATION,
                    AiCapability.AI_ANALYSIS,
                    AiCapability.AI_ENHANCEMENT
                ).forEach { cap ->
                    val dec = policy.decisionFor(cap)
                    Surface(
                        color = ObsidianSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ObsidianBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cap.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = dec.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = decisionColor(dec)
                            )
                        }
                    }
                }
            }

            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Configure Global AIGG Defaults",
                        style = MaterialTheme.typography.titleSmall,
                        color = CyanPulse
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Strict Rights Check (Assets)", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = draft.requireRightsCheck,
                            onCheckedChange = { draft = draft.copy(requireRightsCheck = it) }
                        )
                    }

                    Text("Minimum Resolution Floor", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("720p", "1080p", "4K").forEach { res ->
                            FilterChip(
                                selected = draft.minResolution == res,
                                onClick = { draft = draft.copy(minResolution = res) },
                                label = { Text(res) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanPulse.copy(alpha = 0.2f),
                                    selectedLabelColor = CyanPulse
                                )
                            )
                        }
                    }

                    GlobalCapSelector("VIDEO_GENERATION", draft.videoGen) {
                        draft = draft.copy(videoGen = it)
                    }
                    GlobalCapSelector("IMAGE_GENERATION", draft.imageGen) {
                        draft = draft.copy(imageGen = it)
                    }
                    GlobalCapSelector("TEXT_GENERATION", draft.textGen) {
                        draft = draft.copy(textGen = it)
                    }
                    GlobalCapSelector("AUDIO_GENERATION", draft.audioGen) {
                        draft = draft.copy(audioGen = it)
                    }

                    Button(
                        onClick = { onSave(draft) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_global_policy_button")
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("Publish Global Policy")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GlobalCapSelector(
    label: String,
    current: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(PolicyDecision.ALLOW, PolicyDecision.REQUIRE_APPROVAL, PolicyDecision.DENY).forEach { opt ->
                FilterChip(
                    selected = current == opt.name,
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
