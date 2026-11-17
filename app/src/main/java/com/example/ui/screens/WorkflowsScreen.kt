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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
import com.example.model.AssetDoc
import com.example.model.CaslParser
import com.example.model.ChannelDoc
import com.example.model.GlobalPolicyDoc
import com.example.model.PolicyResolver
import com.example.model.SimulationResult
import com.example.model.UiState
import com.example.model.WorkflowDoc
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
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.RoseDeny
import com.example.ui.theme.VioletGenerative

@Composable
fun WorkflowsScreen(
    workflowsState: UiState<List<WorkflowDoc>>,
    channels: List<ChannelDoc>,
    globalPolicy: GlobalPolicyDoc?,
    assets: List<AssetDoc>,
    selectedWorkflowId: String?,
    simulationResult: SimulationResult?,
    onSelectWorkflow: (String?) -> Unit,
    onRunSimulation: (WorkflowDoc, ChannelDoc, GlobalPolicyDoc, List<AssetDoc>) -> Unit,
    onCreateWorkflow: (ChannelDoc, String, String, String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    val workflows = (workflowsState as? UiState.Success)?.data.orEmpty()
    val selectedWorkflow = workflows.firstOrNull { it.id == selectedWorkflowId }

    if (selectedWorkflow != null) {
        BackHandler { onSelectWorkflow(null) }
        val channel = channels.firstOrNull { it.id == selectedWorkflow.channelId } ?: channels.firstOrNull()
        WorkflowStudioDetail(
            workflow = selectedWorkflow,
            channel = channel,
            globalPolicy = globalPolicy ?: GlobalPolicyDoc(),
            assets = assets,
            simulationResult = simulationResult,
            onBack = { onSelectWorkflow(null) },
            onRunSimulation = onRunSimulation
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
                        title = "Workflow Engine & CASL",
                        subtitle = "Hybrid Editing + Generative pipelines governed by AIGG & Quality Gates",
                        trailing = {
                            if (channels.isNotEmpty()) {
                                Button(
                                    onClick = { showCreateDialog = true },
                                    modifier = Modifier.testTag("create_workflow_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                    Text("New Workflow")
                                }
                            }
                        }
                    )
                }

                when (workflowsState) {
                    is UiState.Loading -> item { LoadingCard("Loading Workflows...") }
                    is UiState.Error -> item { ErrorBanner(workflowsState.message) }
                    is UiState.Success -> {
                        if (workflows.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "No Workflows Configured",
                                    subtitle = "Create a workflow to orchestrate CASL steps and Hybrid Production."
                                )
                            }
                        } else {
                            items(workflows, key = { it.id }) { wf ->
                                WorkflowCard(
                                    workflow = wf,
                                    onClick = { onSelectWorkflow(wf.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog && channels.isNotEmpty()) {
        CreateWorkflowDialog(
            channels = channels,
            onDismiss = { showCreateDialog = false },
            onConfirm = { ch, name, trigger, branch ->
                onCreateWorkflow(ch, name, trigger, branch)
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkflowCard(
    workflow: WorkflowDoc,
    onClick: () -> Unit
) {
    val stateColor = when (workflow.lifecycleState) {
        "COMPLETED" -> EmeraldPass
        "AWAITING_APPROVAL" -> AmberGovernance
        "FAILED" -> RoseDeny
        "RUNNING" -> CyanPulse
        else -> VioletGenerative
    }

    StudioCard(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("workflow_card_${workflow.id}")
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
                    Text(workflow.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${workflow.channelName} · ${workflow.version} · Trigger: ${workflow.triggerType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(workflow.lifecycleState, stateColor)
            }

            Text(
                text = workflow.lastTraceSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPill(workflow.branchMode, branchColor(workflow.branchMode))
                StatusPill("Steps: ${workflow.currentStepIndex}/${workflow.totalSteps}", CyanPulse)
                StatusPill("QC: ${workflow.lastQualityGate}", if (workflow.lastQualityGate == "PASS") EmeraldPass else AmberGovernance)
            }
        }
    }
}

@Composable
private fun WorkflowStudioDetail(
    workflow: WorkflowDoc,
    channel: ChannelDoc?,
    globalPolicy: GlobalPolicyDoc,
    assets: List<AssetDoc>,
    simulationResult: SimulationResult?,
    onBack: () -> Unit,
    onRunSimulation: (WorkflowDoc, ChannelDoc, GlobalPolicyDoc, List<AssetDoc>) -> Unit
) {
    var showRawCasl by remember { mutableStateOf(false) }
    val steps = remember(workflow.caslJson) { CaslParser.parseSteps(workflow.caslJson) }

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
                        modifier = Modifier.testTag("workflow_detail_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Workflows")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(workflow.name, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "${workflow.channelName} · ${workflow.lifecycleState} · ${workflow.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanPulse
                        )
                    }
                }
            }

            item {
                StudioCard(borderColor = CyanPulse.copy(alpha = 0.45f)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Hybrid Production Execution", style = MaterialTheme.typography.titleMedium)
                        Text(
                            workflow.lastTraceSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    if (channel != null) {
                                        onRunSimulation(workflow, channel, globalPolicy, assets)
                                    }
                                },
                                enabled = channel != null,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanPulse,
                                    contentColor = Color(0xFF04222B)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("run_casl_simulation_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                Text("Run CASL Pipeline")
                            }
                            OutlinedButton(
                                onClick = { showRawCasl = !showRawCasl },
                                modifier = Modifier.testTag("toggle_casl_dsl_button")
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                Text(if (showRawCasl) "Hide DSL" else "JSON DSL")
                            }
                        }
                    }
                }
            }

            if (showRawCasl) {
                item {
                    StudioCard(borderColor = VioletGenerative.copy(alpha = 0.4f)) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("CASL JSON DSL", style = MaterialTheme.typography.titleSmall, color = VioletGenerative)
                            Text(
                                text = workflow.caslJson,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (simulationResult != null) {
                item {
                    SectionHeader(
                        title = "Live Execution Trace",
                        subtitle = simulationResult.summaryText
                    )
                }
                items(simulationResult.traces, key = { "${it.stepId}_${it.stepIndex}" }) { trace ->
                    val outcomeColor = when (trace.outcome) {
                        "EXECUTED", "PASS" -> EmeraldPass
                        "FALLBACK_EXECUTED" -> CyanPulse
                        "AWAITING_APPROVAL", "PASS_WITH_WARNINGS" -> AmberGovernance
                        else -> RoseDeny
                    }
                    StudioCard(borderColor = outcomeColor.copy(alpha = 0.5f)) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${trace.stepIndex + 1}. ${trace.label}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                StatusPill(trace.outcome, outcomeColor)
                            }
                            Text(
                                trace.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "CASL Step Graph & AIGG Gate Preview",
                    subtitle = "Shows Editing vs Generative Branch and resolved Channel/Global permissions"
                )
            }

            items(steps, key = { it.stepId }) { step ->
                val resolved = if (channel != null) {
                    PolicyResolver.resolve(globalPolicy, channel, step.capability)
                } else null
                Surface(
                    color = ObsidianSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(step.label, style = MaterialTheme.typography.titleMedium)
                            StatusPill(step.branch, branchColor(step.branch))
                        }
                        Text(
                            "Node: ${step.nodeType} · Skill: ${step.skillName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Capability: ${step.capability.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanPulse
                            )
                            if (resolved != null) {
                                StatusPill(resolved.effectiveDecision.name, decisionColor(resolved.effectiveDecision))
                            }
                        }
                        if (step.fallbackLabel.isNotBlank()) {
                            Text(
                                "Automatic Fallback: ${step.fallbackLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AmberGovernance
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateWorkflowDialog(
    channels: List<ChannelDoc>,
    onDismiss: () -> Unit,
    onConfirm: (ChannelDoc, String, String, String) -> Unit
) {
    var selectedChannel by remember { mutableStateOf(channels.first()) }
    var name by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("MANUAL") }
    var branchMode by remember { mutableStateOf("HYBRID") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New CASL Workflow") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Workflow Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Target Channel", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    channels.forEach { ch ->
                        FilterChip(
                            selected = selectedChannel.id == ch.id,
                            onClick = { selectedChannel = ch },
                            label = { Text(ch.name) }
                        )
                    }
                }
                Text("Trigger Type", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("MANUAL", "SCHEDULED", "EVENT").forEach { trig ->
                        FilterChip(
                            selected = trigger == trig,
                            onClick = { trigger = trig },
                            label = { Text(trig) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedChannel, name, trigger, branchMode) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
