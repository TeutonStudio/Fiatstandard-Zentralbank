const element = (id) => {
  const gefunden = document.getElementById(id);
  if (!gefunden) throw new Error(`Element #${id} fehlt.`);
  return gefunden;
};

const apiFeld = element("api-base");
const spielIdFeld = element("spiel-id");
const zustandAusgabe = element("zustand");
const aktionenAuswahl = element("aktionen");

const gespeicherteApi = localStorage.getItem("fiat-api-base");
if (gespeicherteApi) apiFeld.value = gespeicherteApi;

const apiBasis = () => {
  const wert = apiFeld.value.trim().replace(/\/$/, "");
  if (!wert) throw new Error("API-Basis-URL fehlt.");
  localStorage.setItem("fiat-api-base", wert);
  return wert;
};

const spielId = () => {
  const wert = spielIdFeld.value.trim();
  if (!/^\d+$/.test(wert)) throw new Error("Eine gültige Spiel-ID ist erforderlich.");
  return wert;
};

async function anfragen(pfad, optionen = {}) {
  const antwort = await fetch(`${apiBasis()}${pfad}`, {
    ...optionen,
    headers: { "Content-Type": "application/json", ...(optionen.headers || {}) },
  });
  const text = await antwort.text();
  const daten = text ? JSON.parse(text) : null;
  if (!antwort.ok) {
    const fehler = new Error(daten?.meldung || `HTTP ${antwort.status}`);
    fehler.antwort = daten;
    throw fehler;
  }
  fehlerLeeren();
  return daten;
}

function fehlerZeigen(fehler) {
  const bereich = element("fehler-bereich");
  bereich.hidden = false;
  element("fehler").textContent = JSON.stringify(
    fehler.antwort || { code: "CLIENT_FEHLER", meldung: fehler.message },
    null,
    2,
  );
}

function fehlerLeeren() {
  element("fehler-bereich").hidden = true;
  element("fehler").textContent = "";
}

function zustandZeigen(daten) {
  zustandAusgabe.textContent = JSON.stringify(daten.zustand || daten, null, 2);
}

function aktionsName(aktion) {
  const art = aktion.art || "Aktion";
  return `${art.split(".").pop()}${aktion.zugId ? ` · Zug ${aktion.zugId}` : ""}`;
}

async function aktionenLaden() {
  const daten = await anfragen(`/api/v1/games/${spielId()}/actions`);
  aktionenAuswahl.replaceChildren();
  for (const aktion of daten.aktionen) {
    const option = document.createElement("option");
    option.textContent = aktionsName(aktion);
    option.value = JSON.stringify(aktion);
    aktionenAuswahl.append(option);
  }
}

element("health").addEventListener("click", () => {
  anfragen("/health")
    .then((daten) => { element("server-status").textContent = `Online · API v${daten.version}`; })
    .catch(fehlerZeigen);
});

element("erstellen").addEventListener("click", () => {
  const namen = element("spieler").value.split(",").map((name) => name.trim()).filter(Boolean);
  anfragen("/api/v1/games", {
    method: "POST",
    body: JSON.stringify({ version: 1, spielerNamen: namen }),
  }).then((daten) => {
    spielIdFeld.value = daten.spielId;
    zustandZeigen(daten);
    return aktionenLaden();
  }).catch(fehlerZeigen);
});

element("laden").addEventListener("click", () => {
  anfragen(`/api/v1/games/${spielId()}`).then(zustandZeigen).catch(fehlerZeigen);
});

element("aktionen-laden").addEventListener("click", () => {
  aktionenLaden().catch(fehlerZeigen);
});

element("aktion-senden").addEventListener("click", () => {
  if (!aktionenAuswahl.value) {
    fehlerZeigen(new Error("Bitte zuerst eine erlaubte Aktion auswählen."));
    return;
  }
  anfragen(`/api/v1/games/${spielId()}/actions`, {
    method: "POST",
    body: JSON.stringify({ version: 1, aktion: JSON.parse(aktionenAuswahl.value) }),
  }).then((daten) => {
    zustandZeigen(daten);
    return aktionenLaden();
  }).catch(fehlerZeigen);
});
