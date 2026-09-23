# -*- coding: utf-8 -*-
"""Регрессии качества шаблонных трактовок RU/EN."""

from collections import Counter
from pathlib import Path
import re
import xml.etree.ElementTree as ET

from backend import interpretations as I
from backend import astrology
from backend.constants import SIGNS


ROOT = Path(__file__).parents[1]


def _js_object_body(source: str, language: str) -> str:
    """Return a top-level i18n language object without relying on line numbers."""
    match = re.search(rf"(?m)^\s*{re.escape(language)}\s*:\s*\{{", source)
    assert match, f"i18n.js has no {language!r} dictionary"

    start = source.find("{", match.start())
    depth = 0
    quote = None
    escaped = False
    line_comment = False
    block_comment = False
    i = start
    while i < len(source):
        char = source[i]
        following = source[i + 1] if i + 1 < len(source) else ""
        if line_comment:
            if char == "\n":
                line_comment = False
        elif block_comment:
            if char == "*" and following == "/":
                block_comment = False
                i += 1
        elif quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
        elif char == "/" and following == "/":
            line_comment = True
            i += 1
        elif char == "/" and following == "*":
            block_comment = True
            i += 1
        elif char in ('"', "'", "`"):
            quote = char
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[start + 1:i]
        i += 1
    raise AssertionError(f"Unclosed {language!r} dictionary in i18n.js")


def _js_dictionary_keys(source: str, language: str) -> list[str]:
    body = _js_object_body(source, language)
    # The dictionaries are intentionally flat. Anchoring at line start avoids
    # treating object-like text inside translation values as keys.
    return re.findall(r'(?m)^\s*(?:["\']([^"\']+)["\']|([A-Za-z_$][\w$]*))\s*:', body)


def _flatten_key_matches(matches: list[tuple[str, str]]) -> list[str]:
    return [quoted or bare for quoted, bare in matches]


def _android_strings(path: Path) -> tuple[dict[str, str], set[str]]:
    root = ET.parse(path).getroot()
    values: dict[str, str] = {}
    non_translatable: set[str] = set()
    for element in root.findall("string"):
        name = element.attrib["name"]
        assert name not in values, f"Duplicate Android string {name!r} in {path}"
        values[name] = "".join(element.itertext())
        if element.attrib.get("translatable", "true").lower() == "false":
            non_translatable.add(name)
    return values, non_translatable


FORMAT_ARGUMENT = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z%]")


def _format_arguments(value: str) -> Counter[str]:
    return Counter(match.group(0) for match in FORMAT_ARGUMENT.finditer(value) if match.group(0) != "%%")


def _signs(sign: str) -> dict:
    return {
        key: sign
        for key in ("sun", "moon", "asc", "mercury", "venus", "mars", "saturn", "mc", "h2", "h7")
    }


def test_plain_story_templates_are_grammatically_safe_for_all_signs():
    for lang in ("ru", "en"):
        for sign in SIGNS:
            texts = " ".join(
                section["text"] for section in I.plain_story(_signs(sign), [], lang)["sections"]
            ).lower()
            assert "под давлением можете" not in texts
            assert "under pressure you may" not in texts


def test_energy_and_money_sections_keep_distinct_contexts():
    for lang in ("ru", "en"):
        for sign in SIGNS:
            sections = I.plain_story(_signs(sign), [], lang)["sections"]
            by_id = {section.get("id"): section for section in sections if section.get("id")}
            if {"energy", "money"} <= by_id.keys():
                energy = by_id["energy"]["text"]
                money = by_id["money"]["text"]
            else:
                # Backward-compatible stable lookup for the current payload,
                # independent of section insertion/reordering.
                energy_title = "Энергия и действие" if lang == "ru" else "Energy and drive"
                money_title = "Деньги и ценности" if lang == "ru" else "Money and values"
                by_title = {section["title"]: section["text"] for section in sections}
                assert energy_title in by_title and money_title in by_title
                energy, money = by_title[energy_title], by_title[money_title]
            assert energy != money
            if lang == "ru":
                assert "способ действовать" in energy.lower()
                assert "материальной сфере" in money.lower()
            else:
                assert "way of taking action" in energy.lower()
                assert "material matters" in money.lower()


def test_daily_balance_sphere_has_no_medical_claims_or_broken_templates():
    forbidden = (
        "здоров", "психосомат", "диагноз", "лечен", "вам подходит подход",
        "health", "psychosomat", "diagnos", "treatment", "an approach through",
    )
    for lang in ("ru", "en"):
        for asc in SIGNS:
            for moon in SIGNS:
                text = I.sphere_health(asc, moon, "Vir", lang).lower()
                assert all(word not in text for word in forbidden)


