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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.data.ExtractedStyleProfile
import com.example.model.AssetDoc
import com.example.model.ChannelDoc
import com.example.model.SkillDoc
import com.example.model.SkillLevel
import com.example.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBanner
import com.example.ui.components.LoadingCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioCard
import com.example.ui.components.branchColor
import com.example.ui.theme.AmberGovernance
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldPass
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.RoseDeny
import com.example.ui.theme.VioletGenerative

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    skillsState: UiState<List<SkillDoc>>,
    assetsState: UiState<List<AssetDoc>>,
    channels: List<ChannelDoc>,
    styleExtractionState: UiState<ExtractedStyleProfile>?,
    onToggleSkill: (SkillDoc) -> Unit,
    onAddAsset: (String, String, String, String, Int, String) -> Unit,
    onVerifyAssetRights: (AssetDoc) -> Unit,
    onAnalyzeReference: (ChannelDoc, String) -> Unit,
    onApplyExtractedStyle: (ChannelDoc, ExtractedStyleProfile) -> Unit
) {
    var activeSegment by remember { mutableStateOf(0) } // 0 = Skills, 1 = Assets & Ref Intelligence
    var showAddAssetDialog by remember { mutableStateOf(false) }
    var referenceNotes by remember {
        mutableStateOf("Fast 2s action cuts on beat drops, vibrant cyan HUD overlays, J-cut audio transitions with BGM ducked -14dB under crisp callouts.")
    }
    var targetChannel by remember(channels) { mutableStateOf(channels.firstOrNull()) }

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
                    title = "Skills & Asset Intelligence",
                    subtitle = "Encapsulated L0-L3 Skills, Asset Provenance, Rights Verification & Style Extraction"
                )
            }

            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = activeSegment == 0,
                        onClick = { activeSegment = 0 },
                        label = { Text("Skill Engine (L0–L3)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPulse.copy(alpha = 0.2f),
                            selectedLabelColor = CyanPulse
                        ),
                        modifier = Modifier.testTag("studio_tab_skills")
                    )
                    FilterChip(
                        selected = activeSegment == 1,
                        onClick = { activeSegment = 1 },
                        label = { Text("Assets & Reference Intelligence") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VioletGenerative.copy(alpha = 0.2f),
                            selectedLabelColor = VioletGenerative
                        ),
                        modifier = Modifier.testTag("studio_tab_assets")
                    )
                }
            }

            if (activeSegment == 0) {
                when (skillsState) {
                    is UiState.Loading -> item { LoadingCard("Loading Skill Registry...") }
                    is UiState.Error -> item { ErrorBanner(skillsState.message) }
                    is UiState.Success -> {
                        val skills = skillsState.data
                        if (skills.isEmpty()) {
                            item { EmptyStateCard("No Skills", "Skills appear here once registered.") }
                        } else {
                            items(skills, key = { it.id }) { skill ->
                                val lvl = SkillLevel.fromString(skill.level)
                                StudioCard {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                StatusPill(lvl.badge, CyanPulse)
                                                Column {
                                                    Text(skill.name, style = MaterialTheme.typography.titleMedium)
                                                    Text(
                                                        "${lvl.title} · ${skill.version}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Switch(
                                                checked = skill.enabled,
                                                onCheckedChange = { onToggleSkill(skill) },
                                                modifier = Modifier.testTag("toggle_skill_${skill.id}")
                                            )
                                        }
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            StatusPill(skill.branch, branchColor(skill.branch))
                                            StatusPill(
                                                if (skill.requiredCapability == "NONE") "Media Engine" else skill.requiredCapability,
                                                if (skill.requiredCapability == "NONE") EmeraldPass else VioletGenerative
                                            )
                                        }
                                        Text(
                                            "In: ${skill.inputSchemaSummary}\nOut: ${skill.outputSchemaSummary}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "Fallback: ${skill.fallbackStrategy}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AmberGovernance
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Reference-to-Editing Intelligence Card
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
                                Text("Reference-to-Editing Intelligence", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                "Analyze reference video notes with Gemini to synthesize a structured Style Profile for a Channel.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (channels.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    channels.forEach { ch ->
                                        FilterChip(
                                            selected = targetChannel?.id == ch.id,
                                            onClick = { targetChannel = ch },
                                            label = { Text(ch.name) }
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = referenceNotes,
                                onValueChange = { referenceNotes = it },
                                label = { Text("Reference Video / Timeline Notes") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    targetChannel?.let { ch -> onAnalyzeReference(ch, referenceNotes) }
                                },
                                enabled = targetChannel != null && styleExtractionState !is UiState.Loading && referenceNotes.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VioletGenerative,
                                    contentColor = Color(0xFF1E1042)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("extract_style_button")
                            ) {
                                if (styleExtractionState is UiState.Loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Extract Structured Style Profile")
                                }
                            }

                            when (styleExtractionState) {
                                is UiState.Error -> ErrorBanner(styleExtractionState.message)
                                is UiState.Success -> {
                                    val p = styleExtractionState.data
                                    Surface(
                                        color = ObsidianSurfaceElevated,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, ObsidianBorder)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            StatusPill("Confidence ${p.confidenceScore}%", EmeraldPass)
                                            Text("Visual: ${p.visualProfile}", style = MaterialTheme.typography.bodySmall)
                                            Text("Tempo: ${p.tempoProfile}", style = MaterialTheme.typography.bodySmall)
                                            Text("Audio: ${p.audioProfile}", style = MaterialTheme.typography.bodySmall)
                                            Button(
                                                onClick = {
                                                    targetChannel?.let { ch -> onApplyExtractedStyle(ch, p) }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("apply_style_profile_button")
                                            ) {
                                                Text("Apply Style Profile to ${targetChannel?.name ?: "Channel"}")
                                            }
                                        }
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Asset & Provenance Registry",
                        subtitle = "Unknown rights assets are automatically flagged and excluded by Quality Gates",
                        trailing = {
                            if (channels.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { showAddAssetDialog = true },
                                    modifier = Modifier.testTag("add_asset_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                    Text("Register")
                                }
                            }
                        }
                    )
                }

                when (assetsState) {
                    is UiState.Loading -> item { LoadingCard("Loading Assets...") }
                    is UiState.Error -> item { ErrorBanner(assetsState.message) }
                    is UiState.Success -> {
                        val assets = assetsState.data
                        items(assets, key = { it.id }) { asset ->
                            val rightsColor = when (asset.rightsStatus) {
                                "VERIFIED" -> EmeraldPass
                                "UNKNOWN" -> RoseDeny
                                else -> AmberGovernance
                            }
                            StudioCard(borderColor = if (asset.rightsStatus == "UNKNOWN") RoseDeny.copy(alpha = 0.5f) else ObsidianBorder) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(asset.title, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${asset.assetType} · ${asset.resolution} · Quality ${asset.qualityScore}/100",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        StatusPill(asset.rightsStatus, rightsColor)
                                    }
                                    Text(
                                        "Provenance: ${asset.provenanceChain}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CyanPulse
                                    )
                                    if (asset.rightsStatus == "UNKNOWN") {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = RoseDeny, modifier = Modifier.size(16.dp))
                                                Text("Quarantined (Unknown Rights)", style = MaterialTheme.typography.labelSmall, color = RoseDeny)
                                            }
                                            TextButton(
                                                onClick = { onVerifyAssetRights(asset) },
                                                modifier = Modifier.testTag("verify_rights_${asset.id}")
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Text(" Verify License")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddAssetDialog && channels.isNotEmpty()) {
        AddAssetDialog(
            channels = channels,
            onDismiss = { showAddAssetDialog = false },
            onConfirm = { chId, title, type, res, score, rights ->
                onAddAsset(chId, title, type, res, score, rights)
                showAddAssetDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddAssetDialog(
    channels: List<ChannelDoc>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Int, String) -> Unit
) {
    var selectedChannel by remember { mutableStateOf(channels.first()) }
    var title by remember { mutableStateOf("") }
    var assetType by remember { mutableStateOf("VIDEO") }
    var resolution by remember { mutableStateOf("1080p") }
    var rightsStatus by remember { mutableStateOf("VERIFIED") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register Asset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Asset Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Type", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("VIDEO", "IMAGE", "AUDIO", "REFERENCE", "TEMPLATE").forEach { t ->
                        FilterChip(selected = assetType == t, onClick = { assetType = t }, label = { Text(t) })
                    }
                }
                Text("Rights Status", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("VERIFIED", "UNKNOWN", "RESTRICTED").forEach { r ->
                        FilterChip(selected = rightsStatus == r, onClick = { rightsStatus = r }, label = { Text(r) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedChannel.id, title, assetType, resolution, 92, rightsStatus) },
                enabled = title.isNotBlank()
            ) {
                Text("Register")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
