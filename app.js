const STORAGE_KEY = "laststop:v1";
const EARTH_RADIUS_M = 6371000;
const API_ORIGIN = window.location.protocol === "file:" ? "https://laststop-mvp.vercel.app" : "";
const TRAVEL_MODES = {
  walk: { label: "Walk", speedMps: 1.35 },
  auto: { label: "Auto", speedMps: 8.3 },
  car: { label: "Car", speedMps: 11.1 },
  bus: { label: "Bus", speedMps: 6.9 },
  train: { label: "Train", speedMps: 13.9 }
};
const TRAVEL_DETECTION_DISTANCE_M = 1000;
const TRAVEL_DETECTION_WINDOW_MS = 90000;
const TRAVEL_PROMPT_SNOOZE_MS = 30 * 60 * 1000;
const DEFAULT_PLACES = [
  { name: "Home", lat: null, lng: null },
  { name: "Office", lat: null, lng: null },
  { name: "Airport", lat: null, lng: null },
  { name: "Station", lat: null, lng: null }
];
const DEFAULT_ITEMS = ["Bag", "Phone", "Wallet", "Laptop", "Keys", "Earbuds"];
const ALERTS = [
  { key: "10m", seconds: 600, label: "10 min", message: "Destination approaching. Start getting ready." },
  { key: "5m", seconds: 300, label: "5 min", message: "Start getting ready. Check your belongings." },
  { key: "2m", seconds: 120, label: "2 min", message: "Check your belongings before you get out." },
  { key: "30s", seconds: 30, label: "30 sec", message: "Get ready to get out. Look around your seat." }
];

const state = {
  currentPosition: null,
  destination: null,
  items: DEFAULT_ITEMS.map((name) => ({ name, selected: ["Bag", "Phone", "Wallet"].includes(name) })),
  savedPlaces: DEFAULT_PLACES,
  history: [],
  watchId: null,
  idleWatchId: null,
  journeyActive: false,
  firedAlerts: new Set(),
  movementSamples: [],
  travelPromptShown: false,
  travelPromptSnoozedUntil: 0,
  lastDistance: null,
  lastEta: null,
  travelMode: "auto",
  deferredInstallPrompt: null
};

const els = {
  installButton: document.querySelector("#installButton"),
  locationButton: document.querySelector("#locationButton"),
  locationStatus: document.querySelector("#locationStatus"),
  savedPlaces: document.querySelector("#savedPlaces"),
  saveCurrentButton: document.querySelector("#saveCurrentButton"),
  searchForm: document.querySelector("#searchForm"),
  searchInput: document.querySelector("#searchInput"),
  searchResults: document.querySelector("#searchResults"),
  destinationName: document.querySelector("#destinationName"),
  latitudeInput: document.querySelector("#latitudeInput"),
  longitudeInput: document.querySelector("#longitudeInput"),
  confirmCoordsButton: document.querySelector("#confirmCoordsButton"),
  shareLinkInput: document.querySelector("#shareLinkInput"),
  parseLinkButton: document.querySelector("#parseLinkButton"),
  selectedDestination: document.querySelector("#selectedDestination"),
  itemsGrid: document.querySelector("#itemsGrid"),
  customItemForm: document.querySelector("#customItemForm"),
  customItemInput: document.querySelector("#customItemInput"),
  journeyButton: document.querySelector("#journeyButton"),
  modeButtons: document.querySelectorAll(".mode-button"),
  distanceText: document.querySelector("#distanceText"),
  etaText: document.querySelector("#etaText"),
  stageText: document.querySelector("#stageText"),
  historyList: document.querySelector("#historyList"),
  exitMode: document.querySelector("#exitMode"),
  exitChecklist: document.querySelector("#exitChecklist"),
  readyButton: document.querySelector("#readyButton"),
  travelPrompt: document.querySelector("#travelPrompt"),
  travelYesButton: document.querySelector("#travelYesButton"),
  travelNoButton: document.querySelector("#travelNoButton"),
  toast: document.querySelector("#toast")
};

