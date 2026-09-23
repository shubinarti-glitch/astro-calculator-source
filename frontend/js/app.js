"use strict";

// ---------- Звёздное небо ----------
(function makeStars() {
  const box = document.getElementById("stars");
  const n = 90;
  let html = "";
  for (let i = 0; i < n; i++) {
    const top = Math.random() * 100;
    const left = Math.random() * 100;
    const dur = (2 + Math.random() * 5).toFixed(1);
    const delay = (Math.random() * 5).toFixed(1);
    html += `<span style="top:${top}%;left:${left}%;--dur:${dur}s;animation-delay:${delay}s"></span>`;
  }
  box.innerHTML = html;
})();

// ---------- Состояние ----------
let mode = "natal"; // 'natal' | 'transit'
let simpleMode = false; // «простыми словами» — без терминов (только натал)
let lastData = null; // последние данные результата
let lastMode = null; // режим последнего результата (для смены языка)

const $ = (id) => document.getElementById(id);

// ---------- Тема (тёмная / светлая) ----------
let lastChart = { dark: "", light: "" }; // SVG-колесо в обеих темах

function applyChartTheme() {
  const box = $("chart-svg");
  if (!box) return;
  const isLight = document.documentElement.getAttribute("data-theme") === "light";
  box.innerHTML = (isLight ? lastChart.light : lastChart.dark) || "";
}

function renderChart(data) {
  lastChart = { dark: data.svg || "", light: data.svg_light || data.svg || "" };
  applyChartTheme();
  if ($("chart-png-btn")) $("chart-png-btn").classList.toggle("hidden", !lastChart.dark);
}

// Экспорт колеса карты в PNG (через canvas, офлайн, без сторонних библиотек).
function downloadChartPng() {
  const svgEl = $("chart-svg").querySelector("svg");
  if (!svgEl) return;
  const xml = new XMLSerializer().serializeToString(svgEl);
  const vb = (svgEl.getAttribute("viewBox") || "0 0 820 820").split(/\s+/).map(Number);
  const w = svgEl.viewBox && svgEl.viewBox.baseVal && svgEl.viewBox.baseVal.width || vb[2] || 820;
  const h = svgEl.viewBox && svgEl.viewBox.baseVal && svgEl.viewBox.baseVal.height || vb[3] || 820;
  const scale = 2; // ретина-качество
  const img = new Image();
  const svg64 = "data:image/svg+xml;base64," + btoa(unescape(encodeURIComponent(xml)));
  img.onload = () => {
    const canvas = document.createElement("canvas");
    canvas.width = w * scale;
    canvas.height = h * scale;
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = document.documentElement.getAttribute("data-theme") === "light" ? "#ffffff" : "#0c0c1e";
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    canvas.toBlob((blob) => {
      if (!blob) return;
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "natal-chart.png";
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 2000);
    }, "image/png");
  };
  img.onerror = () => alert(LANG === "en" ? "Could not export the image." : "Не удалось сохранить картинку.");
  img.src = svg64;
}
if ($("chart-png-btn")) $("chart-png-btn").addEventListener("click", downloadChartPng);

function applyTheme(theme) {
  document.documentElement.setAttribute("data-theme", theme);
  try {
    localStorage.setItem("astro_theme", theme);
  } catch (e) {}
  document.querySelectorAll(".theme-opt").forEach((b) =>
    b.classList.toggle("active", b.dataset.themeSet === theme)
  );
  applyChartTheme(); // переключаем уже показанную карту
}
(function initTheme() {
  let saved = "light";
  try {
    saved = localStorage.getItem("astro_theme") || "light";
  } catch (e) {}
  applyTheme(saved);
})();
$("theme-switch").addEventListener("click", (e) => {
  const opt = e.target.closest(".theme-opt");
  if (opt) applyTheme(opt.dataset.themeSet);
});

// ---------- Глазик у полей пароля ----------
document.addEventListener("click", (e) => {
  const eye = e.target.closest(".pw-eye");
  if (!eye) return;
  const input = eye.parentElement.querySelector("input");
  const show = input.type === "password";
  input.type = show ? "text" : "password";
  eye.classList.toggle("on", show);
  eye.setAttribute("aria-label", t(show ? "ui_password_hide" : "ui_password_show"));
});

// ---------- Трактовка аспекта по тапу (на телефоне hover нет) ----------
$("aspects-table").addEventListener("click", (e) => {
  const tr = e.target.closest("tr[title]");
  if (!tr) return;
  const next = tr.nextElementSibling;
  if (next && next.classList.contains("aspect-interp-row")) { next.remove(); return; }
  tr.parentElement.querySelectorAll(".aspect-interp-row").forEach((r) => r.remove());
  const row = document.createElement("tr");
  row.className = "aspect-interp-row";
  const td = document.createElement("td");
  td.colSpan = tr.children.length;
  td.textContent = tr.getAttribute("title");
  row.appendChild(td);
  tr.after(row);
});

// ---------- Кнопка «наверх» ----------
(function scrollTopBtn() {
  const btn = $("scroll-top");
  if (!btn) return;
  const onScroll = () => btn.classList.toggle("hidden", window.scrollY < 400);
  window.addEventListener("scroll", onScroll, { passive: true });
  btn.addEventListener("click", () => window.scrollTo({ top: 0, behavior: "smooth" }));
  onScroll();
})();

// ---------- Чат поддержки (сообщение уходит оператору на почту) ----------
(function supportWidget() {
  const btn = $("support-btn"), panel = $("support-panel");
  if (!btn || !panel) return;
  btn.addEventListener("click", () => {
    panel.classList.toggle("hidden");
    if (!panel.classList.contains("hidden")) $("support-message").focus();
  });
  $("support-close").addEventListener("click", () => panel.classList.add("hidden"));
  $("support-send").addEventListener("click", async () => {
    const message = $("support-message").value.trim();
    const email = $("support-email").value.trim();
    const name = $("support-name").value.trim();
    const status = $("support-status");
    if (!message) { showSupportStatus(status, t("support_empty"), false); return; }
    if (!$("support-consent").checked) { showSupportStatus(status, t("support_consent_need"), false); return; }
    $("support-send").disabled = true;
    showSupportStatus(status, t("support_sending"), true);
    try {
      await postJSON("/api/support", {
        message, email, name,
        privacy_accepted: true,
        privacy_version: "2026-09-01",
      });
      $("support-message").value = "";
      $("support-consent").checked = false;
      showSupportStatus(status, t("support_ok"), true);
    } catch (ex) {
      showSupportStatus(status, ex.message || t("support_err"), false);
    } finally {
      $("support-send").disabled = false;
    }
  });
})();
function showSupportStatus(el, text, ok) {
  el.textContent = text;
  el.classList.remove("hidden");
  el.classList.toggle("support-ok", ok);
  el.classList.toggle("support-err", !ok);
}

// Справочник архетипов знаков — ленивая загрузка при раскрытии
let PLANETS = null; // карта описаний планет для всплывающего окна (грузится вместе с архетипами)
const planetChip = (p) => `<button type="button" class="planet-link" data-planet="${p.key}">${p.symbol} ${p.name}</button>`;
$("archetypes-details").addEventListener("toggle", async function () {
  if (!this.open || this._loaded) return;
  this._loaded = true;
  try {
    const [list, planets] = await Promise.all([
      fetch(`/api/archetypes?lang=${LANG}`).then((r) => r.json()),
      fetch(`/api/planets?lang=${LANG}`).then((r) => r.json()),
    ]);
    PLANETS = planets;
    $("archetypes-grid").innerHTML = list
      .map((a) => {
        const rulers = `<span class="arc-ruler-lbl">${t("arc_ruler")}:</span> ${a.rulers.map(planetChip).join(", ")}`;
        const exalt = a.exalt ? ` · <span class="arc-ruler-lbl">${t("arc_exalt")}:</span> ${planetChip(a.exalt)}` : "";
        return `<div class="arc-card">
            <h4><span class="arc-sym">${a.symbol}</span>${a.sign_ru}</h4>
            <div class="arc-type">${a.archetype} · ${t("arc_element")}: ${a.element}</div>
            <div class="arc-rulers">${rulers}${exalt}</div>
            <p>${a.essence}</p>
            <p><span class="arc-plus">${t("arc_light")}</span> ${a.light}</p>
            <p><span class="arc-minus">${t("arc_shadow")}</span> ${a.shadow}</p>
            ${a.detail ? `<details class="arc-detail"><summary>${t("arc_more")}</summary>${a.detail.split("\n\n").map((p) => `<p>${p}</p>`).join("")}</details>` : ""}
          </div>`;
      })
      .join("");
  } catch {
    this._loaded = false;
    $("archetypes-grid").innerHTML = `<p class="section-note">${t("arc_load_err")}</p>`;
  }
});

// Всплывающее окно с полным описанием планеты (переиспользует паттерн .modal-overlay).
function ensurePlanetModal() {
  if ($("planet-modal")) return;
  const ov = document.createElement("div");
  ov.className = "modal-overlay hidden";
  ov.id = "planet-modal";
  ov.innerHTML =
    `<div class="modal planet-modal">
       <button class="modal-close" id="planet-modal-close" aria-label="${t("arc_close")}">×</button>
       <h3 class="planet-modal-title"><span id="planet-modal-sym"></span> <span id="planet-modal-name"></span></h3>
       <p id="planet-modal-role" class="planet-modal-role"></p>
       <div id="planet-modal-spheres"></div>
     </div>`;
  document.body.appendChild(ov);
  ov.addEventListener("click", (e) => {
    if (e.target === ov || e.target.id === "planet-modal-close") closePlanetModal();
  });
}
function closePlanetModal() {
  const m = $("planet-modal");
  if (m) m.classList.add("hidden");
}
function openPlanetModal(key) {
  if (!PLANETS || !PLANETS[key]) return;
  ensurePlanetModal();
  const p = PLANETS[key];
  $("planet-modal-sym").textContent = p.symbol;
  $("planet-modal-name").textContent = p.name;
  $("planet-modal-role").textContent = p.role;
  const box = $("planet-modal-spheres");
  box.innerHTML = "";
  [["arc_planet_fn", p.function], ["arc_planet_love", p.love], ["arc_planet_work", p.work]].forEach(([lbl, v]) => {
    if (!v) return;
    const para = document.createElement("p");
    const b = document.createElement("span");
    b.className = "planet-sphere-lbl";
    b.textContent = t(lbl) + " ";
    para.appendChild(b);
    para.appendChild(document.createTextNode(v)); // текст планеты — через textContent (без innerHTML)
    box.appendChild(para);
  });
  $("planet-modal").classList.remove("hidden");
}
$("archetypes-grid").addEventListener("click", (e) => {
  const btn = e.target.closest(".planet-link");
  if (btn) openPlanetModal(btn.dataset.planet);
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") closePlanetModal();
});

// Public glossary: fetch only the current language, never embed the bilingual corpus.
let glossaryRequest = 0;
let glossaryController = null;
async function buildGlossary() {
  const box = $("glossary-grid");
  if (!box) return;
  const lang = LANG === "en" ? "en" : "ru";
  const request = ++glossaryRequest;
  if (glossaryController) glossaryController.abort();
  const controller = new AbortController();
  glossaryController = controller;
  const timeout = setTimeout(() => controller.abort(), 15000);
  box.setAttribute("aria-live", "polite");
  box.setAttribute("aria-busy", "true");
  const status = document.createElement("p");
  status.setAttribute("role", "status");
  status.textContent = lang === "en" ? "Loading glossary…" : "Загрузка словаря…";
  box.replaceChildren(status);
  try {
    const response = await fetch(`/api/glossary?lang=${lang}`, { signal: controller.signal });
    if (!response.ok) throw new Error("Glossary request failed");
    const entries = await response.json();
    if (!Array.isArray(entries) || !entries.length || !entries.every(
      (row) => Array.isArray(row) && row.length === 2 && row.every((value) => typeof value === "string")
    )) throw new Error("Invalid glossary response");
    if (request !== glossaryRequest) return;
    const cards = entries.map(([term, def]) => {
      const card = document.createElement("div");
      card.className = "gloss-card";
      const heading = document.createElement("h4");
      heading.textContent = term;
      const [main, ex] = def.split("|;");
      const description = document.createElement("p");
      description.textContent = main;
      card.append(heading, description);
      if (ex) {
        const example = document.createElement("p");
        example.className = "gloss-ex";
        example.textContent = ex;
        card.append(example);
      }
      return card;
    });
    box.replaceChildren(...cards);
  } catch (error) {
    if (request !== glossaryRequest) return;
    status.textContent = lang === "en" ? "Could not load glossary." : "Не удалось загрузить словарь.";
    const retry = document.createElement("button");
    retry.type = "button";
    retry.textContent = lang === "en" ? "Retry" : "Повторить";
    retry.addEventListener("click", buildGlossary);
    box.replaceChildren(status, retry);
  } finally {
    clearTimeout(timeout);
    if (request === glossaryRequest) {
      box.setAttribute("aria-busy", "false");
      glossaryController = null;
    }
  }
}
buildGlossary();

document.addEventListener("langchange", buildGlossary);

// Подсказки большой тройки: тап по карточке открывает/закрывает (для мобильных)
document.addEventListener("click", (e) => {
  const card = e.target.closest(".big-card.has-tip");
  document.querySelectorAll(".big-card.open").forEach((c) => {
    if (c !== card) c.classList.remove("open");
  });
  if (card) card.classList.toggle("open");
});

// ---------- Вкладки ----------
const BTN_TEXT = {
  natal: "btn_natal",
  transit: "btn_transit",
  synastry: "btn_synastry",
  return: "btn_return",
  progression: "btn_progression",
  calendar: "btn_calendar",
  forecast: "btn_forecast",
  rectification: "btn_rectification",
  vedic: "btn_vedic",
};
function updateCalcBtn() {
  $("calc-btn").querySelector(".btn-text").textContent = t(BTN_TEXT[mode] || "btn_natal");
}
document.querySelectorAll(".tab").forEach((tab) => {
  tab.addEventListener("click", () => {
    document.querySelectorAll(".tab").forEach((el) => el.classList.remove("active"));
    tab.classList.add("active");
    mode = tab.dataset.mode;
    const usesCurrentLocation = ["transit", "calendar", "forecast", "return", "vedic"].includes(mode);
    $("current-location-block").classList.toggle("hidden", !usesCurrentLocation);
    if (usesCurrentLocation) syncCurrentLocationFromBirth(false);
    $("transit-block").classList.toggle("hidden", mode !== "transit");
    $("synastry-block").classList.toggle("hidden", mode !== "synastry");
    $("return-block").classList.toggle("hidden", mode !== "return");
    $("progression-block").classList.toggle("hidden", mode !== "progression");
    $("calendar-block").classList.toggle("hidden", mode !== "calendar");
    $("forecast-block").classList.toggle("hidden", mode !== "forecast");
    $("rect-block").classList.toggle("hidden", mode !== "rectification");
    $("vedic-block").classList.toggle("hidden", mode !== "vedic");
    // Панчангу дата рождения не нужна (персонализация опциональна) — иначе HTML5-валидация молча блокирует форму
    $("birth-date").required = mode !== "vedic";
    $("birth-time").required = mode !== "vedic";
    if (mode === "synastry") populateBSaved();
    if (mode === "return") setupReturnForm();
    // Пример считает именно натал — на других вкладках кнопка сбивает с толку
    $("sample-btn").classList.toggle("hidden", mode !== "natal");
    updateCalcBtn();
  });
});

// ---------- Форма карты возвращения: соляр = год, лунар = месяц ----------
function populateReturnMonths() {
  const sel = $("return-month");
  if (!sel) return;
  const names = LANG === "en" ? MONTHS_EN : MONTHS_RU_NOM2;
  const cur = sel.value || String(new Date().getMonth() + 1);
  sel.innerHTML = names.map((m, i) => `<option value="${i + 1}">${m}</option>`).join("");
  sel.value = cur;
}

function updateReturnForm() {
  const isLunar = $("return-type").value === "Lunar";
  $("return-month-label").classList.toggle("hidden", !isLunar);
  $("return-hint").textContent = isLunar ? t("return_hint_lunar") : t("return_hint_solar");
}

function setupReturnForm() {
  if (!$("return-year").value) $("return-year").value = new Date().getFullYear();
  populateReturnMonths();
  updateReturnForm();
}

$("return-type").addEventListener("change", updateReturnForm);
document.addEventListener("langchange", () => { populateReturnMonths(); updateReturnForm(); });

// Синастрия: выбор второго человека из сохранённых карт (для вошедших).
function populateBSaved() {
  const wrap = $("b-saved-wrap");
  const sel = $("b-saved");
  if (!wrap || !sel) return;
  const profiles = ($("saved-list") && $("saved-list")._profiles) || [];
  if (!getToken() || !profiles.length) { wrap.classList.add("hidden"); return; }
  sel.innerHTML = `<option value="">${t("syn_pick_saved")}</option>` +
    profiles.map((p) => `<option value="${p.id}">${escapeHtml(p.label)}</option>`).join("");
  wrap.classList.remove("hidden");
}
function fillPersonB(d) {
  const pad = (n) => String(n).padStart(2, "0");
  $("b-name").value = d.name && d.name !== "Без имени" && d.name !== "Chart" ? d.name : "";
  if (d.year && d.month && d.day) $("b-date").value = `${d.year}-${pad(d.month)}-${pad(d.day)}`;
  $("b-time").value = `${pad(d.hour ?? 12)}:${pad(d.minute ?? 0)}`;
  $("b-lat").value = d.lat ?? "";
  $("b-lng").value = d.lng ?? "";
  $("b-tz").value = d.tz_str || "";
  $("b-is-dst").value = d.is_dst == null ? "" : String(Boolean(d.is_dst));
  $("b-city").value = d.city || "";
  if (d.lat != null && d.lng != null && !Number.isNaN(parseFloat(d.lat))) {
    showGeoConfirm("b-geo-confirm", d.city, d.lat, d.lng, d.tz_str);
  }
}
$("b-saved").addEventListener("change", function () {
  const profiles = ($("saved-list") && $("saved-list")._profiles) || [];
  const p = profiles.find((x) => x.id === +this.value);
  if (p) fillPersonB(p.data);
});

// ---------- Язык интерфейса ----------
function syncLangButtons() {
  document.querySelectorAll(".lang-opt").forEach((b) => b.classList.toggle("active", b.dataset.lang === LANG));
}
document.querySelectorAll(".lang-opt").forEach((b) => {
  b.addEventListener("click", () => setLang(b.dataset.lang));
});
applyI18n();
syncLangButtons();
updateCalcBtn();

// «Небо сейчас» — общие транзиты планет (публичный эндпоинт, без регистрации).
function skyEsc(s) {
  return String(s == null ? "" : s).replace(/[&<>"]/g, (c) => (
    { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]
  ));
}
function skyDate(iso) {
  if (!iso) return "…";
  const p = iso.split("-");
  return p.length === 3 ? `${p[2]}.${p[1]}.${p[0]}` : iso;
}
function skyMoment(iso, fallback) {
  if (!iso) return skyDate(fallback);
  const dt = new Date(iso);
  if (Number.isNaN(dt.getTime())) return skyDate(fallback);
  return dt.toLocaleString(LANG === "en" ? "en-GB" : "ru-RU", {
    day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit",
  });
}
async function renderSkyNow() {
  const grid = document.getElementById("sky-now-grid");
  if (!grid) return;
  try {
    const browserTz = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
    const r = await fetch(`/api/transits/current?lang=${LANG}&tz=${encodeURIComponent(browserTz)}`);
    if (!r.ok) throw new Error("http " + r.status);
    const data = await r.json();
    grid.innerHTML = (data.transits || []).map((tr) => {
      const retro = tr.retrograde
        ? ` <span class="sky-retro" title="${skyEsc(t("sky_retro"))}">℞</span>` : "";
      return `<article class="sky-card">
        <div class="sky-card-head"><span class="sky-planet">${skyEsc(tr.planet_ru)}</span>${retro}</div>
        <div class="sky-sign">${skyEsc(tr.sign_symbol || "")} ${skyEsc(tr.sign_ru)}</div>
        <div class="sky-period">${skyMoment(tr.since_datetime, tr.since)} — ${skyMoment(tr.until_datetime, tr.until)}</div>
        <p class="sky-meaning">${skyEsc(tr.meaning)}</p>
      </article>`;
    }).join("");
  } catch (e) {
    grid.innerHTML = `<p class="sky-err">${skyEsc(t("sky_err"))}</p>`;
  }
}
renderSkyNow();
document.addEventListener("langchange", renderSkyNow);
(function initVedicMonth() {
  const el = $("vedic-month");
  if (el && !el.value) {
    const d = new Date();
    el.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
  }
})();
(function initCalendarPeriod() {
  const d = new Date();
  const first = new Date(d.getFullYear(), d.getMonth(), 1);
  const last = new Date(d.getFullYear(), d.getMonth() + 1, 0);
  const fmt = (x) => `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, "0")}-${String(x.getDate()).padStart(2, "0")}`;
  if ($("cal-start") && !$("cal-start").value) $("cal-start").value = fmt(first);
  if ($("cal-end") && !$("cal-end").value) $("cal-end").value = fmt(last);
})();
document.addEventListener("langchange", () => {
  syncLangButtons();
  updateCalcBtn();
  refreshLock();
  if (!$("cabinet-modal").classList.contains("hidden")) loadCabinet();
  if (getToken()) loadProfiles();
  if (getToken()) loadDaily();
  if (!$("premium-modal").classList.contains("hidden")) openPremiumModal();
  // Сбросить кэш архетипов и перезагрузить, если раздел открыт.
  const ad = $("archetypes-details");
  if (ad) {
    ad._loaded = false;
    if (ad.open) ad.dispatchEvent(new Event("toggle"));
  }
  // Если результат показан — пересчитываем в новом языке (данные и колесо карты
  // приходят с сервера на языке расчёта).
  if (lastData && !$("results").classList.contains("hidden")) {
    $("birth-form").requestSubmit();
  }
});

// ---------- Автодополнение города (переиспользуемое) ----------
function showGeoConfirm(confirmId, name, lat, lng, tz) {
  if (!confirmId) return;
  const el = $(confirmId);
  if (!el) return;
  const short = (name || "").split(",")[0];
  el.innerHTML =
    `<span class="geo-check">✓</span><span>${t("place_set")} <b>${escapeHtml(short)}</b>` +
    `<span class="geo-meta">${(+lat).toFixed(4)}, ${(+lng).toFixed(4)} · ${tz || t("tz_detected")}</span></span>`;
  el.classList.remove("hidden");
}

function setupAutocomplete(inputId, resultsId, latId, lngId, tzId, confirmId, onSelect = null) {
  const input = $(inputId);
  const results = $(resultsId);
  let timer = null;

  input.addEventListener("input", () => {
    const q = input.value.trim();
    $(latId).value = "";
    $(lngId).value = "";
    $(tzId).value = "";
    if (confirmId && $(confirmId)) $(confirmId).classList.add("hidden");
    clearTimeout(timer);
    if (q.length < 2) {
      results.classList.add("hidden");
      return;
    }
    timer = setTimeout(async () => {
      try {
        const r = await fetch(`/api/geocode?q=${encodeURIComponent(q)}&lang=${LANG}`);
        if (!r.ok) throw new Error();
        const items = await r.json();
        if (!items.length) {
          results.classList.add("hidden");
          return;
        }
        results.innerHTML = items.map((it, i) => `<li data-i="${i}">${escapeHtml(it.display_name)}</li>`).join("");
        results._items = items;
        results.classList.remove("hidden");
      } catch {
        results.classList.add("hidden");
      }
    }, 450);
  });

  results.addEventListener("click", (e) => {
    const li = e.target.closest("li");
    if (!li) return;
    const it = results._items[+li.dataset.i];
    input.value = it.display_name.split(",")[0];
    $(latId).value = it.lat;
    $(lngId).value = it.lng;
    $(tzId).value = it.tz_str;
    results.classList.add("hidden");
    showGeoConfirm(confirmId, it.display_name, it.lat, it.lng, it.tz_str);
    if (onSelect) onSelect(it);
  });
}

const cityInput = $("city");
let currentLocationTouched = false;

function syncCurrentLocationFromBirth(force = false) {
  if (currentLocationTouched && !force) return;
  const lat = $("lat").value;
  const lng = $("lng").value;
  const tz = $("tz").value;
  $("current-city").value = $("city").value;
  $("current-lat").value = lat;
  $("current-lng").value = lng;
  $("current-tz").value = tz;
  if (lat && lng) showGeoConfirm("current-geo-confirm", $("city").value, lat, lng, tz);
}

function getCurrentLocation(birth) {
  const lat = parseFloat($("current-lat").value);
  const lng = parseFloat($("current-lng").value);
  if (Number.isNaN(lat) || Number.isNaN(lng)) {
    const fallback = { lat: birth.lat, lng: birth.lng, tz_str: birth.tz_str, city: birth.city };
    localStorage.setItem("astro_current_location", JSON.stringify(fallback));
    return fallback;
  }
  const result = {
    lat,
    lng,
    tz_str: $("current-tz").value || birth.tz_str,
    city: $("current-city").value.trim() || birth.city,
  };
  localStorage.setItem("astro_current_location", JSON.stringify(result));
  return result;
}

