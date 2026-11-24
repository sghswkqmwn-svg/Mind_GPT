const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_mindgpt_123";
const BOB_UID = "bob_mindgpt_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated user: denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("chats").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("servers").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").set({
      userId: ALICE_UID,
      title: "Hacked",
      lastMessagePreview: "",
      category: "general",
      pinned: false,
      messageCount: 0,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("ChatThread & ChatMessage: owner CRUD succeeds, cross-user & ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  const chatRef = aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1");
  await assertSucceeds(
    chatRef.set({
      userId: ALICE_UID,
      title: "Medieval Castle Build",
      lastMessagePreview: "Let's design a fortified keep.",
      category: "castle",
      pinned: false,
      messageCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // List own chats succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").get()
  );

  // Rename & Pin partial updates succeed
  await assertSucceeds(
    chatRef.update({ title: "Grand Castle Keep", updatedAt: new Date(Date.now() - 2000) })
  );
  await assertSucceeds(
    chatRef.update({ pinned: true, updatedAt: new Date(Date.now() - 1000) })
  );

  // Immutable createdAt change must fail
  await assertFails(
    chatRef.update({ createdAt: new Date(Date.now() - 500), updatedAt: new Date(Date.now() - 500) })
  );

  // Ghost field & @DocumentId `id` field must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_bad").set({
      id: "chat_bad",
      userId: ALICE_UID,
      title: "Bad Chat",
      lastMessagePreview: "",
      category: "general",
      pinned: false,
      messageCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Create message & update feedback
  const msgRef = chatRef.collection("messages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      userId: ALICE_UID,
      chatId: "chat_1",
      role: "model",
      content: "Use `//set stone_bricks` for the outer wall.",
      feedback: "none",
      toolTag: "worldedit",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    msgRef.update({ feedback: "like", updatedAt: new Date(Date.now() - 1000) })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("chats").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("MinecraftProject & ServerProfile: valid payloads succeed, future timestamps & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  const projRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1");
  await assertSucceeds(
    projRef.set({
      userId: ALICE_UID,
      title: "Skyblock Spawn Hub",
      toolType: "spawn",
      minecraftEdition: "Java",
      minecraftVersion: "1.21.4",
      promptSummary: "Floating emerald hub with 4 portals",
      content: "# Skyblock Spawn\nUse prismarine and quartz.",
      pinned: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Future timestamp must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_future").set({
      userId: ALICE_UID,
      title: "Future Build",
      toolType: "house",
      minecraftEdition: "Java",
      minecraftVersion: "1.21.4",
      promptSummary: "Test",
      content: "Content",
      pinned: false,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Server profile without secrets
  const srvRef = aliceDb.collection("users").doc(ALICE_UID).collection("servers").doc("srv_1");
  await assertSucceeds(
    srvRef.set({
      userId: ALICE_UID,
      name: "Loyal SMP",
      host: "play.example.net",
      gamePort: 25565,
      rconPort: 25575,
      panelUrl: "https://panel.example.net/api/client/servers/abc123",
      connectionMode: "PANEL_HTTPS",
      lastStatus: "Reachable (24ms)",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("servers").get());
});
