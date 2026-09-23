"use strict";
// Страница «Об авторе»: переводы и переключатели языка/темы.
// Отдельный файл, а не инлайн-скрипт: CSP разрешает только 'self' и один хэш темы.

function syncSwitches() {
  document.querySelectorAll(".lang-opt").forEach((b) => b.classList.toggle("active", b.dataset.lang === LANG));
  const theme = document.documentElement.getAttribute("data-theme");
  document.querySelectorAll(".theme-opt").forEach((b) => b.classList.toggle("active", b.dataset.themeSet === theme));
  document.documentElement.setAttribute("lang", LANG);
}

document.querySelectorAll(".lang-opt").forEach((b) => {
  b.addEventListener("click", () => { setLang(b.dataset.lang); syncSwitches(); });
});

document.querySelectorAll(".theme-opt").forEach((b) => {
  b.addEventListener("click", () => {
    const t = b.dataset.themeSet;
    document.documentElement.setAttribute("data-theme", t);
    try { localStorage.setItem("astro_theme", t); } catch (e) {}
    syncSwitches();
  });
});

applyI18n();
syncSwitches();
