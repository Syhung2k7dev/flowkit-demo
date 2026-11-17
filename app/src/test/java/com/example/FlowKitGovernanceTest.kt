package com.example

import com.example.model.AiCapability
import com.example.model.AssetDoc
import com.example.model.CaslParser
import com.example.model.ChannelDoc
import com.example.model.GlobalPolicyDoc
import com.example.model.PolicyDecision
import com.example.model.PolicyResolver
import com.example.model.QualityGateResult
import com.example.model.WorkflowDoc
import com.example.model.WorkflowLifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FlowKitGovernanceTest {

    @Test
    fun globalDenyCannotBeOverriddenByChannelAllow() {
        val global = GlobalPolicyDoc(videoGen = "DENY")
        val channel = ChannelDoc(id = "ch1", name = "Test", videoGen = "ALLOW")
        val resolved = PolicyResolver.resolve(global, channel, AiCapability.VIDEO_GENERATION)
        assertEquals(PolicyDecision.DENY, resolved.effectiveDecision)
        assertTrue(resolved.overriddenAttempt)
    }

    @Test
    fun globalStrictApprovalCannotBeRelaxedToAllow() {
        val global = GlobalPolicyDoc(imageGen = "REQUIRE_APPROVAL")
        val channel = ChannelDoc(id = "ch1", name = "Test", imageGen = "ALLOW")
        val resolved = PolicyResolver.resolve(global, channel, AiCapability.IMAGE_GENERATION)
        assertEquals(PolicyDecision.REQUIRE_APPROVAL, resolved.effectiveDecision)
        assertTrue(resolved.overriddenAttempt)
    }

    @Test
    fun caslSimulationExecutesAutomaticFallbackWhenVideoGenDenied() {
        val global = GlobalPolicyDoc(videoGen = "DENY", textGen = "ALLOW", aiAnalysis = "ALLOW")
        val channel = ChannelDoc(id = "ch_gp", name = "Gameplay", videoGen = "DENY", textGen = "ALLOW", aiAnalysis = "ALLOW")
        val casl = """
            {
              "steps": [
                {"id":"s1","label":"Analyze","type":"SKILL_CALL","branch":"ANALYSIS","skill":"Vision","capability":"AI_ANALYSIS","fallback":"Histogram"},
                {"id":"s2","label":"GenClip","type":"IF_ELSE","branch":"GENERATIVE","skill":"GenVideo","capability":"VIDEO_GENERATION","fallback":"Library 1080p clip"},
                {"id":"s3","label":"QC","type":"QUALITY_GATE","branch":"QUALITY","skill":"QC","capability":"NONE","fallback":""}
              ]
            }
        """.trimIndent()
        val wf = WorkflowDoc(id = "wf1", channelId = "ch_gp", caslJson = casl, totalSteps = 3)
        val result = CaslParser.simulate(wf, channel, global, emptyList())
        assertEquals(WorkflowLifecycle.COMPLETED, result.finalLifecycle)
        assertEquals(QualityGateResult.PASS, result.qualityGate)
        assertEquals("FALLBACK_EXECUTED", result.traces[1].outcome)
    }

    @Test
    fun caslSimulationPausesAtStrictApprovalGateAndResumesWhenGranted() {
        val global = GlobalPolicyDoc(imageGen = "REQUIRE_APPROVAL")
        val channel = ChannelDoc(id = "ch_story", name = "Story", imageGen = "INHERIT")
        val casl = """
            {
              "steps": [
                {"id":"s1","label":"Keyframe","type":"SKILL_CALL","branch":"GENERATIVE","skill":"ImageGen","capability":"IMAGE_GENERATION","fallback":"Template"},
                {"id":"s2","label":"QC","type":"QUALITY_GATE","branch":"QUALITY","skill":"QC","capability":"NONE","fallback":""}
              ]
            }
        """.trimIndent()
        val wf = WorkflowDoc(id = "wf2", channelId = "ch_story", caslJson = casl, currentStepIndex = 0, totalSteps = 2)

        val paused = CaslParser.simulate(wf, channel, global, emptyList(), approvalGrantedForResume = false)
        assertEquals(WorkflowLifecycle.AWAITING_APPROVAL, paused.finalLifecycle)

        val resumed = CaslParser.simulate(
            wf.copy(currentStepIndex = paused.blockedStepIndex.toLong()),
            channel,
            global,
            listOf(AssetDoc(channelId = "ch_story", rightsStatus = "VERIFIED")),
            approvalGrantedForResume = true
        )
        assertEquals(WorkflowLifecycle.COMPLETED, resumed.finalLifecycle)
    }
}
