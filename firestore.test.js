const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_owner_1";
const BOB_UID = "bob_attacker_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validChannelPayload(uid) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    name: "Gameplay Channel",
    category: "Gaming",
    description: "High-retention 1080p gameplay highlights",
    systemPrompt: "Prioritize real gameplay footage and fast-paced cuts.",
    promptVersion: "v1.0",
    previousSystemPrompt: "",
    minResolution: "1080p",
    editingPriority: "EDITING_FIRST",
    textGen: "ALLOW",
    imageGen: "ALLOW",
    videoGen: "DENY",
    audioGen: "REQUIRE_APPROVAL",
    aiAnalysis: "ALLOW",
    aiEnhancement: "INHERIT",
    styleVisual: "Crisp high-contrast HUD focus",
    styleTempo: "Cut every 2.4s on action peak",
    styleAudio: "Duck BGM -14dB under SFX",
    styleConfidence: 92,
    productionsCount: 4,
    createdAt: now,
    updatedAt: now,
  };
}

function validProposalPayload(uid) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    channelId: "ch_gameplay",
    targetType: "SYSTEM_PROMPT",
    targetId: "ch_gameplay",
    title: "Tighten pacing rule for intro hook",
    currentValue: "Start with 10s context.",
    proposedValue: "Start with 3s highlight hook before context.",
    reason: "Viewer retention telemetry shows drop-off at 5s.",
    impactScope: "Gameplay Channel + 1 active workflow",
    riskLevel: "LOW",
    proposer: "AI Core Advisor",
    status: "PENDING",
    versionTag: "v1.1-draft",
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated access is denied across all FlowKit collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("channels").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("channels").doc("ch_1").set(validChannelPayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("proposals").get());
});

test("Cross-user isolation blocks Bob from reading or writing Alice's workspace", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("channels").doc("ch_gameplay").set(validChannelPayload(ALICE_UID))
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("channels").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("channels").doc("ch_gameplay").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("channels").doc("ch_gameplay").update({
      name: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner channel creation, partial update, and shadow-field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("channels");

  await assertSucceeds(col.doc("ch_gameplay").set(validChannelPayload(ALICE_UID)));

  // Valid policy partial update
  await assertSucceeds(
    col.doc("ch_gameplay").update({
      videoGen: "DENY",
      imageGen: "REQUIRE_APPROVAL",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field injection ("Shadow Update")
  await assertFails(
    col.doc("ch_gameplay").update({
      name: "New Name",
      isAdminOverride: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject @DocumentId 'id' leakage in create payload
  const withId = { ...validChannelPayload(ALICE_UID), id: "ch_bad" };
  await assertFails(col.doc("ch_bad").set(withId));

  // Reject immutable createdAt tamper
  await assertFails(
    col.doc("ch_gameplay").update({
      systemPrompt: "Changed",
      promptVersion: "v1.1",
      previousSystemPrompt: "Old",
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Approval proposal enforces PENDING -> APPROVED/REJECTED terminal state lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const proposals = aliceDb.collection("users").doc(ALICE_UID).collection("proposals");

  // Cannot create proposal already APPROVED
  await assertFails(
    proposals.doc("prop_bad").set({ ...validProposalPayload(ALICE_UID), status: "APPROVED" })
  );

  await assertSucceeds(proposals.doc("prop_1").set(validProposalPayload(ALICE_UID)));

  // Transition PENDING -> APPROVED succeeds
  await assertSucceeds(
    proposals.doc("prop_1").update({
      status: "APPROVED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal lock: cannot transition APPROVED -> REJECTED or PENDING
  await assertFails(
    proposals.doc("prop_1").update({
      status: "REJECTED",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Audit logs are immutable after creation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const logs = aliceDb.collection("users").doc(ALICE_UID).collection("audit_logs");
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    logs.doc("log_1").set({
      userId: ALICE_UID,
      eventType: "PolicyPublished",
      actor: "Owner",
      channelId: "ch_gameplay",
      summary: "Published Global Policy v1.0",
      severity: "INFO",
      createdAt: now,
    })
  );

  await assertFails(
    logs.doc("log_1").update({
      summary: "Tampered log",
    })
  );
});