setupAutocomplete("city", "city-results", "lat", "lng", "tz", "geo-confirm", () => syncCurrentLocationFromBirth(false));
setupAutocomplete("b-city", "b-city-results", "b-lat", "b-lng", "b-tz", "b-geo-confirm");
setupAutocomplete("current-city", "current-city-results", "current-lat", "current-lng", "current-tz", "current-geo-confirm", () => { currentLocationTouched = true; });
$("current-city").addEventListener("input", () => { currentLocationTouched = true; });
["lat", "lng"].forEach((id) => $(id).addEventListener("input", () => {
  $("tz").value = "";
  cityInput.value = "";
  $("geo-confirm").classList.add("hidden");
  syncCurrentLocationFromBirth(false);
}));
$("current-use-birth").addEventListener("click", () => {
  currentLocationTouched = false;
  syncCurrentLocationFromBirth(true);
});

// ---------- Ректификация: список событий ----------
const RECT_EVENT_TYPES = ["", "relationship", "career", "child", "move", "loss", "health"];
function rectTypeOptions(sel = "") {
  return RECT_EVENT_TYPES
    .map((v) => `<option value="${v}"${v === sel ? " selected" : ""}>${t("rect_type_" + (v || "any"))}</option>`)
    .join("");
}
function addRectEventRow(date = "", label = "", type = "") {
  const row = document.createElement("div");
  row.className = "rect-event-row";
  row.innerHTML = `
    <input type="date" class="rect-ev-date" value="${date}" />
    <select class="rect-ev-type">${rectTypeOptions(type)}</select>
    <input type="text" class="rect-ev-label" placeholder="${t("event_ph")}" list="event-suggestions" value="${label}" />
    <button type="button" class="rect-ev-del" title="${t("del")}">×</button>`;
  row.querySelector(".rect-ev-del").addEventListener("click", () => row.remove());
  $("rect-events").appendChild(row);
}
function gatherRectEvents() {
  return Array.from(document.querySelectorAll(".rect-event-row"))
    .map((row) => {
      const date = row.querySelector(".rect-ev-date").value;
      if (!date) return null;
      const [y, m, d] = date.split("-").map(Number);
      return {
        date: { year: y, month: m, day: d },
        label: row.querySelector(".rect-ev-label").value,
        type: row.querySelector(".rect-ev-type").value,
      };
    })
    .filter(Boolean);
}
$("rect-add-event").addEventListener("click", () => addRectEventRow());
for (let i = 0; i < 3; i++) addRectEventRow();

// Подсказки событий в datalist — строятся из словаря, чтобы переводились.
const RECT_SUGGESTION_KEYS = [
  "rect_sugg_wedding", "rect_sugg_child", "rect_sugg_divorce", "rect_sugg_move",
  "rect_sugg_job", "rect_sugg_business", "rect_sugg_property", "rect_sugg_loss",
  "rect_sugg_injury", "rect_sugg_graduation", "rect_sugg_meeting", "rect_sugg_turning",
];
function buildEventSuggestions() {
  const dl = $("event-suggestions");
  if (!dl) return;
  dl.innerHTML = RECT_SUGGESTION_KEYS
    .map((k) => `<option value="${t(k)}"></option>`)
    .join("");
}
// Перерисовать уже созданные строки событий при смене языка (типы, плейсхолдер, кнопка).
function refreshRectEventRows() {
  document.querySelectorAll(".rect-event-row").forEach((row) => {
    const sel = row.querySelector(".rect-ev-type");
    if (sel) sel.innerHTML = rectTypeOptions(sel.value);
    const label = row.querySelector(".rect-ev-label");
    if (label) label.setAttribute("placeholder", t("event_ph"));
    const del = row.querySelector(".rect-ev-del");
    if (del) del.setAttribute("title", t("del"));
  });
}
buildEventSuggestions();
document.addEventListener("langchange", () => { buildEventSuggestions(); refreshRectEventRows(); });

// ---------- Ректификация: переключатель режима ввода времени ----------
document.querySelectorAll('input[name="rect-mode"]').forEach((radio) => {
  radio.addEventListener("change", () => {
    const isWindow = radio.value === "window" && radio.checked;
    $("rect-range-fields").classList.toggle("hidden", isWindow);
    $("rect-window-fields").classList.toggle("hidden", !isWindow);
  });
});

// ---------- Ректификация: анкета по Асценденту ----------
// Каждый пункт — одна группа радио; коды совпадают с backend _RECT_ASC_TRAITS.
const RECT_ASC_QUESTIONS = [
  { key: "temp",   options: ["temp_fire", "temp_earth", "temp_air", "temp_water"] },
  { key: "mode",   options: ["mode_cardinal", "mode_fixed", "mode_mutable"] },
  { key: "look",   options: ["look_bright", "look_solid", "look_slim", "look_soft"] },
  { key: "manner", options: ["manner_leader", "manner_friendly", "manner_reserved", "manner_deep"] },
  { key: "value",  options: ["value_freedom", "value_stability", "value_ideas", "value_feeling"] },
];
function buildRectAscQuestionnaire() {
  const box = $("rect-asc-q");
  if (!box) return;
  const prev = gatherRectAscTraits(); // сохраняем выбор при пересборке (смена языка)
  const chosen = new Set(prev);
  box.innerHTML = RECT_ASC_QUESTIONS.map(
    (q) => `<div class="rect-q">
      <div class="rect-q-title">${t("rq_" + q.key)}</div>
      <div class="rect-q-opts">
        ${q.options
          .map(
            (code) =>
              `<label class="rect-q-opt"><input type="radio" name="rectq_${q.key}" value="${code}"${chosen.has(code) ? " checked" : ""} /> ${t("ro_" + code)}</label>`
          )
          .join("")}
      </div>
    </div>`
  ).join("");
}
document.addEventListener("langchange", buildRectAscQuestionnaire);
function gatherRectAscTraits() {
  return RECT_ASC_QUESTIONS.map((q) => {
    const sel = document.querySelector(`input[name="rectq_${q.key}"]:checked`);
    return sel ? sel.value : null;
  }).filter(Boolean);
}
buildRectAscQuestionnaire();

// ---------- Ректификация: предрасположенности (Этап 3) + знак Солнца родителей ----------
const RECT_PRED_KEYS = ["childless", "manychildren", "earlymarriage", "celibacy", "fame",
  "isolation", "art", "accidents", "emigration", "wealth", "parentloss", "illness"];
const RECT_PRED_RATINGS = ["unknown", "yes_strong", "yes", "no", "no_strong"];
const RECT_SIGNS = [
  ["Ari", "Овен", "Aries"], ["Tau", "Телец", "Taurus"], ["Gem", "Близнецы", "Gemini"],
  ["Can", "Рак", "Cancer"], ["Leo", "Лев", "Leo"], ["Vir", "Дева", "Virgo"],
  ["Lib", "Весы", "Libra"], ["Sco", "Скорпион", "Scorpio"], ["Sag", "Стрелец", "Sagittarius"],
  ["Cap", "Козерог", "Capricorn"], ["Aqu", "Водолей", "Aquarius"], ["Pis", "Рыбы", "Pisces"],
];
function signOptions(sel = "") {
  const none = LANG === "en" ? "— don't know —" : "— не знаю —";
  return `<option value="">${none}</option>` +
    RECT_SIGNS.map(([c, ru, en]) => `<option value="${c}"${c === sel ? " selected" : ""}>${LANG === "en" ? en : ru}</option>`).join("");
}
function gatherRectPredispositions() {
  return Array.from(document.querySelectorAll(".rect-pred-sel"))
    .map((s) => ({ key: s.dataset.key, rating: s.value }))
    .filter((p) => p.rating && p.rating !== "unknown");
}
function gatherParentSunSigns() {
  return [$("rect-parent-mother")?.value, $("rect-parent-father")?.value].filter(Boolean);
}
function buildRectPredispositions() {
  const box = $("rect-pred-list");
  if (!box) return;
  const prev = {};
  gatherRectPredispositions().forEach((p) => { prev[p.key] = p.rating; });
  box.innerHTML = RECT_PRED_KEYS.map(
    (k) => `<div class="rect-pred-row">
      <span class="rp-label">${t("rp_" + k)}</span>
      <select class="rect-pred-sel" data-key="${k}">
        ${RECT_PRED_RATINGS.map((r) => `<option value="${r}"${(prev[k] || "unknown") === r ? " selected" : ""}>${t("rr_" + r)}</option>`).join("")}
      </select>
    </div>`
  ).join("");
  const mo = $("rect-parent-mother"), fa = $("rect-parent-father");
  if (mo) mo.innerHTML = signOptions(mo.value);
  if (fa) fa.innerHTML = signOptions(fa.value);
}
document.addEventListener("langchange", buildRectPredispositions);
buildRectPredispositions();

// Разумные значения по умолчанию для периодов (прогноз, календарь)
(function initDateDefaults() {
  const today = new Date();
  const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  const plusMonths = (m) => {
    const d = new Date(today);
    d.setMonth(d.getMonth() + m);
    return d;
  };
  const setIfEmpty = (id, d) => {
    const el = $(id);
    if (el && !el.value) el.value = iso(d);
  };
  setIfEmpty("fc-start", today);
  setIfEmpty("fc-end", plusMonths(12));
  setIfEmpty("cal-start", today);
  setIfEmpty("cal-end", plusMonths(2));
})();

document.addEventListener("click", (e) => {
  if (!e.target.closest(".autocomplete")) {
    document.querySelectorAll(".suggestions").forEach((s) => s.classList.add("hidden"));
  }
});

// ---------- Сбор данных формы ----------
function getBirthData() {
  const [year, month, day] = $("birth-date").value.split("-").map(Number);
  const [hour, minute] = $("birth-time").value.split(":").map(Number);
  const dstValue = $("birth-is-dst").value;
  return {
    name: $("name").value || (LANG === "en" ? "Chart" : "Без имени"),
    year, month, day, hour, minute,
    lat: parseFloat($("lat").value),
    lng: parseFloat($("lng").value),
    tz_str: $("tz").value || null,
    is_dst: dstValue === "" ? null : dstValue === "true",
    city: cityInput.value || "",
    houses_system: $("houses-system").value,
    zodiac_type: "Tropic",
    lang: LANG,
  };
}

function getPersonB() {
  const [year, month, day] = ($("b-date").value || "").split("-").map(Number);
  const [hour, minute] = ($("b-time").value || "12:00").split(":").map(Number);
  const dstValue = $("b-is-dst").value;
  return {
    name: $("b-name").value || t("person2"),
    year, month, day, hour, minute,
    lat: parseFloat($("b-lat").value),
    lng: parseFloat($("b-lng").value),
    tz_str: $("b-tz").value || null,
    is_dst: dstValue === "" ? null : dstValue === "true",
    city: $("b-city").value || "",
    houses_system: $("houses-system").value,
    zodiac_type: "Tropic",
  };
}

function validate(b, who = "") {
  const suffix = who ? ` (${who})` : "";
  if (!b.year || !b.month || !b.day) return `${t("v_need_date")}${suffix}.`;
  if (Number.isNaN(b.lat) || Number.isNaN(b.lng))
    return `${t("v_need_city")}${suffix}.`;
  return null;
}

// ---------- Отправка ----------
// Пример-карта для новичков: подставляет известные данные рождения и считает.
$("sample-btn").addEventListener("click", () => {
  $("name").value = LANG === "en" ? "Albert Einstein" : "Альберт Эйнштейн";
  $("birth-date").value = "1879-03-14";
  $("birth-time").value = "11:30";
  $("lat").value = "48.4011";
  $("lng").value = "9.9876";
  $("tz").value = "Europe/Berlin";
  cityInput.value = LANG === "en" ? "Ulm, Germany" : "Ульм, Германия";
  currentLocationTouched = false;
  syncCurrentLocationFromBirth(true);
  const cityOk = $("city-confirm");
  if (cityOk) { cityOk.textContent = "✓ " + cityInput.value; cityOk.classList.remove("hidden"); }
  const natalTab = document.querySelector('.tab[data-mode="natal"]');
  if (natalTab) natalTab.click();
  $("birth-form").requestSubmit();
});

// Обратный геокодинг: по введённым координатам определить населённый пункт.
$("rev-geo-btn").addEventListener("click", async () => {
  const lat = parseFloat($("lat").value);
  const lng = parseFloat($("lng").value);
  if (Number.isNaN(lat) || Number.isNaN(lng)) { alert(t("rev_geo_need")); return; }
  const btn = $("rev-geo-btn");
  const orig = btn.textContent;
  btn.disabled = true; btn.textContent = "…";
  try {
    const r = await fetch(`/api/reverse-geocode?lat=${lat}&lng=${lng}&lang=${LANG}`);
    if (!r.ok) throw new Error("rev");
    const d = await r.json();
    if (d.short) cityInput.value = d.short;
    if (d.tz_str) $("tz").value = d.tz_str;
    showGeoConfirm("geo-confirm", d.short, lat, lng, d.tz_str);
    syncCurrentLocationFromBirth(false);
  } catch (e) {
    alert(t("rev_geo_fail"));
  } finally {
    btn.disabled = false; btn.textContent = orig;
  }
});

$("birth-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const birth = getBirthData();
  if (mode !== "vedic") {
    const err = validate(birth);
    if (err) return showError(err);
  }

  showLoader();
  try {
    let data;
    if (mode === "synastry") {
      const personB = getPersonB();
      const errB = validate(personB, t("second_person_v"));
      if (errB) throw new Error(errB);
      // Не-премиум видит бесплатный тизер (индекс + сферы), а не глухой пейволл.
      if (!IS_PREMIUM) {
        const prev = await postJSON("/api/synastry/preview", { person_a: birth, person_b: personB });
        renderSynastryTeaser(prev);
        return; // тизер — не полноценный расчёт: не пишем в историю и lastData
      }
      data = await postJSON("/api/synastry", { person_a: birth, person_b: personB });
      renderSynastry(data);
    } else if (mode === "return") {
      const year = parseInt($("return-year").value, 10);
      if (!year) throw new Error(t("e_return_year"));
      const rtype = $("return-type").value;
      const body = { natal: birth, year, return_type: rtype, location: getCurrentLocation(birth) };
      if (rtype === "Lunar") body.month = parseInt($("return-month").value, 10) || (new Date().getMonth() + 1);
      data = await postJSON("/api/return", body);
      renderReturn(data);
    } else if (mode === "progression") {
      const pd = $("prog-date").value;
      const pt = $("prog-time").value || "12:00";
      if (!pd) throw new Error(t("e_prog_date"));
      const [py, pm, pday] = pd.split("-").map(Number);
      const [ph, pmin] = pt.split(":").map(Number);
      const body = { natal: birth, target_date: { year: py, month: pm, day: pday, hour: ph, minute: pmin } };
      data = await postJSON("/api/progression", body);
      renderProgression(data);
    } else if (mode === "calendar") {
      const s = $("cal-start").value;
      const en = $("cal-end").value;
      if (!s || !en) throw new Error(t("e_period"));
      const [sy, sm, sd] = s.split("-").map(Number);
      const [ey, em, ed] = en.split("-").map(Number);
      const body = {
        natal: birth,
        location: getCurrentLocation(birth),
        start: { year: sy, month: sm, day: sd },
        end: { year: ey, month: em, day: ed },
      };
      data = await postJSON("/api/calendar", body);
      renderCalendar(data);
    } else if (mode === "forecast") {
      const s = $("fc-start").value;
      const en = $("fc-end").value;
      if (!s || !en) throw new Error(t("e_forecast_period"));
      const [sy, sm, sd] = s.split("-").map(Number);
      const [ey, em, ed] = en.split("-").map(Number);
      const body = {
        natal: birth,
        location: getCurrentLocation(birth),
        start: { year: sy, month: sm, day: sd },
        end: { year: ey, month: em, day: ed },
      };
      data = await postJSON("/api/forecast", body);
      renderForecast(data);
    } else if (mode === "rectification") {
      const events = gatherRectEvents();
      const ascTraits = gatherRectAscTraits();
      const predispositions = gatherRectPredispositions();
      const parentSunSigns = gatherParentSunSigns();
      if (!events.length && !ascTraits.length && !predispositions.length) throw new Error(t("e_rect_input"));
      birth.hour = 12;
      birth.minute = 0; // время неизвестно — перебирается на сервере
      const rectMode = (document.querySelector('input[name="rect-mode"]:checked') || {}).value || "range";
      const body = {
        natal: birth,
        events,
        asc_traits: ascTraits,
        predispositions,
        parent_sun_signs: parentSunSigns,
      };
      if (rectMode === "window") {
        const [ch, cm] = ($("rect-center").value || "12:00").split(":").map(Number);
        body.center_minute = ch * 60 + cm;
        body.window_minutes = parseInt($("rect-window").value, 10);
      } else {
        const [sh, sm] = ($("rect-start").value || "00:00").split(":").map(Number);
        const [eh, em] = ($("rect-end").value || "23:59").split(":").map(Number);
        body.start_minute = sh * 60 + sm;
        body.end_minute = eh * 60 + em;
        body.step_minute = parseInt($("rect-step").value, 10);
      }
      data = await postJSON("/api/rectification", body);
      renderRectification(data);
    } else if (mode === "vedic") {
      const mv = $("vedic-month").value; // YYYY-MM
      if (!mv) throw new Error(t("e_month"));
      const [vy, vm] = mv.split("-").map(Number);
      const personalize = $("vedic-personalize").checked;
      if (Number.isNaN(birth.lat) || Number.isNaN(birth.lng))
        throw new Error(t("e_city_loc"));
      const current = getCurrentLocation(birth);
      const body = { year: vy, month: vm, lat: current.lat, lng: current.lng, tz_str: current.tz_str, lang: LANG };
      if (personalize && birth.year && birth.month && birth.day) body.natal = birth;
      data = await postJSON("/api/vedic-calendar", body);
      renderVedic(data);
    } else if (mode === "transit") {
      const td = $("transit-date").value;
      const tt = $("transit-time").value || "12:00";
      if (!td) throw new Error(t("e_transit_date"));
      const [ty, tm, tday] = td.split("-").map(Number);
      const [th, tmin] = tt.split(":").map(Number);
      const body = {
        natal: birth,
        transit_location: getCurrentLocation(birth),
        transit_date: { year: ty, month: tm, day: tday, hour: th, minute: tmin },
      };
      data = await postJSON("/api/transit", body);
      renderTransit(data);
    } else {
      data = await postJSON("/api/natal", birth);
      renderNatal(data);
    }
    lastData = data;
    lastMode = mode; // для перерисовки при смене языка
    recordHistory(birth);
  } catch (ex) {
    if (!ex.paywall) showError(ex.message || t("v_calc_failed")); // пейволл уже показал превью
  }
});

// ---------- История расчётов (для кабинета) ----------
// Поля формы, которые нужно запомнить, чтобы повторить расчёт в один клик.
const MODE_FIELDS = {
  transit: ["transit-date", "transit-time"],
  return: ["return-type", "return-year", "return-month"],
  progression: ["prog-date", "prog-time"],
  calendar: ["cal-start", "cal-end"],
  forecast: ["fc-start", "fc-end"],
  vedic: ["vedic-month", "vedic-personalize"],
};

function recordHistory(birth) {
  if (!getToken()) return;
  const fields = {};
  (MODE_FIELDS[mode] || []).forEach((id) => {
    const el = $(id);
    if (el) fields[id] = el.type === "checkbox" ? el.checked : el.value;
  });
  const params = { birth, fields };
  if (mode === "synastry") params.personB = getPersonB();
  const who = birth.name && birth.name !== "Без имени" ? birth.name : (birth.city || "");
  const label = mode === "synastry" && params.personB && params.personB.name
    ? `${who} + ${params.personB.name}` : who;
  fetch("/api/history", {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ kind: mode, label, params }),
  }).catch(() => {});
}

function repeatHistory(item) {
  const p = item.params || {};
  if (p.birth) fillBirthData(p.birth);
  Object.entries(p.fields || {}).forEach(([id, v]) => {
    const el = $(id);
    if (el) { if (el.type === "checkbox") el.checked = !!v; else el.value = v; }
  });
  if (item.kind === "synastry" && p.personB) fillPersonB(p.personB);
  const tab = document.querySelector(`.tab[data-mode="${item.kind}"]`);
  if (tab) tab.click();
  $("cabinet-modal").classList.add("hidden");
  $("birth-form").requestSubmit();
}

// Карта режим → функция отрисовки (для смены языка)
const RENDER_BY_MODE = {
  natal: (d) => renderNatal(d),
  transit: (d) => renderTransit(d),
  synastry: (d) => renderSynastry(d),
  return: (d) => renderReturn(d),
  progression: (d) => renderProgression(d),
  calendar: (d) => renderCalendar(d),
  forecast: (d) => renderForecast(d),
  rectification: (d) => renderRectification(d),
  vedic: (d) => renderVedic(d),
};

async function postJSON(url, body) {
  const r = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(body),
  });
  if (!r.ok) {
    if ((r.status === 401 || r.status === 402) && !url.startsWith("/api/auth") && !url.startsWith("/api/billing")) {
      // премиум-функция: гостю — вход, пользователю — тарифы
      openPaywall(r.status);
      const e = new Error(t("premium_required"));
      e.paywall = true;
      throw e;
    }
    const detail = await r.json().catch(() => ({}));
    throw new Error(detail.detail || `${t("ui_server_error")} (${r.status})`);
  }
  return r.json();
}

// ---------- Управление отображением ----------
function showLoader() {
  $("placeholder").classList.add("hidden");
  $("error").classList.add("hidden");
  $("results").classList.add("hidden");
  $("daily-block").classList.add("hidden");
  $("loader").classList.remove("hidden");
  $("calc-btn").disabled = true;
}
function showError(msg) {
  $("loader").classList.add("hidden");
  $("placeholder").classList.add("hidden");
  $("daily-block").classList.add("hidden");
  $("results").classList.add("hidden");
  $("error").textContent = "⚠ " + msg;
  $("error").classList.remove("hidden");
  $("result-toolbar").classList.add("hidden"); // нечего печатать при ошибке
  $("calc-btn").disabled = false;
}
function showResults() {
  $("loader").classList.add("hidden");
  $("error").classList.add("hidden");
  $("placeholder").classList.add("hidden");
  $("daily-block").classList.add("hidden");
  $("results").classList.remove("hidden");
  $("result-toolbar").classList.toggle("hidden", !getToken()); // печать — для вошедших
  $("calc-btn").disabled = false;
  $("interp-section").classList.add("hidden"); // показываем только для натала
  // По умолчанию — раскладка «карта + таблицы»; календарь переключит сам.
  $("chart-area").classList.remove("hidden");
  $("chart-svg").classList.remove("hidden"); // сбрасываем скрытие колеса (тизер синастрии его прячет)
  $("data-grids").classList.remove("hidden");
  $("calendar-view").classList.add("hidden");
  $("forecast-view").classList.add("hidden");
  $("rect-view").classList.add("hidden");
  $("vedic-view").classList.add("hidden");
  $("portrait").classList.add("hidden");
  $("psych").classList.add("hidden");
  $("spheres").classList.add("hidden");
  $("deep-report-btn").classList.add("hidden");
  $("simple-toggle-row").classList.add("hidden"); // переключатель показывает только натал
  $("guest-teaser").classList.add("hidden"); // тизер — только под наталом гостю
  $("locked-preview").classList.add("hidden"); // размытое превью — только при пейволле
}

// Переключатель «простыми словами» — перерисовывает натал в упрощённом виде.
$("simple-mode").addEventListener("change", function () {
  simpleMode = this.checked;
  if (lastNatalData) renderNatal(lastNatalData);
});

function interpFullHtml(p) {
  if (!p.interp_full || !p.interp_full.length) return "";
  const secs = p.interp_full
    .map((s) => `<p class="interp-sec"><strong>${s.label}.</strong> ${s.text}</p>`)
    .join("");
  return `<details class="interp-more">
      <summary>${t("read_more")}</summary>
      <div class="interp-more-body">${secs}</div>
    </details>`;
}

function interpCards(planets, simple = false) {
  return planets
    .filter((p) => p.interp_sign || p.interp_house || p.interp_plain)
    .map((p) => {
      if (simple) {
        const where = p.house_sphere ? `, «${p.house_sphere}»` : "";
        const blocks = (p.interp_full_plain && p.interp_full_plain.length)
          ? p.interp_full_plain.map((s) => `<p class="interp-sec"><strong>${s.label}.</strong> ${s.text}</p>`).join("")
          : (p.interp_plain ? `<p>${p.interp_plain}</p>` : "");
        return `<div class="interp-card">
          <div class="interp-head"><span class="glyph">${p.symbol}</span>${p.name_ru} — ${p.sign_ru}${where}</div>
          ${blocks}
        </div>`;
      }
      return `<div class="interp-card">
          <div class="interp-head"><span class="glyph">${p.symbol}</span>${p.name_ru} — ${p.sign_ru}${p.house_num ? `, ${houseFull(p.house_num)}` : ""}</div>
          ${p.interp_sign ? `<p>${p.interp_sign}</p>` : ""}
          ${p.interp_house ? `<p>${p.interp_house}</p>` : ""}
          ${interpFullHtml(p)}
        </div>`;
    })
    .join("");
}

// Вводные пояснения техник
const MODE_INTRO = {
  get transit() { return t("intro_transit"); },
  get return() { return t("intro_return"); },
  get progression() { return t("intro_progression"); },
  get synastry() { return t("intro_synastry"); },
};

