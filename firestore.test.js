const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_metal_123";
const BOB_UID = "bob_shred_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validPresetPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    name: "Djent 8-String Razor",
    category: "MODERN_METAL",
    favorite: true,
    ampModelId: "DJENT_PRECISION",
    gain: 0.78,
    bass: 0.42,
    mid: 0.65,
    treble: 0.72,
    presence: 0.75,
    depth: 0.45,
    master: 0.7,
    cabModelId: "V30_4X12_TIGHT",
    micPosition: "CAP_EDGE",
    cabResonance: 0.48,
    gateOn: true,
    gateThresholdDb: -38.0,
    odOn: true,
    odDrive: 0.12,
    odTone: 0.74,
    odLevel: 0.85,
    eqOn: true,
    eqBands: [-2.0, -1.5, 1.0, 3.5, 2.0, 3.0, -1.0],
    chorusOn: false,
    chorusMix: 0.2,
    delayOn: false,
    delayTimeMs: 360.0,
    delayFeedback: 0.25,
    delayMix: 0.18,
    reverbOn: true,
    reverbDecay: 0.28,
    reverbMix: 0.14,
    toneMatched: false,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

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

test("Unauthenticated user cannot read or write presets", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("presets").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("presets")
      .doc("preset_1")
      .set(validPresetPayload(ALICE_UID))
  );
});

test("Owner can create, list, partially update, and delete preset", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const presetsCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("presets");

  await assertSucceeds(
    presetsCol.doc("preset_1").set(validPresetPayload(ALICE_UID))
  );
  await assertSucceeds(presetsCol.orderBy("updatedAt", "desc").get());

  await assertSucceeds(
    presetsCol.doc("preset_1").update({
      favorite: false,
      gain: 0.84,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(presetsCol.doc("preset_1").delete());
});

test("Rejects ghost fields, @DocumentId id key, future timestamps, and invalid EQ bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const presetsCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("presets");

  await assertFails(
    presetsCol
      .doc("preset_ghost")
      .set(validPresetPayload(ALICE_UID, { id: "preset_ghost" }))
  );
  await assertFails(
    presetsCol
      .doc("preset_future")
      .set(
        validPresetPayload(ALICE_UID, {
          createdAt: new Date(Date.now() + 60000),
        })
      )
  );
  await assertFails(
    presetsCol
      .doc("preset_bad_eq")
      .set(validPresetPayload(ALICE_UID, { eqBands: [0, 0, 0] }))
  );
});

test("Cross-user read, write, and delete are denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("presets")
      .doc("bob_preset")
      .set(validPresetPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDoc = aliceDb
    .collection("users")
    .doc(BOB_UID)
    .collection("presets")
    .doc("bob_preset");

  await assertFails(bobDoc.get());
  await assertFails(bobDoc.update({ name: "Hijacked", updatedAt: new Date(Date.now() - 1000) }));
  await assertFails(bobDoc.delete());
});