function loadState() {
  const saved = localStorage.getItem(STORAGE_KEY);
  if (!saved) return;

  try {
    const parsed = JSON.parse(saved);
    state.destination = parsed.destination ?? null;
    state.items = Array.isArray(parsed.items) ? parsed.items : state.items;
    state.savedPlaces = Array.isArray(parsed.savedPlaces) ? parsed.savedPlaces : state.savedPlaces;
    state.history = Array.isArray(parsed.history) ? parsed.history.slice(0, 8) : [];
    state.travelMode = parsed.travelMode && TRAVEL_MODES[parsed.travelMode] ? parsed.travelMode : state.travelMode;
  } catch {
    localStorage.removeItem(STORAGE_KEY);
  }
}

function saveState() {
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      destination: state.destination,
      items: state.items,
      savedPlaces: state.savedPlaces,
      history: state.history,
      travelMode: state.travelMode
    })
  );
}

function render() {
  renderSavedPlaces();
  renderItems();
  renderDestination();
  renderMetrics();
  renderTravelMode();
  renderHistory();
  renderExitChecklist();
}

function renderSavedPlaces() {
  els.savedPlaces.innerHTML = "";
  state.savedPlaces.forEach((place, index) => {
    const button = document.createElement("button");
    button.className = "chip";
    button.type = "button";
    button.innerHTML = `${icon("bookmark")}<span>${escapeHtml(place.lat == null ? `${place.name} +` : place.name)}</span>`;
    button.addEventListener("click", () => {
      if (place.lat == null || place.lng == null) {
        if (!state.currentPosition) {
          showToast("Enable GPS first, then save this place.");
          return;
        }
        state.savedPlaces[index] = {
          ...place,
          lat: state.currentPosition.lat,
          lng: state.currentPosition.lng
        };
        setDestination(state.savedPlaces[index]);
        showToast(`${place.name} saved from your current location.`);
      } else {
        setDestination(place);
      }
    });
    els.savedPlaces.appendChild(button);
  });
}

function renderSearchResults(results) {
  els.searchResults.innerHTML = "";
  if (!results.length) {
    const empty = document.createElement("div");
    empty.className = "empty-search";
    empty.innerHTML = `
      ${icon("search")}
      <strong>No results found</strong>
      <span>Try a nearby landmark, area name, or paste a Google Maps link.</span>
      <button type="button" data-action="open-link-tab">${icon("link")}<span>Use Maps Link</span></button>
    `;
    empty.querySelector("button").addEventListener("click", () => activateTab("share"));
    els.searchResults.appendChild(empty);
    return;
  }

  results.slice(0, 6).forEach((result) => {
    const confidence = scoreResult(result, els.searchInput.value.trim());
    const button = document.createElement("button");
    button.className = `result-button ${confidence < 40 ? "weak" : ""}`;
    button.type = "button";
    button.innerHTML = `
      ${icon("pin")}
      <strong>${escapeHtml(getResultName(result))}</strong>
      <span>${escapeHtml(result.display_name)}</span>
      <em>${escapeHtml(result.provider || "Search")} ${confidence < 40 ? "- low match" : ""}</em>
    `;
    button.addEventListener("click", () => {
      setDestination({
        name: getResultName(result),
        lat: Number(result.lat),
        lng: Number(result.lon),
        provider: result.provider || "Search",
        confidence
      });
      showToast("Destination selected.");
    });
    els.searchResults.appendChild(button);
  });
}

function renderItems() {
  els.itemsGrid.innerHTML = "";
  state.items.forEach((item, index) => {
    const button = document.createElement("button");
    button.className = `chip ${item.selected ? "active" : ""}`;
    button.type = "button";
    button.innerHTML = `${icon(itemIconName(item.name))}<span>${escapeHtml(item.name)}</span>`;
    button.addEventListener("click", () => {
      state.items[index].selected = !state.items[index].selected;
      saveState();
      renderItems();
      renderExitChecklist();
    });
    els.itemsGrid.appendChild(button);
  });
}