// Одна карточка аспекта. swap=true показывает действующую планету (p2) первой.
function aspectCard(a, swap = false) {
  const cls = FC_NATURE[a.nature] || "";
  const first = swap
    ? `<span class="glyph">${a.p2_symbol}</span>${a.p2_ru}`
    : `<span class="glyph">${a.p1_symbol}</span>${a.p1_ru}`;
  const second = swap
    ? `<span class="glyph">${a.p1_symbol}</span>${a.p1_ru}`
    : `<span class="glyph">${a.p2_symbol}</span>${a.p2_ru}`;
  return `<div class="fc-event ${cls}">
    <div class="fc-event-head">
      ${first}
      <span>${a.aspect_symbol} ${a.aspect_ru}</span>
      ${second}
      <span class="fc-event-date">${t("orb")} ${a.orbit}°</span>
    </div>
    <p>${a.interp}</p>
  </div>`;
}

// Карточки трактовок из списка аспектов (для транзитов, прогрессий, синастрии).
// swap=true показывает действующую планету (p2) первой — для транзитов/прогрессий.
function aspectInterpCards(aspects, { limit = 24, swap = false } = {}) {
  const list = aspects
    .filter((a) => a.interp)
    .slice()
    .sort((a, b) => a.orbit - b.orbit)
    .slice(0, limit);
  if (!list.length) return `<p class="section-note">${t("no_aspects")}</p>`;
  return list.map((a) => aspectCard(a, swap)).join("");
}

// Аспекты, сгруппированные по тону (для прогрессий — структурно и понятно).
function toneGroupedCards(aspects, swap = false) {
  const good = [], tense = [], other = [];
  aspects
    .filter((a) => a.interp)
    .slice()
    .sort((a, b) => a.orbit - b.orbit)
    .forEach((a) => {
      if (a.nature === "harmonious") good.push(a);
      else if (a.nature === "tense") tense.push(a);
      else other.push(a);
    });
  if (!good.length && !tense.length && !other.length) return `<p class="section-note">${t("no_aspects")}</p>`;
  const block = (title, arr, cls) =>
    arr.length
      ? `<div class="tr-block"><h4 class="tr-h ${cls}">${title}</h4>${arr.slice(0, 12).map((a) => aspectCard(a, swap)).join("")}</div>`
      : "";
  return (
    block(t("pg_harm"), good, "good") +
    block(t("pg_tense"), tense, "tense") +
    block(t("pg_conj"), other, "")
  );
}

// Одна карточка влияния транзитного обзора (из overview).
function trCard(i) {
  const cls = i.tone === "good" ? "tag-good" : i.tone === "tense" ? "tag-bad" : "tag-neutral";
  return `<div class="fc-event ${cls}">
    <div class="fc-event-head">
      <span class="glyph">${i.t_symbol}</span>${i.t_name}
      <span>${i.aspect_symbol} ${i.aspect}</span>
      <span class="glyph">${i.n_symbol}</span>${i.n_name}
      <span class="fc-event-date">${t("tr_int_" + i.intensity)} · ${t("orb")} ${i.orbit}°</span>
    </div>
    <p>${i.interp}</p>
  </div>`;
}

// Структурированный обзор транзитов: шапка + главные влияния + группы по сферам.
function transitOverview(ov) {
  if (!ov) return "";
  let h = `<p class="section-note">${MODE_INTRO.transit}</p>`;
  h += `<p class="tr-headline">${ov.headline}</p>`;
  if (ov.key && ov.key.length) {
    h += `<div class="tr-block"><h4 class="tr-h key">⭐ ${t("tr_key_title")}</h4>` +
      ov.key.map(trCard).join("") + `</div>`;
  }
  (ov.groups || []).forEach((g) => {
    h += `<div class="tr-sphere tone-${g.tone}"><h4 class="tr-sphere-h">${g.icon} ${g.label}` +
      ` <span class="tr-tone-tag tone-${g.tone}">${t("tr_tone_" + g.tone)}</span></h4>` +
      g.items.map(trCard).join("");
    if (g.count > g.items.length) {
      h += `<p class="tr-more">${t("tr_more").replace("{n}", g.count - g.items.length)}</p>`;
    }
    h += `</div>`;
  });
  return h;
}

// Главные акценты прогрессий: эмоциональная глава (Луна) + жизненный этап (Солнце).
function progressionHighlights(hl) {
  if (!hl) return "";
  let out = `<div class="prog-highlights">`;
  const m = hl.prog_moon, s = hl.prog_sun;
  if (m) {
    out += `<div class="prog-hl-card moon"><h4>🌙 ${t("prog_moon_title")}</h4>` +
      `<p class="prog-hl-sub"><span class="glyph">${m.sign_symbol}</span> ${m.sign_ru}` +
      `${m.house_num ? ` · ${houseFull(m.house_num)}` : ""}</p><p>${m.text}</p></div>`;
  }
  if (s) {
    const badge = s.changed ? ` <span class="prog-changed">${t("prog_sun_changed")}</span>` : "";
    out += `<div class="prog-hl-card sun"><h4>☀️ ${t("prog_sun_title")}${badge}</h4>` +
      `<p class="prog-hl-sub"><span class="glyph">${s.sign_symbol}</span> ${s.sign_ru}</p><p>${s.text}</p></div>`;
  }
  out += `</div>`;
  return out;
}

function showInterpSection(title, html) {
  $("interp-section-title").textContent = title;
  $("interp-list").innerHTML = html;
  $("interp-section").classList.remove("hidden");
}

// Увеличение карты по клику (лайтбокс) с зумом
let chartZoom = 1;
const CHART_ZOOM_MIN = 1, CHART_ZOOM_MAX = 6;

function chartFitSize() {
  const cont = $("chart-modal-svg");
  const svg = cont.querySelector("svg");
  if (!svg) return { w: 0, h: 0 };
  const vb = (svg.getAttribute("viewBox") || "").split(/[\s,]+/).map(Number);
  let ratio = vb.length === 4 && vb[3] ? vb[2] / vb[3] : (svg.clientWidth / svg.clientHeight) || 1;
  const cw = cont.clientWidth, ch = cont.clientHeight;
  let w = cw, h = cw / ratio;
  if (h > ch) { h = ch; w = ch * ratio; }
  return { w, h };
}

function applyChartZoom() {
  const svg = $("chart-modal-svg").querySelector("svg");
  if (!svg) return;
  if (chartZoom <= 1) {
    svg.style.maxWidth = "100%";
    svg.style.maxHeight = "100%";
    svg.style.width = "auto";
    svg.style.height = "auto";
  } else {
    const { w, h } = chartFitSize();
    svg.style.maxWidth = "none";
    svg.style.maxHeight = "none";
    svg.style.width = w * chartZoom + "px";
    svg.style.height = h * chartZoom + "px";
  }
}

function setChartZoom(z) {
  chartZoom = Math.min(CHART_ZOOM_MAX, Math.max(CHART_ZOOM_MIN, z));
  applyChartZoom();
}

function openChartModal() {
  const svg = $("chart-svg").innerHTML;
  if (!svg.trim()) return;
  $("chart-modal-svg").innerHTML = svg;
  $("chart-modal").classList.remove("hidden");
  chartZoom = 1;
  applyChartZoom();
}
function closeChartModal() { $("chart-modal").classList.add("hidden"); }

$("chart-svg").addEventListener("click", openChartModal);
$("chart-modal-close").addEventListener("click", closeChartModal);
$("chart-zoom-in").addEventListener("click", () => setChartZoom(chartZoom + 0.5));
$("chart-zoom-out").addEventListener("click", () => setChartZoom(chartZoom - 0.5));
$("chart-zoom-reset").addEventListener("click", () => setChartZoom(1));
$("chart-modal-svg").addEventListener("wheel", (e) => {
  e.preventDefault();
  setChartZoom(chartZoom + (e.deltaY < 0 ? 0.3 : -0.3));
}, { passive: false });
$("chart-modal").addEventListener("click", (e) => {
  if (e.target.id === "chart-modal") closeChartModal();
});
document.addEventListener("keydown", (e) => {
  if ($("chart-modal").classList.contains("hidden")) return;
  if (e.key === "Escape") closeChartModal();
  else if (e.key === "+" || e.key === "=") setChartZoom(chartZoom + 0.5);
  else if (e.key === "-") setChartZoom(chartZoom - 0.5);
});

// ---------- Хелперы рендера ----------
function pos(p) {
  const retro = p.retrograde ? ` <span class="retro" title="${t("gl_retro_tip")}">R</span>` : "";
  return `${p.deg}°${String(p.min).padStart(2, "0")}′ <span class="glyph">${p.sign_symbol}</span> ${p.sign_ru}${retro}`;
}

// Характер аспекта — стабильный код (RU/EN независимо)
const NATURE_CLASS = {
  harmonious: "tag-good",
  tense: "tag-bad",
  neutral: "tag-neutral",
  weak: "tag-neutral",
  creative: "tag-creative",
};

const DIGNITY_STRONG = ["domicile", "exaltation"];
function dignityBadge(p) {
  if (!p.dignity) return "";
  const cls = DIGNITY_STRONG.includes(p.dignity_code) ? "dignity-strong" : "dignity-weak";
  const tip = t("gl_dig_" + (p.dignity_code || "")) || t("gl_dig");
  return ` <span class="dignity-badge ${cls}" title="${tip}">${p.dignity}</span>`;
}

function planetsTable(planets, showDignity = false, simple = false) {
  let rows = `<tr><th>${t("col_planet")}</th><th>${t("col_position")}</th><th>${simple ? t("col_sphere") : t("col_house")}</th></tr>`;
  for (const p of planets) {
    const houseCell = simple
      ? `<span style="font-size:12px;color:var(--text-dim)">${p.house_sphere || "—"}</span>`
      : (p.house_num ? houseShort(p.house_num) : "—");
    rows += `<tr>
      <td><span class="glyph">${p.symbol}</span>${p.name_ru}${showDignity && !simple ? dignityBadge(p) : ""}</td>
      <td>${pos(p)}</td>
      <td>${houseCell}</td>
    </tr>`;
  }
  return rows;
}

const CROSS_PLAIN = {
  ru: { cardinal: "Инициатива", fixed: "Устойчивость", mutable: "Гибкость" },
  en: { cardinal: "Initiative", fixed: "Stability", mutable: "Flexibility" },
};
function balanceBars(profile, simple = false) {
  const ed = profile.element_distribution || {};
  const qd = profile.quality_distribution || {};
  const els = ["fire", "earth", "air", "water"];
  const qs = ["cardinal", "fixed", "mutable"];
  const qLbl = (k) => (simple ? (CROSS_PLAIN[LANG === "en" ? "en" : "ru"][k] || elLbl(k)) : elLbl(k));
  const row = (label, cls, pct) =>
    `<div class="bar-row"><span>${label}</span><div class="bar-track"><div class="bar-fill ${cls}" style="width:${pct}%"></div></div><span class="bar-val">${pct}%</span></div>`;
  const elHtml = els.map((k) => row(elLbl(k), k, ed[k + "_percentage"] || 0)).join("");
  const qHtml = qs.map((k) => row(qLbl(k), "q", qd[k + "_percentage"] || 0)).join("");
  const qTitle = simple ? (LANG === "en" ? "Style of action" : "Стиль действия") : t("qualities");
  return `<div class="balance-grid"><div class="balance-col"><h4>${t("elements")}</h4>${elHtml}</div><div class="balance-col"><h4>${qTitle}</h4>${qHtml}</div></div>`;
}

function buildSpheres(s) {
  if (!s) return "";
  const card = (icon, title, text, note) =>
    `<div class="portrait-card"><h3>${icon} ${title}</h3><p>${text}</p>${note ? `<p class="section-note" style="margin-top:8px">${note}</p>` : ""}</div>`;
  return (
    card("💗", t("sph_love"), s.love) +
    card("💼", t("sph_career"), s.career) +
    card("🌿", t("sph_health"), s.health, t("sph_disclaimer"))
  );
}

function buildPsych(psych) {
  if (!psych) return "";
  let html = `<div class="portrait-card psych-card"><h3>🧠 ${t("psych_title")}</h3>`;
  if (psych.temperament) {
    html += `<div class="psych-block"><b>${t("psych_temperament")}: ${psych.temperament.name}</b><p>${psych.temperament.text}</p></div>`;
  }
  if (psych.dominant) {
    const d = psych.dominant;
    const houseStr = d.house_num ? `, ${houseFull(d.house_num)}` : "";
    html += `<div class="psych-block"><b>${t("psych_dominant")}: <span class="glyph">${d.symbol}</span>${d.name_ru} — ${d.sign_ru}${houseStr}</b><p>${d.text}</p></div>`;
  }
  if (psych.axes && psych.axes.length) {
    html += `<div class="psych-block"><b>${t("psych_axes")}</b><ul class="psych-axes">`;
    psych.axes.forEach((a) => { html += `<li><span class="pa-label">${a.label}.</span> ${a.text}</li>`; });
    html += `</ul></div>`;
  }
  if (psych.missing && psych.missing.length) {
    html += `<div class="psych-block"><b>${t("psych_missing")}</b>`;
    psych.missing.forEach((m) => { html += `<p><b>${m.element}:</b> ${m.text}</p>`; });
    html += `</div>`;
  }
  if (psych.self_esteem) {
    html += `<div class="psych-block"><b>${t("psych_esteem")}</b><p>${psych.self_esteem}</p></div>`;
  }
  html += `</div>`;
  return html;
}

function buildPortrait(profile, simple = false) {
  let html = `<div class="portrait-card"><h3>${t("portrait")}</h3><p>${profile.core_text}</p></div>`;
  if (profile.ruler) {
    const r = profile.ruler;
    const houseStr = r.house_num ? `, ${houseFull(r.house_num)}` : "";
    const line = t("ruler_line")
      .replace("{asc}", r.asc_sign_ru)
      .replace("{ruler}", r.name_ru)
      .replace("{sign}", r.sign_ru)
      .replace("{house}", houseStr);
    let coLine = "";
    if (r.coruler) {
      const c = r.coruler;
      const ch = c.house_num ? `, ${houseFull(c.house_num)}` : "";
      coLine = `<div class="ruler-coruler">${t("coruler_line")
        .replace("{coruler}", `${c.symbol} ${c.name_ru}`)
        .replace("{sign}", c.sign_ru)
        .replace("{house}", ch)}</div>`;
    }
    html += `<div class="portrait-card"><h3>${t("chart_ruler")}</h3>
      <div class="ruler-line"><span class="glyph">${r.symbol}</span><span>${line}</span></div>${coLine}</div>`;
  }
  html += `<div class="portrait-card"><h3>${t("balance")}</h3>${balanceBars(profile, simple)}<p style="margin-top:14px">${profile.balance.text}</p></div>`;
  return html;
}

function housesTable(houses, simple = false) {
  let rows = simple
    ? `<tr><th>${t("col_sphere")}</th><th>${t("col_cusp")}</th></tr>`
    : `<tr><th>${t("col_house")}</th><th>${t("col_cusp")}</th><th>${t("col_sphere")}</th></tr>`;
  for (const h of houses) {
    if (simple) {
      rows += `<tr>
        <td>${h.sphere || h.meaning || ""}</td>
        <td>${pos(h)}</td>
      </tr>`;
    } else {
      rows += `<tr>
        <td>${houseShort(h.house_num)}</td>
        <td>${pos(h)}</td>
        <td style="color:var(--text-dim);font-size:12.5px">${h.meaning || ""}</td>
      </tr>`;
    }
  }
  return rows;
}

function aspectsTable(aspects, p1h, p2h) {
  if (!p1h) p1h = t("col_p1");
  if (!p2h) p2h = t("col_p2");
  let rows = `<tr><th>${p1h}</th><th>${t("col_aspect")}</th><th>${p2h}</th><th>${t("col_orb")}</th><th></th></tr>`;
  for (const a of aspects) {
    const cls = NATURE_CLASS[a.nature] || "tag-neutral";
    const title = a.interp ? ` title="${a.interp.replace(/"/g, "&quot;")}"` : "";
    rows += `<tr${title}>
      <td><span class="glyph">${a.p1_symbol}</span>${a.p1_ru}</td>
      <td class="${cls}">${a.aspect_symbol} ${a.aspect_ru}</td>
      <td><span class="glyph">${a.p2_symbol}</span>${a.p2_ru}</td>
      <td>${a.orbit}°</td>
      <td style="color:var(--text-dim);font-size:12px">${a.movement}</td>
    </tr>`;
  }
  return rows;
}

function bigThree(planets, houses, bt) {
  const sun = planets.find((p) => p.name === "Sun");
  const moon = planets.find((p) => p.name === "Moon");
  const asc = houses[0];
  const card = (glyph, who, signRu, info) => {
    const tip = info && info.text ? `<div class="bt-tip">${info.text}</div>` : "";
    return `<div class="big-card${info ? " has-tip" : ""}" tabindex="0"><div class="glyph">${glyph}</div><div class="who">${who}</div><div class="sign">${signRu}</div>${tip}</div>`;
  };
  let html = `<div class="big-three">`;
  if (sun) html += card("☉", t("lum_sun"), sun.sign_ru, bt && bt.sun);
  if (moon) html += card("☽", t("lum_moon"), moon.sign_ru, bt && bt.moon);
  if (asc) html += card("ASC", t("lum_asc"), asc.sign_ru, bt && bt.asc);
  html += `</div>`;
  return html;
}

function summaryBlock(meta, lunar, extraRows = []) {
  const placeCity = meta.city && meta.city !== "—" ? escAttr(meta.city) : "";
  let rows = [
    [t("sum_datetime"), formatDateTime(meta.local_datetime)],
  ];
  if (placeCity) rows.push([t("sum_place"), placeCity]);
  rows.push([t("sum_coords"), `${meta.lat}, ${meta.lng}`]);
  rows.push([t("sum_tz"), meta.tz_str]);
  rows.push([t("sum_houses"), meta.houses_system]);
  if (lunar) rows.push([t("sum_moon"), `${lunar.emoji} ${lunar.name_ru}`]);
  rows = rows.concat(extraRows);
  return rows
    .map(
      (r) =>
        `<div class="summary-row"><span class="label">${r[0]}</span><span class="value">${r[1]}</span></div>`
    )
    .join("");
}

// Секта (день/ночь) и Жребий Фортуны — традиционная классика.
function essentialsBlock(ess) {
  if (!ess) return "";
  const sect = ess.sect || {};
  const lot = ess.lot_fortune || {};
  const sectLabel = sect.is_day ? t("ess_day") : t("ess_night");
  const sectIcon = sect.is_day ? "☀️" : "🌙";
  return `<div class="ess-block">
    <div class="ess-item">
      <div class="ess-head">${sectIcon} ${t("ess_sect")}: <b>${sectLabel}</b></div>
      <div class="ess-text">${sect.text || ""}</div>
    </div>
    <div class="ess-item">
      <div class="ess-head">⊗ ${t("ess_fortune")}: <b>${lot.symbol || ""} ${lot.sign_ru || ""} ${lot.deg != null ? lot.deg + "°" : ""}${lot.house_num ? ", " + houseFull(lot.house_num) : ""}</b></div>
      <div class="ess-text">${lot.text || ""}</div>
    </div>
  </div>`;
}

const MONTHS_RU = [
  "января", "февраля", "марта", "апреля", "мая", "июня",
  "июля", "августа", "сентября", "октября", "ноября", "декабря",
];
const MONTHS_EN = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
];
const MONTHS_RU_NOM2 = [
  "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
  "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
];

// Английский порядковый номер и ярлыки дома
function ordEn(n) {
  const s = ["th", "st", "nd", "rd"], v = n % 100;
  return n + (s[(v - 20) % 10] || s[v] || s[0]);
}
function houseFull(n) {
  return LANG === "en" ? `${ordEn(n)} house` : `${n}-й дом`;
}
function houseShort(n) {
  return LANG === "en" ? ordEn(n) : `${n}-й`;
}
const ELEMENT_LBL = {
  ru: { fire: "Огонь", earth: "Земля", air: "Воздух", water: "Вода", cardinal: "Кардин.", fixed: "Фиксир.", mutable: "Мутаб." },
  en: { fire: "Fire", earth: "Earth", air: "Air", water: "Water", cardinal: "Cardinal", fixed: "Fixed", mutable: "Mutable" },
};
const elLbl = (k) => (ELEMENT_LBL[LANG] || ELEMENT_LBL.ru)[k];

// Показываем МЕСТНОЕ время места рождения как есть (не переводим в пояс зрителя).
function formatDateTime(iso) {
  if (!iso) return "—";
  const m = iso.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
  if (!m) return iso.replace("T", " ").slice(0, 16);
  const [, y, mo, d, h, mi] = m;
  if (LANG === "en") return `${MONTHS_EN[+mo - 1]} ${+d}, ${y}, ${h}:${mi}`;
  return `${+d} ${MONTHS_RU[+mo - 1]} ${y} г. в ${h}:${mi}`;
}
function formatDate(dateStr) {
  const m = (dateStr || "").match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (!m) return dateStr || "—";
  const [, y, mo, d] = m;
  if (LANG === "en") return `${MONTHS_EN[+mo - 1]} ${+d}, ${y}`;
  return `${+d} ${MONTHS_RU[+mo - 1]} ${y}`;
}

// Дата + время платежа: created_at в UTC → переводим в пояс смотрящего (админ-таблица).
// ВНИМАНИЕ: не называть это formatDateTime — иначе перекроет натальную версию выше и
// время рождения начнёт сдвигаться в пояс браузера (см. formatDateTime на строке ~1489).
function formatPayDateTime(iso) {
  const dt = new Date(iso);
  if (isNaN(dt)) return formatDate(iso);
  const loc = LANG === "en" ? "en-GB" : "ru-RU";
  return `${dt.toLocaleDateString(loc)} ${dt.toLocaleTimeString(loc, { hour: "2-digit", minute: "2-digit" })}`;
}
// Человеко-понятные статусы платежа ЮKassa (в админке вместо сырых pending/succeeded).
const PAY_STATUS = {
  ru: { pending: "ожидает оплаты", waiting_for_capture: "ожидает подтверждения", succeeded: "оплачено", canceled: "отменён", refunded: "возвращён" },
  en: { pending: "awaiting payment", waiting_for_capture: "awaiting capture", succeeded: "paid", canceled: "canceled", refunded: "refunded" },
};
function payStatusLabel(status) {
  return (PAY_STATUS[LANG === "en" ? "en" : "ru"] || {})[status] || status;
}

// ---------- Рендер: натальная карта ----------
// «Просто о себе» — тематический рассказ без терминов.
function buildStory(story) {
  if (!story) return "";
  let h = `<p class="story-intro">${t("story_intro")}</p>`;
  (story.sections || []).forEach((s) => {
    h += `<div class="story-card"><h3>${s.title}</h3><p>${s.text}</p></div>`;
  });
  const tr = story.traits;
  if (tr && ((tr.pros && tr.pros.length) || (tr.cons && tr.cons.length))) {
    const list = (arr) => arr.map((x) => `<li>${x}</li>`).join("");
    h += `<div class="story-card story-traits"><h3>${t("story_traits")}</h3><div class="traits-cols">` +
      `<div class="traits-col pros"><h4>${t("story_pros")}</h4><ul>${list(tr.pros || [])}</ul></div>` +
      `<div class="traits-col cons"><h4>${t("story_cons")}</h4><ul>${list(tr.cons || [])}</ul></div>` +
      `</div></div>`;
  }
  if (story.links && story.links.length) {
    h += `<div class="story-card story-links"><h3>${t("story_links")}</h3>`;
    story.links.forEach((l) => {
      h += `<p class="story-link ${l.tone}"><span>${l.tone === "tense" ? "⚡" : "✓"}</span> ${l.text}</p>`;
    });
    h += `</div>`;
  }
  return h;
}

let lastNatalData = null;
function renderNatal(data) {
  lastNatalData = data;
  showResults();
  $("guest-teaser").classList.toggle("hidden", !!getToken()); // гостю — тизер платных разделов
  $("simple-toggle-row").classList.remove("hidden"); // переключатель «Просто о себе» только в натале
  const simple = simpleMode;
  renderChart(data);

  // ── Режим «Просто о себе»: тематический рассказ без терминов, без таблиц ──
  if (simple && data.story) {
    ["portrait", "psych", "spheres", "data-grids", "deep-report-btn", "interp-section"]
      .forEach((id) => $(id) && $(id).classList.add("hidden"));
    $("summary").innerHTML =
      `<h3>${escAttr(data.meta.name)}</h3>` +
      `<p class="story-meta">${escAttr(data.meta.city || "")}${data.meta.city ? " · " : ""}${formatDateTime(data.meta.local_datetime)}</p>`;
    $("story").innerHTML = buildStory(data.story);
    $("story").classList.remove("hidden");
    deepReportData = data;
    return;
  }
  $("story").classList.add("hidden");
  $("data-grids").classList.remove("hidden");

  $("planets-title").textContent = t("sec_planets");
  $("aspects-title").textContent = t("sec_aspects");
  $("houses-section").classList.remove("hidden");

  const allPoints = data.planets.concat(data.angles || []);
  $("summary").innerHTML =
    `<h3>${escAttr(data.meta.name)}</h3>` +
    bigThree(data.planets, data.houses, data.big_three) +
    summaryBlock(data.meta, data.lunar_phase) +
    essentialsBlock(data.essentials);

  // Все секции показываем всегда; в простом режиме меняется только язык трактовок и дома→сферы.
  if (data.profile) {
    $("portrait").innerHTML = buildPortrait(data.profile, simple);
    $("portrait").classList.remove("hidden");
  }
  if (data.psych) {
    $("psych").innerHTML = buildPsych(data.psych);
    $("psych").classList.remove("hidden");
  }
  if (data.spheres) {
    $("spheres").innerHTML = buildSpheres(data.spheres);
    $("spheres").classList.remove("hidden");
  }

  $("planets-table").innerHTML = planetsTable(allPoints, true, simple);
  $("houses-section-title").textContent = t("sec_houses");
  $("houses-table").innerHTML = housesTable(data.houses, simple);
  $("aspects-table").innerHTML = aspectsTable(data.aspects);

  showInterpSection(t("sec_interp"), interpCards(data.planets, simple));

  $("deep-report-btn").classList.remove("hidden");
  deepReportData = data;
}

