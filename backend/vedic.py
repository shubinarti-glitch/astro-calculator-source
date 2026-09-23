# -*- coding: utf-8 -*-
"""Ведический календарь (Панчанг): титхи, накшатра, вара и оценка благоприятности дней.

Считается в сидерическом зодиаке (аянамса Лахири) на Swiss Ephemeris. Для конкретного
человека добавляется Тарабала — качество дня относительно его джанма-накшатры.
"""
from __future__ import annotations
from .editorial_data import text as _editorial_text

import calendar as _cal
from datetime import datetime
from typing import Optional
import pytz

from .ephe import ensure_ephemeris
from .editorial_data import table as _editorial_table

ensure_ephemeris()

import swisseph as swe  # noqa: E402

swe.set_sid_mode(swe.SIDM_LAHIRI)
_SFLAG = swe.FLG_SWIEPH | swe.FLG_SIDEREAL
_SEG = 360.0 / 27.0

# 27 накшатр: (RU, EN, качество)
NAKSHATRAS = [
    ("Ашвини", "Ashwini", "good"), ("Бхарани", "Bharani", "bad"),
    ("Криттика", "Krittika", "bad"), ("Рохини", "Rohini", "good"),
    ("Мригашира", "Mrigashira", "good"), ("Ардра", "Ardra", "bad"),
    ("Пунарвасу", "Punarvasu", "good"), ("Пушья", "Pushya", "good"),
    ("Ашлеша", "Ashlesha", "bad"), ("Магха", "Magha", "neutral"),
    (_editorial_text('vedic.adce709104eef61a25183dac3c632a2e26170b1384d011210a93cc602c387c34'), _editorial_text('vedic.dd7ff7681c7d193439a630255a20e288299c361bf9f72a4f660896db76fa2b51'), "neutral"), (_editorial_text('vedic.37fde0ccf999dafbd2112f8b2e84a3c580486ff5f9ab44901add0ef6102c2c11'), _editorial_text('vedic.156771e3e2761911bb55a88e5aae69afc5993880176002a732542f1875a4084b'), "good"),
    ("Хаста", "Hasta", "good"), ("Читра", "Chitra", "good"),
    ("Свати", "Swati", "good"), ("Вишакха", "Vishakha", "neutral"),
    ("Анурадха", "Anuradha", "good"), ("Джйештха", "Jyeshtha", "bad"),
    ("Мула", "Mula", "bad"), (_editorial_text('vedic.03ce7bf7ffd2096bbb781f0127301903de6699adcad64ef946784bea977fef8b'), _editorial_text('vedic.00b6f83b795099b652c438976ef23b080d7225d6c5ad4dfefe6ea7592402200f'), "neutral"),
    (_editorial_text('vedic.26afb99ba78a92cd32370ab7efd44de2e25965cffd99c5f6dd2ccdcba3c0fe75'), _editorial_text('vedic.aa5c8ea1629cd532ed87d27084afa9a196e39f5abb7d3f7fc629342f04451242'), "good"), ("Шравана", "Shravana", "good"),
    ("Дхаништха", "Dhanishta", "good"), ("Шатабхиша", "Shatabhisha", "neutral"),
    (_editorial_text('vedic.7de759afabb93b84a3dbaa5dbb704460d5860241787e0aca389aa5b9c77e57ff'), _editorial_text('vedic.373266900ec1c26785985a9c24761d75ff50881829efa284d8fc64f9d7c598ef'), "bad"), (_editorial_text('vedic.31e0e5674320b693ad8625da55aac065ad6b7402ef80aff326c9f93566dd5629'), _editorial_text('vedic.689c60cddd2880d9a23f85f5c74bab12a3ec426576876e7c904c71cf12aca3e6'), "good"),
    ("Ревати", "Revati", "good"),
]

# Названия титхи (1..15), используются в обоих пакшах
_TITHI_NAMES = [
    ("Пратипада", "Pratipada"), ("Двитийя", "Dwitiya"), ("Тритийя", "Tritiya"),
    ("Чатуртхи", "Chaturthi"), ("Панчами", "Panchami"), ("Шаштхи", "Shashthi"),
    ("Саптами", "Saptami"), ("Аштами", "Ashtami"), ("Навами", "Navami"),
    ("Дашами", "Dashami"), ("Экадаши", "Ekadashi"), ("Двадаши", "Dwadashi"),
    ("Трайодаши", "Trayodashi"), ("Чатурдаши", "Chaturdashi"),
]
_FULL = ("Пурнима", "Purnima")
_NEW = ("Амавасья", "Amavasya")