function renderDestination() {
  if (!state.destination) {
    els.selectedDestination.innerHTML = `${icon("pin")}<span>No destination selected</span>`;
    els.selectedDestination.classList.add("empty");
    return;
  }

  els.selectedDestination.classList.remove("empty");
  els.selectedDestination.innerHTML = `${icon("pin")}<span>${escapeHtml(state.destination.name)}: ${state.destination.lat.toFixed(5)}, ${state.destination.lng.toFixed(5)}</span>`;
}

function renderMetrics() {
  els.distanceText.textContent = state.lastDistance == null ? "--" : formatDistance(state.lastDistance);
  els.etaText.textContent = state.lastEta == null ? "--" : formatEta(state.lastEta);
  els.stageText.textContent = state.journeyActive ? getStageLabel(state.lastEta) : "Idle";
  els.journeyButton.textContent = state.journeyActive ? "Stop Journey" : "Start Journey";
  els.journeyButton.classList.toggle("active", state.journeyActive);
}

function renderTravelMode() {
  els.modeButtons.forEach((button) => {
    button.classList.toggle("active", button.dataset.mode === state.travelMode);
  });
}

function renderHistory() {
  els.historyList.innerHTML = "";
  if (!state.history.length) {
    const empty = document.createElement("p");
    empty.className = "small-text";
    empty.textContent = "No journeys yet";
    els.historyList.appendChild(empty);
    return;
  }

  state.history.forEach((entry) => {
    const row = document.createElement("div");
    row.className = "history-item";
    row.innerHTML = `<strong>${escapeHtml(entry.name)}</strong><span>${escapeHtml(entry.time)}</span>`;
    els.historyList.appendChild(row);
  });
}

function renderExitChecklist() {
  els.exitChecklist.innerHTML = "";
  getSelectedItems().forEach((item) => {
    const label = document.createElement("label");
    label.className = "exit-item";
    label.innerHTML = `<input type="checkbox">${icon(itemIconName(item.name))}<span>${escapeHtml(item.name)}</span>`;
    els.exitChecklist.appendChild(label);
  });
}

function setDestination(destination) {
  state.destination = {
    name: destination.name || "Destination",
    lat: Number(destination.lat),
    lng: Number(destination.lng),
    provider: destination.provider || "Manual",
    confidence: destination.confidence ?? 100
  };
  state.lastDistance = null;
  state.lastEta = null;
  saveState();
  render();
}

async function enableLocation() {
  if (!("geolocation" in navigator)) {
    showToast("This browser does not support GPS.");
    return;
  }

  els.locationStatus.textContent = "Requesting location...";
  navigator.geolocation.getCurrentPosition(
    (position) => {
      updateCurrentPosition(position);
      startIdleTracking();
      els.locationStatus.textContent = `GPS ready: ${state.currentPosition.lat.toFixed(5)}, ${state.currentPosition.lng.toFixed(5)}`;
      showToast("GPS enabled.");
      if ("Notification" in window && Notification.permission === "default") {
        Notification.requestPermission();
      }
    },
    (error) => {
      els.locationStatus.textContent = "GPS permission failed";
      showToast(error.message || "Could not get location.");
    },
    { enableHighAccuracy: true, timeout: 12000, maximumAge: 5000 }
  );
}

function startIdleTracking() {
  if (state.idleWatchId != null || !("geolocation" in navigator)) return;

  state.idleWatchId = navigator.geolocation.watchPosition(
    (position) => {
      updateCurrentPosition(position);
      detectTravel();
    },
    () => {},
    { enableHighAccuracy: true, timeout: 15000, maximumAge: 5000 }
  );
}