// ---------- Подробный разбор (для углублённого изучения) ----------
let deepReportData = null;

function buildDeepReport(data) {
  const m = data.meta;
  let h = `<h1>${escAttr(m.name)}</h1><div class="rc-sub">${formatDateTime(m.local_datetime)}${m.city ? " · " + escAttr(m.city) : ""} · ${m.lat}, ${m.lng}</div>`;

  const deep = data.deep || {};

  h += `<h2>${t("dr_core")}</h2>`;
  if (data.big_three) {
    ["sun", "moon", "asc"].forEach((k) => {
      const b = data.big_three[k];
      if (b && b.text) h += `<p>${b.text}</p>`;
    });
  }
  if (deep.lunar_phase && deep.lunar_phase.text) {
    h += `<h3>${t("dr_lunar")}</h3><p>${deep.lunar_phase.text}</p>`;
  }

  if (data.profile && data.profile.balance) {
    h += `<h2>${t("dr_balance")}</h2><p>${data.profile.balance.text}</p>`;
    (deep.hemispheres || []).forEach((hm) => { h += `<p>${hm}</p>`; });
  }
  if (data.profile && data.profile.ruler) {
    const r = data.profile.ruler;
    h += `<h2>${t("dr_ruler")}</h2><p><span class="glyph">${r.symbol}</span> ${r.name_ru} — ${r.sign_ru}${r.house_num ? `, ${houseFull(r.house_num)}` : ""}.</p>`;
    if (r.coruler) {
      const c = r.coruler;
      h += `<p>${t("coruler_line").replace("{coruler}", `${c.symbol} ${c.name_ru}`).replace("{sign}", c.sign_ru).replace("{house}", c.house_num ? `, ${houseFull(c.house_num)}` : "")}</p>`;
    }
  }

  // Секта и Жребий Фортуны
  if (data.essentials) {
    const e = data.essentials;
    h += `<h2>${t("ess_title")}</h2>`;
    if (e.sect) h += `<h3>${t("ess_sect")}: ${e.sect.is_day ? t("ess_day") : t("ess_night")}</h3><p>${e.sect.text}</p>`;
    if (e.lot_fortune) h += `<h3>${t("ess_fortune")}: ${e.lot_fortune.symbol} ${e.lot_fortune.sign_ru}${e.lot_fortune.house_num ? ", " + houseFull(e.lot_fortune.house_num) : ""}</h3><p>${e.lot_fortune.text}</p>`;
  }

  // Психологический портрет
  const ps = data.psych;
  if (ps) {
    h += `<h2>🧠 ${t("psych_title")}</h2>`;
    if (ps.temperament) h += `<h3>${t("psych_temperament")}: ${ps.temperament.name}</h3><p>${ps.temperament.text}</p>`;
    if (ps.dominant) h += `<h3>${t("psych_dominant")}: ${ps.dominant.name_ru} — ${ps.dominant.sign_ru}${ps.dominant.house_num ? `, ${houseFull(ps.dominant.house_num)}` : ""}</h3><p>${ps.dominant.text}</p>`;
    if (ps.axes && ps.axes.length) {
      h += `<h3>${t("psych_axes")}</h3>`;
      ps.axes.forEach((a) => { h += `<p><b>${a.label}.</b> ${a.text}</p>`; });
    }
    if (ps.missing && ps.missing.length) {
      h += `<h3>${t("psych_missing")}</h3>`;
      ps.missing.forEach((m) => { h += `<p><b>${m.element}:</b> ${m.text}</p>`; });
    }
    if (ps.self_esteem) h += `<h3>${t("psych_esteem")}</h3><p>${ps.self_esteem}</p>`;
  }

  // Аспектные конфигурации
  if ((deep.patterns || []).length) {
    h += `<h2>${t("dr_patterns")}</h2>`;
    deep.patterns.forEach((p) => {
      h += `<h3>${p.name}: ${p.planets.join(", ")}</h3><p>${p.text}</p>`;
    });
  }
  // Стеллиумы
  if ((deep.stelliums || []).length) {
    h += `<h2>${t("dr_stelliums")}</h2>`;
    deep.stelliums.forEach((s) => {
      h += `<p><b>${s.planets.join(", ")}</b> ${t("dr_stellium_in")} ${s.where}.</p>`;
    });
  }

  h += `<h2>${t("dr_planets")}</h2>`;
  (data.planets || []).forEach((p) => {
    h += `<h3><span class="glyph">${p.symbol}</span>${p.name_ru} — ${p.sign_ru}${p.house_num ? `, ${houseFull(p.house_num)}` : ""}${p.dignity ? ` (${p.dignity})` : ""}${p.retrograde ? " R" : ""}</h3>`;
    if (p.interp_full && p.interp_full.length) {
      // полная статья — все блоки раскрыты (печатный документ)
      p.interp_full.forEach((s) => { h += `<p><b>${s.label}.</b> ${s.text}</p>`; });
      if (p.interp_house) h += `<p>${p.interp_house}</p>`;
    } else {
      if (p.interp_sign) h += `<p>${p.interp_sign}</p>`;
      if (p.interp_house) h += `<p>${p.interp_house}</p>`;
    }
  });

  // Ретроградные планеты
  if ((deep.retrogrades || []).length) {
    h += `<h2>${t("dr_retro")}</h2>`;
    deep.retrogrades.forEach((r) => {
      h += `<div class="rc-asp"><b><span class="glyph">${r.symbol}</span>${r.name_ru} R</b> — ${r.text}</div>`;
    });
  }

  const asp = (data.aspects || []).filter((a) => a.interp);
  if (asp.length) {
    h += `<h2>${t("dr_aspects")}</h2>`;
    asp.forEach((a) => {
      h += `<div class="rc-asp"><b>${a.p1_ru} ${a.aspect_ru} ${a.p2_ru}</b> (${t("orb")} ${a.orbit}°). ${a.interp}</div>`;
    });
  }

  if (data.spheres) {
    h += `<h2>${t("dr_spheres")}</h2>`;
    h += `<h3>${t("dr_love")}</h3><p>${data.spheres.love}</p>`;
    h += `<h3>${t("dr_career")}</h3><p>${data.spheres.career}</p>`;
    h += `<h3>${t("dr_health")}</h3><p>${data.spheres.health}</p>`;
  }

  h += `<p class="info-disclaimer" style="margin-top:18px">${t("dr_disclaimer")}</p>`;
  return h;
}

$("deep-report-btn").addEventListener("click", () => {
  if (!premiumGate()) return;
  if (!deepReportData) return;
  $("deep-content").innerHTML = buildDeepReport(deepReportData);
  $("deep-modal").classList.remove("hidden");
});
$("deep-close").addEventListener("click", () => $("deep-modal").classList.add("hidden"));
$("deep-modal").addEventListener("click", (e) => {
  if (e.target.id === "deep-modal") $("deep-modal").classList.add("hidden");
});
// Печать/PDF через изолированный iframe — надёжно и одинаково в Chrome/Edge/Firefox.
// В iframe подгружается основной стиль сайта (светлая тема) + печатные правки,
// поэтому любой результат (натал, синастрия, прогноз, Панчанг…) выглядит как на сайте.
const PRINT_OVERRIDE = `
  @page { size: A4 portrait; margin: 14mm 14mm 17mm; }
  html, body { background: #fff !important; margin: 0; padding: 0; color: #29253a !important; }
  body { font-family: Manrope, Arial, sans-serif; font-size: 10.5pt; line-height: 1.55; }
  /* Скрыть интерактив и служебное */
  button, .result-toolbar, .lang-switch, .tabs, .modal-close, .report-actions,
  .vedic-actions, #deep-report-btn, .bt-tip, .save-btn, .simple-toggle,
  #guest-teaser, .simple-toggle-row, .chart-zoom-hint { display: none !important; }
  .hidden { display: none !important; }
  /* Раскрыть свёрнутые секции для печати */
  details > summary { list-style: none; }
  details > summary::-webkit-details-marker { display: none; }
  .data-section.collapsible > summary::after,
  .interp-more > summary, .syn-index > summary::before { display: none !important; }
  /* Убрать свечения/тени и тёмные подложки */
  * { text-shadow: none !important; box-shadow: none !important; }
  .modal-overlay { position: static !important; display: block !important; background: #fff !important; backdrop-filter: none !important; }
  .modal { width: 100% !important; max-width: none !important; max-height: none !important; overflow: visible !important; border: none !important; padding: 0 !important; background: #fff !important; }
  /* Карта по центру и не на весь лист */
  .chart-area { display: block !important; }
  .chart-svg { max-width: 660px; margin: 0 auto 14px; }
  .chart-svg svg { width: 100%; height: auto; }
  /* Аккуратные разрывы страниц */
  .syn-item, .interp-card, .fc-event, .vedic-cell, .cal-cell, .big-card, .rc-asp,
  .portrait-card, .summary, tr { break-inside: avoid; page-break-inside: avoid; }
  .data-grids, #portrait, #psych, #spheres { display: block !important; }
  .portrait-card { margin-bottom: 5mm; }
  .interp-card { break-inside: avoid; page-break-inside: avoid; margin-bottom: 4mm; }
  .interp-card > h4, .interp-card > summary { break-after: avoid; page-break-after: avoid; }
  h1, h2, h3, h4 { break-after: avoid; page-break-after: avoid; color: #2a2150 !important; }
  p, li { orphans: 3; widows: 3; }
  table { width: 100% !important; font-size: 8.5pt; }
  .table-wrap { overflow: visible !important; }
  .data-section { margin-top: 8mm; }
  .print-document { max-width: 182mm; margin: 0 auto; }
  .print-cover {
    min-height: 250mm; box-sizing: border-box; display: flex; flex-direction: column;
    justify-content: center; align-items: center; text-align: center; position: relative;
    border: 1px solid #d8cda9; padding: 18mm 14mm; break-after: page; page-break-after: always;
    background: linear-gradient(145deg, #fbf8ff 0%, #fff 48%, #faf6eb 100%) !important;
  }
  .print-cover::before, .print-cover::after {
    content: ""; position: absolute; border: 1px solid #c9a86a; border-radius: 50%;
    width: 122mm; height: 122mm; opacity: .22; left: 50%; top: 42%; transform: translate(-50%, -50%);
  }
  .print-cover::after { width: 92mm; height: 92mm; border-color: #7765c9; opacity: .16; }
  .print-cover-mark { color: #c9a86a; font-size: 31pt; line-height: 1; position: relative; z-index: 1; }
  .print-cover-logo { width: 30mm; height: 30mm; margin-bottom: 5mm; border-radius: 7mm; position: relative; z-index: 1; }
  .print-cover-brand { font: 700 31pt/1.05 "Cormorant Garamond", Georgia, serif; color: #2a2150; position: relative; z-index: 1; }
  .print-cover-domain { color: #7765c9; letter-spacing: 3px; font-size: 9pt; margin-top: 3mm; position: relative; z-index: 1; }
  .print-cover-rule { width: 42mm; border-top: 1.5px solid #c9a86a; margin: 12mm auto 9mm; position: relative; z-index: 1; }
  .print-cover-type { font: 600 23pt/1.15 "Cormorant Garamond", Georgia, serif; color: #4b4168; position: relative; z-index: 1; }
  .print-cover-name { font-size: 16pt; font-weight: 700; margin-top: 4mm; color: #29253a; position: relative; z-index: 1; }
  .print-cover-meta { display: grid; grid-template-columns: 1fr 1fr; gap: 3mm 8mm; width: 128mm; margin-top: 13mm; position: relative; z-index: 1; }
  .print-cover-meta div { border-top: 1px solid #ddd5e9; padding-top: 2.5mm; }
  .print-cover-meta span { display: block; color: #8a8298; font-size: 7.5pt; text-transform: uppercase; letter-spacing: .7px; }
  .print-cover-meta b { display: block; color: #39324d; font-size: 10pt; margin-top: 1mm; }
  .print-cover-note { margin-top: auto; max-width: 135mm; color: #8a8298; font-size: 8pt; position: relative; z-index: 1; }
  .print-toc { break-after: page; page-break-after: always; padding: 12mm 4mm 4mm; }
  .print-toc h1 { font: 700 26pt/1.1 "Cormorant Garamond", Georgia, serif; border-bottom: 2px solid #c9a86a; padding-bottom: 4mm; }
  .print-toc ol { margin: 8mm 0 0; padding: 0; list-style: none; counter-reset: toc; }
  .print-toc li { counter-increment: toc; display: flex; gap: 3mm; padding: 2.5mm 0; border-bottom: 1px solid #ece7f1; color: #39324d; }
  .print-toc li::before { content: counter(toc, decimal-leading-zero); color: #9a7a3a; font-weight: 700; }
  .print-toc .toc-level-3 { padding-left: 8mm; color: #6b6378; font-size: 9pt; }
  .print-legal { margin-top: 10mm; padding: 5mm; border: 1px solid #d8cda9; background: #fbf8ef !important; color: #5f586a; font-size: 8.5pt; break-inside: avoid; }
  .print-legal strong { color: #2a2150; }
  .print-body { padding-top: 2mm; }
  /* Фирменная шапка проекта — как на сайте (золото + фиолет, засечки) */
  .print-brand { text-align: center; border-bottom: 2px solid #c9a86a; padding-bottom: 10px; margin-bottom: 18px; }
  .print-brand-logo { font-size: 22pt; color: #c9a86a; line-height: 1; }
  .print-brand-main { font-family: "Cormorant Garamond", Georgia, serif; font-size: 26pt; font-weight: 700; color: #2a2150; line-height: 1.1; }
  .print-brand-sub { font-size: 9.5pt; letter-spacing: 2px; color: #6b5bc4; margin-top: 2px; }
  .print-brand-doc { font-size: 12pt; color: #444; margin-top: 6px; font-weight: 600; }
  .print-brand-date { font-size: 9pt; color: #999; margin-top: 2px; }
  /* Повторяющийся колонтитул внизу каждой страницы */
  .print-footer { position: fixed; bottom: 0; left: 0; right: 0; text-align: center; font-size: 8pt; color: #aaa; padding-bottom: 2mm; }
`;

function printCover(title, brandTitle, dateStr, meta = {}) {
  const name = escapeHtml(meta.name || (LANG === "en" ? "Personal report" : "Персональный отчёт"));
  const city = escapeHtml(meta.city || "—");
  // The server's local timestamp is already resolved for the birth location.
  // Split its literal components; Date() would convert it to the viewer's timezone.
  const local = String(meta.local_datetime || "").match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})/);
  const birthDate = escapeHtml(local ? local[1] : "—");
  const birthTime = escapeHtml(local ? local[2] : "—");
  const housesName = escapeHtml(meta.houses_system_name || meta.houses_system || "—");
  const labels = LANG === "en"
    ? { date: "Birth date", time: "Birth time", city: "Birth place", houses: "House system", note: "Astrological interpretations describe tendencies and opportunities and do not replace professional medical, financial or legal advice." }
    : { date: "Дата рождения", time: "Время рождения", city: "Место рождения", houses: "Система домов", note: "Астрологические трактовки описывают тенденции и возможности и не заменяют профессиональные медицинские, финансовые или юридические рекомендации." };
  return `<section class="print-cover">
    <div class="print-cover-mark" aria-hidden="true">✦</div>
    <div class="print-cover-brand">${escapeHtml(brandTitle)}</div>
    <div class="print-cover-domain">ASTROSMAP.RU</div>
    <div class="print-cover-rule"></div>
    <div class="print-cover-type">${escapeHtml(title || (LANG === "en" ? "Astrological report" : "Астрологический отчёт"))}</div>
    <div class="print-cover-name">${name}</div>
    <div class="print-cover-meta">
      <div><span>${labels.date}</span><b>${birthDate}</b></div>
      <div><span>${labels.time}</span><b>${birthTime}</b></div>
      <div><span>${labels.city}</span><b>${city}</b></div>
      <div><span>${labels.houses}</span><b>${housesName}</b></div>
    </div>
    <p class="print-cover-note">${labels.note}<br>${dateStr}</p>
  </section>`;
}

function printToc(doc, lang) {
  const headings = Array.from(doc.querySelectorAll(".editorial-report h2"))
    .map((node) => ({ text: node.textContent.trim(), level: node.tagName === "H3" ? 3 : 2 }))
    .filter((item) => item.text);
  if (!headings.length) return "";
  const title = lang === "en" ? "Contents" : "Содержание";
  return `<section class="print-toc"><h1>${title}</h1><ol>${headings
    .map((item) => `<li class="toc-level-${item.level}">${escapeHtml(item.text)}</li>`)
    .join("")}</ol></section>`;
}

function ensurePrintToc(doc, lang) {
  if (!doc || doc.querySelector(".print-toc")) return;
  const toc = printToc(doc, lang);
  if (toc) doc.querySelector(".print-cover")?.insertAdjacentHTML("afterend", toc);
}

function printLegal(lang) {
  return lang === "en"
    ? `<aside class="print-legal"><strong>Important notice.</strong> This report is intended for information and entertainment. Astrological interpretations describe tendencies and do not constitute medical, psychological, legal or financial advice. For decisions affecting health, safety, rights or finances, consult a qualified professional. © AstroSMap — Project Artemisa. astrosmap.ru</aside>`
    : `<aside class="print-legal"><strong>Важное предупреждение.</strong> Отчёт носит информационно-развлекательный характер. Астрологические трактовки описывают тенденции и не являются медицинской, психологической, юридической или финансовой рекомендацией. По вопросам здоровья, безопасности, прав и финансов обращайтесь к профильному специалисту. © AstroSMap — Project Artemisa. astrosmap.ru</aside>`;
}

// Собрать печатный документ во временном iframe. Одна раскладка на два сценария:
// системная печать (printFrom) и одноклик-экспорт в PDF (downloadPdf).
// extraHead — доп. содержимое <head> (например, подключение html2pdf для PDF).
// html2canvas не понимает width/height:100% у SVG — задаём явные пиксели по viewBox.
// Вызывается и при сборке фрейма, и перед рендером PDF: внешний скрипт в <head>
// блокирует парсер, и на момент сборки тело документа может быть ещё пустым.
function fixPrintSvg(doc) {
  try {
    doc.querySelectorAll(".chart-svg svg, .report-chart svg").forEach((svg) => {
      const vb = (svg.getAttribute("viewBox") || "").split(/\s+/).map(Number);
      const w = 660;
      const h = vb.length === 4 && vb[2] > 0 ? Math.round((w * vb[3]) / vb[2]) : w;
      svg.setAttribute("width", w);
      svg.setAttribute("height", h);
      svg.style.width = w + "px";
      svg.style.height = h + "px";
    });
  } catch (e) {}
}

// Editorial reports are built from the last server response, never from UI cards.
// This does not fetch additional data or broaden the response's access level.
function reportLabel(key) {
  const labels = {
    overview: ["Обзор периода", "Period overview"], theme: ["Темы периода", "Themes of the period"],
    highlights: ["Ключевые влияния", "Key influences"], couple: ["Отношения", "Relationship analysis"],
    sphere_forecast: ["Прогноз по сферам жизни", "Life-area forecast"],
    profection: ["Профекция", "Annual profection"], progressed_moon: ["Прогрессивная Луна", "Progressed Moon"],
    days: ["Календарь по дням", "Daily calendar"], events: ["События периода", "Events in the period"],
    candidates: ["Варианты времени рождения", "Birth-time candidates"],
    aspects: ["Аспекты и трактовки", "Aspects and interpretations"], directed_aspects: ["Дирекционные аспекты", "Directed aspects"],
    planets: ["Положения планет", "Planet positions"], houses: ["Дома", "Houses"],
    transit_planets: ["Транзитные планеты", "Transiting planets"], prog_planets: ["Прогрессивные планеты", "Progressed planets"],
    a_planets: ["Планеты первого участника", "First person's planets"], b_planets: ["Планеты второго участника", "Second person's planets"],
    meta: ["Исходные данные", "Calculation data"], natal_meta: ["Данные рождения", "Birth data"],
    transit_meta: ["Дата и место транзита", "Transit date and location"], prog_meta: ["Данные прогрессии", "Progression data"],
    a_meta: ["Первый участник", "First person"], b_meta: ["Второй участник", "Second person"],
    location_meta: ["Место расчёта", "Calculation location"], summary: ["Резюме", "Summary"],
    text: ["Трактовка", "Interpretation"], interp: ["Трактовка", "Interpretation"],
    interp_full: ["Подробная трактовка", "Full interpretation"], interp_sign: ["В знаке", "In the sign"], interp_house: ["В доме", "In the house"],
    name: ["Название", "Name"], name_ru: ["Название", "Name"], sign_ru: ["Знак", "Sign"],
    date: ["Дата", "Date"], start: ["Начало периода", "Period starts"], end: ["Конец периода", "Period ends"],
    period_start: ["Начало периода", "Period starts"], period_end: ["Конец периода", "Period ends"],
    city: ["Место", "Location"], local_datetime: ["Местное время", "Local time"],
    lat: ["Широта", "Latitude"], lng: ["Долгота", "Longitude"], tz_str: ["Часовой пояс", "Time zone"],
    strengths: ["Сильные стороны", "Strengths"], challenges: ["Задачи", "Challenges"], advice: ["Рекомендации", "Advice"],
    verdict: ["Итог", "Conclusion"], score: ["Расчётный индекс", "Calculated index"],
    house_num: ["Дом", "House"], orbit: ["Орб", "Orb"], retrograde: ["Ретроградность", "Retrograde"],
    headline: ["Общий фон", "Overview"], groups: ["Сферы жизни", "Life areas"], items: ["Влияния", "Influences"],
    label: ["Тема", "Theme"], t_name: ["Транзитная планета", "Transiting planet"], n_name: ["Натальная планета", "Natal planet"],
    p1_ru: ["Первый объект", "First object"], p2_ru: ["Второй объект", "Second object"], aspect_ru: ["Аспект", "Aspect"],
    nature_label: ["Характер влияния", "Nature of influence"], movement: ["Движение", "Motion"], orb: ["Орб", "Orb"],
    deg: ["Градусы", "Degrees"], min: ["Минуты дуги", "Arcminutes"], dignity: ["Достоинство", "Dignity"],
    target_date: ["Дата расчёта", "Calculation date"], elapsed_years: ["Возраст", "Age"], solar_arc: ["Солнечная дуга", "Solar arc"],
    prog_moon: ["Прогрессивная Луна", "Progressed Moon"], prog_sun: ["Прогрессивное Солнце", "Progressed Sun"],
    overlay: ["Связь с натальной картой", "Natal overlay"], focus: ["Главная сфера", "Main focus"], mood: ["Эмоциональный фон", "Emotional tone"],
    lord: ["Управитель периода", "Period ruler"], tone: ["Общий настрой", "Overall tone"],
    spheres: ["Сферы жизни", "Life areas"], composite: ["Композитная карта", "Composite chart"], overlays: ["Наложения домов", "House overlays"],
    description_ru: ["Описание", "Description"], rule_ru: ["Критерий", "Criterion"], points: ["Баллы", "Points"], value: ["Значение", "Value"],
    breakdown: ["Обоснование", "Supporting detail"], profile: ["Общие особенности", "General characteristics"], psych: ["Психологические темы", "Psychological themes"],
    balance: ["Баланс стихий", "Element balance"], ruler: ["Управитель карты", "Chart ruler"], coruler: ["Соуправитель", "Co-ruler"],
    temperament: ["Темперамент", "Temperament"], dominant: ["Ведущая планета", "Dominant planet"], axes: ["Психологические темы", "Psychological themes"],
    missing: ["Зоны развития", "Growth areas"], self_esteem: ["Внутренняя опора", "Inner support"],
    love: ["Отношения", "Relationships"], career: ["Карьера", "Career"], health: ["Благополучие", "Wellbeing"],
    best: ["Наиболее вероятное время", "Most likely time"], top: ["Варианты времени", "Time candidates"],
    time: ["Время", "Time"], confidence: ["Относительная оценка, %", "Relative score, %"],
    asc_sign: ["Знак Асцендента", "Ascendant sign"], asc_deg: ["Градус Асцендента", "Ascendant degree"], mc_sign: ["Знак MC", "MC sign"],
    factor: ["Соответствие событию", "Event correspondence"], window: ["Интервал времени", "Time window"],
    from: ["Начало", "From"], to: ["Конец", "To"], alternatives: ["Альтернативы", "Alternatives"],
    luminary_changes: ["Смена знаков", "Sign changes"], point: ["Объект", "Object"], from_sign: ["Исходный знак", "Original sign"], to_sign: ["Новый знак", "New sign"],
    lunar: ["Лунный календарь", "Lunar calendar"], phase_ru: ["Фаза Луны", "Moon phase"],
    weekday: ["День недели", "Weekday"], tithi_name: ["Титхи", "Tithi"], nakshatra_name: ["Накшатра", "Nakshatra"],
    paksha: ["Пакша", "Paksha"], tara: ["Тарабала", "Tarabala"], quality_ru: ["Характер дня", "Day quality"],
    note: ["Примечание", "Note"], nak_meaning: ["Значение накшатры", "Nakshatra meaning"], paksha_advice: ["Фаза и рекомендации", "Phase guidance"], day_advice: ["Рекомендации на день", "Daily guidance"],
    year: ["Год", "Year"], month: ["Месяц", "Month"], exact_datetime: ["Точное время", "Exact time"],
  };
  if (labels[key]) return labels[key][LANG === "en" ? 1 : 0];
  return ""; // Unknown schema fields are not editorial copy.
}

