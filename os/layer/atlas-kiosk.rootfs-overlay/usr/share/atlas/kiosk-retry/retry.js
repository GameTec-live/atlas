const targetUrl = "https://localhost/";
const retryAlarmPrefix = "atlas-kiosk-retry:";
const retryPeriodMinutes = 0.5;
const requestTimeoutMs = 10_000;

function retryAlarmName(tabId) {
  return `${retryAlarmPrefix}${tabId}`;
}

function retryTabId(alarmName) {
  if (!alarmName.startsWith(retryAlarmPrefix)) {
    return undefined;
  }

  const tabId = Number(alarmName.slice(retryAlarmPrefix.length));
  return Number.isInteger(tabId) && tabId >= 0 ? tabId : undefined;
}

function scheduleRetry(tabId) {
  if (!Number.isInteger(tabId) || tabId < 0) {
    return;
  }

  chrome.alarms.create(retryAlarmName(tabId), {
    delayInMinutes: retryPeriodMinutes,
    periodInMinutes: retryPeriodMinutes,
  });
}

async function clearRetry(tabId) {
  await chrome.alarms.clear(retryAlarmName(tabId));
}

async function probeAndReload(tabId) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), requestTimeoutMs);

  try {
    const response = await fetch(targetUrl, {
      cache: "no-store",
      credentials: "omit",
      signal: controller.signal,
    });

    if (response.ok) {
      await clearRetry(tabId);
    }
  } catch {
    // Chromium's error page cannot reload itself, so replace it below.
  } finally {
    clearTimeout(timeout);
  }

  try {
    await chrome.tabs.update(tabId, { url: targetUrl });
  } catch {
    // The kiosk tab was closed, so there is nothing left to recover.
    await clearRetry(tabId);
  }
}

chrome.webNavigation.onErrorOccurred.addListener(
  (details) => {
    if (details.frameId === 0) {
      scheduleRetry(details.tabId);
    }
  },
  { url: [{ schemes: ["https"], hostEquals: "localhost" }] },
);
chrome.webRequest.onHeadersReceived.addListener(
  (details) => {
    if (details.type === "main_frame" && details.statusCode >= 500) {
      scheduleRetry(details.tabId);
    } else if (details.type === "main_frame") {
      void clearRetry(details.tabId);
    }
  },
  { urls: [`${targetUrl}*`], types: ["main_frame"] },
);
chrome.alarms.onAlarm.addListener((alarm) => {
  const tabId = retryTabId(alarm.name);
  if (tabId !== undefined) {
    void probeAndReload(tabId);
  }
});