function startJourney() {
  if (!state.destination) {
    showToast("Select a destination first.");
    return;
  }
  if (state.currentPosition && shouldConfirmDestination()) {
    const ok = confirm(
      `This destination is ${formatDistance(distanceMeters(state.currentPosition, state.destination))} away and may be a wrong search result. Start anyway?`
    );
    if (!ok) return;
  }
  if (!("geolocation" in navigator)) {
    showToast("GPS is not available on this device.");
    return;
  }

  state.journeyActive = true;
  state.firedAlerts = new Set();
  els.exitMode.classList.add("hidden");
  state.watchId = navigator.geolocation.watchPosition(
    (position) => {
      updateCurrentPosition(position);
      updateJourneyProgress();
    },
    (error) => showToast(error.message || "Location update failed."),
    { enableHighAccuracy: true, timeout: 15000, maximumAge: 4000 }
  );
  updateJourneyProgress();
  showToast("Journey started.");
  renderMetrics();
}

function stopJourney(completed = false) {
  if (state.watchId != null) {
    navigator.geolocation.clearWatch(state.watchId);
  }
  state.watchId = null;
  state.journeyActive = false;

  if (completed && state.destination) {
    state.history.unshift({
      name: state.destination.name,
      time: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
    });
    state.history = state.history.slice(0, 8);
    saveState();
  }

  render();
}

function updateCurrentPosition(position) {
  state.currentPosition = {
    lat: position.coords.latitude,
    lng: position.coords.longitude,
    accuracy: position.coords.accuracy,
    speed: position.coords.speed
  };
  rememberMovementSample();
}

function rememberMovementSample() {
  if (!state.currentPosition) return;
  const now = Date.now();
  state.movementSamples.push({
    at: now,
    lat: state.currentPosition.lat,
    lng: state.currentPosition.lng
  });
  state.movementSamples = state.movementSamples.filter((sample) => now - sample.at <= TRAVEL_DETECTION_WINDOW_MS);
}

function detectTravel() {
  if (state.journeyActive || state.travelPromptShown || Date.now() < state.travelPromptSnoozedUntil) return;
  if (state.movementSamples.length < 2) return;

  const first = state.movementSamples[0];
  const last = state.movementSamples[state.movementSamples.length - 1];
  const moved = distanceMeters(first, last);
  const elapsed = last.at - first.at;

  if (elapsed > 15000 && moved >= TRAVEL_DETECTION_DISTANCE_M) {
    showTravelPrompt();
  }
}

function showTravelPrompt() {
  state.travelPromptShown = true;
  els.travelPrompt.classList.remove("hidden");
  notify("Looks like you're traveling. Want to set a reminder?");
}

function updateJourneyProgress() {
  if (!state.currentPosition || !state.destination) return;

  state.lastDistance = distanceMeters(state.currentPosition, state.destination);
  const speed = getEtaSpeed();
  state.lastEta = Math.round(state.lastDistance / speed);

  fireDueAlerts();
  renderMetrics();

  if (state.lastDistance <= 70) {
    showExitMode();
  }
}

function fireDueAlerts() {
  ALERTS.forEach((alert) => {
    if (state.lastEta == null || state.firedAlerts.has(alert.key)) return;
    if (state.lastEta <= alert.seconds) {
      state.firedAlerts.add(alert.key);
      notify(alert.message);
      if (alert.key === "30s") showExitMode();
    }
  });
}

function showExitMode() {
  if (!state.journeyActive) return;
  renderExitChecklist();
  els.exitMode.classList.remove("hidden");
  notify("You have arrived. Check your belongings.");
  if ("vibrate" in navigator) {
    navigator.vibrate([250, 120, 250, 120, 450]);
  }
}

function notify(message) {
  showToast(message);
  if ("vibrate" in navigator) {
    navigator.vibrate([180, 80, 180]);
  }
  if ("Notification" in window && Notification.permission === "granted") {
    new Notification("LastStop", { body: message, icon: "./icon.svg" });
  }
}

function getStageLabel(eta) {
  if (eta == null) return "Tracking";
  if (eta <= 30) return "Exit";
  if (eta <= 120) return "2 min";
  if (eta <= 300) return "5 min";
  if (eta <= 600) return "10 min";
  return "Quiet";
}