function reportValue(value, depth = 0, seen = new Set()) {
  if (value == null || value === "") return "";
  if (typeof value !== "object") {
    const text = typeof value === "boolean" ? (value ? (LANG === "en" ? "Yes" : "Да") : (LANG === "en" ? "No" : "Нет")) : String(value);
    if (text.length > 100 && seen.has(text)) return "";
    if (text.length > 100) seen.add(text);
    return splitReportParagraph(text).map((part) => `<p>${escapeHtml(part).replace(/\n/g, "<br>")}</p>`).join("");
  }
  if (Array.isArray(value)) return value.map((item) => `<section>${reportValue(item, depth + 1, seen)}</section>`).join("");
  const entries = Object.entries(value).filter(([key, val]) => !["svg", "svg_light"].includes(key) && val != null && val !== "");
  const rows = [], prose = [];
  for (const [key, val] of entries) {
    if (!reportLabel(key)) continue;
    // Tone strings are narrative in return reports, but enum flags elsewhere.
    if (key === "tone" && !String(val).includes(" ")) continue;
    // Short scalar facts form appendices; long text is never clipped into cells.
    if (typeof val !== "object" && String(val).length < 100 && !/text|interp|advice|summary|verdict/.test(key)) {
      rows.push(`<tr><th scope="row">${escapeHtml(reportLabel(key))}</th><td>${reportValue(val, depth + 1, seen)}</td></tr>`);
    } else {
      const tag = depth < 2 ? "h3" : "h4";
      const content = reportValue(val, depth + 1, seen);
      if (content) prose.push((/^(text|interp|summary|headline)$/.test(key) ? "" : `<${tag}>${escapeHtml(reportLabel(key))}</${tag}>`) + content);
    }
  }
  return (rows.length ? `<table><tbody>${rows.join("")}</tbody></table>` : "") + prose.join("");
}

