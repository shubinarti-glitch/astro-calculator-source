# -*- coding: utf-8 -*-
"""Ядро астрологических расчётов на базе Kerykeion (Swiss Ephemeris)."""
from __future__ import annotations
from .editorial_data import text as _editorial_text

import contextvars
import math
import warnings
from datetime import datetime, timedelta
from functools import lru_cache
from typing import Any, Optional

# Путь к эфемеридам обязательно настраивается ДО импорта Kerykeion-расчётов.
from .ephe import ensure_ephemeris

ensure_ephemeris()

warnings.simplefilter("ignore")  # глушим DeprecationWarning'и Kerykeion

from kerykeion import (  # noqa: E402
    AstrologicalSubjectFactory,
    ChartDataFactory,
    ChartDrawer,
    AspectsFactory,
    RelationshipScoreFactory,
    PlanetaryReturnFactory,
    TransitsTimeRangeFactory,
    EphemerisDataFactory,
)

from . import constants as C  # noqa: E402
from . import interpretations as I  # noqa: E402

# Текущий язык запроса (ru/en). Устанавливается в начале каждого отчёта.
_LANG = contextvars.ContextVar("lang", default="ru")


def _set_lang(lang) -> str:
    code = "en" if str(lang).lower() == "en" else "ru"
    _LANG.set(code)
    return code


def _lang() -> str:
    return _LANG.get()


def _ord(n: int) -> str:
    """Английский порядковый номер: 1->1st, 2->2nd, 3->3rd, 11->11th …"""
    if 10 <= (n % 100) <= 20:
        suf = "th"
    else:
        suf = {1: "st", 2: "nd", 3: "rd"}.get(n % 10, "th")
    return f'{n}{suf}'

try:
    from timezonefinder import TimezoneFinder  # noqa: E402

    _TF = TimezoneFinder()
except Exception:  # pragma: no cover
    _TF = None


def resolve_timezone(lat: float, lng: float, fallback: str = "UTC") -> str:
    """Определяет IANA-таймзону по координатам (офлайн, через timezonefinder)."""
    if _TF is None:
        return fallback
    tz = _TF.timezone_at(lat=lat, lng=lng) or _TF.closest_timezone_at(lat=lat, lng=lng)
    return tz or fallback


# --------------------------------------------------------------------------- #
#  Создание субъекта
# --------------------------------------------------------------------------- #
@lru_cache(maxsize=256)
def build_subject(
    *,
    name: str,
    year: int,
    month: int,
    day: int,
    hour: int,
    minute: int,
    lat: float,
    lng: float,
    tz_str: Optional[str] = None,
    is_dst: Optional[bool] = None,
    city: str = "",
    nation: str = "",
    houses_system: str = "P",
    zodiac_type: str = "Tropic",
    **_ignore,  # лишние поля (напр. lang) игнорируются
):
    """Создаёт модель Kerykeion из данных рождения (полностью офлайн)."""
    if not tz_str:
        tz_str = resolve_timezone(lat, lng)

    try:
        return AstrologicalSubjectFactory.from_birth_data(
            name=name or _editorial_text('astrology.0106f3ae25023cf58ed2b23e078a455399cb5da527c9f99cb9022733ba21a629'),
            year=year,
            month=month,
            day=day,
            hour=hour,
            minute=minute,
            lng=float(lng),
            lat=float(lat),
            tz_str=tz_str,
            is_dst=is_dst,
            city=city or "—",
            nation=nation or "",
            online=False,
            houses_system_identifier=houses_system,
            zodiac_type=zodiac_type,
        )
    except Exception as exc:
        msg = str(exc).lower()
        if _editorial_text('astrology.432ab99a48f05576130612e6b96ad91e1ce5d21c7cbe7461d5142c1746091354') in msg or _editorial_text('astrology.e4ac547615b5f9798a19e5b8ce77d8864b14e4f9a9279b5c90b2be10d9ac299e') in msg:
            raise ValueError(
                _editorial_text('astrology.c197de83032daa1a94a2ed0b3c634a195bed30f95b2a705df02dc7b6a358417f')
            ) from exc
        if _editorial_text('astrology.b5d886a2688e60f1a0c525b918380c7d0f931199625b2fbb3df20089c8ee7803') in msg or "ambiguous" in msg and "dst" in msg:
            raise ValueError(
                _editorial_text('astrology.5ccd4f1c8617fbb9c9cf4d5240dfc31cfc6c78ed3ceb09213cbbb05b0a294332')
            ) from exc
        raise


# --------------------------------------------------------------------------- #
#  Сериализация (с русскими подписями)
# --------------------------------------------------------------------------- #
def _deg_min(position: float) -> tuple[int, int]:
    d = int(position)
    m = int(round((position - d) * 60))
    if m == 60:
        d += 1
        m = 0
    return d, m


def serialize_point(point: Any, is_house: bool = False) -> dict:
    """Превращает точку/планету Kerykeion в словарь с локализованными подписями."""
    lang = _lang()
    data = point.model_dump() if hasattr(point, "model_dump") else dict(point)
    name = data["name"]
    sign = data["sign"]
    deg, minute = _deg_min(data["position"])

    out = {
        "name": name,
        "name_ru": C.point_name(name, lang),
        "symbol": C.point_symbol(name),
        "sign": sign,
        "sign_ru": C.sign_name(sign, lang),
        "sign_symbol": C.sign_symbol(sign),
        "element": C.sign_element(sign, lang),
        "deg": deg,
        "min": minute,
        "position": round(data["position"], 4),
        "abs_pos": round(data["abs_pos"], 4),
        "retrograde": bool(data.get("retrograde")) if data.get("retrograde") is not None else False,
        "speed": round(data.get("speed", 0.0), 4),
    }

    if is_house:
        num = C.HOUSE_NUM.get(name)
        out["house_num"] = num
        out["meaning"] = C.house_meaning(num, lang)
        out["sphere"] = I.house_sphere(num, lang)
    else:
        house_name = data.get("house")
        out["house_num"] = C.HOUSE_NUM.get(house_name) if house_name else None
        out["dignity"] = I.dignity(name, sign, lang)
        out["dignity_code"] = I.dignity_code(name, sign)
        out["interp_sign"] = I.interpret_sign(name, sign, lang)
        out["interp_house"] = I.interpret_house(name, out["house_num"], lang)
        out["interp_full"] = I.interpret_sign_full(name, sign, lang, retro=out["retrograde"])
        # Авторский разбор «планета в доме» — вторым блоком, сразу после «Разбор» (знак).
        ah = I.authored_house(name, out["house_num"], lang)
        if ah:
            label = "House" if lang == "en" else "Дом"
            pos = 1 if out["interp_full"] and out["interp_full"][0]["label"] in ("Разбор", "Reading") else 0
            out["interp_full"].insert(pos, {"label": label, "text": ah})
        out["house_sphere"] = I.house_sphere(out["house_num"], lang)
        out["interp_plain"] = I.interpret_plain(name, sign, out["house_num"], lang)
        out["interp_full_plain"] = I.interpret_sign_full_plain(name, sign, out["house_num"], lang, retro=out["retrograde"])

    return out


def serialize_aspect(asp: Any) -> dict:
    lang = _lang()
    data = asp.model_dump() if hasattr(asp, "model_dump") else dict(asp)
    kind = data["aspect"]
    p1, p2 = data["p1_name"], data["p2_name"]
    return {
        "p1": p1,
        "p1_ru": C.point_name(p1, lang),
        "p1_symbol": C.point_symbol(p1),
        "p2": p2,
        "p2_ru": C.point_name(p2, lang),
        "p2_symbol": C.point_symbol(p2),
        "aspect": kind,
        "aspect_ru": C.aspect_name(kind, lang),
        "aspect_symbol": C.aspect_symbol(kind),
        "nature": C.ASPECTS.get(kind, {}).get("nature", ""),  # код (для CSS)
        "nature_label": C.aspect_nature(kind, lang),
        "orbit": round(abs(data["orbit"]), 2),
        "degrees": data.get("aspect_degrees"),
        "movement": C.movement_name(data.get("aspect_movement", ""), lang),
        "interp": I.interpret_aspect(p1, kind, p2, lang),
    }


# В некоторых картах (напр. возвращениях) узлы и Лилит хранятся как «истинные».
_NODE_FALLBACK = {
    "mean_north_lunar_node": "true_north_lunar_node",
    "mean_south_lunar_node": "true_south_lunar_node",
    "mean_lilith": "true_lilith",
}


def _apply_transit_interp(aspects: list[dict]) -> list[dict]:
    """Трактовка «транзитная»: действующая планета — p2 (транзит), цель — p1 (натал).

    В dual_chart_aspects(natal, moving) первый субъект (p1) — натальный, второй (p2) — действующий.
    """
    lang = _lang()
    for a in aspects:
        t = I.interpret_transit(
            a["p2"], a["aspect"], a["p1"], lang,
            orbit=a.get("orbit"), movement=a.get("movement", ""),
        )
        if t:
            a["interp"] = t
    return aspects


def _apply_progression_interp(aspects: list[dict]) -> list[dict]:
    """Трактовка вторичных прогрессий: p2 — прогрессивная точка, p1 — натальная."""
    lang = _lang()
    for a in aspects:
        text = I.interpret_progression(a["p2"], a["aspect"], a["p1"], lang)
        if text:
            a["interp"] = text
    return aspects


def _collect_points(model) -> list[dict]:
    points = []
    for attr in C.PLANET_ORDER:
        p = getattr(model, attr, None)
        if p is None and attr in _NODE_FALLBACK:
            p = getattr(model, _NODE_FALLBACK[attr], None)
        if p is not None:
            points.append(serialize_point(p))
    return points


def _collect_angles(model) -> list[dict]:
    angles = []
    for attr in C.ANGLE_ORDER:
        p = getattr(model, attr, None)
        if p is not None:
            angles.append(serialize_point(p))
    return angles


def _collect_houses(model) -> list[dict]:
    houses = []
    for attr in C.HOUSE_ORDER:
        h = getattr(model, attr, None)
        if h is not None:
            houses.append(serialize_point(h, is_house=True))
    return houses


def _lunar_phase(model) -> dict:
    lp = model.lunar_phase
    data = lp.model_dump() if hasattr(lp, "model_dump") else dict(lp)
    name_en = data.get("moon_phase_name", "")
    return {
        "name": name_en,
        "name_ru": C.moon_phase_name(name_en, _lang()),
        "emoji": data.get("moon_emoji", ""),
        "phase": data.get("moon_phase"),
    }


def _meta(model) -> dict:
    return {
        "name": model.name,
        "city": model.city,
        "nation": model.nation,
        "lat": round(model.lat, 4),
        "lng": round(model.lng, 4),
        "tz_str": model.tz_str,
        "local_datetime": model.iso_formatted_local_datetime,
        "utc_datetime": model.iso_formatted_utc_datetime,
        "houses_system": C.house_system_name(getattr(model, "houses_system_identifier", "P"), _lang()),
        "zodiac_type": model.zodiac_type,
    }


# --------------------------------------------------------------------------- #
#  Публичные функции отчётов
# --------------------------------------------------------------------------- #
def _build_profile(model, chart_data) -> dict:
    """Профессиональный «портрет»: баланс стихий/крестов, синтез светил, управитель карты."""
    cd = chart_data.model_dump() if hasattr(chart_data, "model_dump") else dict(chart_data)
    element_dist = cd.get("element_distribution", {})
    quality_dist = cd.get("quality_distribution", {})

    lang = _lang()
    balance = I.interpret_balance(element_dist, quality_dist, lang)

    sun_sign = model.sun.sign
    moon_sign = model.moon.sign
    asc_sign = model.first_house.sign
    core_text = I.synthesize_core(sun_sign, moon_sign, asc_sign, lang)

    # Управитель карты (управитель знака Асцендента) и его положение.
    ruler = None
    ruler_name = I.chart_ruler(asc_sign)
    if ruler_name:
        attr = ruler_name.lower()
        rp = getattr(model, attr, None)
        if rp is not None:
            rdata = serialize_point(rp)
            ruler = {
                "name_ru": rdata["name_ru"],
                "symbol": rdata["symbol"],
                "sign_ru": rdata["sign_ru"],
                "sign_symbol": rdata["sign_symbol"],
                "house_num": rdata["house_num"],
                "asc_sign_ru": C.sign_in(asc_sign, lang),
            }
            # Современный со-управитель (для Скорпиона/Водолея/Рыб).
            co_name = I.modern_coruler(asc_sign)
            cp = getattr(model, co_name.lower(), None) if co_name else None
            if cp is not None:
                cd = serialize_point(cp)
                ruler["coruler"] = {
                    "name_ru": cd["name_ru"], "symbol": cd["symbol"],
                    "sign_ru": cd["sign_ru"], "house_num": cd["house_num"],
                }

    return {
        "element_distribution": element_dist,
        "quality_distribution": quality_dist,
        "balance": balance,
        "core_text": core_text,
        "ruler": ruler,
    }


