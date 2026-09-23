"use strict";
// Standalone documents retain their complete Russian source; no HTML from users.
(() => {
  const requested = new URLSearchParams(location.search).get("lang");
  let saved = "ru";
  try { saved = localStorage.getItem("astro_lang"); } catch (_) {}
  const initial = requested === "en" || requested === "ru" ? requested : saved === "en" ? "en" : "ru";
  const originalTitle = document.title;
  const originalAttrs = new Map();
  const about = Boolean(document.querySelector(".about-wrap"));
  const privacy = location.pathname.endsWith("privacy.html");
  function attr(selector, name, english, lang) {
    document.querySelectorAll(selector).forEach(el => {
      if (!originalAttrs.has(el)) originalAttrs.set(el, new Map());
      const attrs = originalAttrs.get(el);
      if (!attrs.has(name)) attrs.set(name, el.getAttribute(name));
      const value = lang === "en" ? english : attrs.get(name);
      if (value === null) el.removeAttribute(name); else el.setAttribute(name, value);
    });
  }
  function apply(lang) {
    document.documentElement.lang = lang;
    try { localStorage.setItem("astro_lang", lang); } catch (_) {}
    document.querySelectorAll("[data-standalone-ru]").forEach(el => { el.hidden = lang !== "ru"; });
    document.querySelectorAll("[data-standalone-en]").forEach(el => { el.hidden = lang !== "en"; });
    const title = about ? "Artem — astrologer and tarot reader | Project Artemisa" :
      `${privacy ? "Personal Data Processing Policy" : "User Agreement"} — AstroSMap (Project Artemisa)`;
    document.title = lang === "en" ? title : originalTitle;
    if (about) {
      if (typeof setLang === "function") setLang(lang);
      if (typeof syncSwitches === "function") syncSwitches();
      attr('meta[name="description"]', "content", "Astrologer and tarot reader Artem: Western, Kabbalistic and Vedic astrology, Tarot (two schools), and a custom calculation tool. Book a consultation.", lang);
      attr('meta[property="og:title"]', "content", "Artem — astrologer and tarot reader", lang);
      attr('meta[property="og:description"]', "content", "Western, Kabbalistic and Vedic astrology. Tarot: two schools. A custom calculation tool. Book a consultation.", lang);
      attr(".about-photo img", "alt", "Artem — astrologer and tarot reader", lang);
      attr("#theme-switch", "title", "Light / dark theme", lang);
      attr('[data-theme-set="light"]', "aria-label", "Light theme", lang);
      attr('[data-theme-set="dark"]', "aria-label", "Dark theme", lang);
      attr("#lang-switch", "title", "Language", lang);
    }
    document.querySelectorAll('a[href^="/"]').forEach(link => {
      const url = new URL(link.getAttribute("href"), location.origin);
      if (url.origin !== location.origin) return;
      url.searchParams.set("lang", lang);
      link.setAttribute("href", url.pathname + url.search + url.hash);
    });
  }
  document.querySelectorAll(".lang-opt").forEach(button => {
    button.addEventListener("click", () => {
      const lang = button.dataset.lang === "en" ? "en" : "ru";
      const url = new URL(location.href);
      url.searchParams.set("lang", lang);
      history.replaceState(null, "", url);
      apply(lang);
    });
  });
  apply(initial);
})();
