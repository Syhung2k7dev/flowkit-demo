package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.model.ApprovalProposalDoc
import com.example.model.AssetDoc
import com.example.model.ChannelDoc
import com.example.model.GlobalPolicyDoc
import com.example.model.UiState
import com.example.model.WorkflowDoc
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBanner
import com.example.ui.components.LoadingCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioCard
import com.example.ui.theme.AmberGovernance
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldPass
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.RoseDeny
import com.example.ui.theme.VioletGenerative

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApprovalsScreen(
    proposalsState: UiState<List<ApprovalProposalDoc>>,
    channels: List<ChannelDoc>,
    workflows: List<WorkflowDoc>,
    globalPolicy: GlobalPolicyDoc?,
    assets: List<AssetDoc>,
    onResolveProposal: (ApprovalProposalDoc, Boolean, List<ChannelDoc>, List<WorkflowDoc>, GlobalPolicyDoc?, List<AssetDoc>) -> Unit
) {
    var statusFilter by remember { mutableStateOf("PENDING") }

    val allProposals = (proposalsState as? UiState.Success)?.data.orEmpty()
    val filtered = remember(allProposals, statusFilter) {
        if (statusFilter == "ALL") allProposals else allProposals.filter { it.status == statusFilter }
    }

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
                    title = "Approval Center",
                    subtitle = "Centralized Git/PR-style Owner Review for Constitutions, AIGG Gates & Workflow Resumes"
                )
            }

            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("PENDING", "APPROVED", "REJECTED", "ALL").forEach { filter ->
                        FilterChip(
                            selected = statusFilter == filter,
                            onClick = { statusFilter = filter },
                            label = {
                                val count = if (filter == "ALL") allProposals.size else allProposals.count { it.status == filter }
                                Text("$filter ($count)")
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberGovernance.copy(alpha = 0.2f),
                                selectedLabelColor = AmberGovernance
                            )
                        )
                    }
                }
            }

            when (proposalsState) {
                is UiState.Loading -> item { LoadingCard("Loading Approval Queue...") }
                is UiState.Error -> item { ErrorBanner(proposalsState.message) }
                is UiState.Success -> {
                    if (filtered.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No $statusFilter Proposals",
                                subtitle = "Governance requests from AI Core Advisor or paused AIGG Workflow gates appear here."
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { proposal ->
                            ProposalCard(
                                proposal = proposal,
                                onApprove = {
                                    onResolveProposal(proposal, true, channels, workflows, globalPolicy, assets)
                                },
                                onReject = {
                                    onResolveProposal(proposal, false, channels, workflows, globalPolicy, assets)
                                }
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
private fun ProposalCard(
    proposal: ApprovalProposalDoc,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val statusColor = when (proposal.status) {
        "APPROVED" -> EmeraldPass
        "REJECTED" -> RoseDeny
        else -> AmberGovernance
    }
    val riskColor = when (proposal.riskLevel) {
        "HIGH" -> RoseDeny
        "MEDIUM" -> AmberGovernance
        else -> EmeraldPass
    }

    StudioCard(borderColor = statusColor.copy(alpha = 0.45f)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(proposal.targetType, VioletGenerative)
                    StatusPill(proposal.status, statusColor)
                    StatusPill("${proposal.riskLevel} RISK", riskColor)
                }
                Text(
                    proposal.versionTag,
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanPulse
                )
            }

            Text(proposal.title, style = MaterialTheme.typography.titleLarge)
            Text(
                "Proposer: ${proposal.proposer} · Scope: ${proposal.impactScope}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Diff View (Current vs Proposed)
            Surface(
                color = ObsidianSurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("- CURRENT", style = MaterialTheme.typography.labelSmall, color = RoseDeny)
                    Text(
                        proposal.currentValue,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("+ PROPOSED", style = MaterialTheme.typography.labelSmall, color = EmeraldPass)
                    Text(
                        proposal.proposedValue,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Text(
                "Evidence / Reason: ${proposal.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (proposal.status == "PENDING") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseDeny),
                        border = BorderStroke(1.dp, RoseDeny.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reject_proposal_${proposal.id}")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Reject")
                    }
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPass,
                            contentColor = Color(0xFF042218)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("approve_proposal_${proposal.id}")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Approve & Publish")
                    }
                }
            }
        }
    }
}
