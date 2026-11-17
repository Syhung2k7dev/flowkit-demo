package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.AiCoreRepository
import com.example.data.FlowKitRepository
import com.example.model.UiState
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutFlowKit
import com.example.ui.screens.ApprovalsScreen
import com.example.ui.screens.ChannelsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.WorkflowsScreen
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.viewmodel.FlowKitViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class FlowKitDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    OVERVIEW("overview", R.string.nav_overview, Icons.Default.Dashboard),
    CHANNELS("channels", R.string.nav_channels, Icons.Default.Tv),
    WORKFLOWS("workflows", R.string.nav_workflows, Icons.Default.AccountTree),
    STUDIO("studio", R.string.nav_studio, Icons.Default.AutoAwesomeMosaic),
    APPROVALS("approvals", R.string.nav_approvals, Icons.Default.Approval)
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            FlowKitControlDeck(
                currentUserId = user.uid,
                userEmail = user.email.orEmpty()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowKitControlDeck(
    currentUserId: String,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: FlowKitViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                FlowKitViewModel(
                    repository = FlowKitRepository(db),
                    aiCore = AiCoreRepository(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val globalPolicyState by viewModel.globalPolicyState.collectAsStateWithLifecycle()
    val channelsState by viewModel.channelsState.collectAsStateWithLifecycle()
    val workflowsState by viewModel.workflowsState.collectAsStateWithLifecycle()
    val skillsState by viewModel.skillsState.collectAsStateWithLifecycle()
    val assetsState by viewModel.assetsState.collectAsStateWithLifecycle()
    val proposalsState by viewModel.proposalsState.collectAsStateWithLifecycle()
    val auditLogsState by viewModel.auditLogsState.collectAsStateWithLifecycle()

    val selectedChannelId by viewModel.selectedChannelId.collectAsStateWithLifecycle()
    val selectedWorkflowId by viewModel.selectedWorkflowId.collectAsStateWithLifecycle()
    val simulationResult by viewModel.simulationResult.collectAsStateWithLifecycle()
    val aiAdvisorState by viewModel.aiAdvisorState.collectAsStateWithLifecycle()
    val styleExtractionState by viewModel.styleExtractionState.collectAsStateWithLifecycle()
    val hookGenerationState by viewModel.hookGenerationState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    var currentDestination by remember { mutableStateOf(FlowKitDestination.OVERVIEW) }
    val globalPolicy = (globalPolicyState as? UiState.Success)?.data
    val channels = (channelsState as? UiState.Success)?.data.orEmpty()
    val workflows = (workflowsState as? UiState.Success)?.data.orEmpty()
    val assets = (assetsState as? UiState.Success)?.data.orEmpty()
    val proposals = (proposalsState as? UiState.Success)?.data.orEmpty()
    val pendingApprovals = proposals.count { it.status == "PENDING" }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 700.dp

        Scaffold(
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "FLOWKIT",
                                style = MaterialTheme.typography.titleLarge,
                                color = CyanPulse
                            )
                            if (userEmail.isNotBlank()) {
                                Text(
                                    text = userEmail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                signOutFlowKit(credentialManager, scope)
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = stringResource(R.string.sign_out)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ObsidianSurface
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = ObsidianSurface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        FlowKitDestination.entries.forEach { dest ->
                            val label = stringResource(dest.labelRes)
                            NavigationBarItem(
                                selected = currentDestination == dest,
                                onClick = { currentDestination = dest },
                                icon = {
                                    DestinationIcon(
                                        dest = dest,
                                        pendingApprovals = pendingApprovals,
                                        label = label
                                    )
                                },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = ObsidianSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        FlowKitDestination.entries.forEach { dest ->
                            val label = stringResource(dest.labelRes)
                            NavigationRailItem(
                                selected = currentDestination == dest,
                                onClick = { currentDestination = dest },
                                icon = {
                                    DestinationIcon(
                                        dest = dest,
                                        pendingApprovals = pendingApprovals,
                                        label = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (currentDestination) {
                        FlowKitDestination.OVERVIEW -> DashboardScreen(
                            globalPolicyState = globalPolicyState,
                            channelsState = channelsState,
                            workflowsState = workflowsState,
                            proposalsState = proposalsState,
                            auditLogsState = auditLogsState,
                            onUpdateGlobalPolicy = viewModel::updateGlobalPolicy,
                            onNavigateToApprovals = { currentDestination = FlowKitDestination.APPROVALS },
                            onNavigateToWorkflows = { currentDestination = FlowKitDestination.WORKFLOWS },
                            onNavigateToChannels = { currentDestination = FlowKitDestination.CHANNELS }
                        )

                        FlowKitDestination.CHANNELS -> ChannelsScreen(
                            channelsState = channelsState,
                            globalPolicy = globalPolicy,
                            selectedChannelId = selectedChannelId,
                            aiAdvisorState = aiAdvisorState,
                            hookGenerationState = hookGenerationState,
                            onSelectChannel = viewModel::selectChannel,
                            onCreateChannel = viewModel::createChannel,
                            onUpdateChannelPolicy = viewModel::updateChannelPolicy,
                            onProposeConstitutionChange = viewModel::proposeChannelPromptChange,
                            onRollbackConstitution = viewModel::rollbackChannelConstitution,
                            onRunAiAdvisor = viewModel::runAiConstitutionAdvisor,
                            onQueueAiSuggestion = viewModel::submitAiSuggestionToApprovalCenter,
                            onRunGovernedHook = viewModel::runGovernedHookGeneration
                        )

                        FlowKitDestination.WORKFLOWS -> WorkflowsScreen(
                            workflowsState = workflowsState,
                            channels = channels,
                            globalPolicy = globalPolicy,
                            assets = assets,
                            selectedWorkflowId = selectedWorkflowId,
                            simulationResult = simulationResult,
                            onSelectWorkflow = viewModel::selectWorkflow,
                            onRunSimulation = viewModel::runWorkflowSimulation,
                            onCreateWorkflow = viewModel::createWorkflow
                        )

                        FlowKitDestination.STUDIO -> StudioScreen(
                            skillsState = skillsState,
                            assetsState = assetsState,
                            channels = channels,
                            styleExtractionState = styleExtractionState,
                            onToggleSkill = viewModel::toggleSkill,
                            onAddAsset = viewModel::addAsset,
                            onVerifyAssetRights = viewModel::verifyAssetRights,
                            onAnalyzeReference = viewModel::analyzeReferenceNotes,
                            onApplyExtractedStyle = viewModel::applyExtractedStyle
                        )

                        FlowKitDestination.APPROVALS -> ApprovalsScreen(
                            proposalsState = proposalsState,
                            channels = channels,
                            workflows = workflows,
                            globalPolicy = globalPolicy,
                            assets = assets,
                            onResolveProposal = viewModel::resolveProposal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationIcon(
    dest: FlowKitDestination,
    pendingApprovals: Int,
    label: String
) {
    if (dest == FlowKitDestination.APPROVALS && pendingApprovals > 0) {
        BadgedBox(
            badge = {
                Badge { Text("$pendingApprovals") }
            }
        ) {
            Icon(imageVector = dest.icon, contentDescription = label)
        }
    } else {
        Icon(imageVector = dest.icon, contentDescription = label)
    }
}