# Рикта-титхи (4, 9, 14) — неблагоприятны для начинаний
_RIKTA = {4, 9, 14}

# Тарабала: 9 тар (RU, EN, качество)
_TARAS = [
    ("Джанма", "Janma", "neutral"), ("Сампат", "Sampat", "good"),
    ("Випат", "Vipat", "bad"), ("Кшема", "Kshema", "good"),
    ("Пратьяк", "Pratyak", "bad"), ("Садхана", "Sadhana", "good"),
    ("Вадха", "Vadha", "bad"), ("Митра", "Mitra", "good"),
    ("Ати-Митра", "Ati-Mitra", "good"),
]

_WEEKDAYS = [
    ("Понедельник", "Monday"), ("Вторник", "Tuesday"), ("Среда", "Wednesday"),
    ("Четверг", "Thursday"), ("Пятница", "Friday"), ("Суббота", "Saturday"),
    ("Воскресенье", "Sunday"),
]

_QUALITY_RU = {"good": "благоприятный", "neutral": "нейтральный", "bad": "неблагоприятный"}
_QUALITY_EN = {"good": "favorable", "neutral": "neutral", "bad": "unfavorable"}

# Человеческое описание дня по накшатре (что за энергия, для чего хорош) — простым языком.
NAKSHATRA_GUIDE = _editorial_table("vedic.NAKSHATRA_GUIDE")

# Совет по фазе Луны (пакше) — интуитивно понятно обычному пользователю.
_PAKSHA_ADVICE = _editorial_table("vedic._PAKSHA_ADVICE")
# Итоговый совет по качеству дня.
_DAY_ADVICE = _editorial_table("vedic._DAY_ADVICE")


def _li(pair, lang):
    return pair[1] if lang == "en" else pair[0]


def _jd_for_local_noon(year: int, month: int, day: int, tz_str: str) -> float:
    """Julian Day (UT) для местного полудня указанной даты."""
    try:
        zone = pytz.timezone(tz_str)
    except pytz.UnknownTimeZoneError as exc:
        raise ValueError(f"{_editorial_text('vedic.bfdb747e3bf66883553030f6170eb29a9fb1295bf8bb99b178704bfbffdeaf33')}{tz_str}") from exc
    local = zone.localize(datetime(year, month, day, 12, 0), is_dst=None)
    ut = local.astimezone(pytz.UTC)
    return swe.julday(ut.year, ut.month, ut.day, ut.hour + ut.minute / 60 + ut.second / 3600)


def _sidereal(jd: float, body: int) -> float:
    return swe.calc_ut(jd, body, _SFLAG)[0][0]