function getEtaSpeed() {
  const gpsSpeed = Number(state.currentPosition?.speed);
  if (Number.isFinite(gpsSpeed) && gpsSpeed > 1) {
    return gpsSpeed;
  }
  return TRAVEL_MODES[state.travelMode].speedMps;
}

function distanceMeters(a, b) {
  const lat1 = toRad(a.lat);
  const lat2 = toRad(b.lat);
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
}

function toRad(value) {
  return (value * Math.PI) / 180;
}

function formatDistance(meters) {
  if (meters < 1000) return `${Math.round(meters)} m`;
  return `${(meters / 1000).toFixed(1)} km`;
}

function formatEta(seconds) {
  if (seconds < 60) return `${Math.max(1, seconds)} sec`;
  return `${Math.ceil(seconds / 60)} min`;
}

function getSelectedItems() {
  return state.items.filter((item) => item.selected);
}

function parseCoordinateText(text) {
  const patterns = [
    /@(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)/,
    /[?&]q=(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)/,
    /geo:(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)/,
    /(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)/
  ];

  for (const pattern of patterns) {
    const match = text.match(pattern);
    if (!match) continue;
    const lat = Number(match[1]);
    const lng = Number(match[2]);
    if (isValidCoordinate(lat, lng)) return { lat, lng };
  }

  return null;
}

async function searchDestination(query) {
  const response = await fetch(apiPath(`/api/search?q=${encodeURIComponent(query)}`));
  if (!response.ok) {
    throw new Error("Search failed.");
  }
  const data = await response.json();
  return data.results || [];
}

async function resolveMapsLink(rawUrl) {
  const response = await fetch(apiPath(`/api/resolve-maps?url=${encodeURIComponent(rawUrl)}`));
  if (!response.ok) {
    throw new Error("Could not resolve that Maps link.");
  }
  return response.json();
}

function apiPath(path) {
  return `${API_ORIGIN}${path}`;
}

function getResultName(result) {
  if (result.name) return result.name;
  const parts = result.display_name.split(",");
  return parts[0]?.trim() || "Destination";
}

function scoreResult(result, query) {
  const words = query
    .toLowerCase()
    .split(/[^a-z0-9]+/)
    .filter((word) => word.length > 2 && !["office", "bangalore", "bengaluru", "india"].includes(word));
  const haystack = `${result.name || ""} ${result.display_name || ""}`.toLowerCase();

  if (!words.length) return 60;
  const hits = words.filter((word) => haystack.includes(word)).length;
  return Math.round((hits / words.length) * 100);
}

function shouldConfirmDestination() {
  if (!state.currentPosition || !state.destination) return false;
  const distance = distanceMeters(state.currentPosition, state.destination);
  return distance > 25000 || state.destination.confidence < 40;
}

function isValidCoordinate(lat, lng) {
  return Number.isFinite(lat) && Number.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
}

function showToast(message) {
  els.toast.textContent = message;
  els.toast.classList.remove("hidden");
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => els.toast.classList.add("hidden"), 3200);
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function icon(name) {
  return `<svg class="icon"><use href="#icon-${name}"></use></svg>`;
}

function itemIconName(name) {
  const value = name.toLowerCase();
  if (value.includes("phone")) return "phone";
  if (value.includes("wallet")) return "wallet";
  if (value.includes("laptop") || value.includes("charger")) return "laptop";
  if (value.includes("key") || value.includes("card")) return "key";
  if (value.includes("ear") || value.includes("headphone")) return "earbuds";
  return "bag";
}

function activateTab(name) {
  document.querySelectorAll(".tab").forEach((item) => item.classList.toggle("active", item.dataset.tab === name));
  document.querySelectorAll(".tab-panel").forEach((panel) => panel.classList.toggle("active", panel.dataset.panel === name));
}