function splitReportParagraph(text, target = 750, maximum = 900) {
  const parts = [];
  let rest = String(text);
  while (rest.length > maximum) {
    const window = rest.slice(0, maximum + 1);
    const boundaries = [...window.matchAll(/[.!?…][»”"')\]]*\s+/gu)]
      .map((match) => match.index + match[0].length)
      .filter((end) => end >= 600 && end <= maximum);
    let cut = boundaries.sort((a, b) => Math.abs(a - target) - Math.abs(b - target))[0];
    // A very long sentence still needs a bounded paragraph: split at a word,
    // retaining every whitespace character and punctuation mark verbatim.
    if (!cut) {
      const spaces = [...window.matchAll(/\s+/g)].map((match) => match.index + match[0].length).filter((end) => end <= maximum);
      cut = spaces.at(-1) || maximum;
    }
    parts.push(rest.slice(0, cut));
    rest = rest.slice(cut);
  }
  if (rest) parts.push(rest);
  return parts;
}

function editorialDeepHtml(html) {
  const doc = new DOMParser().parseFromString(html, "text/html");
  doc.querySelectorAll("script,style,button,input,select,textarea").forEach((el) => el.remove());
  doc.body.querySelectorAll("*").forEach((el) => {
    for (const attr of Array.from(el.attributes)) el.removeAttribute(attr.name);
    if (el.tagName === "DIV") {
      const p = doc.createElement("p");
      p.append(...el.childNodes);
      el.replaceWith(p);
    }
  });
  doc.body.querySelectorAll("p").forEach((p) => {
    const chunks = splitReportParagraph(p.textContent);
    if (chunks.length < 2) return;
    // DOM ranges preserve emphasis (including the initial bold label), unlike
    // replacing the paragraph with plain text or slicing serialized HTML.
    const walker = doc.createTreeWalker(p, 4 /* SHOW_TEXT */);
    const nodes = [];
    let node, total = 0;
    while ((node = walker.nextNode())) {
      nodes.push({ node, start: total, end: total + node.textContent.length });
      total += node.textContent.length;
    }
    const boundary = (offset) => {
      const item = nodes.find((entry) => entry.end >= offset) || nodes.at(-1);
      return [item.node, offset - item.start];
    };
    let offset = 0;
    for (const chunk of chunks) {
      const range = doc.createRange();
      range.setStart(...boundary(offset));
      offset += chunk.length;
      range.setEnd(...boundary(offset));
      const paragraph = doc.createElement("p");
      paragraph.append(range.cloneContents());
      p.before(paragraph);
    }
    p.remove();
  });
  doc.body.querySelectorAll("h2,h3,h4").forEach((heading) => {
    const paragraph = heading.nextElementSibling;
    if (!paragraph || paragraph.tagName !== "P" || heading.textContent.length + paragraph.textContent.length > 1100) return;
    const lead = doc.createElement("div");
    lead.className = "report-lead";
    heading.before(lead);
    lead.append(heading, paragraph);
  });
  return doc.body.innerHTML;
}

function natalReportAppendices(data) {
  const label = (ru, en) => LANG === "en" ? en : ru;
  const cell = (value) => escapeHtml(value == null || value === "" ? "—" : String(value));
  const table = (title, headers, rows) => rows.length
    ? `<section><h2>${cell(title)}</h2><table><thead><tr>${headers.map((h) => `<th scope="col">${cell(h)}</th>`).join("")}</tr></thead><tbody>${rows.map((row) => `<tr>${row.map((v) => `<td>${cell(v)}</td>`).join("")}</tr>`).join("")}</tbody></table></section>` : "";
  const position = (p) => `${p.sign_symbol || ""} ${p.sign_ru || ""}${p.deg != null ? ` ${p.deg}°${String(p.min ?? 0).padStart(2, "0")}′` : ""}`.trim();
  const m = data.meta || {};
  const facts = [
    [label("Имя", "Name"), m.name],
    [label("Дата и местное время", "Local date and time"), m.local_datetime],
    [label("Место рождения", "Birth place"), m.city],
    [label("Широта", "Latitude"), m.lat], [label("Долгота", "Longitude"), m.lng],
    [label("Часовой пояс", "Time zone"), m.tz_str],
    [label("Система домов", "House system"), m.houses_system_name || m.houses_system],
  ].filter(([, value]) => value != null && value !== "");
  let html = table(label("Исходные данные расчёта", "Calculation data"), [label("Параметр", "Parameter"), label("Значение", "Value")], facts);
  // Explicit column selections keep prose, internal IDs and API flags out of appendices.
  html += table(label("Планеты и угловые точки", "Planets and angles"),
    [label("Объект", "Object"), label("Положение", "Position"), label("Дом", "House"), label("Достоинство", "Dignity"), label("Ретроградность", "Retrograde")],
    [...(data.planets || []), ...(data.angles || [])].map((p) => [p.name_ru, position(p), p.house_num, p.dignity, p.retrograde ? "R" : "—"]));
  html += table(label("Куспиды домов", "House cusps"),
    [label("Дом", "House"), label("Положение", "Position"), label("Сфера жизни", "Life area")],
    (data.houses || []).map((h) => [h.house_num, position(h), h.sphere || h.meaning]));
  html += table(label("Таблица аспектов", "Aspect table"),
    [label("Первый объект", "First object"), label("Аспект", "Aspect"), label("Второй объект", "Second object"), label("Орб", "Orb")],
    (data.aspects || []).map((a) => [a.p1_ru, a.aspect_ru, a.p2_ru, a.orbit == null ? null : `${a.orbit}°`]));
  return html;
}

function buildEditorialReport(data, tool, title) {
  if (!data) return "";
  const toolTitle = title || t("tab_" + tool);
  let body = `<h1>${escapeHtml(toolTitle)}</h1>`;
  const svg = data.svg_light || data.svg;
  if (svg) body += `<figure class="report-chart">${svg}</figure>`;
  if (tool === "natal") {
    body += editorialDeepHtml(buildDeepReport(data));
    body += natalReportAppendices(data);
  } else {
    // Each technique uses only its own payload, including every day/event/candidate.
    // Prioritize narrative before detailed calculation data, independent of UI selection.
    const sections = {
      transit: ["natal_meta", "transit_meta", "overview", "aspects", "transit_planets"],
      progression: ["natal_meta", "prog_meta", "target_date", "elapsed_years", "solar_arc", "highlights", "aspects", "directed_aspects", "prog_planets"],
      return: ["natal_meta", "meta", "period_start", "period_end", "theme", "profile", "psych", "spheres", "planets", "houses", "aspects"],
      synastry: ["a_meta", "b_meta", "couple", "score", "aspects", "a_planets", "b_planets"],
      forecast: ["natal_meta", "location_meta", "start", "end", "summary", "profection", "progressed_moon", "sphere_forecast", "events"],
      calendar: ["natal_meta", "location_meta", "start", "end", "events", "lunar"],
      rectification: ["meta", "best", "top"], vedic: ["year", "month", "days"],
    };
    const keys = sections[tool] || [];
    const seen = new Set();
    for (const key of keys) {
      let sectionData = data[key];
      if (tool === "synastry" && key === "score" && sectionData) {
        sectionData = { value: sectionData.value, description_ru: sectionData.description_ru };
      }
      if (key === "lunar" && sectionData && !Array.isArray(sectionData)) {
        sectionData = Object.entries(sectionData).map(([date, day]) => ({ date, ...day }));
      }
      const content = reportValue(sectionData, 0, seen);
      if (content) body += `<section><h2>${escapeHtml(reportLabel(key))}</h2>${content}</section>`;
    }
  }
  return `<article class="editorial-report">${body}</article>`;
}

const EDITORIAL_PRINT_STYLE = `
  .editorial-report { display:block; color:#29253a; }
  .editorial-report section { display:block; margin:0 0 16pt; }
  .editorial-report h1,.editorial-report h2,.editorial-report h3,.editorial-report h4 { break-after:avoid; font-family:Georgia,serif; }
  .editorial-report h2 { margin-top:24pt; border-bottom:1px solid #d9d3e4; padding-bottom:5pt; }
  .editorial-report p { margin:0 0 9pt; orphans:3; widows:3; white-space:normal; break-inside:avoid; }
  .editorial-report table { width:100%; border-collapse:collapse; margin:12pt 0; font-size:9pt; }
  .editorial-report th,.editorial-report td { text-align:left; vertical-align:top; padding:5pt; border-bottom:1px solid #e2dfea; overflow-wrap:anywhere; }
  .editorial-report th { width:auto; }
  .editorial-report tbody th { width:30%; }
  .editorial-report .report-lead { break-inside:avoid; page-break-inside:avoid; }
  .print-legal { break-inside:avoid; }
  .editorial-report td p { margin:0; }
  .editorial-report tr { break-inside:avoid; }
  .report-chart { margin:20pt auto; text-align:center; break-inside:avoid; }
  .report-chart svg { max-width:100%; height:auto; }
`;

function buildPrintFrame(srcId, title, extraHead) {
  const src = document.getElementById(srcId);
  if (!src) return null;
  const reportData = srcId === "deep-content" ? deepReportData : lastData;
  const reportTool = srcId === "deep-content" ? "natal" : lastMode;
  if (!reportData || !reportTool) return null;
  const content = buildEditorialReport(reportData, reportTool, title);
  const old = document.getElementById("print-frame");
  if (old) old.remove();
  const frame = document.createElement("iframe");
  frame.id = "print-frame";
  frame.setAttribute("aria-hidden", "true");
  frame.style.cssText = "position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden;";
  document.body.appendChild(frame);
  // Название как на сайте: главный бренд — «Project Artemisa», под ним — тип документа.
  const brandTitle = "AstroSMap — Project Artemisa";
  const brandSub = (typeof t === "function" ? t("hero_title") : "Натальная карта");
  const dateStr = new Date().toLocaleDateString(LANG === "en" ? "en-GB" : "ru-RU");
  // Фирменная шапка проекта вверху документа + повторяющийся колонтитул внизу.
  const brandHeader =
    `<div class="print-brand">
       <div class="print-brand-logo" aria-hidden="true">✦</div>
       <div class="print-brand-main">${brandTitle}</div>
       <div class="print-brand-sub">astrosmap.ru</div>
       ${title ? `<div class="print-brand-doc">${title}</div>` : ""}
       <div class="print-brand-date">${dateStr}</div>
     </div>`;
  const brandFooter = `<div class="print-footer">${brandTitle} · astrosmap.ru</div>`;
  const cover = reportTool === "natal"
    ? printCover(title || brandSub, brandTitle, dateStr, reportData.meta)
    : `<section class="print-cover">
        <div class="print-cover-mark" aria-hidden="true">✦</div>
        <div class="print-cover-brand">${escapeHtml(brandTitle)}</div>
        <div class="print-cover-domain">ASTROSMAP.RU</div>
        <div class="print-cover-rule"></div>
        <div class="print-cover-type">${escapeHtml(title || t("tab_" + reportTool))}</div>
        <p class="print-cover-note">${escapeHtml(dateStr)}</p>
      </section>`;
  const doc = frame.contentWindow.document;
  doc.open();
  doc.write(
    `<!DOCTYPE html><html lang="${LANG}" data-theme="light"><head><meta charset="utf-8">` +
    `<title>${brandTitle} — ${title || brandSub}</title>` +
    `<link rel="stylesheet" href="${location.origin}/css/style.css">` +
    `<style>${PRINT_OVERRIDE}${EDITORIAL_PRINT_STYLE}</style>${extraHead || ""}</head>` +
    `<body data-theme="light"><main class="print-document">${cover}<div class="print-body">${brandHeader}${content}${printLegal(LANG)}</div></main>${brandFooter}</body></html>`
  );
  doc.close();
  // Раскрыть все свёрнутые блоки, чтобы в вывод попало всё содержимое.
  try { doc.querySelectorAll("details").forEach((d) => (d.open = true)); } catch (e) {}
  // В некоторых браузерах doc.close() завершает разбор разметки асинхронно.
  // Повторный вызов безопасен и гарантирует, что заголовки уже появились в DOM.
  ensurePrintToc(doc, LANG);
  let tocTries = 0;
  const tocPoll = setInterval(() => {
    ensurePrintToc(doc, LANG);
    if (doc.querySelector(".print-toc") || ++tocTries >= 20) clearInterval(tocPoll);
  }, 50);
  fixPrintSvg(doc);
  return { frame, doc, brandTitle, dateStr };
}

function printFrom(srcId, title) {
  if (!premiumGate()) return;
  const built = buildPrintFrame(srcId, title);
  if (!built) return;
  const { frame, doc } = built;
  let fired = false;
  const fire = () => {
    if (fired) return;
    fired = true;
    try { doc.querySelectorAll("details").forEach((d) => (d.open = true)); } catch (e) {}
    try {
      frame.contentWindow.focus();
      frame.contentWindow.print();
    } catch (e) { /* ignore */ }
    setTimeout(() => frame.remove(), 1000);
  };
  // дать стилю/шрифтам загрузиться (важно для Edge и для внешнего style.css)
  frame.onload = () => setTimeout(fire, 350);
  // запасной триггер, если onload не сработал
  setTimeout(fire, 1500);
}

// Одноклик-экспорт активного отчёта в готовый PDF-файл — без системного диалога.
// Рендер идёт в том же печатном iframe (та же вёрстка, что и «Печать»), а html2pdf
// подключается локально (script-src 'self') и работает внутри iframe.
async function renderPagedReportPdf(win, source, options, onProgress = () => {}) {
  if (win.document.fonts) await win.document.fonts.ready;
  const worker = win.html2pdf().set({ ...options, enableLinks: false }).from(source).toContainer();
  const container = await worker.get("container");
  const overlay = await worker.get("overlay");
  try {
    const size = await worker.get("pageSize");
    // Exactly the same CSS-pixel page height used by html2pdf's pagebreak plugin.
    const pageHeight = size.inner.px.height;
    const width = Math.ceil(container.getBoundingClientRect().width);
    const height = Math.ceil(container.scrollHeight);
    if (!(width > 0 && pageHeight > 0 && height > 0)) throw new Error("Invalid PDF layout");
    const total = Math.ceil(height / pageHeight);
    // Obtain the bundled jsPDF instance without ever rasterizing the full document.
    const seed = win.document.createElement("canvas");
    seed.width = seed.height = 1;
    const pdf = await win.html2pdf().set({ ...options, enableLinks: false }).from(seed).toPdf().get("pdf");
    pdf.deletePage(1);
    seed.width = seed.height = 0;
    for (let page = 0; page < total; page++) {
      // toCanvas removes its overlay after rendering. Reattach the SAME laid-out
      // container rather than invoking toContainer again and adding pagebreaks twice.
      if (!win.document.body.contains(overlay)) win.document.body.appendChild(overlay);
      const sliceHeight = Math.min(pageHeight, height - page * pageHeight);
      const canvas = await worker.set({ html2canvas: {
        ...options.html2canvas, width, height: sliceHeight, y: page * pageHeight,
        scrollX: 0, scrollY: 0,
      } }).toCanvas().get("canvas");
      try {
        if (!canvas.width || !canvas.height) throw new Error("Empty PDF page canvas");
        const encoded = canvas.toDataURL("image/jpeg", options.image.quality);
        if (encoded === "data:,") throw new Error("PDF canvas limit exceeded");
        pdf.addPage();
        pdf.addImage(encoded, "JPEG", 10, 10, size.inner.width, size.inner.height * sliceHeight / pageHeight);
        if (page > 0) {
          pdf.setFontSize(8);
          pdf.setTextColor(145, 137, 156);
          pdf.text(`${page + 1} / ${total}`, 200, 291, { align: "right" });
        }
        onProgress(page + 1, total);
      } finally {
        canvas.width = canvas.height = 0; // release each raster before the next page
        await worker.set({ canvas: null });
      }
    }
    return pdf;
  } finally {
    overlay.remove();
  }
}

function downloadPdf(srcId, title, btn, onDone) {
  const extraHead =
    `<script src="${location.origin}/js/vendor/html2pdf.bundle.min.js"></script>`;
  const built = buildPrintFrame(srcId, title, extraHead);
  if (!built) return;
  const { frame, doc, brandTitle, dateStr } = built;
  const safe = String(title || brandTitle).replace(/[\\/:*?"<>|\s]+/g, "_").replace(/^_+|_+$/g, "") || "astro";
  const isoDate = new Date().toISOString().slice(0, 10);
  const fname = `${safe}-${isoDate}.pdf`;
  const label = btn ? btn.textContent : "";
  if (btn) { btn.disabled = true; btn.textContent = (typeof t === "function" ? t("pdf_wait") : "…"); }
  const cleanup = () => {
    if (btn) { btn.disabled = false; btn.textContent = label; }
    if (typeof onDone === "function") onDone(); // списываем кредит только после успешного экспорта
    setTimeout(() => frame.remove(), 500);
  };
  const fail = () => {
    if (btn) { btn.disabled = false; btn.textContent = label; }
    frame.remove();
    alert(LANG === "en"
      ? "Could not build the PDF. Please use the Print button instead."
      : "Не удалось собрать PDF. Воспользуйтесь кнопкой «Печать».");
  };
  let done = false;
  const run = () => {
    if (done) return;
    done = true;
    const win = frame.contentWindow;
    try { doc.querySelectorAll("details").forEach((d) => (d.open = true)); } catch (e) {}
    ensurePrintToc(doc, LANG);
    fixPrintSvg(doc);
    // html2canvas рисует position:fixed футер посреди страницы — в PDF он не нужен
    try { doc.querySelectorAll(".print-footer").forEach((el) => el.remove()); } catch (e) {}
    // В iframe 0×0 html2canvas обрезает SVG-колесо: даём фрейму реальный размер за экраном
    frame.style.cssText = "position:fixed;left:-9999px;top:0;width:820px;height:1160px;border:0;";
    const opt = {
      margin: 10,
      filename: fname,
      image: { type: "jpeg", quality: 0.9 },
      html2canvas: { scale: 2, useCORS: true, backgroundColor: "#ffffff", logging: false },
      jsPDF: { unit: "mm", format: "a4", orientation: "portrait" },
      pagebreak: { mode: ["css", "legacy"] },
    };
    try {
      renderPagedReportPdf(win, win.document.body, opt, (page, total) => {
        if (btn) btn.textContent = `${t("pdf_wait")} ${page}/${total}`;
      }).then((pdf) => pdf.save(fname, { returnPromise: true })).then(cleanup).catch(fail);
    } catch (e) { fail(); }
  };
  // Дождаться загрузки html2pdf внутри iframe и прогрузки стилей, затем рендерить.
  let tries = 0;
  const poll = setInterval(() => {
    if (done) { clearInterval(poll); return; }
    if (frame.contentWindow && frame.contentWindow.html2pdf) {
      clearInterval(poll);
      setTimeout(run, 300);
    } else if (++tries > 60) { // ~15 c таймаут
      clearInterval(poll);
      fail();
    }
  }, 250);
}

$("deep-print").addEventListener("click", () => printFrom("deep-content"));
if ($("deep-pdf")) {
  $("deep-pdf").addEventListener("click", (e) => downloadPdf("deep-content", "", e.currentTarget));
}

function activeToolTitle() {
  return lastMode ? t("tab_" + lastMode) : "";
}
// Кнопка системной печати результата любого инструмента.
$("result-print").addEventListener("click", () => printFrom("results", activeToolTitle()));
// Кнопка скачивания результата готовым PDF-файлом.
if ($("result-pdf")) {
  $("result-pdf").addEventListener("click", (e) => {
    const btn = e.currentTarget;
    if (IS_PREMIUM) { downloadPdf("results", activeToolTitle(), btn); return; }
    if (REPORT_CREDITS > 0) {
      // Разовый отчёт: качаем и списываем кредит только по факту успешного экспорта.
      downloadPdf("results", activeToolTitle(), btn, consumeReportCredit);
      return;
    }
    openPaywall(getToken() ? 402 : 401); // нет ни подписки, ни разового отчёта
  });
}

async function consumeReportCredit() {
  try {
    const r = await postJSON("/api/report/consume", {});
    REPORT_CREDITS = r.report_credits;
    refreshPremiumBtn();
  } catch (e) { /* кредит не списался — не критично, отчёт уже скачан */ }
}

// ---------- Рендер: транзиты ----------
function renderTransit(data) {
  showResults();
  renderChart(data);
  $("planets-title").textContent = t("t_transit_planets");
  $("aspects-title").textContent = t("t_transit_aspects");
  $("houses-section").classList.add("hidden");

  $("summary").innerHTML =
    `<h3>${t("t_transits_for")}: ${data.natal_meta.name}</h3>` +
    summaryBlock(
      data.transit_meta,
      data.transit_lunar_phase,
      [[t("sum_natal"), formatDateTime(data.natal_meta.local_datetime)]]
    );

  $("planets-table").innerHTML = planetsTable(data.transit_planets);
  $("aspects-table").innerHTML = aspectsTable(data.aspects, t("nat"), t("transit"));

  showInterpSection(
    t("t_what_transits"),
    data.overview
      ? transitOverview(data.overview)
      : `<p class="section-note">${MODE_INTRO.transit}</p>` + aspectInterpCards(data.aspects, { swap: true })
  );
}

// ---------- Рендер: прогрессии ----------
function renderProgression(data) {
  showResults();
  renderChart(data);

  const sa = data.solar_arc;
  $("summary").innerHTML =
    `<h3>${t("t_prog_title")}</h3>` +
    summaryBlock(
      data.prog_meta,
      null,
      [
        [t("t_prog_elapsed"), `${data.elapsed_years}`],
        [t("t_prog_arc"), `${sa.deg}°${String(sa.min).padStart(2, "0")}′`],
        [t("t_prog_date"), formatDateTime(data.prog_meta.local_datetime)],
        [t("sum_natal"), formatDateTime(data.natal_meta.local_datetime)],
      ]
    );

  $("planets-title").textContent = t("t_prog_planets");
  $("planets-table").innerHTML = planetsTable(data.prog_planets);

  $("houses-section").classList.remove("hidden");
  $("houses-section-title").textContent = t("t_prog_directions");
  $("houses-table").innerHTML = data.directed_aspects.length
    ? aspectsTable(data.directed_aspects, t("direction"), t("nat"))
    : `<tr><td style="color:var(--text-dim)">${t("t_prog_no_dir")}</td></tr>`;

  $("aspects-title").textContent = t("t_prog_aspects");
  $("aspects-table").innerHTML = aspectsTable(data.aspects, t("nat"), t("progression_w"));

  showInterpSection(
    t("t_what_prog"),
    `<p class="section-note">${MODE_INTRO.progression}</p>` +
      progressionHighlights(data.highlights) +
      `<h4 class="tr-h" style="margin-top:18px">${t("pg_aspects_title")}</h4>` +
      toneGroupedCards(data.aspects, true)
  );
}

// ---------- Рендер: соляр / лунар ----------
function renderReturn(data) {
  showResults();
  renderChart(data);

  const allPoints = data.planets.concat(data.angles || []);
  const isSolar = (data.return_type || "").toLowerCase().startsWith("sol");
  const th = data.theme || {};
  const themeBlock = `<p class="ret-intro">${isSolar ? t("ret_intro_solar") : t("ret_intro_lunar")}</p>
    <div class="ret-theme">
      ${th.overlay ? `<div class="ret-theme-card overlay"><h4>⭐ ${t("ret_overlay")}</h4><p>${th.overlay}</p></div>` : ""}
      <div class="ret-theme-card tone"><h4>🎯 ${t("ret_tone")}</h4><p>${th.tone || ""}</p></div>
      ${th.focus ? `<div class="ret-theme-card focus"><h4>🏠 ${t("ret_focus")}</h4><p>${th.focus}</p></div>` : ""}
      <div class="ret-theme-card mood"><h4>🌙 ${t("ret_mood")}</h4><p>${th.mood || ""}</p></div>
      ${th.lord ? `<div class="ret-theme-card lord"><h4>👑 ${t("ret_lord")}</h4><p>${th.lord}</p></div>` : ""}
    </div>`;
  const periodRows = [
    [isSolar ? t("ret_period_solar") : t("ret_period_lunar"), formatDateTime(data.period_start || data.meta.local_datetime)],
  ];
  if (data.period_end) {
    periodRows.push([isSolar ? t("ret_period_solar_end") : t("ret_period_lunar_end"), formatDateTime(data.period_end)]);
  }
  periodRows.push([t("sum_natal"), formatDateTime(data.natal_meta.local_datetime)]);
  $("summary").innerHTML =
    `<h3>${data.return_type_ru}</h3>` +
    themeBlock +
    bigThree(data.planets, data.houses, data.big_three) +
    summaryBlock(data.meta, data.lunar_phase, periodRows);

  $("planets-title").textContent = t("t_ret_planets");
  $("planets-table").innerHTML = planetsTable(allPoints, true);
  $("houses-section").classList.remove("hidden");
  $("houses-section-title").textContent = t("sec_houses");
  $("houses-table").innerHTML = housesTable(data.houses);
  $("aspects-title").textContent = t("t_ret_aspects");
  $("aspects-table").innerHTML = aspectsTable(data.aspects);

  if (data.profile) {
    $("portrait").innerHTML = buildPortrait(data.profile);
    $("portrait").classList.remove("hidden");
  }
  if (data.psych) {
    $("psych").innerHTML = buildPsych(data.psych);
    $("psych").classList.remove("hidden");
  }
  if (data.spheres) {
    $("spheres").innerHTML = buildSpheres(data.spheres);
    $("spheres").classList.remove("hidden");
  }
  showInterpSection(
    t("t_ret_analysis"),
    `<p class="section-note">${MODE_INTRO.return}</p>` + interpCards(data.planets)
  );
}

// ---------- Рендер: синастрия ----------
function synItem(it, aName, bName) {
  return `<div class="syn-item ${it.nature}">
    <div class="syn-pair">
      <span class="glyph">${it.p1_symbol}</span> ${it.p1_ru}<span class="syn-who"> · ${escAttr(aName)}</span>
      <span class="syn-asp">${it.aspect_ru}</span>
      <span class="glyph">${it.p2_symbol}</span> ${it.p2_ru}<span class="syn-who"> · ${escAttr(bName)}</span>
    </div>
    <div class="syn-text">${it.text}</div>
  </div>`;
}

function renderSynastry(data) {
  showResults();
  renderChart(data);

  const aName = data.a_meta.name;
  const bName = data.b_meta.name;
  const s = data.score;
  const c = data.couple || { strengths: [], challenges: [], verdict: "" };

  const destinyBadge = s.is_destiny_sign ? `<div class="destiny">${t("t_destiny")}</div>` : "";

  const SYN_TONE = { good: "good", mixed: "neutral", challenging: "bad", quiet: "quiet" };
  const SYN_TONE_LBL = {
    good: t("syn_tone_good"), mixed: t("syn_tone_mixed"),
    challenging: t("syn_tone_bad"), quiet: t("syn_tone_quiet"),
  };
  const spheresBlock = c.spheres && c.spheres.length
    ? `<div class="syn-block">
         <h4 class="syn-h">🧭 ${t("syn_spheres_title")}</h4>
         <div class="syn-spheres">${c.spheres.map((sp) => `
           <div class="syn-sphere ${SYN_TONE[sp.tone] || "neutral"}">
             <div class="syn-sphere-top">
               <span class="syn-sphere-name">${sp.label}</span>
               <span class="syn-sphere-tag">${SYN_TONE_LBL[sp.tone] || ""}</span>
             </div>
             <div class="syn-sphere-text">${sp.text}</div>
             ${sp.advice ? `<div class="syn-sphere-advice">💡 ${sp.advice}</div>` : ""}
           </div>`).join("")}</div>
       </div>`
    : "";

  // Композитная карта — карта самих отношений.
  const compItems = (c.composite || []).filter((x) => x.text);
  const compositeBlock = compItems.length
    ? `<div class="syn-block">
         <h4 class="syn-h">🌟 ${t("syn_composite_title")}</h4>
         <p class="syn-sub">${t("syn_composite_hint")}</p>
         <div class="syn-list">${compItems.map((x) => `
           <div class="syn-comp">
             <div class="syn-comp-pos"><span class="glyph">${x.symbol}</span> ${x.name_ru} — ${x.sign_ru} ${x.deg}°</div>
             <div class="syn-text">${x.text}</div>
           </div>`).join("")}</div>
       </div>`
    : "";

  // Накладки домов — где партнёры влияют друг на друга (детально, свёрнуто).
  const overlaysBlock = (c.overlays && c.overlays.length)
    ? `<details class="collapsible syn-extra"><summary>🏠 ${t("syn_overlays_title")}</summary>
         <p class="syn-sub">${t("syn_overlays_hint")}</p>
         <div class="syn-list">${c.overlays.map((o) => `
           <div class="syn-ov"><span class="glyph">${o.symbol}</span> ${o.text}</div>`).join("")}</div>
       </details>`
    : "";

  const strengthsBlock = c.strengths && c.strengths.length
    ? `<div class="syn-block">
         <h4 class="syn-h syn-h-good">💞 ${t("syn_strengths")}</h4>
         <div class="syn-list">${c.strengths.map((it) => synItem(it, aName, bName)).join("")}</div>
       </div>`
    : "";
  const challengesBlock = c.challenges && c.challenges.length
    ? `<div class="syn-block">
         <h4 class="syn-h syn-h-bad">⚠ ${t("syn_challenges")}</h4>
         <div class="syn-list">${c.challenges.map((it) => synItem(it, aName, bName)).join("")}</div>
       </div>`
    : "";

  // Технический индекс — в сворачиваемом блоке (для интересующихся).
  const breakdownRows = s.breakdown
    .map((b) => `<div class="summary-row"><span class="label">${b.rule_ru}</span><span class="value">+${b.points}</span></div>`)
    .join("");

  $("summary").innerHTML =
    `<h3>${t("t_compat")}: ${escAttr(aName)} & ${escAttr(bName)}</h3>` +
    `<p class="syn-intro">${t("syn_intro")}</p>` +
    `<div class="score-big"><span class="score-num">${s.value}</span><span class="score-desc">${s.description_ru}</span></div>` +
    destinyBadge +
    (c.verdict ? `<p class="syn-verdict">${c.verdict}</p>` : "") +
    spheresBlock +
    compositeBlock +
    strengthsBlock +
    challengesBlock +
    overlaysBlock +
    `<details class="collapsible syn-index"><summary>${t("syn_index_title")}</summary>` +
    `<p class="score-hint">${t("t_score_hint")}</p>${breakdownRows}</details>`;

  $("planets-title").textContent = `${t("t_planets_of")} ${aName}`;
  $("planets-table").innerHTML = planetsTable(data.a_planets);
  $("houses-section").classList.remove("hidden");
  $("houses-section-title").textContent = `${t("t_planets_of")} ${bName}`;
  $("houses-table").innerHTML = planetsTable(data.b_planets);
  $("aspects-title").textContent = t("t_inter_aspects");
  $("aspects-table").innerHTML = aspectsTable(data.aspects, aName, bName);

  showInterpSection(
    t("t_syn_analysis"),
    `<p class="section-note">${MODE_INTRO.synastry}</p>` + aspectInterpCards(data.aspects)
  );
}

// ---------- Рендер: тизер синастрии (бесплатный) ----------
// Показываем индекс совместимости и тон по сферам; детальный разбор — под подпиской.
function renderSynastryTeaser(d) {
  showResults();
  // #summary лежит внутри #chart-area — саму область оставляем, прячем только колесо.
  $("chart-svg").classList.add("hidden");
  $("chart-png-btn").classList.add("hidden");
  $("data-grids").classList.add("hidden");
  $("interp-section").classList.add("hidden");
  $("result-toolbar").classList.add("hidden"); // печатать/PDF тизер незачем

  const s = d.score;
  const SYN_TONE = { good: "good", mixed: "neutral", challenging: "bad", quiet: "quiet" };
  const SYN_TONE_LBL = {
    good: t("syn_tone_good"), mixed: t("syn_tone_mixed"),
    challenging: t("syn_tone_bad"), quiet: t("syn_tone_quiet"),
  };
  const spheres = (d.spheres || []).map((sp) => `
    <div class="syn-sphere ${SYN_TONE[sp.tone] || "neutral"}">
      <div class="syn-sphere-top">
        <span class="syn-sphere-name">${sp.label}</span>
        <span class="syn-sphere-tag">${SYN_TONE_LBL[sp.tone] || ""}</span>
      </div>
      <div class="syn-sphere-locked">🔒 ${t("syn_teaser_locked")}</div>
    </div>`).join("");
  const counts = t("syn_teaser_counts")
    .replace("{s}", d.strength_count).replace("{c}", d.challenge_count);

  $("summary").innerHTML =
    `<h3>${t("t_compat")}: ${escAttr(d.a_name)} & ${escAttr(d.b_name)}</h3>` +
    `<div class="score-big"><span class="score-num">${s.value}</span><span class="score-desc">${s.description_ru}</span></div>` +
    (s.is_destiny_sign ? `<div class="destiny">${t("t_destiny")}</div>` : "") +
    `<div class="syn-block"><h4 class="syn-h">🧭 ${t("syn_spheres_title")}</h4>` +
    `<div class="syn-spheres">${spheres}</div></div>` +
    `<div class="syn-teaser-lock">
       <p class="syn-teaser-counts">${counts}</p>
       <p class="syn-teaser-hint">${t("syn_teaser_intro")}</p>
       <button class="btn-primary" id="syn-teaser-cta">${t("syn_teaser_cta")}</button>
     </div>`;
  $("syn-teaser-cta").addEventListener("click", () => openPaywall(getToken() ? 402 : 401));
}

// ---------- Рендер: ректификация ----------
function renderRectification(data) {
  showResults();
  $("chart-area").classList.add("hidden");
  $("data-grids").classList.add("hidden");
  $("rect-view").classList.remove("hidden");

  const b = data.best;
  const meta = data.meta;
  const ascInfo =
    b.asc_max_hits > 0
      ? `<div class="rb-asc">${t("t_rect_asc_match").replace("{n}", b.asc_hits).replace("{m}", meta.traits_used)}</div>`
      : "";
  const win = meta.window;
  const winLine =
    win && !meta.auto_failed
      ? `<div class="rb-window">${t("t_rect_rising")} <b>${win.sign}</b> · ${t("t_rect_window")} <b>${win.from}–${win.to}</b></div>`
      : "";
  const rel = meta.reliability || "low";
  const relLine = `<div class="rb-reliab reliab-${rel}">${t("t_rect_reliab")}: ${t("t_rect_reliab_" + rel)}</div>`;
  const altLine =
    meta.alternatives && meta.alternatives.length
      ? `<div class="rb-alt">${t("t_rect_alt")}: ${meta.alternatives.map((a) => `${a.time} (${a.confidence}%)`).join(", ")}</div>`
      : "";
  const autoFail = meta.auto_failed ? `<div class="rb-autofail">⚠ ${t("t_rect_autofail")}</div>` : "";
  const psConfirm = (meta.pred_summary || []).filter((p) => p.supports && p.wanted && p.strength >= 0.3);
  const psLine = psConfirm.length
    ? `<div class="rb-pred">${t("t_rect_pred_confirms")}: ${psConfirm.map((p) => t("rp_" + p.key)).join(", ")}</div>`
    : "";
  $("rect-best").innerHTML =
    `<div class="rb-label">${t("t_rect_best")}</div>
     <div class="rb-time">${b.time}</div>
     <div class="rb-meta">${t("t_rect_asc")} ${b.asc_deg}° ${b.asc_sign} · ${t("t_rect_mc")} ${b.mc_sign}</div>
     ${winLine}
     ${relLine}
     ${autoFail}
     ${psLine}
     ${altLine}
     <div class="rb-conf">${t("t_rect_checked").replace("{n}", meta.candidates_checked)}</div>
     ${ascInfo}`;

  // Смена знака Солнца/Луны/Асцендента в интервале (важно для ректификации).
  const lc = (data.meta.luminary_changes || [])
    .map(
      (c) =>
        `<div class="rect-lc-row">⚠ ${t("t_rect_change")
          .replace("{p}", c.point)
          .replace("{a}", c.from_sign)
          .replace("{b}", c.to_sign)}${c.time ? ` (~${c.time})` : ""}</div>`
    )
    .join("");
  $("rect-luminary").innerHTML = lc
    ? `<div class="rect-lc">${lc}</div>`
    : "";

  $("rect-candidates").innerHTML = data.top
    .map(
      (c) =>
        `<div class="rect-cand-row ${c.time === b.time ? "is-best" : ""}">
          <span class="rc-time">${c.time}</span>
          <div class="rc-track"><div class="rc-fill" style="width:${c.confidence}%"></div></div>
          <span class="rc-val">${c.confidence}%</span>
        </div>`
    )
    .join("");

  if (b.breakdown && b.breakdown.length) {
    let rows = `<tr><th>${t("col_date")}</th><th>${t("col_event")}</th><th>${t("col_match")}</th><th>${t("col_orb")}</th></tr>`;
    for (const ev of b.breakdown) {
      rows += `<tr>
        <td>${formatDate(ev.date)}</td>
        <td>${ev.label || "—"}</td>
        <td>${ev.factor}</td>
        <td>${ev.orb != null ? ev.orb + "°" : "—"}</td>
      </tr>`;
    }
    $("rect-breakdown").innerHTML = rows;
    $("rect-breakdown").style.display = "";
  } else {
    // Подбор только по анкете Асцендента — таблицы событий нет.
    $("rect-breakdown").innerHTML = "";
    $("rect-breakdown").style.display = "none";
  }
}

// ---------- Рендер: прогноз ----------
const FC_NATURE = { harmonious: "good", tense: "bad" };

function renderForecast(data) {
  showResults();
  $("chart-area").classList.add("hidden");
  $("data-grids").classList.add("hidden");
  $("forecast-view").classList.remove("hidden");

  const pf = data.profection;
  const pm = data.progressed_moon;

  $("forecast-summary").innerHTML =
    `<h3>${t("t_fc_for")}: ${data.natal_meta.name}</h3>` +
    summaryBlock(data.natal_meta, null, [
      [t("t_fc_period"), `${formatDate(data.start)} — ${formatDate(data.end)}`],
      [t("forecast_location"), data.location_meta?.city || "—"],
      [t("sum_tz"), data.location_meta?.tz_str || "—"],
      [t("t_fc_age"), `${pf.age} ${t("t_fc_age_unit")}`],
      [t("t_fc_house_year"), `${houseShort(pf.house_num)} (${pf.sphere})`],
      [t("t_fc_lord_year"), pf.lord ? `${pf.lord.name_ru} ${pf.lord.sign_loc || pf.lord.sign_ru}` : "—"],
    ]) +
    `<p class="score-hint" style="margin-top:12px">${data.summary}</p>`;

  $("forecast-blocks").innerHTML =
    `<div class="portrait-card"><h3>${t("t_fc_profection")}</h3><p>${pf.text}</p></div>` +
    `<div class="portrait-card"><h3>${t("t_fc_prog_moon")}</h3><p>${pm.text}</p></div>`;

  // Прогноз по сферам жизни — главное: «что вас ждёт».
  const evTitle = document.querySelector("#forecast-view .forecast-events-title");
  if (evTitle) {
    evTitle.style.display = "";
    evTitle.textContent = t("fc_spheres_title");
  }
  const sf = data.sphere_forecast || [];
  $("forecast-list").innerHTML =
    `<p class="section-note">${t("fc_spheres_hint")}</p>` + sf.map(fcSphereCard).join("");
}

function fcSphereCard(s) {
  const highlights = (s.highlights || []).length
    ? `<div class="fc-sphere-hl"><div class="fc-hl-label">${t("fc_influences")}</div>` +
      s.highlights
        .map((h) => {
          const cls = FC_NATURE[h.nature] || "";
          return `<div class="fc-hl ${cls}">
            <div class="fc-hl-head">
              <span class="glyph">${h.p1_symbol}</span>${h.p1_ru}
              <span>${h.aspect_symbol} ${h.aspect_ru}</span>
              <span class="glyph">${h.p2_symbol}</span>${h.p2_ru}
              <span class="fc-event-date">${formatDateTime(h.exact_datetime || h.date)}</span>
            </div>
            <p>${h.text}</p>
          </div>`;
        })
        .join("") +
      `</div>`
    : "";
  return `<div class="fc-sphere-card tone-${s.tone}">
    <div class="fc-sphere-head">
      <span class="fc-sphere-ic">${s.icon}</span>
      <span class="fc-sphere-name">${s.name}</span>
      <span class="fc-tone-badge tone-${s.tone}">${t("tone_" + s.tone)}</span>
    </div>
    <p class="fc-sphere-text">${s.text}</p>
    ${highlights}
  </div>`;
}

// ---------- Рендер: ведический календарь (Панчанг) ----------
let lastVedic = null;

function renderVedic(data) {
  showResults();
  $("chart-area").classList.add("hidden");
  $("data-grids").classList.add("hidden");
  $("vedic-view").classList.remove("hidden");
  lastVedic = data;

  const MONTHS_RU_NOM = ["Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"];
  const monthName = (LANG === "en" ? MONTHS_EN : MONTHS_RU_NOM)[data.month - 1];
  const monthTitle = `${monthName} ${data.year}`;
  const forWhom = data.personalized ? "" : ` · ${t("vedic_general")}`;

  $("vedic-head").innerHTML =
    `<h3>${t("tab_vedic")} — ${monthTitle}${forWhom}</h3>
     <div class="vedic-legend">
       <span><i class="vlg-dot vlg-good"></i>${t("leg_good")}: ${data.counts.good}</span>
       <span><i class="vlg-dot vlg-neutral"></i>${t("leg_neutral")}: ${data.counts.neutral}</span>
       <span><i class="vlg-dot vlg-bad"></i>${t("leg_bad")}: ${data.counts.bad}</span>
     </div>
     <div class="vedic-actions">
       <button class="vedic-btn" id="vedic-csv">${t("vedic_download")}</button>
     </div>`;

  // Сетка: понедельник-первый
  const dow = LANG === "en"
    ? ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    : ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"];
  let grid = dow.map((d) => `<div class="vedic-dow">${d}</div>`).join("");
  const firstDow = data.days.length ? data.days[0].weekday_idx : 0; // 0=Mon
  for (let i = 0; i < firstDow; i++) grid += `<div class="vedic-cell empty"></div>`;
  data.days.forEach((d, i) => {
    grid += `<div class="vedic-cell ${d.quality}" data-i="${i}">
      <div class="vc-day">${d.day}</div>
      <div class="vc-nak">${d.nakshatra_name}</div>
      <div class="vc-tithi">${d.tithi_name}${d.tara ? " · " + d.tara.name : ""}</div>
    </div>`;
  });
  $("vedic-grid").innerHTML = grid;
  $("vedic-detail").innerHTML = `<span style="color:var(--text-dim)">${t("vd_click")}</span>`;

  $("vedic-grid").querySelectorAll(".vedic-cell[data-i]").forEach((cell) => {
    cell.addEventListener("click", () => showVedicDay(data.days[+cell.dataset.i]));
  });
  $("vedic-csv").addEventListener("click", () => downloadVedicCsv(data));
}

function showVedicDay(d) {
  const taraLine = d.tara ? `<div class="vd-row"><b>${t("vd_tara")}:</b> ${d.tara.name}</div>` : "";
  $("vedic-detail").innerHTML =
    `<h4>${formatDate(d.date)} · ${d.weekday}</h4>
     <div class="vd-quality vd-${d.quality}">${d.quality_ru}</div>
     ${d.summary ? `<p class="vd-summary">${d.summary}</p>` : ""}
     ${d.day_advice ? `<div class="vd-advice"><b>${t("vd_advice")}:</b> ${d.day_advice}</div>` : ""}
     ${d.note ? `<div class="vd-note">⚠ ${d.note}</div>` : ""}
     <details class="vd-terms">
       <summary>${t("vd_terms")}</summary>
       <div class="vd-row"><b>${t("vd_nakshatra")}:</b> ${d.nakshatra} ${d.nakshatra_name} — ${d.nak_meaning}</div>
       <div class="vd-row"><b>${t("vd_tithi")}:</b> ${d.tithi} ${d.tithi_name} (${d.paksha})</div>
       <div class="vd-row"><b>${t("vd_phase")}:</b> ${d.paksha_advice}</div>
       ${taraLine}
     </details>`;
}

function downloadVedicCsv(data) {
  const head = [t("col_date"), t("vd_weekday"), t("vd_nakshatra"), t("vd_tithi"), t("vd_paksha"), t("vd_tara"), t("vd_quality"), t("vd_note")];
  const rows = data.days.map((d) => [
    d.date, d.weekday, d.nakshatra_name, d.tithi_name, d.paksha,
    d.tara ? d.tara.name : "", d.quality_ru, d.note,
  ]);
  const csv = [head, ...rows].map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(",")).join("\r\n");
  const blob = new Blob(["﻿" + csv], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `panchang-${data.year}-${String(data.month).padStart(2, "0")}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

// ---------- Рендер: календарь транзитов ----------
let lastCalByDate = {};
let lastCalLunar = {};
function renderCalendar(data) {
  showResults();
  $("chart-area").classList.add("hidden");
  $("data-grids").classList.add("hidden");
  $("calendar-view").classList.remove("hidden");

  $("calendar-summary").innerHTML =
    `<h3>${t("cal_title")}</h3>` +
    summaryBlock(
      data.natal_meta,
      null,
      [
        [t("t_fc_period"), `${formatDate(data.start)} — ${formatDate(data.end)}`],
        [t("forecast_location"), data.location_meta?.city || "—"],
        [t("sum_tz"), data.location_meta?.tz_str || "—"],
        [t("cal_exact"), `${data.count}`],
      ]
    ) +
    `<p class="section-note">${t("cal_luna_hint")}</p>`;

  // Группируем события по дате.
  const byDate = {};
  for (const e of data.events) (byDate[e.date] = byDate[e.date] || []).push(e);
  lastCalByDate = byDate;
  lastCalLunar = data.lunar || {};

  // Сетки по месяцам периода.
  const [sy, sm] = data.start.split("-").map(Number);
  const [ey, em] = data.end.split("-").map(Number);
  const dow = LANG === "en"
    ? ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    : ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"];
  const monthNames = LANG === "en"
    ? MONTHS_EN
    : ["Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"];

  let html = "";
  let y = sy, mo = sm, guard = 0;
  while ((y < ey || (y === ey && mo <= em)) && guard < 30) {
    guard++;
    const daysIn = new Date(y, mo, 0).getDate();
    const firstDow = (new Date(y, mo - 1, 1).getDay() + 6) % 7; // Пн = 0
    let cells = dow.map((d) => `<div class="cal-dow">${d}</div>`).join("");
    for (let i = 0; i < firstDow; i++) cells += `<div class="cal-cell empty-cell"></div>`;
    for (let d = 1; d <= daysIn; d++) {
      const ds = `${y}-${String(mo).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
      const evs = byDate[ds] || [];
      const lun = lastCalLunar[ds];
      const moon = lun ? `<div class="cc-moon" title="${lun.phase_ru} · ${lun.sign_ru}">${lun.emoji}<span class="glyph">${lun.sign_symbol}</span></div>` : "";
      if (!evs.length) {
        cells += `<div class="cal-cell day-cell" data-date="${ds}"><div class="cc-day">${d}</div>${moon}</div>`;
      } else {
        const tone = evs.some((e) => e.nature === "tense") ? "bad"
          : (evs.some((e) => e.nature === "harmonious") ? "good" : "neutral");
        const dots = evs.slice(0, 6).map((e) => `<span class="cc-dot ${FC_NATURE[e.nature] || "neutral"}"></span>`).join("");
        cells += `<div class="cal-cell day-cell has-events ${tone}" data-date="${ds}">
          <div class="cc-day">${d}</div>${moon}
          <div class="cc-dots">${dots}</div>
          <div class="cc-count">${evs.length}</div>
        </div>`;
      }
    }
    html += `<div class="cal-month"><div class="cal-month-title">${monthNames[mo - 1]} ${y}</div><div class="cal-grid">${cells}</div></div>`;
    mo++;
    if (mo > 12) { mo = 1; y++; }
  }
  $("calendar-grids").innerHTML = html || `<div class="cal-empty">${t("cal_empty")}</div>`;
  $("calendar-detail").innerHTML = `<span style="color:var(--text-dim)">${t("vd_click")}</span>`;

  $("calendar-grids").querySelectorAll(".cal-cell.day-cell").forEach((cell) => {
    cell.addEventListener("click", () => {
      $("calendar-grids").querySelectorAll(".cal-cell.selected").forEach((c) => c.classList.remove("selected"));
      cell.classList.add("selected");
      showCalendarDay(cell.dataset.date);
    });
  });
}

function showCalendarDay(date) {
  const evs = lastCalByDate[date] || [];
  const lun = lastCalLunar[date];
  let html = `<h4>${formatDate(date)}</h4>`;
  if (lun) {
    html += `<div class="cal-luna-card">
      <div class="cal-luna-head">${lun.emoji} ${lun.phase_ru} · <span class="glyph">${lun.sign_symbol}</span> ${lun.sign_ru}</div>
      ${lun.mood ? `<p><b>${t("luna_mood")}:</b> ${lun.mood}</p>` : ""}
      ${lun.advice ? `<p><b>${t("luna_advice")}:</b> ${lun.advice}</p>` : ""}
    </div>`;
  }
  if (!evs.length) {
    html += `<p class="section-note">${t("luna_no_aspects")}</p>`;
  }
  html += evs
    .map((e) => {
      const cls = FC_NATURE[e.nature] || "";
      return `<div class="fc-event ${cls}">
        <div class="fc-event-head">
          <span class="glyph">${e.p1_symbol}</span>${e.p1_ru}
          <span>${e.aspect_symbol} ${e.aspect_ru}</span>
          <span class="glyph">${e.p2_symbol}</span>${e.p2_ru}
          <span class="fc-event-date">${formatDateTime(e.exact_datetime || e.date)} · ${t("orb")} ${e.orb}°</span>
        </div>
        ${e.interp ? `<p>${e.interp}</p>` : ""}
      </div>`;
    })
    .join("");
  $("calendar-detail").innerHTML = html;
}

// ========================================================================== //
//  Личный кабинет: авторизация и сохранённые карты
// ========================================================================== //
const TOKEN_KEY = "astro_token";
let authMode = "login";

const getToken = () => localStorage.getItem(TOKEN_KEY);
const setToken = (t) => localStorage.setItem(TOKEN_KEY, t);
const clearToken = () => localStorage.removeItem(TOKEN_KEY);

function authHeaders() {
  const t = getToken();
  return { "Accept-Language": LANG, ...(t ? { Authorization: `Bearer ${t}` } : {}) };
}

let IS_PREMIUM = false;
let PREMIUM_UNTIL = null;
let HAS_CONSULT = false;
let REPORT_CREDITS = 0; // разовые PDF-отчёты (без подписки)
// Telegram астролога для оплаченных консультаций (тарифы «Премиум+»)
const ASTROLOGER_TG = "https://t.me/Astrosmap";

function openPaywall(status) {
  // Полный платный раздел (не натал) → размытое превью прямо в области результата.
  if (mode && mode !== "natal") { showLockedPreview(mode); return; }
  // Надстройки над наталом (глубокий разбор, PDF) → сразу окно.
  if (!getToken()) {
    $("auth-modal").classList.remove("hidden");
  } else {
    openPremiumModal();
  }
}

const MODE_TAB_KEY = {
  transit: "tab_transit", synastry: "tab_synastry", return: "tab_return",
  progression: "tab_progression", calendar: "tab_calendar", forecast: "tab_forecast",
  rectification: "tab_rectification", vedic: "tab_vedic",
};
// Размытое превью платного раздела: показываем, что «за деньгами», + призыв.
function showLockedPreview(m) {
  showResults();
  $("chart-area").classList.add("hidden");
  $("data-grids").classList.add("hidden");
  $("guest-teaser").classList.add("hidden");
  $("result-toolbar").classList.add("hidden");
  const name = t(MODE_TAB_KEY[m] || "tab_transit");
  const guest = !getToken();
  const mock =
    `<div class="lp-mock" aria-hidden="true">
       <div class="lp-mock-summary"></div>
       <div class="lp-mock-rows">${Array.from({ length: 5 }).map(() =>
         `<div class="lp-mock-row"><span></span><span></span><span></span></div>`).join("")}</div>
     </div>`;
  $("locked-preview").innerHTML = mock +
    `<div class="lp-card">
       <div class="lp-lock">🔒</div>
       <h3>${t("locked_title").replace("{name}", name)}</h3>
       <p>${t("locked_sub")}</p>
       <ul class="premium-benefits">
         <li>${t("premium_ben_1")}</li>
         <li>${t("premium_ben_2")}</li>
         <li>${t("premium_ben_3")}</li>
       </ul>
       <button id="lp-cta" class="btn-primary">${guest ? t("locked_cta_guest") : t("locked_cta_premium")}</button>
     </div>`;
  $("locked-preview").classList.remove("hidden");
  $("lp-cta").addEventListener("click", () => {
    if (guest) { $("auth-modal").classList.remove("hidden"); setAuthMode("register"); }
    else openPremiumModal();
  });
}

function openPremiumModal() {
  const st = $("premium-status");
  if (IS_PREMIUM && PREMIUM_UNTIL) {
    st.textContent = `✦ ${t("premium_active_until")} ${formatDate(new Date(PREMIUM_UNTIL * 1000).toISOString().slice(0, 10))}`;
    st.classList.remove("hidden");
  } else {
    st.classList.add("hidden");
  }
  const cs = $("consult-status");
  if (HAS_CONSULT) {
    cs.innerHTML = `🔮 ${t("consult_ready")} <a href="${ASTROLOGER_TG}" target="_blank" rel="noopener">${t("consult_link")}</a>`;
    cs.classList.remove("hidden");
  } else {
    cs.classList.add("hidden");
  }
  const rc = $("report-status");
  if (rc) {
    if (!IS_PREMIUM && REPORT_CREDITS > 0) {
      rc.textContent = `📄 ${t("report_credits_have").replace("{n}", REPORT_CREDITS)}`;
      rc.classList.remove("hidden");
    } else {
      rc.classList.add("hidden");
    }
  }
  $("premium-error").classList.add("hidden");
  $("premium-modal").classList.remove("hidden");
}

function refreshPremiumBtn() {
  const btn = $("premium-btn");
  if (IS_PREMIUM && PREMIUM_UNTIL) {
    btn.textContent = `✦ ${t("premium_until_btn")} ${formatDate(new Date(PREMIUM_UNTIL * 1000).toISOString().slice(0, 10))}`;
    btn.classList.add("premium-on");
  } else {
    btn.textContent = t("premium_btn");
    btn.classList.remove("premium-on");
  }
  refreshLock();
}

// Статус подписки на никнейме: премиум — золотой перелив, без — обычный. Клик по имени → подсказка.
function refreshLock() {
  const name = $("user-name");
  const pop = $("lock-popover");
  if (!name || !pop) return;
  const gem = $("premium-gem");
  const isPrem = IS_PREMIUM && PREMIUM_UNTIL;
  if (gem) gem.classList.toggle("hidden", !isPrem);
  if (isPrem) {
    name.classList.add("premium-name");
    pop.innerHTML = `<b>${t("lock_prem_title")}</b><p>${t("lock_prem_until").replace(
      "{d}", formatDate(new Date(PREMIUM_UNTIL * 1000).toISOString().slice(0, 10)))}</p>`;
  } else {
    name.classList.remove("premium-name");
    pop.innerHTML = `<b>${t("lock_free_title")}</b><p>${t("lock_free_text")}</p>` +
      `<button class="adm-mini" id="lock-buy">${t("premium_btn")}</button>`;
  }
}
$("user-name").addEventListener("click", (e) => {
  e.stopPropagation();
  $("lock-popover").classList.toggle("hidden");
});
$("lock-popover").addEventListener("click", (e) => {
  if (e.target.id === "lock-buy") {
    $("lock-popover").classList.add("hidden");
    openPremiumModal();
  }
});
document.addEventListener("click", (e) => {
  if (!e.target.closest(".user-id")) $("lock-popover").classList.add("hidden");
});

$("premium-btn").addEventListener("click", openPremiumModal);
$("premium-close").addEventListener("click", () => $("premium-modal").classList.add("hidden"));
$("premium-modal").addEventListener("click", (e) => {
  if (e.target.id === "premium-modal") $("premium-modal").classList.add("hidden");
});
// Deep-link из приложения: astrosmap.ru/#premium сразу открывает окно подписки.
if (location.hash === "#premium") openPremiumModal();
window.addEventListener("hashchange", () => {
  if (location.hash === "#premium") openPremiumModal();
  if (location.hash === "#glossary") openGlossary();
});
// astrosmap.ru/#glossary — раскрывает словарь терминов и прокручивает к нему.
function openGlossary() {
  const g = document.getElementById("glossary-details");
  if (!g) return;
  g.open = true;
  g.scrollIntoView({ behavior: "smooth", block: "start" });
}
if (location.hash === "#glossary") setTimeout(openGlossary, 300);
document.querySelectorAll("#premium-plans [data-plan]").forEach((btn) => {
  btn.addEventListener("click", async () => {
    btn.disabled = true;
    try {
      const data = await postJSON("/api/billing/create", { plan: btn.dataset.plan });
      window.location.href = data.confirmation_url;
    } catch (err) {
      $("premium-error").textContent = err.message;
      $("premium-error").classList.remove("hidden");
      btn.disabled = false;
    }
  });
});

// Гейт премиум-функций на клиенте (глубокий отчёт и PDF); серверные — по 402.
function premiumGate() {
  if (IS_PREMIUM) return true;
  openPaywall(getToken() ? 402 : 401);
  return false;
}

async function checkPaymentReturn() {
  if (!window.location.search.includes("payment=check") || !getToken()) return;
  history.replaceState(null, "", window.location.pathname);
  try {
    const data = await postJSON("/api/billing/check", {});
    if (data.activated) {
      // подписка и/или консультация — при «только консультации» premium не включается
      IS_PREMIUM = !!data.premium;
      PREMIUM_UNTIL = data.subscription && data.subscription.expires_at;
      HAS_CONSULT = !!data.consultation;
      REPORT_CREDITS = data.report_credits || 0;
      refreshPremiumBtn();
      alert(t("premium_activated"));
      if (HAS_CONSULT) openPremiumModal(); // сразу показать контакт для консультации
    } else {
      alert(t("premium_pending"));
    }
  } catch (e) {}
}

// Ссылки из писем: /?email-token=… (подтверждение почты) и /?reset-token=… (сброс пароля).
let RESET_TOKEN = "";
async function checkEmailLinks() {
  const params = new URLSearchParams(window.location.search);
  const verify = params.get("email-token");
  const reset = params.get("reset-token");
  if (!verify && !reset) return;
  history.replaceState(null, "", window.location.pathname);
  if (verify) {
    try {
      const res = await postJSON("/api/auth/verify-email", { token: verify });
      if (res && res.report_gift) {
        REPORT_CREDITS = (REPORT_CREDITS || 0) + 1;
        alert(t("email_verified_gift"));
      } else {
        alert(t("email_verified_ok"));
      }
    } catch (e) {
      alert(t("email_verify_fail"));
    }
  }
  if (reset) {
    RESET_TOKEN = reset;
    $("auth-modal").classList.remove("hidden");
    setAuthMode("reset");
  }
}

function updateAuthUI(username, isAdmin = false) {
  if (username) {
    $("login-btn").classList.add("hidden");
    $("user-box").classList.remove("hidden");
    $("user-name").textContent = username;
    $("saved-block").classList.remove("hidden");
    $("admin-btn").classList.toggle("hidden", !isAdmin);
    // показать кнопку печати, если результат уже на экране
    $("result-toolbar").classList.toggle("hidden", $("results").classList.contains("hidden"));
    loadDaily();
  } else {
    $("login-btn").classList.remove("hidden");
    $("user-box").classList.add("hidden");
    $("saved-block").classList.add("hidden");
    $("saved-list").innerHTML = "";
    if ($("saved-list")) $("saved-list")._profiles = [];
    $("admin-btn").classList.add("hidden");
    $("result-toolbar").classList.add("hidden");
    $("b-saved-wrap").classList.add("hidden");
    $("daily-block").classList.add("hidden");
  }
}

// --- Транзит дня («Ваш день») — карточка на главной для вошедших ---
async function loadDaily() {
  const block = $("daily-block");
  if (!getToken()) { block.classList.add("hidden"); return; }
  // Показываем только в «покойном» состоянии, когда результата на экране нет.
  if ($("results").classList.contains("hidden") === false) { block.classList.add("hidden"); return; }
  try {
    let url = "/api/daily";
    try {
      const loc = JSON.parse(localStorage.getItem("astro_current_location") || "null");
      if (loc && Number.isFinite(+loc.lat) && Number.isFinite(+loc.lng)) {
        const qs = new URLSearchParams({
          current_lat: loc.lat, current_lng: loc.lng,
          current_tz: loc.tz_str || "", current_city: loc.city || "",
        });
        url += `?${qs}`;
      }
    } catch (_) {}
    const r = await fetch(`${url}${url.includes("?") ? "&" : "?"}lang=${LANG}`, { headers: authHeaders() });
    if (!r.ok) { block.classList.add("hidden"); return; }
    const d = await r.json();
    renderDaily(d);
    block.classList.remove("hidden");
    $("placeholder").classList.add("hidden");
  } catch { block.classList.add("hidden"); }
}

function renderDaily(d) {
  const block = $("daily-block");
  if (!d.has_primary) {
    block.innerHTML =
      `<div class="daily-head"><span class="daily-glyph">☀</span><h3>${t("daily_title")}</h3></div>
       <p class="daily-hint">${t("daily_no_primary")}</p>
       <button class="daily-cabinet-link" id="daily-open-cabinet">${t("daily_pick_primary")}</button>`;
    $("daily-open-cabinet").addEventListener("click", () => { $("cabinet-modal").classList.remove("hidden"); loadCabinet(); });
    return;
  }
  const dateStr = formatDate(d.date);
  const items = d.aspects.length
    ? d.aspects.map((a) =>
        `<div class="daily-aspect ${a.nature}">
           <div class="daily-aspect-head">${a.p2_symbol} ${a.p2_ru} ${a.aspect_symbol} ${a.p1_symbol} ${a.p1_ru}</div>
           <div class="daily-aspect-text">${escapeHtml(a.interp || "")}</div>
         </div>`).join("")
    : `<p class="daily-hint">${t("daily_calm")}</p>`;
  const cta = d.premium
    ? `<button class="daily-week-btn" id="daily-week">${t("daily_week_cta")}</button>`
    : `<button class="daily-week-btn locked" id="daily-week">🔒 ${t("daily_week_locked")}</button>`;
  block.innerHTML =
    `<div class="daily-head"><span class="daily-glyph">☀</span>
       <h3>${t("daily_title")} · ${escapeHtml(d.person)}</h3><span class="daily-date">${dateStr}</span></div>
     <div class="daily-aspects">${items}</div>
     ${cta}`;
  $("daily-week").addEventListener("click", () => {
    if (!d.premium) { openPaywall(402); return; }
    const tab = document.querySelector('.tab[data-mode="forecast"]');
    if (tab) tab.click();
  });
}

// --- Модальное окно ---
$("login-btn").addEventListener("click", () => {
  $("auth-modal").classList.remove("hidden");
  $("auth-error").classList.add("hidden");
});
$("teaser-register").addEventListener("click", () => {
  $("auth-modal").classList.remove("hidden");
  setAuthMode("register");
});
$("modal-close").addEventListener("click", () => $("auth-modal").classList.add("hidden"));
$("auth-modal").addEventListener("click", (e) => {
  if (e.target.id === "auth-modal") $("auth-modal").classList.add("hidden");
});

// Режимы окна авторизации: login / register / forgot (письмо со сбросом) / reset (новый пароль).
function setAuthMode(mode) {
  authMode = mode;
  const isReg = mode === "register", isForgot = mode === "forgot", isReset = mode === "reset";
  document.querySelectorAll(".auth-tab").forEach((tb) => tb.classList.toggle("active", tb.dataset.auth === mode));
  $("auth-username-row").classList.toggle("hidden", isForgot || isReset);
  $("auth-email-row").classList.toggle("hidden", !(isReg || isForgot));
  $("auth-password-row").classList.toggle("hidden", isForgot);
  $("auth-password2-row").classList.toggle("hidden", !isReg);
  // подпись поля пароля: в режиме сброса — «новый пароль»
  const pwLabel = $("auth-password-row");
  pwLabel.setAttribute("data-i18n", isReset ? "new_password_label" : "password");
  $("auth-forgot").classList.toggle("hidden", mode !== "login");
  $("register-gift").classList.toggle("hidden", !isReg);
  $("consent-row").classList.toggle("hidden", !isReg);
  $("auth-submit").setAttribute("data-i18n", isReg ? "do_register" : isForgot ? "do_forgot" : isReset ? "do_reset" : "do_login");
  $("auth-error").classList.add("hidden");
  applyI18n();
}

document.querySelectorAll(".auth-tab").forEach((tab) => {
  tab.addEventListener("click", () => setAuthMode(tab.dataset.auth));
});

$("auth-forgot").addEventListener("click", (e) => {
  e.preventDefault();
  setAuthMode("forgot");
});

// Юридические документы (политика ПДн и пользовательское соглашение)
// делегирование: ссылки пересоздаются при смене языка и есть в подвале
document.addEventListener("click", (e) => {
  const id = e.target && e.target.id;
  if (id === "privacy-link" || id === "footer-privacy") {
    e.preventDefault();
    $("privacy-modal").classList.remove("hidden");
  } else if (id === "terms-link" || id === "footer-terms") {
    e.preventDefault();
    $("terms-modal").classList.remove("hidden");
  } else if (id === "footer-offer" || id === "premium-offer-link" || id === "terms-offer-link") {
    e.preventDefault();
    $("offer-modal").classList.remove("hidden");
  }
});
$("privacy-close").addEventListener("click", () => $("privacy-modal").classList.add("hidden"));
$("privacy-modal").addEventListener("click", (e) => {
  if (e.target.id === "privacy-modal") $("privacy-modal").classList.add("hidden");
});
$("terms-close").addEventListener("click", () => $("terms-modal").classList.add("hidden"));
$("terms-modal").addEventListener("click", (e) => {
  if (e.target.id === "terms-modal") $("terms-modal").classList.add("hidden");
});
$("offer-close").addEventListener("click", () => $("offer-modal").classList.add("hidden"));
$("offer-modal").addEventListener("click", (e) => {
  if (e.target.id === "offer-modal") $("offer-modal").classList.add("hidden");
});

$("auth-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const username = $("auth-username").value.trim();
  const password = $("auth-password").value;
  const email = $("auth-email").value.trim();
  if (authMode === "forgot") {
    if (!email.includes("@")) return showAuthError(t("email_need"));
    try {
      await postJSON("/api/auth/forgot", { email, lang: LANG });
      showAuthError(t("forgot_sent"));
    } catch (ex) { showAuthError(ex.message); }
    return;
  }
  if (authMode === "reset") {
    if (password.length < 8) return showAuthError(t("auth_short"));
    try {
      await postJSON("/api/auth/reset", { token: RESET_TOKEN, new_password: password });
      alert(t("reset_done"));
      $("auth-password").value = "";
      setAuthMode("login");
    } catch (ex) { showAuthError(ex.message); }
    return;
  }
  if (username.length < 2 || password.length < 4) {
    return showAuthError(t("auth_short"));
  }
  if (authMode === "register" && !email.includes("@")) {
    return showAuthError(t("email_need"));
  }
  if (authMode === "register" && password !== $("auth-password2").value) {
    return showAuthError(t("password_mismatch"));
  }
  if (authMode === "register" && !$("terms-check").checked) {
    return showAuthError(t("auth_terms"));
  }
  if (authMode === "register" && !$("consent-check").checked) {
    return showAuthError(t("auth_consent"));
  }
  try {
    const url = authMode === "register" ? "/api/auth/register" : "/api/auth/login";
    const data = await postJSON(url, authMode === "register"
      ? {
          username, password, email, lang: LANG,
          privacy_accepted: true,
          terms_accepted: true,
          privacy_version: "2026-09-01",
          terms_version: "2026-09-01",
          consent_source: "web",
        }
      : { username, password });
    setToken(data.token);
    updateAuthUI(data.username, data.is_admin);
    $("auth-modal").classList.add("hidden");
    $("auth-password").value = "";
    await loadProfiles();
    // подтянуть статус подписки
    try {
      const me = await (await fetch("/api/auth/me", { headers: authHeaders() })).json();
      IS_PREMIUM = !!me.premium;
      PREMIUM_UNTIL = me.premium_until;
      HAS_CONSULT = !!me.consultation;
      REPORT_CREDITS = me.report_credits || 0;
      refreshPremiumBtn();
    } catch (e) {}
  } catch (ex) {
    showAuthError(ex.message);
  }
});

function showAuthError(msg) {
  $("auth-error").textContent = msg;
  $("auth-error").classList.remove("hidden");
}

$("logout-btn").addEventListener("click", async () => {
  // Сообщаем серверу — токен отзывается (реальный выход), затем чистим локально.
  try { await fetch("/api/auth/logout", { method: "POST", headers: authHeaders() }); } catch (e) {}
  clearToken();
  IS_PREMIUM = false;
  PREMIUM_UNTIL = null;
  HAS_CONSULT = false;
  REPORT_CREDITS = 0;
  refreshPremiumBtn();
  updateAuthUI(null);
});

// --- Кабинет ---
$("cabinet-btn").addEventListener("click", () => {
  $("cabinet-modal").classList.remove("hidden");
  cabinetTab("profile"); // всегда открываем на «Профиле»
  loadCabinet();
});
$("cabinet-close").addEventListener("click", () => $("cabinet-modal").classList.add("hidden"));
$("cabinet-modal").addEventListener("click", (e) => {
  if (e.target.id === "cabinet-modal") $("cabinet-modal").classList.add("hidden");
});
// Вкладки кабинета: показываем одну панель за раз.
function cabinetTab(name) {
  document.querySelectorAll(".cab-tab").forEach((tb) => tb.classList.toggle("active", tb.dataset.cab === name));
  ["profile", "people", "history", "payments"].forEach((p) =>
    $("cab-panel-" + p).classList.toggle("hidden", p !== name));
}
document.querySelectorAll(".cab-tab").forEach((tb) =>
  tb.addEventListener("click", () => cabinetTab(tb.dataset.cab)));

async function loadCabinet() {
  try {
    const [meR, profR, histR, payR] = await Promise.all([
      fetch("/api/auth/me", { headers: authHeaders() }),
      fetch("/api/profiles", { headers: authHeaders() }),
      fetch("/api/history", { headers: authHeaders() }),
      fetch("/api/billing/my", { headers: authHeaders() }),
    ]);
    if (!meR.ok) throw new Error();
    const me = await meR.json();
    const profiles = profR.ok ? await profR.json() : [];
    const history = histR.ok ? await histR.json() : [];
    const pays = payR.ok ? (await payR.json()).items : [];

    // Шапка кабинета: имя + статус-бейджи
    $("cab-head-name").textContent = me.username;
    const badges = [];
    if (me.premium && me.premium_until) {
      badges.push(`<span class="cab-badge cab-badge-prem">${t("cab_badge_premium").replace("{d}", formatDate(new Date(me.premium_until * 1000).toISOString().slice(0, 10)))}</span>`);
    } else {
      badges.push(`<span class="cab-badge cab-badge-free">${t("cab_badge_free")}</span>`);
    }
    if (me.report_credits > 0) {
      badges.push(`<span class="cab-badge cab-badge-report">${t("cab_badge_reports").replace("{n}", me.report_credits)}</span>`);
    }
    $("cab-badges").innerHTML = badges.join("");

    // Профиль: логин, почта (привязка/смена), смена пароля — кнопкой 🔑 в шапке
    const emailLine = me.email
      ? `<div class="cab-line"><b>${t("email_label")}:</b> ${escapeHtml(me.email)} ${me.email_verified ? "✓" : `<span class="cab-dim">${t("cab_email_unverified")}</span>`}</div>`
      : `<div class="cab-line cab-warn">${t("cab_email_none")}</div>`;
    $("cab-profile").innerHTML =
      `<h4>${t("cab_profile")}</h4>
       <div class="cab-line"><b>${t("username")}:</b> ${escapeHtml(me.username)}</div>
       ${emailLine}
       <div class="cab-email-form">
         <input type="email" id="cab-email-input" placeholder="you@example.com" value="${me.email ? escapeHtml(me.email) : ""}" />
         <button class="adm-mini" id="cab-email-save">${t("cab_email_attach")}</button>
         <span id="cab-email-msg" class="cab-dim"></span>
       </div>
       <button class="adm-mini" id="cab-passwd">${t("passwd_title")}</button>
       <div class="cab-danger-zone">
         <h5>${t("account_delete_title")}</h5>
         <p class="cab-dim">${t("account_delete_hint")}</p>
         <button class="adm-del" id="cab-delete-account">${t("account_delete_button")}</button>
       </div>`;
    $("cab-passwd").addEventListener("click", () => {
      $("cabinet-modal").classList.add("hidden");
      openPasswdModal();
    });
    $("cab-delete-account").addEventListener("click", async () => {
      const password = window.prompt(t("account_delete_password"));
      if (password === null) return;
      if (!password) { alert(t("account_delete_password_need")); return; }
      if (!window.confirm(t("account_delete_confirm"))) return;
      const button = $("cab-delete-account");
      button.disabled = true;
      try {
        const response = await fetch("/api/auth/account", {
          method: "DELETE",
          headers: { ...authHeaders(), "Content-Type": "application/json" },
          body: JSON.stringify({ password }),
        });
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(result.detail || t("account_delete_fail"));
        clearToken();
        IS_PREMIUM = false;
        PREMIUM_UNTIL = null;
        HAS_CONSULT = false;
        REPORT_CREDITS = 0;
        refreshPremiumBtn();
        updateAuthUI(null);
        $("cabinet-modal").classList.add("hidden");
        alert(t("account_delete_done"));
      } catch (error) {
        alert(error.message || t("account_delete_fail"));
        button.disabled = false;
      }
    });
    $("cab-email-save").addEventListener("click", async () => {
      const email = $("cab-email-input").value.trim();
      if (!email.includes("@")) { $("cab-email-msg").textContent = t("email_need"); return; }
      try {
        const r = await postJSON("/api/auth/email", { email, lang: LANG });
        $("cab-email-msg").textContent = r.sent ? t("cab_email_sent") : t("cab_email_saved");
      } catch (ex) { $("cab-email-msg").textContent = ex.message; }
    });

    // Подписка + переключатель еженедельного дайджеста
    const subLine = me.premium && me.premium_until
      ? `<div class="cab-line">✦ ${t("cab_sub_active")} ${formatDate(new Date(me.premium_until * 1000).toISOString().slice(0, 10))}</div>`
      : `<div class="cab-line cab-dim">${t("cab_sub_none")}</div>`;
    const canNotify = me.email && me.email_verified;
    const notifyRow = me.premium
      ? `<label class="cab-notify-row ${canNotify ? "" : "disabled"}">
           <input type="checkbox" id="cab-notify" ${me.notify_weekly ? "checked" : ""} ${canNotify ? "" : "disabled"} />
           <span>${t("cab_notify")}</span>
         </label>
         <div class="cab-dim">${canNotify ? t("cab_notify_hint") : t("cab_notify_need_email")}</div>`
      : "";
    $("cab-sub").innerHTML =
      `<h4>${t("cab_sub")}</h4>${subLine}
       <button class="adm-mini" id="cab-sub-buy">${t("cab_sub_buy")}</button>
       ${notifyRow}`;
    $("cab-sub-buy").addEventListener("click", () => {
      $("cabinet-modal").classList.add("hidden");
      openPremiumModal();
    });
    if ($("cab-notify")) {
      $("cab-notify").addEventListener("change", async function () {
        try { await postJSON("/api/notify", { weekly: this.checked }); }
        catch (ex) { this.checked = !this.checked; alert(ex.message); }
      });
    }

    // Мои люди: ★основной + заметка + запуск расчётов
    if (!profiles.length) {
      $("cab-people").innerHTML = `<div class="cab-dim">${t("cab_no_people")}</div>`;
    } else {
      $("cab-people").innerHTML = profiles.map((p) => {
        const d = p.data;
        const meta = `${d.day || "?"}.${d.month || "?"}.${d.year || "?"}${d.city ? " · " + escapeHtml(d.city) : ""}`;
        const isPrimary = p.id === me.primary_profile_id;
        const star = isPrimary
          ? `<button class="adm-mini primary-on" data-primary="${p.id}" data-on="1">${t("cab_is_primary")}</button>`
          : `<button class="adm-mini" data-primary="${p.id}" data-on="0">${t("cab_primary")}</button>`;
        // Карта без подписки: данные сохранены, но действия недоступны до продления.
        if (p.locked) {
          return `<div class="cab-person is-locked" data-id="${p.id}">
            <div class="cab-person-head"><b>🔒 ${escapeHtml(p.label)}</b> <span class="cab-dim">${meta}</span></div>
            <div class="cab-locked-note">${t("cab_locked")}
              <button class="adm-mini" data-unlock="1">${t("premium_btn")}</button>
            </div>
          </div>`;
        }
        return `<div class="cab-person ${isPrimary ? "is-primary" : ""}" data-id="${p.id}">
          <div class="cab-person-head"><b>${escapeHtml(p.label)}</b> <span class="cab-dim">${meta}</span></div>
          <input class="cab-note" data-note="${p.id}" placeholder="${t("cab_note_ph")}" value="${escapeHtml(p.note || "")}" />
          <div class="cab-person-actions">
            ${star}
            <button class="adm-mini" data-run="natal" data-id="${p.id}">${t("cab_run_natal")}</button>
            <button class="adm-mini" data-run="transit" data-id="${p.id}">${t("cab_run_transit")}</button>
            <button class="adm-mini" data-run="synastry" data-id="${p.id}">${t("cab_run_synastry")}</button>
            <button class="adm-mini" data-run="forecast" data-id="${p.id}">${t("cab_run_forecast")}</button>
          </div>
        </div>`;
      }).join("");
      $("cab-people")._profiles = profiles;
    }

    // История
    $("cab-history").innerHTML = !history.length
      ? `<div class="cab-dim">${t("cab_no_history")}</div>`
      : history.map((it) => {
          const when = formatDate(it.created_at.slice(0, 10));
          return `<div class="cab-hist-row">
            <span>${t("hist_kind_" + it.kind) || it.kind}</span>
            <span class="cab-dim">${escapeHtml(it.label || "")}</span>
            <span class="cab-dim">${when}</span>
            <button class="adm-mini" data-hist="${it.id}">${t("cab_repeat")}</button>
          </div>`;
        }).join("");
    $("cab-history")._items = history;

    // Платежи
    $("cab-payments").innerHTML = !pays.length
      ? `<div class="cab-dim">${t("cab_no_payments")}</div>`
      : `<table class="cab-pay-table">` + pays.map((p) =>
          `<tr><td>${formatDate(p.created_at.slice(0, 10))}</td><td>${escapeHtml(p.plan)}</td><td>${p.amount} ₽</td><td>${escapeHtml(p.status)}</td></tr>`
        ).join("") + `</table>`;
  } catch (e) {
    $("cab-profile").innerHTML = `<div class="cab-dim">${t("v_calc_failed")}</div>`;
  }
}

// Делегирование: заметки, запуск расчётов от человека, повтор из истории
$("cab-people").addEventListener("change", async (e) => {
  const id = e.target && e.target.dataset && e.target.dataset.note;
  if (!id) return;
  try { await postJSON(`/api/profiles/${id}/note`, { note: e.target.value }); } catch (ex) {}
});
$("cab-people").addEventListener("click", async (e) => {
  if (e.target && e.target.dataset && e.target.dataset.unlock) {
    openPremiumModal();
    return;
  }
  const star = e.target.closest("[data-primary]");
  if (star) {
    // Клик по звёздочке: назначить основным, повторный клик по основному — снять.
    const id = +star.dataset.primary;
    const turnOff = star.dataset.on === "1";
    try {
      await postJSON("/api/profiles/primary", { profile_id: turnOff ? null : id });
      await loadCabinet();
      loadDaily();
    } catch (ex) { alert(ex.message); }
    return;
  }
  const btn = e.target.closest("[data-run]");
  if (!btn) return;
  const p = ($("cab-people")._profiles || []).find((x) => x.id === +btn.dataset.id);
  if (!p) return;
  const kind = btn.dataset.run;
  if (kind === "synastry") {
    // человек — вторым участником, себя вводит пользователь
    fillPersonB(p.data);
  } else {
    fillBirthData(p.data);
  }
  if (kind === "transit" && !$("transit-date").value) {
    const now = new Date();
    $("transit-date").value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
  }
  const tab = document.querySelector(`.tab[data-mode="${kind}"]`);
  if (tab) tab.click();
  $("cabinet-modal").classList.add("hidden");
  if (kind !== "synastry") $("birth-form").requestSubmit();
});
$("cab-history").addEventListener("click", (e) => {
  const btn = e.target.closest("[data-hist]");
  if (!btn) return;
  const it = ($("cab-history")._items || []).find((x) => x.id === +btn.dataset.hist);
  if (it) repeatHistory(it);
});

// --- Админ-панель ---
async function loadAdmin() {
  try {
    const [statsR, usersR, usageR, payR] = await Promise.all([
      fetch("/api/admin/stats", { headers: authHeaders() }),
      fetch("/api/admin/users", { headers: authHeaders() }),
      fetch("/api/admin/usage", { headers: authHeaders() }),
      fetch("/api/admin/payments", { headers: authHeaders() }),
    ]);
    if (!statsR.ok || !usersR.ok) throw new Error();
    const stats = await statsR.json();
    const users = await usersR.json();
    $("admin-stats").innerHTML =
      `<div class="admin-stat"><div class="as-num">${stats.users}</div><div class="as-label">${t("admin_users_total")}</div></div>
       <div class="admin-stat"><div class="as-num">${stats.profiles}</div><div class="as-label">${t("admin_charts_total")}</div></div>`;
    let rows = `<tr><th>#</th><th>${t("admin_col_user")}</th><th>${t("admin_col_email")}</th><th>${t("admin_col_registered")}</th><th>${t("admin_col_charts")}</th><th>${t("admin_col_premium")}</th><th>${t("admin_col_role")}</th><th></th></tr>`;
    for (const u of users) {
      const role = u.is_admin
        ? `<span class="adm-badge">${t("admin_role_admin")}</span>`
        : (u.is_banned ? `<span class="adm-badge adm-banned">${t("admin_role_banned")}</span>` : t("admin_role_user"));
      const prem = u.premium_until
        ? `${formatDate(new Date(u.premium_until * 1000).toISOString().slice(0, 10))}`
        : "—";
      const premBtns =
        `<button class="adm-mini" data-prem="${u.id}" data-days="7" title="+7">+7</button>` +
        `<button class="adm-mini" data-prem="${u.id}" data-days="30" title="+30">+30</button>` +
        `<button class="adm-mini" data-prem="${u.id}" data-days="365" title="+365">+365</button>` +
        (u.premium_until ? `<button class="adm-mini" data-prem="${u.id}" data-days="0">×</button>` : "");
      let actions = "";
      if (!u.is_admin) {
        actions = `<button class="adm-del" data-ban="${u.id}" data-banned="${u.is_banned ? 1 : 0}">${u.is_banned ? t("admin_unban") : t("admin_ban")}</button> ` +
                  `<button class="adm-del" data-del="${u.id}">${t("admin_delete")}</button>`;
      }
      const email = u.email
        ? `${escapeHtml(u.email)} ${u.email_verified ? "✓" : `<span class="cab-dim">?</span>`}`
        : `<span class="cab-dim">—</span>`;
      rows += `<tr><td>${u.id}</td><td>${escapeHtml(u.username)}</td><td>${email}</td><td>${formatDate(u.created_at.slice(0, 10))}</td><td>${u.charts}</td><td>${prem} ${premBtns}</td><td>${role}</td><td>${actions}</td></tr>`;
    }
    $("admin-users").innerHTML = rows;

    if (usageR.ok) {
      const usage = await usageR.json();
      $("admin-usage").innerHTML = usage.length
        ? `<tr><th>${t("admin_usage_fn")}</th><th>${t("admin_usage_30d")}</th><th>${t("admin_usage_total")}</th></tr>` +
          usage.map((u) => `<tr><td>${escapeHtml(u.endpoint)}</td><td>${u.recent}</td><td>${u.total}</td></tr>`).join("")
        : `<tr><td class="section-note">${t("admin_usage_empty")}</td></tr>`;
    }
    if (payR.ok) {
      const pay = await payR.json();
      $("admin-pay-summary").innerHTML =
        `<div class="admin-stat"><div class="as-num">${pay.revenue_total} ₽</div><div class="as-label">${t("admin_pay_total")}</div></div>
         <div class="admin-stat"><div class="as-num">${pay.revenue_30d} ₽</div><div class="as-label">${t("admin_pay_30d")}</div></div>`;
      $("admin-payments").innerHTML = pay.items.length
        ? `<tr><th>${t("admin_pay_datetime")}</th><th>${t("admin_pay_email")}</th><th>${t("admin_pay_plan")}</th><th>${t("admin_pay_amount")}</th><th>${t("admin_pay_status")}</th></tr>` +
          pay.items.map((p) => {
            const paid = p.status === "succeeded";
            const email = p.email ? escapeHtml(p.email) : `<span class="cab-dim">${escapeHtml(p.username || "#" + p.user_id)}</span>`;
            return `<tr class="${paid ? "pay-paid" : ""}"><td>${formatPayDateTime(p.created_at)}</td><td>${email}</td><td>${escapeHtml(p.plan_title || p.plan)}</td><td>${p.amount} ₽</td><td class="pay-st pay-st-${escapeHtml(p.status)}">${paid ? "✓ " : ""}${escapeHtml(payStatusLabel(p.status))}</td></tr>`;
          }).join("")
        : `<tr><td class="section-note">${t("admin_pay_empty")}</td></tr>`;
    }
  } catch {
    $("admin-stats").innerHTML = `<p class="section-note">${LANG === "en" ? "Failed to load admin data." : "Не удалось загрузить данные админки."}</p>`;
  }
}

$("admin-btn").addEventListener("click", () => {
  $("admin-modal").classList.remove("hidden");
  loadAdmin();
  textLimit = 60;
  loadTexts();
});

// Вкладки админки
document.querySelectorAll(".adm-tab").forEach((tab) => {
  tab.addEventListener("click", () => {
    document.querySelectorAll(".adm-tab").forEach((x) => x.classList.remove("active"));
    tab.classList.add("active");
    document.querySelectorAll(".adm-pane").forEach((p) => p.classList.add("hidden"));
    $("adm-pane-" + tab.dataset.atab).classList.remove("hidden");
  });
});

// --- Админ: редактор текстов ---
let textLimit = 60;
function escAttr(s) { return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;"); }

async function loadTexts() {
  const search = $("admin-text-search").value.trim();
  const onlyOv = $("admin-text-overridden").checked;
  try {
    const url = `/api/admin/texts?search=${encodeURIComponent(search)}&overridden=${onlyOv}&limit=${textLimit}`;
    const r = await fetch(url, { headers: authHeaders() });
    if (!r.ok) throw new Error("load");
    const data = await r.json();
    $("admin-text-count").textContent = t("admin_texts_count")
      .replace("{shown}", data.items.length).replace("{total}", data.total);
    $("admin-text-list").innerHTML = data.items.map((it) => `
      <div class="adm-text-row${it.overridden ? " is-overridden" : ""}" data-key="${escAttr(it.key)}">
        <div class="atr-head">
          <span class="atr-group">${it.group}</span>
          <code class="atr-key">${it.key}</code>
          ${it.overridden ? `<span class="atr-flag">${t("admin_texts_changed")}</span>` : ""}
        </div>
        <label class="atr-lbl">RU</label>
        <textarea class="atr-ru" rows="2">${escAttr(it.ru)}</textarea>
        <label class="atr-lbl">EN</label>
        <textarea class="atr-en" rows="2">${escAttr(it.en)}</textarea>
        <div class="atr-actions">
          <button class="atr-save">${t("admin_texts_save")}</button>
          <button class="atr-reset">${t("admin_texts_reset")}</button>
          <span class="atr-status"></span>
        </div>
      </div>`).join("") || `<p class="section-note">${t("admin_texts_empty")}</p>`;
    $("admin-text-more").classList.toggle("hidden", data.items.length >= data.total);
  } catch (e) {
    $("admin-text-list").innerHTML = `<p class="section-note">${LANG === "en" ? "Failed to load texts." : "Не удалось загрузить тексты."}</p>`;
  }
}

let textSearchTimer = null;
$("admin-text-search").addEventListener("input", () => {
  clearTimeout(textSearchTimer);
  textSearchTimer = setTimeout(() => { textLimit = 60; loadTexts(); }, 300);
});
$("admin-text-overridden").addEventListener("change", () => { textLimit = 60; loadTexts(); });
$("admin-text-more").addEventListener("click", () => { textLimit += 60; loadTexts(); });

async function loadTextAudit() {
  try {
    const r = await fetch("/api/admin/texts/audit?limit=100", { headers: authHeaders() });
    if (!r.ok) throw new Error("audit");
    const rows = await r.json();
    $("admin-audit-list").innerHTML = rows.length
      ? rows.map((a) => `<div class="adm-audit-row">
           <span class="aar-act ${a.action}">${a.action === "reset" ? "↺" : "✎"}</span>
           <code>${escAttr(a.key)}</code>
           <span class="aar-meta">${escAttr(a.username || "—")} · ${formatDate((a.created_at || "").slice(0, 10))}</span>
         </div>`).join("")
      : `<p class="section-note">${t("admin_audit_empty")}</p>`;
  } catch (e) {
    $("admin-audit-list").innerHTML = `<p class="section-note">${LANG === "en" ? "Failed to load history." : "Не удалось загрузить историю."}</p>`;
  }
}
const _adm_audit = document.querySelector(".adm-audit");
if (_adm_audit) _adm_audit.addEventListener("toggle", () => { if (_adm_audit.open) loadTextAudit(); });

$("admin-text-list").addEventListener("click", async (e) => {
  const row = e.target.closest(".adm-text-row");
  if (!row) return;
  const key = row.dataset.key;
  const status = row.querySelector(".atr-status");
  if (e.target.classList.contains("atr-save")) {
    const ru = row.querySelector(".atr-ru").value;
    const en = row.querySelector(".atr-en").value;
    status.textContent = "…";
    const r = await fetch("/api/admin/texts", {
      method: "POST",
      headers: { ...authHeaders(), "Content-Type": "application/json" },
      body: JSON.stringify({ key, ru, en }),
    });
    if (r.ok) {
      status.textContent = "✓";
      row.classList.add("is-overridden");
      if (!row.querySelector(".atr-flag")) {
        row.querySelector(".atr-head").insertAdjacentHTML("beforeend", `<span class="atr-flag">${t("admin_texts_changed")}</span>`);
      }
    } else {
      status.textContent = "✕";
    }
  } else if (e.target.classList.contains("atr-reset")) {
    status.textContent = "…";
    const r = await fetch("/api/admin/texts/reset", {
      method: "POST",
      headers: { ...authHeaders(), "Content-Type": "application/json" },
      body: JSON.stringify({ key }),
    });
    if (r.ok) loadTexts();
    else status.textContent = "✕";
  }
});
$("admin-close").addEventListener("click", () => $("admin-modal").classList.add("hidden"));
$("admin-modal").addEventListener("click", (e) => {
  if (e.target.id === "admin-modal") $("admin-modal").classList.add("hidden");
});
$("admin-users").addEventListener("click", async (e) => {
  const prem = e.target.closest("[data-prem]");
  if (prem) {
    const days = Number(prem.dataset.days);
    if (days === 0 && !confirm(LANG === "en" ? "Remove premium from this user?" : "Снять премиум у пользователя?")) return;
    await fetch(`/api/admin/users/${prem.dataset.prem}/premium`, {
      method: "POST",
      headers: { ...authHeaders(), "Content-Type": "application/json" },
      body: JSON.stringify({ days }),
    });
    loadAdmin();
    return;
  }
  const ban = e.target.closest("[data-ban]");
  if (ban) {
    const banned = ban.dataset.banned !== "1";
    if (banned && !confirm(LANG === "en" ? "Block this user? They won't be able to log in." : "Заблокировать пользователя? Он не сможет войти.")) return;
    await fetch(`/api/admin/users/${ban.dataset.ban}/ban`, {
      method: "POST",
      headers: { ...authHeaders(), "Content-Type": "application/json" },
      body: JSON.stringify({ banned }),
    });
    loadAdmin();
    return;
  }
  const del = e.target.closest(".adm-del[data-del]");
  if (!del) return;
  if (!confirm(LANG === "en" ? "Delete this user and all their charts?" : "Удалить пользователя и все его карты?")) return;
  await fetch(`/api/admin/users/${del.dataset.del}`, { method: "DELETE", headers: authHeaders() });
  loadAdmin();
});

// --- Смена пароля (только из кабинета) ---
function openPasswdModal() {
  $("passwd-form").reset();
  $("passwd-msg").classList.add("hidden");
  $("passwd-modal").classList.remove("hidden");
}
$("passwd-close").addEventListener("click", () => $("passwd-modal").classList.add("hidden"));
$("passwd-modal").addEventListener("click", (e) => {
  if (e.target.id === "passwd-modal") $("passwd-modal").classList.add("hidden");
});
$("passwd-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const msg = $("passwd-msg");
  try {
    const r = await fetch("/api/auth/password", {
      method: "POST",
      headers: { ...authHeaders(), "Content-Type": "application/json" },
      body: JSON.stringify({ old_password: $("passwd-old").value, new_password: $("passwd-new").value }),
    });
    const data = await r.json().catch(() => ({}));
    msg.textContent = r.ok ? t("passwd_done") : (data.detail || t("passwd_fail"));
    msg.classList.remove("hidden");
    if (r.ok) setTimeout(() => $("passwd-modal").classList.add("hidden"), 1200);
  } catch {
    msg.textContent = t("passwd_fail");
    msg.classList.remove("hidden");
  }
});

// --- Сохранённые карты ---
async function loadProfiles() {
  try {
    const r = await fetch("/api/profiles", { headers: authHeaders() });
    if (r.status === 401) {
      clearToken();
      updateAuthUI(null);
      return;
    }
    const profiles = await r.json();
    renderSavedList(profiles);
  } catch {
    /* без сети просто не показываем */
  }
}

function renderSavedList(profiles) {
  if (!profiles.length) {
    $("saved-list").innerHTML = `<li style="color:var(--text-dim);font-size:12px;border:none;background:none">${t("ui_saved_empty")}</li>`;
    return;
  }
  $("saved-list").innerHTML = profiles
    .map((p) => {
      const d = p.data;
      const meta = `${d.day || "?"}.${d.month || "?"}.${d.year || "?"}${d.city ? " · " + d.city : ""}`;
      return `<li>
        <button class="saved-load" data-id="${p.id}">
          ${escapeHtml(p.label)}<span class="saved-meta">${escapeHtml(meta)}</span>
        </button>
        <button class="saved-del" data-del="${p.id}" title="${t("del")}">×</button>
      </li>`;
    })
    .join("");
  $("saved-list")._profiles = profiles;
  if (mode === "synastry") populateBSaved();
}

$("saved-list").addEventListener("click", async (e) => {
  const load = e.target.closest(".saved-load");
  const del = e.target.closest(".saved-del");
  if (load) {
    const p = $("saved-list")._profiles.find((x) => x.id === +load.dataset.id);
    if (p) fillBirthData(p.data);
  } else if (del) {
    if (!confirm(t("save_confirm_del"))) return;
    await fetch(`/api/profiles/${del.dataset.del}`, { method: "DELETE", headers: authHeaders() });
    await loadProfiles();
  }
});

$("save-current").addEventListener("click", async () => {
  if (!getToken()) {
    $("auth-modal").classList.remove("hidden");
    setAuthMode("register");
    $("auth-error").textContent = t("save_need_auth");
    $("auth-error").classList.remove("hidden");
    return;
  }
  const birth = getBirthData();
  const err = validate(birth);
  if (err) return alert(err);
  const defaultLabel = birth.name && birth.name !== "Без имени" && birth.name !== "Chart" ? birth.name : birth.city || t("save_default");
  const label = prompt(t("save_name_prompt"), defaultLabel);
  if (!label) return;
  try {
    const resp = await fetch("/api/profiles", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ label, data: birth }),
    });
    // 402 — исчерпан бесплатный лимит: показываем подписку вместо тихого отказа.
    if (resp.status === 402) {
      openPremiumModal();
      return;
    }
    if (!resp.ok) {
      const detail = await resp.json().catch(() => ({}));
      alert(detail.detail || t("save_failed"));
      return;
    }
    await loadProfiles();
  } catch (ex) {
    alert(t("save_failed") + ex.message);
  }
});