def nakshatra_index(jd: float) -> int:
    return int(_sidereal(jd, swe.MOON) // _SEG) % 27


def birth_nakshatra_from_jd(jd: float) -> int:
    return nakshatra_index(jd)


def vedic_calendar(
    year: int,
    month: int,
    lat: float,
    lng: float,
    tz_str: str = "UTC",
    birth_nak: Optional[int] = None,
    lang: str = "ru",
) -> dict:
    """Панчанг на месяц: для каждого дня — титхи, накшатра, вара и оценка благоприятности."""
    days_in_month = _cal.monthrange(year, month)[1]
    days = []
    counts = {"good": 0, "neutral": 0, "bad": 0}

    for day in range(1, days_in_month + 1):
        jd = _jd_for_local_noon(year, month, day, tz_str)
        moon = _sidereal(jd, swe.MOON)
        sun = _sidereal(jd, swe.SUN)

        nak = int(moon // _SEG) % 27
        nak_ru, nak_en, nak_quality = NAKSHATRAS[nak]

        diff = (moon - sun) % 360
        tithi = int(diff // 12) + 1  # 1..30
        paksha_ru, paksha_en = (_editorial_text('vedic.acac004ed579ce344fa722145369b64dbab6ec1c5396b12ccfb73d1087a4c84c'), _editorial_text('vedic.f72ca26be9b3ccf300dccc91ab28497266400f077f8a8ca64220d57923150d86')) if tithi <= 15 else (_editorial_text('vedic.4c341988986b1b808011f04978bdc26bf40e83a5fc277e92961e9656f3d5f8b7'), _editorial_text('vedic.08b2fde8c3b33f44cb943afc11f782776b91bc637c27f3a54b305f4529e3409f'))
        if tithi == 15:
            tithi_name = _FULL
        elif tithi == 30:
            tithi_name = _NEW
        else:
            tithi_name = _TITHI_NAMES[(tithi - 1) % 15]
        is_rikta = (tithi % 15) in _RIKTA

        weekday = datetime(year, month, day).weekday()

        # Оценка
        score = 0
        notes = []
        if nak_quality == "good":
            score += 2
        elif nak_quality == "bad":
            score -= 2
        if is_rikta:
            score -= 2
            notes.append(_editorial_text('vedic.e320d17ed1d4167d77f4ceb6643f877c4675e8904ba3e3b402a1a1e2569a6d5b') if lang != "en" else _editorial_text('vedic.dd3a114379f26e58276b5800f1e1d41f1ad54710e128d799aaa21e812eb7e835'))
        if tithi == 30:
            score -= 1
            notes.append(_editorial_text('vedic.aff35c1c43b9a92e7d8492436a2f0b507143dd068cef8ca06a9e77d80f85bd8c') if lang != "en" else _editorial_text('vedic.c6f05cd18f4f74072ad73f6e35d9a30ccd6e5381536008bb3702df22c4e03a88'))
        if tithi == 15:
            score += 1

        tara = None
        if birth_nak is not None:
            count = (nak - birth_nak) % 27
            tara_idx = count % 9
            t_ru, t_en, t_quality = _TARAS[tara_idx]
            tara = {"name": _li((t_ru, t_en), lang), "quality": t_quality}
            if t_quality == "good":
                score += 2
            elif t_quality == "bad":
                score -= 2
                notes.append((f"{_editorial_text('vedic.ec2dd29acf5fc50d02d14c77b4c0bdd5c25898af5f65240f694297829194265e')}{t_ru}{_editorial_text('vedic.47922c8d070da6cf8019cacd29ad962db3df02dc73be85d0a9a01ea2e34bf851')}") if lang != "en" else f"{_editorial_text('vedic.f6de74be8390d3fa243acddd2384be3236543b395b53a898898d4118394bb647')}{t_en}{_editorial_text('vedic.0e4c72d3dcbb79e4170661a51f004a1e166e2f10371cafa1ddaf55a986fd0849')}")

        quality = "good" if score >= 2 else ("bad" if score <= -2 else "neutral")
        counts[quality] += 1

        # Человеческое пояснение дня
        nak_meaning = _li(NAKSHATRA_GUIDE[nak], lang)
        paksha_key = "waxing" if tithi <= 15 else "waning"
        paksha_advice = _li(_PAKSHA_ADVICE[paksha_key], lang)
        day_advice = _li(_DAY_ADVICE[quality], lang)
        q_word = (_QUALITY_EN if lang == "en" else _QUALITY_RU)[quality]
        nak_cap = nak_meaning[:1].upper() + nak_meaning[1:]
        if lang == "en":
            summary = f"{_editorial_text('vedic.7a7202fb1b53105f00a4308223160a22fd287678fcc8cf3681a36a57ee85e75a')}{q_word}{_editorial_text('vedic.17631436ef7705f392da6c4e3f850d550da91d7cc1137021e7069a71ad90fe22')}{nak_cap}. {paksha_advice}"
        else:
            summary = f"{_editorial_text('vedic.4c6367be727fee6613a6205cd0364f58004b83b193280c2063f7fe17c1119e86')}{q_word}. {nak_cap}. {paksha_advice}"

        days.append({
            "day": day,
            "date": f'{year:04d}-{month:02d}-{day:02d}',
            "weekday": _li(_WEEKDAYS[weekday], lang),
            "weekday_idx": weekday,
            "tithi": tithi if tithi <= 15 else tithi - 15,
            "tithi_name": _li(tithi_name, lang),
            "paksha": _li((paksha_ru, paksha_en), lang),
            "nakshatra": nak + 1,
            "nakshatra_name": _li((nak_ru, nak_en), lang),
            "tara": tara,
            "quality": quality,
            "quality_ru": (_QUALITY_EN if lang == "en" else _QUALITY_RU)[quality],
            "note": "; ".join(notes),
            "nak_meaning": nak_meaning,
            "paksha_advice": paksha_advice,
            "day_advice": day_advice,
            "summary": summary,
        })

    return {
        "year": year,
        "month": month,
        "personalized": birth_nak is not None,
        "counts": counts,
        "days": days,
    }