function bindEvents() {
  document.querySelectorAll(".tab").forEach((tab) => {
    tab.addEventListener("click", () => {
      activateTab(tab.dataset.tab);
    });
  });

  els.locationButton.addEventListener("click", enableLocation);
  els.saveCurrentButton.addEventListener("click", () => {
    if (!state.currentPosition) {
      showToast("Enable GPS first.");
      return;
    }
    const name = prompt("Name this place", "Current place");
    if (!name) return;
    const place = { name, lat: state.currentPosition.lat, lng: state.currentPosition.lng };
    state.savedPlaces.unshift(place);
    setDestination(place);
    showToast(`${name} saved.`);
  });

  els.searchForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const query = els.searchInput.value.trim();
    if (query.length < 3) {
      showToast("Type at least 3 characters.");
      return;
    }

    els.searchResults.innerHTML = '<p class="small-text">Searching...</p>';
    try {
      const results = await searchDestination(query);
      renderSearchResults(results);
    } catch (error) {
      els.searchResults.innerHTML = "";
      showToast("Search failed. Open the Vercel link or check connection.");
    }
  });

  els.confirmCoordsButton.addEventListener("click", () => {
    const lat = Number(els.latitudeInput.value.trim());
    const lng = Number(els.longitudeInput.value.trim());
    if (!isValidCoordinate(lat, lng)) {
      showToast("Enter valid latitude and longitude.");
      return;
    }
    setDestination({ name: els.destinationName.value.trim() || "Destination", lat, lng });
  });

  els.parseLinkButton.addEventListener("click", async () => {
    const link = els.shareLinkInput.value.trim();
    let coordinates = parseCoordinateText(link);

    if (!coordinates && /^https?:\/\//i.test(link)) {
      els.parseLinkButton.textContent = "Resolving...";
      try {
        const resolved = await resolveMapsLink(link);
        coordinates = parseCoordinateText(resolved.url || "");
      } catch (error) {
        showToast(error.message || "Could not resolve link.");
      } finally {
        els.parseLinkButton.textContent = "Use Link Destination";
      }
    }

    if (!coordinates) {
      showToast("Could not find coordinates. Try sharing a dropped pin from Google Maps.");
      return;
    }
    setDestination({ name: "Shared destination", ...coordinates });
  });

  els.customItemForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const name = els.customItemInput.value.trim();
    if (!name) return;
    state.items.push({ name, selected: true });
    els.customItemInput.value = "";
    saveState();
    renderItems();
    renderExitChecklist();
  });

  els.journeyButton.addEventListener("click", () => {
    if (state.journeyActive) {
      stopJourney();
      showToast("Journey stopped.");
    } else {
      startJourney();
    }
  });

  els.modeButtons.forEach((button) => {
    button.addEventListener("click", () => {
      state.travelMode = button.dataset.mode;
      if (state.currentPosition && state.destination) {
        updateJourneyProgress();
      }
      saveState();
      renderTravelMode();
    });
  });

  els.readyButton.addEventListener("click", () => {
    els.exitMode.classList.add("hidden");
    stopJourney(true);
    showToast("Journey completed.");
  });

  els.travelYesButton.addEventListener("click", () => {
    els.travelPrompt.classList.add("hidden");
    activateTab("search");
    document.querySelector("#destinationCard").scrollIntoView({ behavior: "smooth", block: "start" });
    showToast("Choose destination and belongings.");
  });

  els.travelNoButton.addEventListener("click", () => {
    els.travelPrompt.classList.add("hidden");
    state.travelPromptSnoozedUntil = Date.now() + TRAVEL_PROMPT_SNOOZE_MS;
    showToast("Okay. I’ll stay quiet for now.");
  });

  window.addEventListener("beforeinstallprompt", (event) => {
    event.preventDefault();
    state.deferredInstallPrompt = event;
    els.installButton.classList.remove("hidden");
  });

  els.installButton.addEventListener("click", async () => {
    if (!state.deferredInstallPrompt) return;
    state.deferredInstallPrompt.prompt();
    await state.deferredInstallPrompt.userChoice;
    state.deferredInstallPrompt = null;
    els.installButton.classList.add("hidden");
  });
}

if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("./service-worker.js").catch(() => {});
}

loadState();
bindEvents();
render();