def _build_psych_portrait(model, aspects_ser: list, chart_data, lang: str = "ru") -> dict:
    """Психологический портрет: темперамент, ведущая планета, психо-оси, недостающая стихия, самооценка."""
    cd = chart_data.model_dump() if hasattr(chart_data, "model_dump") else dict(chart_data)
    element_dist = cd.get("element_distribution", {})
    quality_dist = cd.get("quality_distribution", {})

    temperament = I.temperament(element_dist, quality_dist, lang)
    missing = I.missing_element(element_dist, lang)

    planet_signs = {}
    for pname in ("Sun", "Moon", "Mercury", "Mars", "Saturn"):
        p = getattr(model, pname.lower(), None)
        if p is not None:
            planet_signs[pname] = p.sign
    axes = I.psych_axes(planet_signs, lang)

    # Ведущая (доминирующая) планета — по углам, аспектам, достоинству, управлению картой.
    angles = [model.first_house.abs_pos, model.tenth_house.abs_pos,
              model.seventh_house.abs_pos, model.fourth_house.abs_pos]
    ruler_name = I.chart_ruler(model.first_house.sign)
    asp_count: dict = {}
    for a in aspects_ser:
        asp_count[a["p1"]] = asp_count.get(a["p1"], 0) + 1
        asp_count[a["p2"]] = asp_count.get(a["p2"], 0) + 1

    best = None
    for pname in ["Sun", "Moon", "Mercury", "Venus", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune", "Pluto"]:
        p = getattr(model, pname.lower(), None)
        if p is None:
            continue
        score = 0.0
        if C.HOUSE_NUM.get(p.house) in (1, 4, 7, 10):
            score += 3
        if any(abs(_angular_diff(p.abs_pos, av)) <= 6 for av in angles):
            score += 3
        score += asp_count.get(pname, 0)
        dc = I.dignity_code(pname, p.sign)
        if dc in ("domicile", "exaltation"):
            score += 2
        elif dc in ("detriment", "fall"):
            score -= 1
        if ruler_name == pname:
            score += 3
        if pname in ("Sun", "Moon"):
            score += 1
        if best is None or score > best[1]:
            best = (pname, score, p)

    dominant = None
    if best:
        pname, _, p = best
        rd = serialize_point(p)
        dominant = {
            "name_ru": rd["name_ru"], "symbol": rd["symbol"], "sign_ru": rd["sign_ru"],
            "house_num": rd["house_num"], "text": I.dominant_planet_text(pname, lang),
        }

    # Самооценка: аспекты к Солнцу (особенно с Сатурном).
    sun_saturn = None
    sun_hard = sun_soft = 0
    for a in aspects_ser:
        pair = {a["p1"], a["p2"]}
        if "Sun" not in pair:
            continue
        if a["nature"] == "tense":
            sun_hard += 1
        elif a["nature"] == "harmonious":
            sun_soft += 1
        if "Saturn" in pair:
            sun_saturn = "soft" if a["nature"] == "harmonious" else "hard"
    esteem = I.self_esteem(sun_saturn, sun_hard, sun_soft, lang)

    return {
        "temperament": temperament,
        "dominant": dominant,
        "axes": axes,
        "missing": missing,
        "self_esteem": esteem,
    }


def _render_chart_svgs(chart_data, lang: str = "ru") -> dict:
    """Рендерит колесо карты в двух темах (тёмная и светлая) на нужном языке."""
    chart_lang = "EN" if str(lang).lower() == "en" else "RU"
    return {
        "svg": ChartDrawer(chart_data, chart_language=chart_lang, theme="dark").generate_svg_string(),
        "svg_light": ChartDrawer(chart_data, chart_language=chart_lang, theme="light").generate_svg_string(),
    }


_DEEP_PLANETS = ["sun", "moon", "mercury", "venus", "mars", "jupiter", "saturn", "uranus", "neptune", "pluto"]
_DEEP_RETRO = ["mercury", "venus", "mars", "jupiter", "saturn", "uranus", "neptune", "pluto", "chiron"]
_PATTERN_POINTS = _DEEP_PLANETS + ["ascendant", "medium_coeli"]


def _detect_patterns(pts: list[tuple], lang: str) -> list[dict]:
    """Аспектные конфигурации (большой трин, тау-квадрат, большой крест, йод) по положениям."""
    import itertools
    n = len(pts)

    def isa(i, j, target, orb):
        return abs(_angular_diff(pts[i][1], pts[j][1]) - target) <= orb

    raw = []
    for i, j, k in itertools.combinations(range(n), 3):
        if isa(i, j, 120, 7) and isa(j, k, 120, 7) and isa(i, k, 120, 7):
            raw.append(("grand_trine", frozenset((i, j, k))))
    for i, j in itertools.combinations(range(n), 2):
        if isa(i, j, 180, 8):
            for k in range(n):
                if k in (i, j):
                    continue
                if isa(i, k, 90, 6) and isa(j, k, 90, 6):
                    raw.append(("t_square", frozenset((i, j, k))))
    for i, j in itertools.combinations(range(n), 2):
        if not isa(i, j, 180, 8):
            continue
        for k, l in itertools.combinations(range(n), 2):
            if len({i, j, k, l}) < 4:
                continue
            if isa(k, l, 180, 8) and isa(i, k, 90, 7) and isa(i, l, 90, 7) and isa(j, k, 90, 7) and isa(j, l, 90, 7):
                raw.append(("grand_cross", frozenset((i, j, k, l))))
    for i, j in itertools.combinations(range(n), 2):
        if isa(i, j, 60, 4):
            for k in range(n):
                if k in (i, j):
                    continue
                if isa(i, k, 150, 3) and isa(j, k, 150, 3):
                    raw.append(("yod", frozenset((i, j, k))))

    # Убираем тау-квадраты, входящие в большой крест.
    crosses = [s for key, s in raw if key == "grand_cross"]
    filtered = []
    for key, s in raw:
        if key == "t_square" and any(s <= c for c in crosses):
            continue
        filtered.append((key, s))

    seen, out = set(), []
    for key, s in filtered:
        sig = (key, s)
        if sig in seen:
            continue
        seen.add(sig)
        out.append({
            "key": key,
            "name": I.pattern_name(key, lang),
            "text": I.pattern_text(key, lang),
            "planets": [C.point_name(pts[x][0], lang) for x in s],
        })
    order = {"grand_cross": 0, "grand_trine": 1, "t_square": 2, "yod": 3}
    out.sort(key=lambda p: order.get(p["key"], 9))
    return out[:8]


def _deep_analysis(model, lang: str) -> dict:
    """Профессиональный углублённый разбор: ретро, фаза Луны, стеллиумы, конфигурации, полушария."""
    # Ретроградные планеты
    retro = []
    for attr in _DEEP_RETRO:
        p = getattr(model, attr, None)
        if p is not None and getattr(p, "retrograde", False):
            retro.append({
                "name_ru": C.point_name(p.name, lang),
                "symbol": C.point_symbol(p.name),
                "text": I.retrograde_note(p.name, lang),
            })

    # Фаза Луны
    lp = model.lunar_phase
    lpname = (lp.model_dump() if hasattr(lp, "model_dump") else dict(lp)).get("moon_phase_name", "")
    lunar = {"name": C.moon_phase_name(lpname, lang), "text": I.lunar_phase_meaning(lpname, lang)}

    # Стеллиумы (3+ планеты в одном знаке или доме)
    by_sign: dict = {}
    by_house: dict = {}
    for attr in _DEEP_PLANETS:
        p = getattr(model, attr, None)
        if p is None:
            continue
        by_sign.setdefault(p.sign, []).append(p.name)
        h = C.HOUSE_NUM.get(p.house)
        if h:
            by_house.setdefault(h, []).append(p.name)
    stelliums = []
    for sign, names in by_sign.items():
        if len(names) >= 3:
            stelliums.append({"where": C.sign_name(sign, lang),
                              "planets": [C.point_name(x, lang) for x in names]})
    for house, names in by_house.items():
        if len(names) >= 3:
            stelliums.append({"where": (f"{_editorial_text('astrology.f4851fee58557351743015665339e7e712937ed68cf5e56edf43e12cd1f2fe6e')}{house}" if lang == "en" else f"{house}{_editorial_text('astrology.685547d6d819c039ea9fd4e48a0d6e77dcbe52bf84d16c95903cbb7cdd7e5a65')}"),
                              "planets": [C.point_name(x, lang) for x in names]})

    # Конфигурации
    pts = []
    for attr in _PATTERN_POINTS:
        p = getattr(model, attr, None)
        if p is not None:
            pts.append((p.name, p.abs_pos))
    patterns = _detect_patterns(pts, lang)

    # Полушария (по домам планет)
    lower = upper = east = west = 0
    for attr in _DEEP_PLANETS:
        p = getattr(model, attr, None)
        h = C.HOUSE_NUM.get(getattr(p, "house", None)) if p is not None else None
        if not h:
            continue
        if h <= 6:
            lower += 1
        else:
            upper += 1
        if h in (10, 11, 12, 1, 2, 3):
            east += 1
        else:
            west += 1
    hemispheres = []
    if abs(lower - upper) >= 2:
        hemispheres.append(I.hemisphere_text("lower" if lower > upper else "upper", lang))
    if abs(east - west) >= 2:
        hemispheres.append(I.hemisphere_text("east" if east > west else "west", lang))

    return {
        "retrogrades": retro,
        "lunar_phase": lunar,
        "stelliums": stelliums,
        "patterns": patterns,
        "hemispheres": hemispheres,
    }


def _chart_essentials(model, lang: str) -> dict:
    """Традиционная классика: секта (день/ночь) и Жребий Фортуны."""
    sun_house = C.HOUSE_NUM.get(getattr(model.sun, "house", None))
    # Дневная карта = Солнце над горизонтом (дома 7–12).
    is_day = sun_house in (7, 8, 9, 10, 11, 12) if sun_house else True
    asc = model.first_house.abs_pos
    sun = model.sun.abs_pos
    moon = model.moon.abs_pos
    lot = ((asc + moon - sun) if is_day else (asc + sun - moon)) % 360
    sign_codes = list(C.SIGNS.keys())
    lot_sign = sign_codes[int(lot // 30) % 12]
    cusps = [getattr(model, h).abs_pos for h in C.HOUSE_ORDER]
    lot_house = _house_of(lot, cusps)
    return {
        "sect": {"is_day": is_day, "text": I.sect_text(is_day, lang)},
        "lot_fortune": {
            "sign_ru": C.sign_name(lot_sign, lang),
            "symbol": C.SIGNS[lot_sign]["symbol"],
            "house_num": lot_house,
            "deg": int(lot % 30),
            "text": I.lot_fortune_text(lot_sign, lot_house, lang),
        },
    }


def _single_chart_payload(model, with_svg: bool = True, with_profile: bool = True, lang: str = "ru") -> dict:
    """Полная сериализация одиночной карты (натал, соляр, лунар …)."""
    _set_lang(lang)
    aspects = AspectsFactory.single_chart_aspects(model)
    chart_data = ChartDataFactory.create_natal_chart_data(model)
    payload = {
        "meta": _meta(model),
        "planets": _collect_points(model),
        "angles": _collect_angles(model),
        "houses": _collect_houses(model),
        "aspects": [serialize_aspect(a) for a in aspects.aspects],
        "lunar_phase": _lunar_phase(model),
    }
    if with_profile:
        payload["profile"] = _build_profile(model, chart_data)
        payload["deep"] = _deep_analysis(model, lang)
        payload["big_three"] = {
            "sun": I.luminary_info("Sun", model.sun.sign, lang),
            "moon": I.luminary_info("Moon", model.moon.sign, lang),
            "asc": I.luminary_info("Ascendant", model.first_house.sign, lang),
        }
        payload["spheres"] = {
            "love": I.sphere_love(model.venus.sign, model.mars.sign, model.seventh_house.sign, lang),
            "career": I.sphere_career(model.tenth_house.sign, model.sun.sign, model.saturn.sign, lang),
            "health": I.sphere_health(model.first_house.sign, model.moon.sign, model.sixth_house.sign, lang),
        }
        payload["psych"] = _build_psych_portrait(model, payload["aspects"], chart_data, lang)
        payload["essentials"] = _chart_essentials(model, lang)
    if with_svg:
        payload.update(_render_chart_svgs(chart_data, lang))
    return payload


def natal_report(params: dict, with_svg: bool = True) -> dict:
    lang = params.get("lang", "ru")
    _set_lang(lang)
    model = build_subject(**params)
    payload = _single_chart_payload(model, with_svg=with_svg, lang=lang)
    # Тематический рассказ «Просто о себе» — без терминов.
    signs = {
        "sun": model.sun.sign, "moon": model.moon.sign, "asc": model.first_house.sign,
        "mercury": model.mercury.sign, "venus": model.venus.sign, "mars": model.mars.sign,
        "saturn": model.saturn.sign, "mc": model.tenth_house.sign,
        "h2": model.second_house.sign, "h7": model.seventh_house.sign,
    }
    payload["story"] = I.plain_story(signs, payload.get("aspects", []), lang)
    return payload


def return_report(
    natal_params: dict,
    year: int,
    month: Optional[int] = None,
    return_type: str = "Solar",
    location: Optional[dict] = None,
    with_svg: bool = True,
) -> dict:
    """Карта возвращения: соляр (Solar — год от дня рождения) или лунар (Lunar — месяц).

    Соляр: ближайшее солнечное возвращение от 1 января указанного года (= день рождения этого года).
    Лунар: ближайшее лунное возвращение от 1-го числа выбранного месяца.
    """
    _set_lang(natal_params.get("lang", "ru"))
    natal_model = build_subject(**natal_params)

    loc = location or {
        "lat": natal_params["lat"], "lng": natal_params["lng"],
        "tz_str": natal_params.get("tz_str"), "city": natal_params.get("city", ""),
    }
    tz = loc.get("tz_str") or resolve_timezone(loc["lat"], loc["lng"])

    rtype = "Lunar" if str(return_type).lower().startswith("lun") else "Solar"
    prf = PlanetaryReturnFactory(
        natal_model,
        lat=loc["lat"],
        lng=loc["lng"],
        tz_str=tz,
        city=loc.get("city") or natal_params.get("city", "—"),
        online=False,
    )
    # Для лунара отталкиваемся от начала выбранного месяца, для соляра — от начала года.
    start_month = (month or 1) if rtype == "Lunar" else 1
    ret_model = prf.next_return_from_date(year, start_month, 1, return_type=rtype)

    lang = natal_params.get("lang", "ru")
    # Карта возвращения описывает период, а не формирует новый портрет личности.
    # Поэтому натальные profile/deep/psych/spheres/essentials здесь не создаём,
    # но сохраняем big_three, который используется Android ReturnScreen.
    payload = _single_chart_payload(ret_model, with_svg=with_svg, with_profile=False, lang=lang)
    payload["big_three"] = {
        "sun": I.luminary_info("Sun", ret_model.sun.sign, lang),
        "moon": I.luminary_info("Moon", ret_model.moon.sign, lang),
        "asc": I.luminary_info("Ascendant", ret_model.first_house.sign, lang),
    }
    payload["return_type"] = rtype
    if lang == "en":
        payload["return_type_ru"] = _editorial_text('astrology.ebf43f61e74abd9d8811ace1a8ef85573d5a79dfe4bb0cd20f6e715f27e765ba') if rtype == "Solar" else _editorial_text('astrology.c2815e010954639351ef917c6bf898eaac9e17874b90abba10bfef07f6b34c5a')
    else:
        payload["return_type_ru"] = _editorial_text('astrology.e4dd68fbb03cf2cca9ac2d9a4276d28f817918e54d4fee052de630dda9017598') if rtype == "Solar" else _editorial_text('astrology.9c694185f08835d7fd8041aad90445c376bcbfbb27d9a38d5d077072f275b3df')
    payload["natal_meta"] = _meta(natal_model)
    # Наложение Асцендента возвращения на натальные дома — главная сфера периода.
    natal_cusps = [getattr(natal_model, h).abs_pos for h in C.HOUSE_ORDER]
    asc_natal_house = _house_of(ret_model.first_house.abs_pos, natal_cusps)
    # Управитель периода (управитель Асцендента возвращения) и его место в карте возвращения.
    lord_name = I.chart_ruler(ret_model.first_house.sign)
    lord_sign = lord_house = None
    if lord_name:
        lp = getattr(ret_model, lord_name.lower(), None)
        if lp is not None:
            lord_sign = lp.sign
            lord_house = C.HOUSE_NUM.get(getattr(lp, "house", None))
    payload["theme"] = I.return_forecast(
        rtype,
        ret_model.first_house.sign,
        C.HOUSE_NUM.get(getattr(ret_model.sun, "house", None)),
        ret_model.moon.sign,
        C.HOUSE_NUM.get(getattr(ret_model.moon, "house", None)),
        asc_natal_house=asc_natal_house,
        lord_name=lord_name, lord_sign=lord_sign, lord_house=lord_house,
        lang=lang,
    )
    # Период действия карты: начало = момент возвращения, конец = следующее точное возвращение.
    try:
        start_dt = datetime.fromisoformat(ret_model.iso_formatted_local_datetime)
        payload["period_start"] = start_dt.isoformat()
        if rtype == "Solar":
            next_model = prf.next_return_from_date(year + 1, 1, 1, return_type=rtype)
        else:
            probe = start_dt + timedelta(days=1)
            next_model = prf.next_return_from_date(probe.year, probe.month, probe.day, return_type=rtype)
        payload["period_end"] = next_model.iso_formatted_local_datetime
    except Exception:
        payload["period_start"] = payload["meta"].get("local_datetime")
        payload["period_end"] = None
    return payload


def transit_report(
    natal_params: dict,
    transit_dt: dict,
    transit_location: Optional[dict] = None,
    with_svg: bool = True,
) -> dict:
    """Натальная карта + транзиты на заданную дату.

    transit_dt: {year, month, day, hour, minute}
    transit_location: {lat, lng} (по умолчанию — место рождения)
    """
    lang = _set_lang(natal_params.get("lang", "ru"))
    natal_model = build_subject(**natal_params)

    loc = transit_location or {
        "lat": natal_params["lat"], "lng": natal_params["lng"],
        "tz_str": natal_params.get("tz_str"), "city": natal_params.get("city", ""),
    }
    tz = loc.get("tz_str") or resolve_timezone(loc["lat"], loc["lng"])
    transit_model = build_subject(
        name="Transit" if lang == "en" else "Транзит",
        year=transit_dt["year"],
        month=transit_dt["month"],
        day=transit_dt["day"],
        hour=transit_dt.get("hour", 12),
        minute=transit_dt.get("minute", 0),
        lat=loc["lat"],
        lng=loc["lng"],
        tz_str=tz,
        city=loc.get("city") or natal_params.get("city", ""),
        houses_system=natal_params.get("houses_system", "P"),
    )

    # Аспекты транзитных планет к натальным.
    dual = AspectsFactory.dual_chart_aspects(
        natal_model,
        transit_model,
        first_subject_is_fixed=True,
        second_subject_is_fixed=True,
    )

    result = {
        "natal_meta": _meta(natal_model),
        "transit_meta": _meta(transit_model),
        "transit_planets": _collect_points(transit_model),
        "aspects": _apply_transit_interp([serialize_aspect(a) for a in dual.aspects]),
        "transit_lunar_phase": _lunar_phase(transit_model),
    }
    result["overview"] = _transit_overview(result["aspects"], lang)

    if with_svg:
        chart_data = ChartDataFactory.create_transit_chart_data(natal_model, transit_model)
        result.update(_render_chart_svgs(chart_data, natal_params.get("lang", "ru")))

    return result


TROPICAL_YEAR_DAYS = 365.242189

# Мажорные аспекты для дирекций солнечной дуги (угол -> имя Kerykeion)
_SA_ASPECTS = {0: "conjunction", 60: "sextile", 90: "square", 120: "trine", 180: "opposition"}
_SA_ORB = 1.2  # дирекции медленные — берём тугой орб


def _sign_from_abs(abs_pos: float) -> tuple[str, float]:
    """Возвращает (3-буквенный код знака, градус внутри знака) по абсолютной долготе."""
    abs_pos %= 360
    order = ["Ari", "Tau", "Gem", "Can", "Leo", "Vir", "Lib", "Sco", "Sag", "Cap", "Aqu", "Pis"]
    idx = int(abs_pos // 30)
    return order[idx], abs_pos - idx * 30


def _serialize_directed(name: str, abs_pos: float) -> dict:
    lang = _lang()
    sign, position = _sign_from_abs(abs_pos)
    deg, minute = _deg_min(position)
    return {
        "name": name,
        "name_ru": C.point_name(name, lang),
        "symbol": C.point_symbol(name),
        "sign": sign,
        "sign_ru": C.sign_name(sign, lang),
        "sign_symbol": C.sign_symbol(sign),
        "deg": deg,
        "min": minute,
        "abs_pos": round(abs_pos % 360, 4),
        "retrograde": False,
        "house_num": None,
    }


def _angular_diff(a: float, b: float) -> float:
    d = abs((a - b) % 360)
    return min(d, 360 - d)


def _solar_arc(natal_model, arc: float) -> tuple[list[dict], list[dict]]:
    """Дирекции солнечной дуги: смещаем все натальные точки на дугу Солнца."""
    directed = []
    natal_points = []  # (name, abs_pos) натальных точек для поиска аспектов
    for attr in C.PLANET_ORDER + C.ANGLE_ORDER:
        p = getattr(natal_model, attr, None)
        if p is None and attr in _NODE_FALLBACK:
            p = getattr(natal_model, _NODE_FALLBACK[attr], None)
        if p is None:
            continue
        data = p.model_dump() if hasattr(p, "model_dump") else dict(p)
        natal_points.append((data["name"], data["abs_pos"]))
        directed.append(_serialize_directed(data["name"], data["abs_pos"] + arc))

    # Аспекты директных точек к натальным.
    aspects = []
    for d in directed:
        for n_name, n_abs in natal_points:
            if d["name"] == n_name:
                continue  # директная точка к своей же натальной позиции — это сама дуга, не аспект
            diff = _angular_diff(d["abs_pos"], n_abs)
            for angle, kind in _SA_ASPECTS.items():
                orb = abs(diff - angle)
                if orb <= _SA_ORB:
                    lang = _lang()
                    aspects.append({
                        "p1": d["name"], "p1_ru": d["name_ru"], "p1_symbol": d["symbol"],
                        "p2": n_name,
                        "p2_ru": C.point_name(n_name, lang),
                        "p2_symbol": C.point_symbol(n_name),
                        "aspect": kind, "aspect_ru": C.aspect_name(kind, lang),
                        "aspect_symbol": C.aspect_symbol(kind),
                        "nature": C.ASPECTS.get(kind, {}).get("nature", ""),
                        "orbit": round(orb, 2), "degrees": angle, "movement": "",
                        "interp": I.interpret_direction(d["name"], kind, n_name, lang),
                    })
    aspects.sort(key=lambda a: a["orbit"])
    return directed, aspects


def progression_report(
    natal_params: dict,
    target_date: dict,
    with_svg: bool = True,
) -> dict:
    """Вторичные прогрессии (день=год) + дирекции солнечной дуги на заданную дату."""
    lang = _set_lang(natal_params.get("lang", "ru"))
    natal_model = build_subject(**natal_params)

    birth = datetime(
        natal_params["year"], natal_params["month"], natal_params["day"],
        natal_params.get("hour", 12), natal_params.get("minute", 0),
    )
    target = datetime(
        target_date["year"], target_date["month"], target_date["day"],
        target_date.get("hour", 12), target_date.get("minute", 0),
    )
    elapsed_years = (target - birth).total_seconds() / (TROPICAL_YEAR_DAYS * 86400)
    prog_dt = birth + timedelta(days=elapsed_years)

    prog_model = build_subject(
        name="Progression" if lang == "en" else "Прогрессия",
        year=prog_dt.year, month=prog_dt.month, day=prog_dt.day,
        hour=prog_dt.hour, minute=prog_dt.minute,
        lat=natal_params["lat"], lng=natal_params["lng"],
        tz_str=natal_model.tz_str,
        city=natal_params.get("city", ""),
        houses_system=natal_params.get("houses_system", "P"),
    )

    dual = AspectsFactory.dual_chart_aspects(
        natal_model, prog_model,
        first_subject_is_fixed=True, second_subject_is_fixed=True,
    )

    arc = (prog_model.sun.abs_pos - natal_model.sun.abs_pos) % 360
    directed, directed_aspects = _solar_arc(natal_model, arc)
    arc_deg, arc_min = _deg_min(arc)

    payload = {
        "natal_meta": _meta(natal_model),
        "prog_meta": _meta(prog_model),
        "target_date": target.strftime("%Y-%m-%dT%H:%M"),
        "elapsed_years": round(elapsed_years, 2),
        "prog_planets": _collect_points(prog_model),
        "aspects": _apply_progression_interp([serialize_aspect(a) for a in dual.aspects]),
        "solar_arc": {"value": round(arc, 2), "deg": arc_deg, "min": arc_min},
        "directed": directed,
        "directed_aspects": directed_aspects,
    }

    # Главные акценты прогрессий: «эмоциональная глава» (Луна) и жизненный этап (Солнце).
    pm_sign = prog_model.moon.sign
    pm_house = C.HOUSE_NUM.get(prog_model.moon.house)
    ps_sign = prog_model.sun.sign
    natal_sun_sign = natal_model.sun.sign
    sun_changed = ps_sign != natal_sun_sign
    payload["highlights"] = {
        "prog_moon": {
            "sign_ru": C.sign_name(pm_sign, lang),
            "sign_symbol": C.sign_symbol(pm_sign),
            "house_num": pm_house,
            "text": I.prog_moon_text(pm_sign, pm_house, lang),
        },
        "prog_sun": {
            "sign_ru": C.sign_name(ps_sign, lang),
            "sign_symbol": C.sign_symbol(ps_sign),
            "changed": sun_changed,
            "natal_sign_ru": C.sign_name(natal_sun_sign, lang),
            "text": I.prog_sun_text(ps_sign, sun_changed, natal_sun_sign, lang),
        },
    }

    if with_svg:
        chart_data = ChartDataFactory.create_transit_chart_data(natal_model, prog_model)
        payload.update(_render_chart_svgs(chart_data, natal_params.get("lang", "ru")))

    return payload


# Транзитные точки, которые не показываем в календаре (слишком быстрые/требуют времени)
_CALENDAR_EXCLUDE_TRANSIT = {
    "Moon", "Ascendant", "Medium_Coeli", "Descendant", "Imum_Coeli",
}

# Медленные планеты — основа долгосрочного прогноза
_FORECAST_SLOW = {
    "Jupiter", "Saturn", "Uranus", "Neptune", "Pluto", "Chiron",
    "Mean_North_Lunar_Node", "True_North_Lunar_Node",
    "Mean_South_Lunar_Node", "True_South_Lunar_Node",
}
_FORECAST_ASPECTS = {"conjunction", "opposition", "square", "trine", "sextile"}


def _transit_pass_minima(moments, include) -> list[tuple[tuple, dict]]:
    """Возвращает каждый отдельный минимум орба, не склеивая ретро-прохождения."""
    samples: dict[tuple, list[dict]] = {}
    for moment in moments.transits:
        m = moment.model_dump() if hasattr(moment, "model_dump") else dict(moment)
        for asp in m["aspects"]:
            if not include(asp):
                continue
            key = (asp["p1_name"], asp["aspect"], asp["p2_name"])
            samples.setdefault(key, []).append({
                "date": m["date"], "orb": abs(asp["orbit"]), "asp": asp,
            })

    out = []
    for key, points in samples.items():
        points.sort(key=lambda p: p["date"])
        groups: list[list[dict]] = []
        for point in points:
            point_dt = datetime.fromisoformat(point["date"])
            if not groups:
                groups.append([point])
                continue
            prev_dt = datetime.fromisoformat(groups[-1][-1]["date"])
            if point_dt - prev_dt > timedelta(days=2.1):
                groups.append([point])
            else:
                groups[-1].append(point)
        for group in groups:
            minima = []
            for i in range(1, len(group) - 1):
                prev_orb, point, next_orb = group[i - 1]["orb"], group[i], group[i + 1]["orb"]
                if point["orb"] <= prev_orb and point["orb"] <= next_orb and (
                    point["orb"] < prev_orb or point["orb"] < next_orb
                ):
                    minima.append(point)
            if not minima and group:
                minima = [min(group, key=lambda p: p["orb"])]
            out.extend((key, point) for point in minima)
    return out


def _refine_transit_pass(natal_model, loc: dict, tz: str, key: tuple, info: dict) -> dict:
    """Уточняет суточный кандидат сначала до 2 часов, затем до 10 минут."""
    center = datetime.fromisoformat(info["date"]).replace(tzinfo=None)

    def find_best(start_dt, end_dt, step_type, step):
        eph = EphemerisDataFactory(
            start_datetime=start_dt, end_datetime=end_dt,
            step_type=step_type, step=step,
            lat=loc["lat"], lng=loc["lng"], tz_str=tz,
        ).get_ephemeris_data_as_astrological_subjects()
        refined = TransitsTimeRangeFactory(natal_model, eph).get_transit_moments()
        best = None
        for moment in refined.transits:
            m = moment.model_dump() if hasattr(moment, "model_dump") else dict(moment)
            for asp in m["aspects"]:
                if (asp["p1_name"], asp["aspect"], asp["p2_name"]) != key:
                    continue
                candidate = {"date": m["date"], "orb": abs(asp["orbit"]), "asp": asp}
                if best is None or candidate["orb"] < best["orb"]:
                    best = candidate
        return best

    coarse = find_best(center - timedelta(hours=18), center + timedelta(hours=18), "hours", 2)
    if coarse is None:
        return info
    fine_center = datetime.fromisoformat(coarse["date"]).replace(tzinfo=None)
    fine = find_best(
        fine_center - timedelta(hours=2), fine_center + timedelta(hours=2), "minutes", 10,
    )
    return fine or coarse

# Сфера жизни по натальной точке, которой касается транзит
_SPHERE_OF = {
    "Venus": {"ru": _editorial_text('astrology.097ad7b8e0a6fb3ed83342d1091ffe5b61eb051a1e680b3b08189b060ac646b8'), "en": _editorial_text('astrology.537a8d24ca437099f0f19c40ed8cef140207dd477eaacb2beacab0ffa62dfdd2')},
    "Mars": {"ru": _editorial_text('astrology.02a55ef5222549174da43b4b7b88a8bcb0808a2455a4bddc14e4b50442253607'), "en": _editorial_text('astrology.8fae67f4940955b9e4510e1115b51f22284f2852f9603f12ea17c1b39ebd9707')},
    "Descendant": {"ru": "Партнёрство", "en": "Partnership"},
    "Moon": {"ru": _editorial_text('astrology.86f852b61a647bb81d2160e92471b52fc1bd4542cc408b1ca70d6a17277a4e68'), "en": _editorial_text('astrology.294654ea98a22c066c83ed7bb928095c480a227a2c7a4b3dee6d645208433a52')},
    "Sun": {"ru": "Самореализация", "en": "Self-realization"},
    "Saturn": {"ru": _editorial_text('astrology.130ff584af0729ca7355663d37aab8fe60c9fd768650e1655d39b4447cbff0b0'), "en": _editorial_text('astrology.6a352f743256d54ad7a64539b67fb77c29f78bfd89f0683c02337d9a059cf14e')},
    "Medium_Coeli": {"ru": _editorial_text('astrology.193c068965adf5cd8649cde2e30a23886361e3809bad063d01bdd2be0dfc8dd7'), "en": _editorial_text('astrology.7d61306bcd1fa05cf8ed5d0ea4531be8c62b0db5083482158e4cbc281db48a5c')},
    "Ascendant": {"ru": _editorial_text('astrology.02f9f9d1a3e4d1dbccc669d1e1781cf4ada84cdedae3e5f5873aea6133a4c393'), "en": _editorial_text('astrology.9095c77e38bd29bf6569e99795d84cff3554f1bfd02e4946e0d9158d65777880')},
    "Mercury": {"ru": _editorial_text('astrology.d323e89cb1b5f1b6a296228e7d32f6fdd14a0304c6f52f1047696f711d49b196'), "en": _editorial_text('astrology.3b862420935cfa1a92d545cf5522f4a3e06cb47939d7d3f799f34650d52cb35e')},
    "Jupiter": {"ru": _editorial_text('astrology.3824d6c20d0bd5907f6565c5a70e99b92d3e0fd449629ae8b536b047ad7b7e54'), "en": _editorial_text('astrology.aebfeaa68279e160471835f8faf2dec92eed8ebc522ae79bd200e6e53badd1d3')},
    "Uranus": {"ru": _editorial_text('astrology.df92f6f42434c5edef545faa7992ec0458cacc3da4a63ee49520dcd092d294ef'), "en": _editorial_text('astrology.442c2d6376df1cb3f01efefe79323ff1b464d0ce19113f9e398286cd16c82ab4')},
    "Neptune": {"ru": _editorial_text('astrology.85891e9e9401ba467d63f6ebeb37fdb6ae8dd74615a72e6bea1825cb8b0dd3c9'), "en": _editorial_text('astrology.d015b6c255b056deaf41e0d4b821199aff674494eeb8bca269bda3cbca077e37')},
    "Pluto": {"ru": _editorial_text('astrology.7944a78ada25c65dd1fdc4db46e48cbab195fc14d67b465718ac063aeeffe5b4'), "en": _editorial_text('astrology.eb385854a375b9b3e6607dbe0e79dcaf6e2d0968e6a144233adb79bf475ca27f')},
    "Mean_North_Lunar_Node": {"ru": _editorial_text('astrology.6c7bf77037bb1a3485057905ff8ce153cd478bf6b46c523ebb88a798d14154f1'), "en": _editorial_text('astrology.e4ed09aec466522189d4804ca5c5d5286dbf713710e2b1eabdcaf1432666ced2')},
    "True_North_Lunar_Node": {"ru": _editorial_text('astrology.6c7bf77037bb1a3485057905ff8ce153cd478bf6b46c523ebb88a798d14154f1'), "en": _editorial_text('astrology.e4ed09aec466522189d4804ca5c5d5286dbf713710e2b1eabdcaf1432666ced2')},
}
_SIGN_ORDER = ["Ari", "Tau", "Gem", "Can", "Leo", "Vir", "Lib", "Sco", "Sag", "Cap", "Aqu", "Pis"]

# Сферы жизни и натальные точки-сигнификаторы (для прогноза «что вас ждёт»)
_LIFE_SPHERES = [
    ("love", (_editorial_text('astrology.097ad7b8e0a6fb3ed83342d1091ffe5b61eb051a1e680b3b08189b060ac646b8'), _editorial_text('astrology.537a8d24ca437099f0f19c40ed8cef140207dd477eaacb2beacab0ffa62dfdd2')), "💗",
     {"Venus", "Descendant", "Mars"}),
    ("career", (_editorial_text('astrology.6e60483b499d714b340f78b894617d87ccb01db1476088b290dfb17a737e1bef'), _editorial_text('astrology.13c5ed30d3a44d1d7db359354deb64e355184b2e212979510d6efb5373e9a03d')), "💼",
     {"Medium_Coeli", "Saturn", "Sun", "Jupiter"}),
    ("health", (_editorial_text('astrology.1678294c898b9f99b909a44ebde233cd629c9780cb07df39ba8e0b72ca62e495'), _editorial_text('astrology.20069a5e663aff4f2c3ae72d59130db44d0424bc3544d66559ab58b955b8d0c2')), "🌿",
     {"Ascendant", "Moon", "Mars"}),
    ("home", (_editorial_text('astrology.5504f1338f15e59763b2dd3b616dbe52e1d1d1c8755aea6985e70942f1bda24f'), _editorial_text('astrology.be51feb2cac6393c1e26fc867e2c0d37607c611a4c85d1a1b30c8078eb77450c')), "🏠",
     {"Imum_Coeli", "Moon"}),
    ("growth", (_editorial_text('astrology.aae3c8d017117e983a9ecdd9b1f5e36c5bcdbbcfeaff9bcf3aee61800ff2b2ba'), _editorial_text('astrology.f981c6178facc74da0834f31725c989bfac4f9d605525368ae504f53ccf7dcb1')), "🧭",
     {"Mercury", "Jupiter", "Mean_North_Lunar_Node", "True_North_Lunar_Node",
      "Mean_South_Lunar_Node", "True_South_Lunar_Node"}),
]

_SPHERE_TONE = {
    "favorable": (_editorial_text('astrology.432f16a30b85b9abf508747fd769004a7d86021ce301292bb0b61ed725a19950'),
                  _editorial_text('astrology.d73110972aadadbb0626ce5525b373007d6471828612413720d248ffd2b374dc')),
    "challenging": (_editorial_text('astrology.71cf8536d60aa17bc59b85bee5e8a9ec3a98d45d452b4b3ceac0b1d2bbcd2f68'),
                    _editorial_text('astrology.cf13071b8c516b59200e790bf3f2f49d1d97ed18264e0112c0a39149566e91d6')),
    "mixed": (_editorial_text('astrology.92c4e304c71dba21e91c2c9c21127f30bbb74e238c12af8dd1f16afd7874c055'),
              _editorial_text('astrology.8edfcd14073e052a1880ad18d35bb427a0bc59c0d9f00be930947d9b80462dd1')),
    "active": (_editorial_text('astrology.7601b70d7bf58fdbc2a3b5d21253aa3851934167c49bf8644fc7048853673130'),
               _editorial_text('astrology.c7a972c20f3202e57e229ef96d0f4e24d406b1b9bf9f4055389173d292b77dd6')),
    "calm": (_editorial_text('astrology.4e06dfc37fea7cef0904dba25f0f58d6cc069b5037da820fb7d14fae00813075'),
             _editorial_text('astrology.47824195206872f041debe5ee1de8ca7a2ddca9c7ff112a5b016c0bf1891629a')),
}


# Сила транзита по действующей (транзитной) планете
_TR_MAJOR = {
    "Saturn", "Uranus", "Neptune", "Pluto", "Chiron",
    "Mean_North_Lunar_Node", "True_North_Lunar_Node",
    "Mean_South_Lunar_Node", "True_South_Lunar_Node",
}
_TR_PERSONAL = {"Sun", "Mercury", "Venus", "Mars"}


def _tr_intensity(moving: str) -> tuple[str, int]:
    if moving in _TR_MAJOR:
        return ("major", 3)
    if moving == "Jupiter":
        return ("notable", 2)
    if moving in _TR_PERSONAL:
        return ("personal", 1)
    return ("passing", 0)


def _sphere_key_of(point: str):
    for key, label, icon, sigs in _LIFE_SPHERES:
        if point in sigs:
            return key, label, icon
    return "general", (_editorial_text('astrology.375fc05b5375ee7d7b09e169919958678ff873145da37281c5ccbae6a8d70f44'), _editorial_text('astrology.b9189a32d3af891d5c7ab242ab6a4011e3ae2895dea306f1470a6134bde6a8f0')), "🌌"


_TR_MAJOR_ASPECTS = {"conjunction", "opposition", "square", "trine", "sextile"}


def _transit_overview(aspects: list[dict], lang: str) -> dict:
    """Структурированный разбор транзитов: шапка + ключевые влияния + группы по сферам."""
    L = lambda pair: pair[1] if lang == "en" else pair[0]
    items = []
    for a in aspects:
        if not a.get("interp"):
            continue
        # Только мажорные аспекты от значимых планет (без быстрой транзитной Луны/углов).
        if a.get("aspect") not in _TR_MAJOR_ASPECTS:
            continue
        lvl, w = _tr_intensity(a.get("p2"))
        if w < 1:
            continue
        skey, slabel, sicon = _sphere_key_of(a.get("p1"))
        nat = a.get("nature")
        tone = "good" if nat == "harmonious" else "tense" if nat == "tense" else "dynamic"
        items.append({
            "t_symbol": a.get("p2_symbol"), "t_name": a.get("p2_ru"),
            "n_symbol": a.get("p1_symbol"), "n_name": a.get("p1_ru"),
            "aspect_symbol": a.get("aspect_symbol"), "aspect": a.get("aspect_ru"),
            "orbit": a.get("orbit"), "interp": a.get("interp"),
            "tone": tone, "intensity": lvl, "weight": w,
            "sphere": skey, "label": L(slabel), "icon": sicon,
        })

    key = sorted([i for i in items if i["weight"] >= 2], key=lambda i: (-i["weight"], i["orbit"]))[:5]

    order = [k for k, _, _, _ in _LIFE_SPHERES] + ["general"]
    groups = []
    for k in order:
        grp = [i for i in items if i["sphere"] == k]
        if not grp:
            continue
        grp.sort(key=lambda i: (-i["weight"], i["orbit"]))
        goods = sum(1 for i in grp if i["tone"] == "good")
        tenses = sum(1 for i in grp if i["tone"] == "tense")
        gtone = "challenging" if tenses > goods else "favorable" if goods > tenses else "mixed" if (goods or tenses) else "active"
        groups.append({"key": k, "label": grp[0]["label"], "icon": grp[0]["icon"],
                       "tone": gtone, "count": len(grp), "items": grp[:6]})

    good = sum(1 for i in items if i["tone"] == "good")
    tense = sum(1 for i in items if i["tone"] == "tense")
    if tense > good:
        mood = (_editorial_text('astrology.0a6b82d8d72f0fd903eb7d43639631957dddb041624c30c460b39b44c8e948e3'), _editorial_text('astrology.2570a5eb6f1bc9658847d2053c1010e9ff644b3f23163e73043d4610c86c2730'))
    elif good > tense:
        mood = (_editorial_text('astrology.f919da576d4ce03cccc63ec613c0f9097c394cc8248290aab28df95942167f52'), _editorial_text('astrology.12abee2e6943b11a02d97268d2bfe829313bb72492ccd672bd23749967dbf503'))
    else:
        mood = (_editorial_text('astrology.391eb6c22b98aed1a0bfc9b6e08338ded97be79dfc1d3976c1b23cd1e7167165'), _editorial_text('astrology.c0593851c90ac8d0cdf6327884afc21312c90bebabb191a31a501ea6399e03c3'))
    if lang == "en":
        headline = f"{len(items)}{_editorial_text('astrology.31077737e43761dedd261d638789efb7564de8b60f1a20efe97303ee5b609d40')}{len(key)}{_editorial_text('astrology.515672e054d57874aefd96924412d5fc1a667677c0000b6760c15b170771c0cf')}{mood[1]}."
    else:
        headline = f"{_editorial_text('astrology.84a894ff62f0ac590920da122438a27ed70252ff339198c9fb22c3628550e20b')}{len(items)}{_editorial_text('astrology.6ab61561854d033143f7dbadc713c3b0aafc058e16c7bcc745fad51f89dae545')}{len(key)}{_editorial_text('astrology.1d36ed14b50f4ecec43615a90e79bcfbfd42c5d80ae3bfcdcb0aefb2648cd66b')}{mood[0]}."

    return {"headline": headline, "key": key, "groups": groups}


def _forecast_by_sphere(events: list[dict], lang: str) -> list[dict]:
    """Группирует ключевые транзиты периода по сферам жизни и оценивает тон каждой."""
    out = []
    for key, name_pair, icon, points in _LIFE_SPHERES:
        # Сфера активируется транзитом к её натальной точке-сигнификатору (p2).
        ev = sorted([e for e in events if e.get("p2") in points], key=lambda e: e["orb"])
        pos = sum(1 for e in ev if e["tone"] == "positive")
        neg = sum(1 for e in ev if e["tone"] == "negative")
        if not ev:
            tone = "calm"
        elif pos and not neg:
            tone = "favorable"
        elif neg and not pos:
            tone = "challenging"
        elif pos and neg:
            tone = "mixed"
        else:
            tone = "active"
        name = name_pair[1] if lang == "en" else name_pair[0]
        intro = _editorial_text('astrology.24e87bb94b4a5e5357ac5afb638702539e20d8868fa01fcda0dbdb4c056899fe') if lang == "en" else _editorial_text('astrology.36f76a461e8a373ce39c5355799adc909bccbbdc8127d714115faaa5139dca39')
        text = f'{intro} {_l_pair(_SPHERE_TONE[tone], lang)}.'
        out.append({
            "key": key,
            "name": name,
            "icon": icon,
            "tone": tone,
            "text": text,
            "count": len(ev),
            "highlights": ev[:5],
        })
    return out


def _l_pair(pair, lang):
    return pair[1] if lang == "en" else pair[0]


def _completed_age(birth: datetime, at: datetime) -> int:
    """Полных лет на дату `at`."""
    age = at.year - birth.year
    if (at.month, at.day) < (birth.month, birth.day):
        age -= 1
    return max(age, 0)


def _annual_profection(model, age: int) -> dict:
    """Годовая профекция (хелленистическая, по цельным знакам): активный дом, знак года, хозяин года."""
    asc_index = model.first_house.sign_num  # 0-11
    house_num = (age % 12) + 1
    sign_index = (asc_index + age) % 12
    lang = _lang()
    profected_sign = _SIGN_ORDER[sign_index]
    sign_ru = C.sign_name(profected_sign, lang)

    lord_name = I.chart_ruler(profected_sign)  # традиционный управитель знака года
    lord = None
    if lord_name:
        rp = getattr(model, lord_name.lower(), None)
        if rp is not None:
            rd = serialize_point(rp)
            lord = {
                "name_ru": rd["name_ru"], "symbol": rd["symbol"],
                "sign_ru": rd["sign_ru"], "sign_loc": C.sign_in(rd["sign"], lang),
                "house_num": rd["house_num"],
            }

    sphere = C.house_meaning(house_num, lang)
    focus = I.house_focus(house_num, lang)
    if lang == "en":
        text = (
            f"{_editorial_text('astrology.3843bfd18688285924cc3944f490cc433627fe2dca7fae3efafe5ce5b9c47f63')}{age}{_editorial_text('astrology.dab142a394a33ce21ae2080e97168604ba8b84f9cd7fe6d27e8b012b88c163bb')}{_ord(house_num)}{_editorial_text('astrology.f8daea1a24ec2e31055f6227df991b08d66a0600882daad78c32a7cf1378b9e2')}{sphere.lower()}{_editorial_text('astrology.68270a84dd85d90596fcfbeb9233586a595f53531d6cdd0dde950c27c4e8fcbd')}{sign_ru}. "
        )
        if lord:
            text += (
                f"{_editorial_text('astrology.60358abd3189ba4e2382cb10a77f64cb65d06e9de70921740d704092b45083bd')}{lord['name_ru']}{_editorial_text('astrology.5e3f4fcd4bc35db569e83d9b979953a604c7c5412067fa68a657874673bedd38')}{lord['sign_ru']}"
                + (f"{_editorial_text('astrology.c9e58b82913b666785e1ef47f2d954a9b0fc764215eeadaec2e9a34c1c06b045')}{lord['house_num']}" if lord['house_num'] else "")
                + f"{_editorial_text('astrology.99b8d595769e05dc366490da774702ce11100d7e41eb83e52677d075bdcb27f8')}{focus}{_editorial_text('astrology.daba704543e1a302624af3af9dce9c99d880b5c7e98f6a11c8b5a0c2418d83fa')}{lord['name_ru']}{_editorial_text('astrology.e3ee915a8e8c7aa02d2fced443314522b20824abd2535d5959c41dc8ab8a09e4')}{_ord(house_num)}{_editorial_text('astrology.ffbb38a5fca9648c20e9a75555733986b3742508c86a22b485fe280a4a154ac7')}"
            )
    else:
        text = (
            f"{_editorial_text('astrology.0159b67b0b8ee8b0cb19a60fa422e97c4d09c78bbdea9eda8007e17260ab6b52')}{age}{_editorial_text('astrology.ae2bf49bf70c0838f53914a03eda5ab3a5a8194aac3db4957e1cf9e503160538')}{house_num}{_editorial_text('astrology.6643edcaad982c6be53b8c09c0fdcbc625e94c3fb58e7e6392b829bb29c3630a')}{sphere.lower()}{_editorial_text('astrology.e6eca097eec89de19d78f7b3cd2a6b9a13ab8a27df39096740d7b73c06b66f18')}{sign_ru}. "
        )
        if lord:
            text += (
                f"{_editorial_text('astrology.a39996d43f7b66ebda00eab21cd286c27283fb0c377d326f197357c00e32a580')}{lord['name_ru']}{_editorial_text('astrology.2d42433e82a29a77869ffc764717a2dee76e5bd55f320a9746070fdf5d939d44')}{lord['sign_ru']}"
                + (f", {lord['house_num']}{_editorial_text('astrology.685547d6d819c039ea9fd4e48a0d6e77dcbe52bf84d16c95903cbb7cdd7e5a65')}" if lord['house_num'] else "")
                + f"{_editorial_text('astrology.87704f27c80dc1723f86983f86577d94da127f77783eee52e35abe25263863aa')}{focus}{_editorial_text('astrology.d440f217881c3628d2fd05151c78f55a7bc10765040f1b2566fc4bf578801294')}{lord['name_ru']}{_editorial_text('astrology.56a336d9c8301a00da8b6c344769b83634531e06bf1ebb6b567e605931cce6bf')}{house_num}{_editorial_text('astrology.8c7ced1371e8d6b773775b97a58213816b9d74fb74bccc10756f4c90c99c35f1')}"
            )
    return {
        "age": age,
        "house_num": house_num,
        "sphere": sphere,
        "sign_ru": sign_ru,
        "lord": lord,
        "text": text,
    }


def _progressed_moon(natal_model, natal_params: dict, at: datetime) -> dict:
    """Прогрессивная Луна (день=год) на дату — эмоциональный фон периода."""
    birth = datetime(
        natal_params["year"], natal_params["month"], natal_params["day"],
        natal_params.get("hour", 12), natal_params.get("minute", 0),
    )
    elapsed_years = (at - birth).total_seconds() / (TROPICAL_YEAR_DAYS * 86400)
    prog_dt = birth + timedelta(days=elapsed_years)
    prog_model = build_subject(
        name="Прогр.Луна",
        year=prog_dt.year, month=prog_dt.month, day=prog_dt.day,
        hour=prog_dt.hour, minute=prog_dt.minute,
        lat=natal_params["lat"], lng=natal_params["lng"],
        tz_str=natal_model.tz_str, city=natal_params.get("city", ""),
        houses_system=natal_params.get("houses_system", "P"),
    )
    lang = _lang()
    moon = serialize_point(prog_model.moon)
    manner = I.sign_manner(moon["sign"], lang)
    if lang == "en":
        text = (
            f"{_editorial_text('astrology.f2b878a815d78336bb747880b288dd4cc7bdb6d2b7cf745946c57938e9020200')}{moon['sign_ru']}{_editorial_text('astrology.ec61ffee7bffc3c82f5d4175d06986aae439039aff2d8f85a892f0046a6f5deb')}{manner}{_editorial_text('astrology.4c4ed91e1e728e533e188c4fb796717d4652c2eb345890b8eac09357f2a0f26c')}"
        )
    else:
        text = (
            f"{_editorial_text('astrology.78f7736207a0b49e11127c3028ac6f1259e8148e63d3d7fe51734648d43afe03')}{moon['sign_ru']}{_editorial_text('astrology.77dcde04dc03c33084c2cdefc1cb26850e1bb81dabe0d0404e44bbde7b9bd8da')}{manner}{_editorial_text('astrology.cb997a2ce8c98e9ec4162ee35e2204fcc1ee06a81af23dd5ef1ba18e92e9de67')}"
        )
    return {"sign": moon["sign"], "sign_ru": moon["sign_ru"], "deg": moon["deg"], "text": text}


def forecast_report(
    natal_params: dict,
    start: dict,
    end: dict,
    location: Optional[dict] = None,
) -> dict:
    """Астрологический прогноз на период: профекция, прогрессивная Луна и ключевые транзиты."""
    _set_lang(natal_params.get("lang", "ru"))
    natal_model = build_subject(**natal_params)

    loc = location or {
        "lat": natal_params["lat"], "lng": natal_params["lng"],
        "tz_str": natal_params.get("tz_str"), "city": natal_params.get("city", ""),
    }
    tz = loc.get("tz_str") or resolve_timezone(loc["lat"], loc["lng"])

    start_dt = datetime(start["year"], start["month"], start["day"], 12, 0)
    end_dt = datetime(end["year"], end["month"], end["day"], 12, 0)
    if end_dt <= start_dt:
        raise ValueError(_editorial_text('astrology.9f1cdcd39d555297c2a729335eace939500a41dae51ecc017715d8fe17418be2'))
    if (end_dt - start_dt).days > 1100:
        raise ValueError(_editorial_text('astrology.08b959126104478ef88e140bd7fe0ec8e97fbbf729d7a67a580cf8f75346b856'))

    birth = datetime(natal_params["year"], natal_params["month"], natal_params["day"])
    age = _completed_age(birth, start_dt)
    profection = _annual_profection(natal_model, age)
    prog_moon = _progressed_moon(natal_model, natal_params, start_dt)

    # Суточный поиск кандидатов; каждый ретроградный проход сохраняется отдельно.
    eph_points = EphemerisDataFactory(
        start_datetime=start_dt, end_datetime=end_dt,
        step_type="days", step=1,
        lat=loc["lat"], lng=loc["lng"], tz_str=tz,
    ).get_ephemeris_data_as_astrological_subjects()
    moments = TransitsTimeRangeFactory(natal_model, eph_points).get_transit_moments()

    passes = _transit_pass_minima(
        moments,
        lambda asp: asp["p1_name"] in _FORECAST_SLOW and asp["aspect"] in _FORECAST_ASPECTS,
    )

    lang = _lang()
    default_sphere = {"ru": _editorial_text('astrology.84c78f79451e45ee8b404284d71e7b1d0e52d7ed7ed21d231f1f2c1fed386661'), "en": _editorial_text('astrology.2df4ea120db5b615d5c4bb8e13096ca1fb4895976864ea50cdd4d75335b9e760')}
    events = []
    for (p1, kind, p2), info in passes:
        if info["orb"] > 3.0:
            continue  # аспект не становится точным в периоде — это фон, а не событие
        info = _refine_transit_pass(natal_model, loc, tz, (p1, kind, p2), info)
        text = I.interpret_transit(p1, kind, p2, lang)
        if not text:
            continue
        nature = C.ASPECTS.get(kind, {}).get("nature", "")
        tone = "positive" if nature == "harmonious" else ("negative" if nature == "tense" else "neutral")
        events.append({
            "date": info["date"][:10],
            "exact_datetime": info["date"],
            "p1": p1,
            "p1_ru": C.point_name(p1, lang),
            "p1_symbol": C.point_symbol(p1),
            "aspect_ru": C.aspect_name(kind, lang),
            "aspect_symbol": C.aspect_symbol(kind),
            "nature": nature,
            "tone": tone,
            "sphere": C._l(_SPHERE_OF.get(p2, default_sphere), lang),
            "p2": p2,
            "p2_ru": C.point_name(p2, lang),
            "p2_symbol": C.point_symbol(p2),
            "orb": round(info["orb"], 2),
            "text": text,
        })
    events.sort(key=lambda e: (e["date"], e["orb"]))

    if lang == "en":
        summary = (
            f"{_editorial_text('astrology.a0e092d3b2b8900d9f35aefa0b0a119a3c4bba5765bfe49f7f83986a6fe46e26')}{_ord(profection['house_num'])}{_editorial_text('astrology.6af072264af0a605148165f983bd2e4d0b16f8b298b7ee977c037812c57bf50f')}{profection['sign_ru']}{_editorial_text('astrology.2a0e5f5c3a5a1d9faa0c6ba9970221e294fd4df8951d142dd0ff997b5de0d0f0')}{C.sign_in(prog_moon['sign'], lang)}{_editorial_text('astrology.d77151f147603bdf7820df055ceca7b679d57da049b074bf1ec47dcdf741be15')}"
        )
    else:
        summary = (
            f"{_editorial_text('astrology.31632658eb7a9d7b4bc9a75d464618d3acda7d8e342198e21f60af30b00e54b2')}{profection['house_num']}{_editorial_text('astrology.ca61285f8477cf303e62248632e454a8fde700aea8b31cd44f97b274f631e0f9')}{profection['sign_ru']}{_editorial_text('astrology.27fb2d19cea2aa01ae85bc34fa3127ddd0f3d4a0b6260333fecbf261549010bb')}{C.sign_in(prog_moon['sign'], lang)}{_editorial_text('astrology.6da1b446ac003116a82ad1559f1277cb9f306b78093991e119be4509ea9a78be')}"
        )

    sphere_forecast = _forecast_by_sphere(events, _lang())

    return {
        "natal_meta": _meta(natal_model),
        "location_meta": {
            "lat": loc["lat"], "lng": loc["lng"], "tz_str": tz,
            "city": loc.get("city") or natal_params.get("city", ""),
        },
        "start": start_dt.strftime("%Y-%m-%d"),
        "end": end_dt.strftime("%Y-%m-%d"),
        "profection": profection,
        "progressed_moon": prog_moon,
        "sphere_forecast": sphere_forecast,
        "events": events,
        "count": len(events),
        "summary": summary,
    }


def transit_calendar_report(
    natal_params: dict,
    start: dict,
    end: dict,
    location: Optional[dict] = None,
) -> dict:
    """Эфемеридный календарь транзитов: даты точных аспектов транзитных планет к наталу за период."""
    _set_lang(natal_params.get("lang", "ru"))
    natal_model = build_subject(**natal_params)

    loc = location or {
        "lat": natal_params["lat"], "lng": natal_params["lng"],
        "tz_str": natal_params.get("tz_str"), "city": natal_params.get("city", ""),
    }
    tz = loc.get("tz_str") or resolve_timezone(loc["lat"], loc["lng"])

    # Полдень местного времени, чтобы дата в UTC не «съезжала» на день назад.
    start_dt = datetime(start["year"], start["month"], start["day"], 12, 0)
    end_dt = datetime(end["year"], end["month"], end["day"], 12, 0)
    if end_dt <= start_dt:
        raise ValueError(_editorial_text('astrology.a87f6de2bf71a60c8a74bcd622109a5e639138d1e7822c58ee1c2cf28fa88e8a'))
    if (end_dt - start_dt).days > 730:
        raise ValueError(_editorial_text('astrology.ffab982d4da2721c46286cb43cf55428bcd4cca4da6db694cc9eb1e3e890e8cc'))

    eph_points = EphemerisDataFactory(
        start_datetime=start_dt,
        end_datetime=end_dt,
        step_type="days",
        step=1,
        lat=loc["lat"],
        lng=loc["lng"],
        tz_str=tz,
    ).get_ephemeris_data_as_astrological_subjects()

    # Лунный календарь: фаза + знак Луны на каждый день (из тех же эфемерид).
    lang = _lang()
    lunar = {}
    for subj in eph_points:
        try:
            ds = f'{subj.year:04d}-{subj.month:02d}-{subj.day:02d}'
        except Exception:
            continue
        if ds in lunar:
            continue
        msign = subj.moon.sign
        lp = subj.lunar_phase
        pname = getattr(lp, "moon_phase_name", "") if not isinstance(lp, dict) else lp.get("moon_phase_name", "")
        emoji = getattr(lp, "moon_emoji", "") if not isinstance(lp, dict) else lp.get("moon_emoji", "")
        lunar[ds] = {
            "sign": msign,
            "sign_ru": C.sign_name(msign, lang),
            "sign_symbol": C.sign_symbol(msign),
            "phase": pname,
            "phase_ru": C.moon_phase_name(pname, lang),
            "emoji": emoji,
            "mood": I.moon_sign_mood(msign, lang),
            "advice": I.lunar_phase_advice(pname, lang),
        }

    moments = TransitsTimeRangeFactory(natal_model, eph_points).get_transit_moments()

    passes = _transit_pass_minima(
        moments, lambda asp: asp["p1_name"] not in _CALENDAR_EXCLUDE_TRANSIT,
    )

    lang = _lang()
    start_str = start_dt.strftime("%Y-%m-%d")
    end_str = end_dt.strftime("%Y-%m-%d")
    events = []
    for (p1, kind, p2), info in passes:
        if info["orb"] > 1.5:
            continue  # аспект не становится точным в периоде
        info = _refine_transit_pass(natal_model, loc, tz, (p1, kind, p2), info)
        d10 = info["date"][:10]
        # Минимум на самой границе при заметном орбе = аспект точен ВНЕ периода.
        if info["orb"] > 0.4 and d10 in (start_str, end_str):
            continue
        events.append({
            "date": info["date"][:10],
            "exact_datetime": info["date"],
            "p1": p1,
            "p1_ru": C.point_name(p1, lang),
            "p1_symbol": C.point_symbol(p1),
            "aspect": kind,
            "aspect_ru": C.aspect_name(kind, lang),
            "aspect_symbol": C.aspect_symbol(kind),
            "nature": C.ASPECTS.get(kind, {}).get("nature", ""),
            "p2": p2,
            "p2_ru": C.point_name(p2, lang),
            "p2_symbol": C.point_symbol(p2),
            "orb": round(info["orb"], 2),
            "interp": I.interpret_transit(p1, kind, p2, lang) or I.interpret_aspect(p1, kind, p2, lang),
        })

    events.sort(key=lambda e: (e["date"], e["orb"]))

    return {
        "natal_meta": _meta(natal_model),
        "location_meta": {
            "lat": loc["lat"], "lng": loc["lng"], "tz_str": tz,
            "city": loc.get("city") or natal_params.get("city", ""),
        },
        "start": start_dt.strftime("%Y-%m-%d"),
        "end": end_dt.strftime("%Y-%m-%d"),
        "events": events,
        "count": len(events),
        "lunar": lunar,
    }


# --------------------------------------------------------------------------- #
#  Ректификация времени рождения
# --------------------------------------------------------------------------- #
# Планеты для солнечно-дуговых дирекций к углам
_RECT_DIR_PLANETS = ["sun", "moon", "mercury", "venus", "mars", "jupiter", "saturn"]
_RECT_DIR_RU = {
    "sun": "Солнце", "moon": "Луна", "mercury": "Меркурий", "venus": "Венера",
    "mars": "Марс", "jupiter": "Юпитер", "saturn": "Сатурн",
}
# Транзитные планеты к углам
_RECT_TRANSIT = ["mars", "jupiter", "saturn", "uranus", "neptune", "pluto"]
# Аспекты для попадания на угол: угол -> вес
_RECT_ASPECTS = {0: 1.0, 180: 1.0, 90: 0.9, 120: 0.6, 60: 0.5}
SYMBOLIC_KEY_DEG_PER_YEAR = 1.0  # символические дирекции: 1°=1 год (птолемеевский ключ)
NAIBOD_KEY_DEG_PER_YEAR = 0.9856473  # ключ Найбода: среднее суточное движение Солнца (°/год)

# Веса трёх ключей дирекций (настраиваемые — тюнятся по бенчмарку benchmark/tune.py).
_RECT_KEY_WEIGHT_SOLARARC = 1.0   # солнечная дуга (истинное движение Солнца)
_RECT_KEY_WEIGHT_SYMBOLIC = 1.0   # символический 1°/год
_RECT_KEY_WEIGHT_NAIBOD = 1.0     # ключ Найбода
# Вес попадания в СРЕДНИЙ куспид (2/3/5/6/8/9/11/12) относительно углового (1/4/7/10).
_RECT_MEAN_CUSP_WEIGHT = 0.4
# Порядок атрибутов домов Kerykeion (индекс i -> куспид дома i+1).
_RECT_HOUSE_ATTRS = [
    "first_house", "second_house", "third_house", "fourth_house",
    "fifth_house", "sixth_house", "seventh_house", "eighth_house",
    "ninth_house", "tenth_house", "eleventh_house", "twelfth_house",
]

# Тип события -> планеты-сигнификаторы (их попадания усиливаются).
_RECT_EVENT_SIG = {
    "relationship": {"venus", "mars"},           # любовь, брак
    "career": {"saturn", "sun"},                 # карьера, работа, статус
    "child": {"moon", "jupiter"},                # рождение ребёнка
    "move": {"moon", "saturn"},                  # переезд, смена места
    "loss": {"saturn", "pluto", "mars"},         # утрата, кризис
    "health": {"mars", "saturn"},                # травма, операция, болезнь
}
# Уточнённый тип (type_detail) -> сигнификаторы. Если задан и известен — имеет приоритет.
_RECT_EVENT_SIG_DETAIL = {
    "divorce":        {"venus", "mars", "uranus", "saturn"},
    "separation":     {"venus", "saturn", "uranus"},
    "emigration":     {"jupiter", "uranus", "moon"},
    "parentloss":     {"saturn", "moon", "sun", "pluto"},
    "parentaldivorce": {"saturn", "moon", "sun", "pluto"},
    "death":          {"saturn", "pluto", "mars", "neptune"},
    "fame":           {"sun", "jupiter"},
    "retirement":     {"saturn"},
    "accident":       {"mars", "uranus"},
    "military":       {"mars", "saturn"},
    "conviction":     {"saturn", "pluto", "mars"},
}
_RECT_SIG_BOOST = 1.7  # множитель счёта, если сработал профильный сигнификатор события

# Анкета по Асценденту: код ответа -> знаки, которым он соответствует.
# Используется для подбора восходящего знака по описанию человека (метод astro-app).
_RECT_ASC_TRAITS = {
    # Темперамент / стиль реакции
    "temp_fire":  ["Ari", "Leo", "Sag"],
    "temp_earth": ["Tau", "Vir", "Cap"],
    "temp_air":   ["Gem", "Lib", "Aqu"],
    "temp_water": ["Can", "Sco", "Pis"],
    # Крест / модальность — КЛЮЧЕВОЙ разделитель знаков внутри стихии.
    "mode_cardinal": ["Ari", "Can", "Lib", "Cap"],   # инициирует, начинает
    "mode_fixed":    ["Tau", "Leo", "Sco", "Aqu"],   # держит курс, упорен
    "mode_mutable":  ["Gem", "Vir", "Sag", "Pis"],   # гибок, подстраивается
    # Внешность / телосложение
    "look_bright": ["Ari", "Leo"],
    "look_solid":  ["Tau", "Can", "Cap"],
    "look_slim":   ["Gem", "Vir", "Aqu", "Sag"],
    "look_soft":   ["Lib", "Sco", "Pis"],
    # Первое впечатление / манера держаться
    "manner_leader":   ["Ari", "Leo", "Cap"],
    "manner_friendly": ["Gem", "Lib", "Aqu", "Sag"],
    "manner_reserved": ["Tau", "Vir", "Cap"],
    "manner_deep":     ["Can", "Sco", "Pis"],
    # Ценности / бескорыстные интересы
    "value_freedom":    ["Ari", "Leo", "Sag"],
    "value_stability":  ["Tau", "Vir", "Cap"],
    "value_ideas":      ["Gem", "Lib", "Aqu"],
    "value_feeling":    ["Can", "Sco", "Pis"],
}
_RECT_ASC_Q_WEIGHT = 4.5  # вклад совпавшего признака анкеты — должен ДОМИНИРОВАТЬ над шумом событий
# Событийная часть ненадёжна сама по себе; ограничиваем её вклад, чтобы детерминированные
# сигналы (анкета Асцендента, предрасположенности) задавали ОКНО, а события — уточняли минуту.
_RECT_EVENT_CAP = 6.0  # максимум суммарного событийного вклада в счёт кандидата
# Сигмы (°) резкости попаданий: грубый проход (детекция регионов) и точный (различение пика).
_RECT_SIGMA_COARSE_DIR = 1.2   # дирекции, грубо
_RECT_SIGMA_COARSE_TR = 1.5    # транзиты, грубо
_RECT_SIGMA_FINE_DIR = 0.45    # дирекции, точно
_RECT_SIGMA_FINE_TR = 0.6      # транзиты, точно


def _sharp(orb: float, sigma: float) -> float:
    """Резкая (гауссова) функция близости: только тугие попадания дают вес."""
    return math.exp(-(orb / sigma) ** 2)


def _rect_best_contact(lon: float, targets: list[float], orb_max: float):
    """Самое ТОЧНОЕ (мин. орб) попадание долготы lon в аспект к одной из целей."""
    best = None
    for t in targets:
        diff = _angular_diff(lon, t)
        for ang, w in _RECT_ASPECTS.items():
            orb = abs(diff - ang)
            if orb <= orb_max and (best is None or orb < best["orb"]):
                best = {"orb": round(orb, 2), "w": w, "angle": ang}
    return best


def _rect_best_contact_labeled(lon: float, labeled_targets: list[tuple], orb_max: float):
    """Как _rect_best_contact, но цели помечены и взвешены:
    labeled_targets = [(долгота, метка, вес_цели), ...]. Возвращает dict с доп. ключами
    "target" = метка цели (напр. номер дома) и "cusp_w" = вес цели (угловой=1.0, средний<1)."""
    best = None
    for t, label, cusp_w in labeled_targets:
        diff = _angular_diff(lon, t)
        for ang, w in _RECT_ASPECTS.items():
            orb = abs(diff - ang)
            if orb <= orb_max and (best is None or orb < best["orb"]):
                best = {"orb": round(orb, 2), "w": w, "angle": ang, "target": label, "cusp_w": cusp_w}
    return best


# ── Этап 3 (по образцу astro-app): предрасположенности натальной карты ──
_RECT_MALEFIC = ("mars", "saturn", "pluto")
_RECT_BENEFIC = ("venus", "jupiter")
_RECT_BARREN = {"Gem", "Leo", "Vir"}
_RECT_FERTILE = {"Can", "Sco", "Pis"}
_RECT_PRED_WEIGHT = 1.6   # масштаб вклада предрасположенностей
_RECT_PARENT_WEIGHT = 1.2  # бонус «Асц = знак Солнца родителя»
_RECT_RATING = {"yes_strong": 1.0, "yes": 0.5, "no_strong": -1.0, "no": -0.5, "unknown": 0.0}
# Ключи предрасположенностей (должны совпадать с фронтендом).
_RECT_PRED_KEYS = ("childless", "manychildren", "earlymarriage", "celibacy", "fame",
                   "isolation", "art", "accidents", "emigration", "wealth", "parentloss", "illness")


def _rect_predispositions(m) -> dict:
    """Сила натальных сигнатур (0..1) — в основном по ДОМАМ, т.е. чувствительны ко времени."""
    def hnum(name):
        p = getattr(m, name, None)
        return C.HOUSE_NUM.get(p.house) if p is not None else None

    def conj(name, angle_pos, orb=6.0):
        p = getattr(m, name, None)
        return p is not None and _angular_diff(p.abs_pos, angle_pos) <= orb

    asc, mc = m.first_house.abs_pos, m.tenth_house.abs_pos
    dsc = (asc + 180) % 360
    cusp5 = m.fifth_house.sign
    ruler_name = I.chart_ruler(m.first_house.sign)
    ruler_house = hnum(ruler_name.lower()) if ruler_name else None
    s = {}

    v = (0.5 if hnum("saturn") == 5 else 0) + sum(0.25 for p in _RECT_MALEFIC if hnum(p) == 5)
    if cusp5 in _RECT_BARREN:
        v += 0.2
    s["childless"] = min(1.0, v)

    v = (0.5 if hnum("jupiter") == 5 else 0) + sum(0.2 for p in _RECT_BENEFIC if hnum(p) == 5)
    if cusp5 in _RECT_FERTILE:
        v += 0.3
    s["manychildren"] = min(1.0, v)

    v = sum(0.35 for p in _RECT_BENEFIC if hnum(p) == 7) + sum(0.3 for p in _RECT_BENEFIC if conj(p, dsc))
    if hnum("moon") == 7:
        v += 0.2
    s["earlymarriage"] = min(1.0, v)

    v = (0.5 if hnum("saturn") == 7 else 0) + (0.4 if conj("saturn", dsc) else 0)
    v += sum(0.2 for p in ("mars", "pluto") if hnum(p) == 7)
    s["celibacy"] = min(1.0, v)

    v = sum(0.35 for p in ("sun", "jupiter") if hnum(p) == 10) + sum(0.35 for p in ("sun", "jupiter") if conj(p, mc))
    v += sum(0.15 for p in ("sun", "moon") if hnum(p) in (1, 4, 7, 10))
    s["fame"] = min(1.0, v)

    v = sum(0.3 for p in (*_RECT_MALEFIC, "neptune") if hnum(p) == 12) + (0.3 if ruler_house == 12 else 0)
    s["isolation"] = min(1.0, v)

    v = sum(0.3 for p in ("venus", "neptune") if hnum(p) == 5) + sum(0.2 for p in ("venus", "neptune") if hnum(p) in (1, 10))
    s["art"] = min(1.0, v)

    v = (0.5 if (conj("mars", asc) or conj("mars", mc)) else 0) + (0.3 if hnum("mars") in (1, 8) else 0)
    if hnum("uranus") in (1, 8):
        v += 0.2
    s["accidents"] = min(1.0, v)

    v = sum(0.3 for p in ("jupiter", "uranus") if hnum(p) == 9) + (0.4 if ruler_house == 9 else 0)
    if hnum("sun") == 9 or hnum("moon") == 9:
        v += 0.2
    s["emigration"] = min(1.0, v)

    v = sum(0.3 for p in _RECT_BENEFIC if hnum(p) in (2, 8)) + (0.2 if hnum("jupiter") == 2 else 0)
    s["wealth"] = min(1.0, v)

    v = sum(0.3 for p in ("saturn", "pluto", "mars") if hnum(p) in (4, 10))
    if hnum("moon") == 4 and hnum("saturn") == 4:
        v += 0.2
    s["parentloss"] = min(1.0, v)

    v = sum(0.25 for p in ("saturn", "neptune", "mars") if hnum(p) in (6, 1)) + (0.3 if ruler_house in (6, 12) else 0)
    s["illness"] = min(1.0, v)

    return s


def _rect_score_candidate(minute, natal_params, ev_data, asc_sign_hits, asc_max_hits,
                          mid, sigma_dir, sigma_tr, lang, pred_ratings=None, parent_sun_signs=None):
    """Счёт одного кандидатного времени.

    Ключ к различимости: резкая (гауссова) близость + КОРРОБОРАЦИЯ. На каждое событие
    суммируем все тугие попадания (несколько техник, сходящихся на углу), а грубые —
    дают ~0. Верное время выделяется тем, что СРАЗУ МНОГО событий тонко «садятся» на углы.
    """
    cand = dict(natal_params)
    cand["hour"], cand["minute"] = minute // 60, minute % 60
    m = build_subject(**cand)
    asc, mc = m.first_house.abs_pos, m.tenth_house.abs_pos  # для транзитов и прогр. Луны
    natal_lons = {p: getattr(m, p).abs_pos for p in _RECT_DIR_PLANETS if getattr(m, p, None)}
    asc_code = m.first_house.sign

    # Все 12 куспидов с метками домов и весом: угловые (1/4/7/10) — полный, средние — пониженный.
    cusp_lons_all = [getattr(m, attr).abs_pos for attr in _RECT_HOUSE_ATTRS]
    cusp_all = [
        (lon, str(i + 1), 1.0 if (i + 1) in (1, 4, 7, 10) else _RECT_MEAN_CUSP_WEIGHT)
        for i, lon in enumerate(cusp_lons_all)
    ]

    _dir = "dir." if lang == "en" else "дир."
    _symdir = "sym.dir." if lang == "en" else "сим.дир."
    _naibod = "Naibod" if lang == "en" else "Найбод"
    _tr = "transit" if lang == "en" else "транзит"
    _pm = _editorial_text('astrology.118517d989cca4ec1b2233b172e82a7a70a7d6764eed9976f06f9f1f8201fad7') if lang == "en" else _editorial_text('astrology.5fc866fa6ab1f36ead7f732957c57ac9813c4ad330310c9b841f4e60c9770367')
    _angle = "angle" if lang == "en" else "угол"
    _planet = "planet" if lang == "en" else "планета"
    _house = "house" if lang == "en" else "дом"
    orb_dir, orb_tr = 3.2 * sigma_dir, 3.2 * sigma_tr

    event_total = 0.0
    ev_breakdown = []
    natal_lon_list = list(natal_lons.values())
    for ev in ev_data:
        sig = ev["sig_planets"]
        ev_sum = 0.0       # корроборация: сумма тугих попаданий
        best = None        # самый тугой контакт — для разбора

        def consider(contact, desc, sigma, planet=None, key_weight=1.0):
            nonlocal ev_sum, best
            if not contact:
                return
            boost = _RECT_SIG_BOOST if (planet and planet in sig) else 1.0
            cusp_w = contact.get("cusp_w", 1.0)  # <1 если цель — средний куспид
            sc = contact["w"] * _sharp(contact["orb"], sigma) * boost * key_weight * cusp_w
            ev_sum += sc
            if best is None or contact["orb"] < best["orb"]:
                best = {"orb": contact["orb"], "desc": desc, "sc": sc}

        # метка куспида по номеру дома: RU "1 дом" / EN "house 1"
        def cusp_label(num):
            return f'{num} {_house}' if lang != "en" else f'{_house} {num}'

        # Три ключа дирекций: (дуга, вес ключа, метка). Все направляются на куспиды и планеты.
        keys = (
            (ev["arc"], _RECT_KEY_WEIGHT_SOLARARC, _dir),
            (ev["sym_arc"], _RECT_KEY_WEIGHT_SYMBOLIC, _symdir),
            (ev["naibod_arc"], _RECT_KEY_WEIGHT_NAIBOD, _naibod),
        )
        for karc, kw, klabel in keys:
            # направленные планеты → все куспиды (угловые + средние, взвешенные)
            for p, lon in natal_lons.items():
                c = _rect_best_contact_labeled((lon + karc) % 360, cusp_all, orb_dir)
                if c:
                    consider(c, f"{klabel} {C.point_name(p.capitalize(), lang)} → {cusp_label(c['target'])}",
                             sigma_dir, p, kw)
            # направленные планеты → натальные планеты
            for p, lon in natal_lons.items():
                consider(_rect_best_contact((lon + karc) % 360, natal_lon_list, orb_dir),
                         f'{klabel} {C.point_name(p.capitalize(), lang)} → {_planet}', sigma_dir, p, kw)
            # направленные куспиды → натальные планеты (вес куспида свёрнут в key_weight)
            for lon, num, cw in cusp_all:
                consider(_rect_best_contact((lon + karc) % 360, natal_lon_list, orb_dir),
                         f'{klabel} {cusp_label(num)} → {_planet}', sigma_dir, None, kw * cw)

        # транзиты медленных планет → углы
        for p, lon in ev["transit_lons"].items():
            consider(_rect_best_contact(lon, [asc, mc], orb_tr),
                     f'{_tr} {C.point_name(p.capitalize(), lang)} → {_angle}', sigma_tr, p)
        # прогрессивная Луна → углы (быстрый триггер; долгота экстраполируется от середины)
        pm_lon = (ev["prog_moon_mid"] + ev["prog_moon_rate"] * (minute - mid)) % 360
        consider(_rect_best_contact(pm_lon, [asc, mc], orb_dir), f'{_pm} → {_angle}', sigma_dir, "moon")

        if best and best["sc"] > 0.02:
            event_total += ev_sum * ev["weight"]
            ev_breakdown.append({"label": ev["label"], "date": ev["date"],
                                 "factor": best["desc"], "orb": best["orb"]})
        else:
            ev_breakdown.append({"label": ev["label"], "date": ev["date"], "factor": "—", "orb": None})

    # Событийный вклад ограничен — он лишь УТОЧНЯЕТ, а окно задают детерминированные сигналы.
    event_total = min(event_total, _RECT_EVENT_CAP)
    total = event_total

    asc_hits = asc_sign_hits.get(asc_code, 0)
    asc_score = asc_hits * _RECT_ASC_Q_WEIGHT
    total += asc_score

    # Этап 3: предрасположенности натальной карты (сильно зависят от домов = от времени).
    strengths = _rect_predispositions(m) if pred_ratings else {}
    pred_score = _RECT_PRED_WEIGHT * sum(strengths.get(k, 0.0) * w for k, w in (pred_ratings or {}).items())
    # Тай-брейк: Асцендент часто совпадает со знаком Солнца родителя.
    parent_score = _RECT_PARENT_WEIGHT if (parent_sun_signs and asc_code in parent_sun_signs) else 0.0
    total += pred_score + parent_score

    return {
        "minute": minute,
        "time": f'{minute // 60:02d}:{minute % 60:02d}',
        "score": round(total, 3),
        "event_score": round(event_total, 3),
        "asc_score": round(asc_score, 2),
        "pred_score": round(pred_score, 2),
        "parent_score": round(parent_score, 2),
        "asc_hits": asc_hits,
        "asc_max_hits": asc_max_hits,
        "asc_sign": C.sign_name(asc_code, lang),
        "asc_deg": _deg_min(m.first_house.position)[0],
        "mc_sign": C.sign_name(m.tenth_house.sign, lang),
        "breakdown": ev_breakdown,
        "_sun": m.sun.sign, "_moon": m.moon.sign, "_asc": asc_code, "_pred": strengths,
    }


def rectification_report(
    natal_params: dict,
    events: list[dict],
    asc_traits: list[str] | None = None,
    predispositions: list[dict] | None = None,
    parent_sun_signs: list[str] | None = None,
    start_minute: int = 0,
    end_minute: int = 1439,
    step_minute: int = 15,
    center_minute: int | None = None,
    window_minutes: int | None = None,
) -> dict:
    """Ректификация: подбор времени рождения по событиям, анкете Асцендента,
    предрасположенностям натальной карты и правилу «Асц = знак Солнца родителя».
    """
    lang = _set_lang(natal_params.get("lang", "ru"))
    asc_traits = [t for t in (asc_traits or []) if t in _RECT_ASC_TRAITS]
    # Оценки предрасположенностей: {ключ: вес} (только осмысленные ответы).
    pred_ratings = {}
    for pr in (predispositions or []):
        k, r = pr.get("key"), pr.get("rating")
        if k in _RECT_PRED_KEYS and r in _RECT_RATING and r != "unknown":
            pred_ratings[k] = _RECT_RATING[r]
    parent_sun_signs = [s for s in (parent_sun_signs or []) if s in _SIGN_ORDER]
    if not events and not asc_traits and not pred_ratings:
        raise ValueError(
            _editorial_text('astrology.e0adde9188ae4769cdc91a8945e2084e0fab408c8b5f54cefa3ed7c263d561fc')
            if lang == "en" else
            _editorial_text('astrology.bb81bb05ee52df6394b08e6df4d3fe2fb27c48e8fc959d482c6abe05ff6aaa4d')
        )
    step_minute = max(1, int(step_minute))
    # Режим уточнения вокруг известного центра: окно ±window_minutes, шаг форсируется до 1 мин.
    if center_minute is not None and window_minutes and window_minutes > 0:
        start_minute = max(0, center_minute - window_minutes)
        end_minute = min(1439, center_minute + window_minutes)
        step_minute = 1
    if end_minute <= start_minute:
        raise ValueError(_editorial_text('astrology.aefe40512166c156b8e43a48f0077ddceb7804e419ea3c7ee3f2604c523b4b27'))
    n_candidates = (end_minute - start_minute) // step_minute + 1
    if n_candidates > 400:
        raise ValueError(_editorial_text('astrology.5c99ea535f1896501f28e2ddacc1004613001fcdc97e089f45890ff653afdca9'))

    birth_date = datetime(natal_params["year"], natal_params["month"], natal_params["day"])

    # Опорная карта в середине диапазона — для расчёта дуги и натальных долгот.
    mid = (start_minute + end_minute) // 2
    ref_params = dict(natal_params)
    ref_params["hour"], ref_params["minute"] = mid // 60, mid % 60
    ref_model = build_subject(**ref_params)
    tz = ref_model.tz_str
    lat, lng = natal_params["lat"], natal_params["lng"]

    # Предрасчёт по событиям: солнечная дуга и транзитные долготы.
    ev_data = []
    for ev in events:
        d = ev["date"]
        edate = datetime(d["year"], d["month"], d["day"])
        years = (edate - birth_date).total_seconds() / (TROPICAL_YEAR_DAYS * 86400)
        if years < 0:
            continue
        prog_dt = birth_date + timedelta(days=years, hours=mid / 60)
        prog_model = build_subject(
            name="p", year=prog_dt.year, month=prog_dt.month, day=prog_dt.day,
            hour=prog_dt.hour, minute=prog_dt.minute, lat=lat, lng=lng, tz_str=tz,
            city=natal_params.get("city", ""),
        )
        arc = (prog_model.sun.abs_pos - ref_model.sun.abs_pos) % 360
        # Скорость прогрессивной Луны (°/мин рождения) — для экстраполяции по кандидатам.
        prog_dt2 = birth_date + timedelta(days=years, hours=(mid + 30) / 60)
        prog_model2 = build_subject(
            name="p2", year=prog_dt2.year, month=prog_dt2.month, day=prog_dt2.day,
            hour=prog_dt2.hour, minute=prog_dt2.minute, lat=lat, lng=lng, tz_str=tz,
            city=natal_params.get("city", ""),
        )
        pm_mid = prog_model.moon.abs_pos
        pm_rate = (((prog_model2.moon.abs_pos - pm_mid + 180) % 360) - 180) / 30.0
        tr_model = build_subject(
            name="t", year=edate.year, month=edate.month, day=edate.day,
            hour=12, minute=0, lat=lat, lng=lng, tz_str=tz, city=natal_params.get("city", ""),
        )
        transit_lons = {p: getattr(tr_model, p).abs_pos for p in _RECT_TRANSIT if getattr(tr_model, p, None)}
        # Сигнификаторы: сначала по уточнённому типу (type_detail), иначе по базовому type.
        detail = str(ev.get("type_detail", "")).strip()
        sig = _RECT_EVENT_SIG_DETAIL.get(detail) or _RECT_EVENT_SIG.get(str(ev.get("type", "")), set())
        ev_data.append({
            "label": ev.get("label", ""),
            "date": edate.strftime("%Y-%m-%d"),
            "weight": float(ev.get("weight", 1.0)),
            "arc": arc,
            "sym_arc": years * SYMBOLIC_KEY_DEG_PER_YEAR,
            "naibod_arc": years * NAIBOD_KEY_DEG_PER_YEAR,
            "transit_lons": transit_lons,
            "prog_moon_mid": pm_mid,
            "prog_moon_rate": pm_rate,
            "sig_planets": sig,
        })

    if events and not ev_data:
        raise ValueError(
            _editorial_text('astrology.ae25fc572e24efe07bbe731b24a04fb9109f684a7fe2b52e8033f8dcf643d2db')
            if lang == "en" else
            _editorial_text('astrology.7286126e02a5202d2248d5692c52a0b176cad7c0cb0e836eb77ac07dc3da3346')
        )

    # Предрасчёт анкеты Асцендента: знак -> сколько выбранных признаков ему соответствуют.
    asc_sign_hits: dict[str, int] = {}
    for code in asc_traits:
        for s in _RECT_ASC_TRAITS[code]:
            asc_sign_hits[s] = asc_sign_hits.get(s, 0) + 1
    asc_max_hits = max(asc_sign_hits.values()) if asc_sign_hits else 0

    # ── Двухпроходный поиск ──
    # Детекция: умеренно резко (σ≈1.2°), мелкий шаг ≤6 мин — чтобы не «перешагнуть» пик.
    det_step = max(1, min(step_minute, 6))
    sig_det_dir, sig_det_tr = _RECT_SIGMA_COARSE_DIR, _RECT_SIGMA_COARSE_TR

    coarse = []
    minute = start_minute
    while minute <= end_minute:
        coarse.append(_rect_score_candidate(minute, natal_params, ev_data, asc_sign_hits,
                                            asc_max_hits, mid, sig_det_dir, sig_det_tr, lang,
                                            pred_ratings, parent_sun_signs))
        minute += det_step

    series = [{"minute": c["minute"], "sun": c["_sun"], "moon": c["_moon"], "asc": c["_asc"]} for c in coarse]

    # До 5 разнесённых «регионов»-кандидатов (минимум 20 мин между ними).
    region_sep = 20
    refine_r = det_step + 3  # окно уточнения покрывает разрыв между грубыми отсчётами
    coarse_sorted = sorted(coarse, key=lambda c: c["score"], reverse=True)
    centers = []
    for c in coarse_sorted:
        if c["score"] <= 0 and centers:
            break
        if all(abs(c["minute"] - cc) > region_sep for cc in centers):
            centers.append(c["minute"])
        if len(centers) >= 5:
            break
    if not centers:
        centers = [coarse_sorted[0]["minute"]]

    # Уточнение до минуты вокруг каждого региона — РЕЗКО (σ≈0.45°), чтобы различить пик.
    fine_by_min = {}
    checked = len(coarse)
    for center in centers:
        mm = max(start_minute, center - refine_r)
        hi = min(end_minute, center + refine_r)
        while mm <= hi:
            if mm not in fine_by_min:
                fine_by_min[mm] = _rect_score_candidate(mm, natal_params, ev_data, asc_sign_hits,
                                                         asc_max_hits, mid, _RECT_SIGMA_FINE_DIR,
                                                         _RECT_SIGMA_FINE_TR, lang,
                                                         pred_ratings, parent_sun_signs)
                checked += 1
            mm += 1
    fine = sorted(fine_by_min.values(), key=lambda c: c["score"], reverse=True)
    best = fine[0]
    base = max(best["score"], 1e-6)
    for c in fine:
        c["confidence"] = max(0, min(100, round(100 * c["score"] / base))) if best["score"] > 0 else 0

    # ── Главное окно — по ЗНАКУ Асцендента (детерминированно, надёжно) ──
    # Восходящий знак однозначно определяется временем; в нём событиями уточняем минуту.
    best_asc_code = best["_asc"]
    sign_mins = [c["minute"] for c in coarse if c["_asc"] == best_asc_code]
    if sign_mins:
        lo_m, hi_m = min(sign_mins), max(sign_mins)
        window = {"from": f'{lo_m // 60:02d}:{lo_m % 60:02d}', "to": f'{hi_m // 60:02d}:{hi_m % 60:02d}',
                  "width": hi_m - lo_m, "sign": best["asc_sign"]}
    else:
        window = {"from": best["time"], "to": best["time"], "width": 0, "sign": best["asc_sign"]}

    # Альтернативные времена — лучшие из других регионов (по событиям).
    alternatives = []
    for center in centers:
        if abs(center - best["minute"]) <= refine_r:
            continue
        reg = [c for c in fine if abs(c["minute"] - center) <= refine_r]
        if reg:
            a = max(reg, key=lambda c: c["score"])
            if a["score"] > 0:
                alternatives.append({"time": a["time"], "confidence": a["confidence"]})
    second_conf = max((a["confidence"] for a in alternatives), default=0)

    # Надёжность = насколько уверенно определён ЗНАК Асцендента (это надёжная часть).
    has_det = bool(asc_sign_hits) or bool(pred_ratings)
    decisive_sign = asc_max_hits >= 2 and best["asc_hits"] == asc_max_hits
    pred_supports = bool(pred_ratings) and best.get("pred_score", 0) > 0
    if best["score"] <= 0:
        reliability = "low"
    elif decisive_sign and (asc_max_hits >= 3 or pred_supports):
        reliability = "high"
    elif decisive_sign or pred_supports:
        reliability = "medium"
    else:
        reliability = "low"

    # Честно: без детерминированных данных (анкета/предрасп.) событий НЕ хватает на минуту.
    auto_failed = best["score"] <= 0 or not has_det

    # Сводка по предрасположенностям при лучшем времени (что карта подтверждает).
    pred_summary = []
    if pred_ratings:
        bp = best.get("_pred", {})
        for k, w in pred_ratings.items():
            strength = round(bp.get(k, 0.0), 2)
            pred_summary.append({"key": k, "wanted": w > 0, "strength": strength,
                                 "supports": (w > 0 and strength >= 0.3) or (w < 0 and strength < 0.2)})

    # Детекция смены знака Солнца/Луны/Асцендента внутри интервала (метод astro-app, шаг 1).
    luminary_changes = []
    for key, pt_name in (("sun", "Sun"), ("moon", "Moon"), ("asc", "ASC")):
        first_sign = series[0][key]
        last_sign = series[-1][key]
        if first_sign != last_sign:
            # минута, на которой произошла последняя смена
            change_min = None
            for i in range(1, len(series)):
                if series[i][key] != series[i - 1][key]:
                    change_min = series[i]["minute"]
            label = pt_name if pt_name == "ASC" else C.point_name(pt_name, lang)
            luminary_changes.append({
                "point": label,
                "from_sign": C.sign_name(first_sign, lang),
                "to_sign": C.sign_name(last_sign, lang),
                "time": f'{change_min // 60:02d}:{change_min % 60:02d}' if change_min is not None else None,
            })

    return {
        "meta": {"date": birth_date.strftime("%Y-%m-%d"), "city": natal_params.get("city", ""),
                 "lat": lat, "lng": lng, "events_used": len(ev_data),
                 "traits_used": len(asc_traits),
                 "candidates_checked": checked,
                 "window": window, "reliability": reliability,
                 "alternatives": alternatives,
                 "auto_failed": auto_failed,
                 "preds_used": len(pred_ratings),
                 "pred_summary": pred_summary,
                 "luminary_changes": luminary_changes},
        "best": best,
        "top": fine[:12],
    }


def _synastry_couple(aspects: list[dict], score_data: dict, lang: str) -> dict:
    """Понятный для пары итог: сильные стороны связи и зоны роста простым языком."""
    strengths, challenges = [], []
    seen = set()
    # Сначала самые точные аспекты — у них приоритет при дедупликации пары планет.
    for a in sorted(aspects, key=lambda x: x["orbit"]):
        txt = I.synastry_pair_text(a["p1"], a["p2"], a["nature"], lang)
        if not txt:
            continue
        key = frozenset((a["p1"], a["p2"]))
        if key in seen:
            continue
        seen.add(key)
        item = {
            "p1_ru": a["p1_ru"], "p1_symbol": a["p1_symbol"],
            "p2_ru": a["p2_ru"], "p2_symbol": a["p2_symbol"],
            "aspect_ru": a["aspect_ru"], "nature": a["nature"],
            "text": txt,
            "weight": I.synastry_weight(a["p1"], a["p2"], a["orbit"]),
        }
        (challenges if a["nature"] == "tense" else strengths).append(item)

    strengths.sort(key=lambda x: x["weight"], reverse=True)
    challenges.sort(key=lambda x: x["weight"], reverse=True)
    strengths = strengths[:6]
    challenges = challenges[:5]
    verdict = I.synastry_verdict(len(strengths), len(challenges),
                                 C.score_desc(score_data.get("score_description", ""), lang), lang)

    # Разбор по сферам отношений: страсть / эмоции / общение / долгосрочность.
    acc = {k: {"harm": 0.0, "tense": 0.0} for k in ("passion", "emotional", "communication", "stability")}
    for a in aspects:
        w = I.synastry_weight(a["p1"], a["p2"], a["orbit"])
        bucket = "tense" if a["nature"] == "tense" else "harm"
        for sph in I.synastry_sphere_of(a["p1"], a["p2"]):
            acc[sph][bucket] += w
    spheres = []
    for key in ("passion", "emotional", "communication", "stability"):
        harm, tense = acc[key]["harm"], acc[key]["tense"]
        if harm + tense == 0:
            tone = "quiet"
        elif tense == 0:
            tone = "good"
        elif harm == 0:
            tone = "challenging"
        elif harm >= 1.6 * tense:
            tone = "good"
        elif tense >= 1.6 * harm:
            tone = "challenging"
        else:
            tone = "mixed"
        sphere = {
            "key": key,
            "label": I.synastry_sphere_label(key, lang),
            "tone": tone,
            "text": I.synastry_sphere_text(key, tone, lang),
        }
        if tone in ("mixed", "challenging"):
            sphere["advice"] = I.synastry_sphere_advice(key, lang)
        spheres.append(sphere)

    return {"verdict": verdict, "strengths": strengths, "challenges": challenges, "spheres": spheres}


def _midpoint(a: float, b: float) -> float:
    """Средняя точка двух долгот по короткой дуге (для композита)."""
    diff = ((b - a + 180) % 360) - 180
    return (a + diff / 2) % 360


def _house_of(lon: float, cusps: list[float]) -> int:
    """Номер дома (1..12), в который попадает долгота lon, по списку 12 куспидов."""
    for i in range(12):
        start = cusps[i]
        span = (cusps[(i + 1) % 12] - start) % 360
        if (lon - start) % 360 < (span or 360):
            return i + 1
    return 12


_SYN_OVERLAY_PLANETS = ["sun", "moon", "mercury", "venus", "mars", "jupiter", "saturn"]
_SYN_COMPOSITE_POINTS = ["sun", "moon", "mercury", "venus", "mars", "jupiter", "saturn"]


def _overlays_one_way(src_model, dst_cusps, who: str, partner: str, lang: str) -> list[dict]:
    out = []
    for attr in _SYN_OVERLAY_PLANETS:
        p = getattr(src_model, attr, None)
        if p is None:
            continue
        hn = _house_of(p.abs_pos, dst_cusps)
        txt = I.synastry_overlay_text(p.name, hn, who, partner, lang)
        if not txt:
            continue
        out.append({
            "planet_ru": C.point_name(p.name, lang),
            "symbol": C.point_symbol(p.name),
            "house": hn,
            "who": who,
            "text": txt,
        })
    return out


def _synastry_extras(model_a, model_b, a_name: str, b_name: str, lang: str) -> dict:
    """Накладки домов (чья планета в каком доме партнёра) + композитная карта."""
    cusps_a = [getattr(model_a, h).abs_pos for h in C.HOUSE_ORDER]
    cusps_b = [getattr(model_b, h).abs_pos for h in C.HOUSE_ORDER]
    overlays = (_overlays_one_way(model_a, cusps_b, a_name, b_name, lang) +
                _overlays_one_way(model_b, cusps_a, b_name, a_name, lang))

    # Композит — средние точки планет и углов.
    sign_codes = list(C.SIGNS.keys())
    composite = []
    pts = list(_SYN_COMPOSITE_POINTS) + ["ascendant", "medium_coeli"]
    attr_to_name = {
        "ascendant": "Ascendant", "medium_coeli": "Medium_Coeli",
    }
    for attr in pts:
        if attr in ("ascendant", "medium_coeli"):
            pa = getattr(model_a, "first_house" if attr == "ascendant" else "tenth_house")
            pb = getattr(model_b, "first_house" if attr == "ascendant" else "tenth_house")
            name = attr_to_name[attr]
        else:
            pa = getattr(model_a, attr, None)
            pb = getattr(model_b, attr, None)
            name = pa.name if pa else None
        if pa is None or pb is None:
            continue
        mid = _midpoint(pa.abs_pos, pb.abs_pos)
        sign = sign_codes[int(mid // 30) % 12]
        deg = mid % 30
        composite.append({
            "name_ru": C.point_name(name, lang),
            "symbol": C.point_symbol(name),
            "sign_ru": C.sign_name(sign, lang),
            "deg": int(deg),
            "text": I.synastry_composite_text(name, sign, lang),
        })

    return {"overlays": overlays, "composite": composite}


def synastry_report(
    person_a: dict,
    person_b: dict,
    with_svg: bool = True,
) -> dict:
    """Синастрия (совместимость двух карт): межкартные аспекты + индекс."""
    lang = _set_lang(person_a.get("lang", "ru"))
    model_a = build_subject(**person_a)
    model_b = build_subject(**person_b)

    # Межкартные аспекты (планеты A к планетам B).
    dual = AspectsFactory.dual_chart_aspects(
        model_a,
        model_b,
        first_subject_is_fixed=True,
        second_subject_is_fixed=True,
    )

    # Индекс совместимости (метод Ч. Дисчеполо).
    rsf = RelationshipScoreFactory(model_a, model_b)
    score = rsf.get_relationship_score()
    score_data = score.model_dump() if hasattr(score, "model_dump") else dict(score)

    breakdown = [
        {
            "rule_ru": C.synastry_rule(item.get("rule", ""), lang),
            "points": item.get("points"),
        }
        for item in score_data.get("score_breakdown", [])
    ]

    aspects = [serialize_aspect(a) for a in dual.aspects]
    couple = _synastry_couple(aspects, score_data, lang)
    couple.update(_synastry_extras(model_a, model_b,
                                   _meta(model_a)["name"], _meta(model_b)["name"], lang))

    result = {
        "a_meta": _meta(model_a),
        "b_meta": _meta(model_b),
        "a_planets": _collect_points(model_a),
        "b_planets": _collect_points(model_b),
        "aspects": aspects,
        "couple": couple,
        "score": {
            "value": score_data.get("score_value"),
            "description_ru": C.score_desc(score_data.get("score_description", ""), lang),
            "is_destiny_sign": score_data.get("is_destiny_sign", False),
            "breakdown": breakdown,
        },
    }

    if with_svg:
        chart_data = ChartDataFactory.create_synastry_chart_data(model_a, model_b)
        result.update(_render_chart_svgs(chart_data, person_a.get("lang", "ru")))

    return result


# --------------------------------------------------------------------------- #
#  Общие транзиты: где сейчас планеты и на какой период (сайт + приложение)
# --------------------------------------------------------------------------- #
# Горизонт поиска границы знака (дней) — с запасом больше макс. времени в знаке.
_SIGN_HORIZON = {
    "sun": 60, "moon": 5, "mercury": 90, "venus": 100, "mars": 120,
    "jupiter": 500, "saturn": 1200, "uranus": 3200, "neptune": 6200, "pluto": 9500,
}


def _planet_abs(attr: str, dt: datetime) -> float:
    """Эклиптическая долгота планеты в указанный момент UTC."""
    m = build_subject(
        name="t", year=dt.year, month=dt.month, day=dt.day,
        hour=dt.hour, minute=dt.minute, lat=0.0, lng=0.0, tz_str="UTC",
    )
    return float(getattr(m, attr).abs_pos) % 360.0


def _sign_change(attr: str, when: datetime, cur_idx: int, direction: int, horizon: int):
    """Ближайший день (direction=±1), где планета уже в ДРУГОМ знаке.
    Экспоненциальный поиск + бисекция — O(log горизонта).
    ponytail: у ретро-планет у самой границы возможна погрешность ±1–2 дня — для обзора ок."""
    lo, hi = 0, 1
    while True:
        probe = min(hi, horizon)  # последняя проба — ровно горизонт, чтобы не перепрыгнуть границу
        d = when + timedelta(days=direction * probe)
        if int(_planet_abs(attr, d) // 30.0) != cur_idx:
            hi = probe
            break
        if probe >= horizon:
            return None  # в пределах горизонта знак не меняется
        lo, hi = probe, hi * 2
    while hi - lo > 1:
        mid = (lo + hi) // 2
        d = when + timedelta(days=direction * mid)
        if int(_planet_abs(attr, d) // 30.0) != cur_idx:
            hi = mid
        else:
            lo = mid
    same_side = when + timedelta(days=direction * lo)
    other_side = when + timedelta(days=direction * hi)
    # Уточняем границу до минуты независимо от направления поиска.
    for _ in range(18):
        if abs((other_side - same_side).total_seconds()) <= 60:
            break
        mid = same_side + (other_side - same_side) / 2
        if int(_planet_abs(attr, mid) // 30.0) == cur_idx:
            same_side = mid
        else:
            other_side = mid
    return other_side


def current_transits(lang: str = "ru", when: Optional[datetime] = None) -> list[dict]:
    """Все 10 планет: в каком знаке, ретро ли, с какого по какое число и что это значит.
    Не зависит от натальной карты — общий небесный фон. Значения — из корпуса interpret_sign
    (редактируется в админке). Считается на начало текущего часа UTC; кэшируется вызывающей стороной на час."""
    lang = _set_lang(lang)
    when = when or datetime.utcnow()
    base = build_subject(
        name="t", year=when.year, month=when.month, day=when.day,
        hour=when.hour, minute=when.minute, lat=0.0, lng=0.0, tz_str="UTC",
    )
    out: list[dict] = []
    for attr in C.PLANET_ORDER[:10]:
        p = getattr(base, attr)
        d = p.model_dump() if hasattr(p, "model_dump") else dict(p)
        name = d["name"]
        sign = d["sign"]
        cur_idx = int(float(d["abs_pos"]) % 360.0 // 30.0)
        horizon = _SIGN_HORIZON.get(attr, 400)
        back = _sign_change(attr, when, cur_idx, -1, horizon)
        fwd = _sign_change(attr, when, cur_idx, +1, horizon)
        out.append({
            "planet": name,
            "planet_ru": C.point_name(name, lang),
            "sign": sign,
            "sign_ru": C.sign_name(sign, lang),
            "sign_symbol": C.sign_symbol(sign),
            "retrograde": bool(d.get("retrograde")),
            "since": back.date().isoformat() if back else None,
            "until": fwd.date().isoformat() if fwd else None,
            "since_datetime": back.isoformat(timespec="minutes") + "Z" if back else None,
            "until_datetime": fwd.isoformat(timespec="minutes") + "Z" if fwd else None,
            "meaning": I.interpret_sign(name, sign, lang),
        })
    return out