function fillBirthData(d) {
  $("name").value = d.name && d.name !== "Без имени" && d.name !== "Chart" ? d.name : "";
  if (d.year && d.month && d.day) {
    $("birth-date").value = `${d.year}-${String(d.month).padStart(2, "0")}-${String(d.day).padStart(2, "0")}`;
  }
  $("birth-time").value = `${String(d.hour ?? 12).padStart(2, "0")}:${String(d.minute ?? 0).padStart(2, "0")}`;
  $("lat").value = d.lat ?? "";
  $("lng").value = d.lng ?? "";
  $("tz").value = d.tz_str || "";
  $("birth-is-dst").value = d.is_dst == null ? "" : String(Boolean(d.is_dst));
  cityInput.value = d.city || "";
  if (d.houses_system) $("houses-system").value = d.houses_system;
  if (d.lat != null && d.lng != null && !Number.isNaN(parseFloat(d.lat))) {
    showGeoConfirm("geo-confirm", d.city, d.lat, d.lng, d.tz_str);
  }
  currentLocationTouched = false;
  syncCurrentLocationFromBirth(true);
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

// --- Инициализация при загрузке ---
(async function initAuth() {
  await checkEmailLinks(); // ссылки из писем работают и без входа
  if (!getToken()) return;
  try {
    const r = await fetch("/api/auth/me", { headers: authHeaders() });
    if (!r.ok) {
      clearToken();
      return;
    }
    const me = await r.json();
    IS_PREMIUM = !!me.premium;
    PREMIUM_UNTIL = me.premium_until;
    HAS_CONSULT = !!me.consultation;
    REPORT_CREDITS = me.report_credits || 0;
    updateAuthUI(me.username, me.is_admin);
    refreshPremiumBtn();
    await loadProfiles();
    await checkPaymentReturn();
  } catch {
    /* offline */
  }
})();

// PWA: регистрация service worker (установка на главный экран)
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js').catch(() => {});
}

// Базовые деттеренты от копирования авторских трактовок (#results и дневной дайджест).
// Обходятся (просмотр кода, скриншот) — задача лишь отпугнуть случайное копирование.
["copy", "cut", "contextmenu"].forEach((evt) => {
  document.addEventListener(evt, (e) => {
    if (e.target.closest && e.target.closest("#results, #daily-block")) e.preventDefault();
  });
});
