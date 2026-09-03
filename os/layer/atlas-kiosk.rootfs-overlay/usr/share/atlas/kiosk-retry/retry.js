const targetUrl = "https://localhost/";
const retryAlarm = "atlas-kiosk-retry";
const retryPeriodMinutes = 0.5;
const requestTimeoutMs = 10_000;

function scheduleRetry() {
  chrome.alarms.create(retryAlarm, {
    delayInMinutes: retryPeriodMinutes,
    periodInMinutes: retryPeriodMinutes,
  });
}

async function probeAndReload() {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), requestTimeoutMs);

  try {
    const response = await fetch(targetUrl, {
      cache: "no-store",
      credentials: "omit",
      signal: controller.signal,
    });

    if (response.ok) {
      await chrome.alarms.clear(retryAlarm);
    }
  } catch {
    // Chromium's error page cannot reload itself, so replace it below.
  } finally {
    clearTimeout(timeout);
  }

  const tabs = await chrome.tabs.query({});
  await Promise.all(
    tabs.flatMap((tab) =>
      tab.id === undefined
        ? []
        : [chrome.tabs.update(tab.id, { url: targetUrl })],
    ),
  );
}

chrome.runtime.onInstalled.addListener(scheduleRetry);
chrome.runtime.onStartup.addListener(scheduleRetry);
chrome.webNavigation.onErrorOccurred.addListener(
  (details) => {
    if (details.frameId === 0) {
      scheduleRetry();
    }
  },
  { url: [{ schemes: ["https"], hostEquals: "localhost" }] },
);
chrome.webRequest.onHeadersReceived.addListener(
  (details) => {
    if (details.type === "main_frame" && details.statusCode >= 500) {
      scheduleRetry();
    }
  },
  { urls: [`${targetUrl}*`], types: ["main_frame"] },
);
chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === retryAlarm) {
    void probeAndReload();
  }
});

scheduleRetry();
