function bridgeKeyForRequest(threadId, connectionId) {
  if (threadId) return threadId;
  return "new:shared";
}

function shouldDisposeIdleBridge({ clientCount }) {
  return clientCount === 0;
}

function shouldPromoteBridgeKey({ bridgeKey, threadId }) {
  return Boolean(threadId && bridgeKey && bridgeKey !== threadId && bridgeKey.startsWith("new:"));
}

function shouldStartFreshThreadAfterResumeError({ method, error }) {
  const message = typeof error === "string" ? error : error?.message || "";
  return method === "thread/resume" && /no rollout found/i.test(message);
}

module.exports = {
  bridgeKeyForRequest,
  shouldDisposeIdleBridge,
  shouldPromoteBridgeKey,
  shouldStartFreshThreadAfterResumeError,
};