def test_rectification_medical_event_type_is_preserved():
    from backend.main import RectificationRequest

    field = RectificationRequest.model_fields["events"]
    assert field is not None

    i18n = (Path(__file__).parents[1] / "frontend" / "js" / "i18n.js").read_text(encoding="utf-8")
    assert 'rect_type_health: "Здоровье / травма"' in i18n
    assert 'rect_type_health: "Health / injury"' in i18n
    assert 'rp_illness: "Хроническая болезнь, слабое здоровье"' in i18n
    assert "Медицинские события используются только как временные ориентиры" in i18n


def test_web_i18n_key_parity_and_no_duplicates():
    source = (ROOT / "frontend" / "js" / "i18n.js").read_text(encoding="utf-8")
    language_keys = {
        language: _flatten_key_matches(_js_dictionary_keys(source, language))
        for language in ("ru", "en")
    }

    for language, keys in language_keys.items():
        duplicates = sorted(key for key, count in Counter(keys).items() if count > 1)
        assert not duplicates, f"Duplicate {language.upper()} i18n keys: {duplicates}"

    ru_keys, en_keys = map(set, (language_keys["ru"], language_keys["en"]))
    assert ru_keys == en_keys, (
        f"Web i18n mismatch; RU-only: {sorted(ru_keys - en_keys)}, "
        f"EN-only: {sorted(en_keys - ru_keys)}"
    )


def test_android_string_parity_and_format_arguments():
    ru, ru_non_translatable = _android_strings(
        ROOT / "android" / "app" / "src" / "main" / "res" / "values" / "strings.xml"
    )
    en, en_non_translatable = _android_strings(
        ROOT / "android" / "app" / "src" / "main" / "res" / "values-en" / "strings.xml"
    )
    ignored = ru_non_translatable | en_non_translatable
    ru_keys, en_keys = set(ru) - ignored, set(en) - ignored
    assert ru_keys == en_keys, (
        f"Android strings mismatch; RU-only: {sorted(ru_keys - en_keys)}, "
        f"EN-only: {sorted(en_keys - ru_keys)}"
    )

    mismatches = {
        key: (dict(_format_arguments(ru[key])), dict(_format_arguments(en[key])))
        for key in sorted(ru_keys)
        if _format_arguments(ru[key]) != _format_arguments(en[key])
    }
    assert not mismatches, f"Android format placeholders mismatch: {mismatches}"


def test_android_translatable_strings_are_not_blank_or_damaged():
    for path in (
        ROOT / "android" / "app" / "src" / "main" / "res" / "values" / "strings.xml",
        ROOT / "android" / "app" / "src" / "main" / "res" / "values-en" / "strings.xml",
    ):
        values, non_translatable = _android_strings(path)
        blank = sorted(name for name, value in values.items() if name not in non_translatable and not value.strip())
        damaged = sorted(name for name, value in values.items() if "\ufffd" in value)
        assert not blank, f"Blank translatable strings in {path}: {blank}"
        assert not damaged, f"Damaged UTF-8 strings in {path}: {damaged}"


USER_TEXT_KEYS = {
    "text", "title", "interp", "advice", "meaning", "description",
    "summary", "mood", "focus", "tone", "overlay", "lord",
}
CYRILLIC = re.compile(r"[А-Яа-яЁё]")
BROKEN_TEXT = re.compile(r"\ufffd|\b(?:None|null|undefined)\b|\$\{[^}]+\}|\{[A-Za-z_][^}]*\}")


def _user_texts(value, path="root"):
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = f"{path}.{key}"
            if key in USER_TEXT_KEYS and isinstance(child, str):
                yield child_path, child
            yield from _user_texts(child, child_path)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from _user_texts(child, f"{path}[{index}]")


def test_generated_english_reports_have_no_cyrillic_or_broken_templates():
    natal = {
        "name": "English regression", "year": 1990, "month": 5, "day": 17,
        "hour": 12, "minute": 30, "lat": 55.7558, "lng": 37.6173,
        "city": "Moscow", "tz_str": "Europe/Moscow", "houses_system": "P",
        "lang": "en",
    }
    reports = {
        "natal": astrology.natal_report(natal, with_svg=False),
        "progression": astrology.progression_report(
            natal, {"year": 2026, "month": 6, "day": 1, "hour": 12, "minute": 0},
            with_svg=False,
        ),
        "solar": astrology.return_report(natal, 2026, return_type="Solar", with_svg=False),
        "lunar": astrology.return_report(natal, 2026, month=6, return_type="Lunar", with_svg=False),
    }
    problems = []
    for report_name, report in reports.items():
        for path, text in _user_texts(report, report_name):
            if CYRILLIC.search(text) or BROKEN_TEXT.search(text):
                problems.append((path, text[:160]))
    assert not problems, f"Broken or non-English user text: {problems}"
