"""Слой переопределения текстов: правки админа сохраняются в JSON и применяются
к «живым» словарям интерпретаций. Так g()/аксессоры сразу отдают изменённый текст.

Каждая правка адресуется плоским ключом вида "NAMESPACE.key" или "NAMESPACE.key.subkey".
Значение — пара (ru, en). Оригиналы снимаются при инициализации (для сброса и пометки «изменено»).
"""
from __future__ import annotations

import json
import os
from typing import Optional

from . import interpretations as I

try:
    from . import vedic as V
except Exception:  # pragma: no cover
    V = None

# Правки текстов — закрытый контент (данные, не код): живут в data/, как и БД.
_HERE = os.path.dirname(__file__)
OVERRIDES_FILE = os.path.join(_HERE, "..", "data", "content_overrides.json")

# Реестр редактируемых словарей: (namespace, модуль, имя_атрибута, форма, человекочитаемая группа)
#   pair   — dict[key] -> (ru, en)
#   nested — dict[key] -> dict[subkey] -> (ru, en)
#   list   — list[(ru, en)]
_REGISTRY = [
    ("AUTHORED_SIGN",      I, "AUTHORED_SIGN",      "pair",   "Авторские: планета в знаке"),
    ("AUTHORED_HOUSE",     I, "AUTHORED_HOUSE",     "pair",   "Авторские: планета в доме"),
    ("AUTHORED_ASPECT",    I, "AUTHORED_ASPECT",    "pair",   "Авторские: аспекты"),
    ("AUTHORED_ANGLE",     I, "AUTHORED_ANGLE",     "pair",   "Авторские: аспекты к углам"),
    ("PLANET_ROLE",        I, "PLANET_ROLE",        "pair",   "Роли планет"),
    ("SIGN_CORE",          I, "SIGN_CORE",          "pair",   "Суть знаков (светила)"),
    ("ELEMENT_PROFILE",    I, "ELEMENT_PROFILE",    "pair",   "Баланс: стихии"),
    ("ELEMENT_LACK",       I, "ELEMENT_LACK",       "pair",   "Баланс: нехватка стихии"),
    ("QUALITY_PROFILE",    I, "QUALITY_PROFILE",    "pair",   "Баланс: кресты"),
    ("ELEMENT_FLAVOR",     I, "ELEMENT_FLAVOR",     "pair",   "Окраска стихией"),
    ("MODALITY_FLAVOR",    I, "MODALITY_FLAVOR",    "pair",   "Окраска крестом"),
    ("ADVICE_BY_SIGN",     I, "ADVICE_BY_SIGN",     "pair",   "Советы по знакам"),
    ("EL_LOVE_TONE",       I, "EL_LOVE_TONE",       "pair",   "Любовь: тон по стихии"),
    ("EL_WORK_TONE",       I, "EL_WORK_TONE",       "pair",   "Работа: тон по стихии"),
    ("DIGNITY_NOTE",       I, "DIGNITY_NOTE",       "pair",   "Достоинства планет"),
    ("ASPECT_INTERP",      I, "ASPECT_INTERP",      "pair",   "Аспекты: тип"),
    ("DOMINANT_PLANET",    I, "DOMINANT_PLANET",    "pair",   "Ведущая планета"),
    ("TRANSIT_THEME",      I, "TRANSIT_THEME",      "pair",   "Транзиты: темы"),
    ("HOUSE_FOCUS",        I, "HOUSE_FOCUS",        "pair",   "Фокус домов"),
    ("RETROGRADE_NOTE",    I, "RETROGRADE_NOTE",    "pair",   "Ретроградность"),
    ("LUNAR_PHASE_MEANING", I, "LUNAR_PHASE_MEANING", "pair", "Фазы Луны"),
    ("HEMISPHERE_INFO",    I, "HEMISPHERE_INFO",    "pair",   "Полушария карты"),
    ("SELF_ESTEEM",        I, "_SELF_ESTEEM",       "pair",   "Самооценка"),
    ("TEMP_QUALITY",       I, "_TEMP_QUALITY",      "pair",   "Темперамент: крест"),
    ("SIGN_FACETS",        I, "SIGN_FACETS",        "nested", "Грани знаков"),
    ("SIGN_ARCHETYPE",     I, "SIGN_ARCHETYPE",     "nested", "Архетипы знаков"),
    ("PLANET_SPHERES",     I, "PLANET_SPHERES",     "nested", "Сферы планет (функция/любовь/работа)"),
    ("ASPECT_PAIR",        I, "ASPECT_PAIR",        "nested", "Аспекты: пары планет"),
    ("PATTERN_INFO",       I, "PATTERN_INFO",       "nested", "Аспектные конфигурации"),
    ("TEMPERAMENT",        I, "TEMPERAMENT",        "nested", "Темперамент"),
]
if V is not None:
    _REGISTRY += [
        ("NAKSHATRA_GUIDE", V, "NAKSHATRA_GUIDE", "list", "Панчанг: накшатры"),
        ("PAKSHA_ADVICE",   V, "_PAKSHA_ADVICE",  "pair", "Панчанг: фаза Луны"),
        ("DAY_ADVICE",      V, "_DAY_ADVICE",     "pair", "Панчанг: совет дня"),
    ]

# flat_key -> {"container": dict|list, "fkey": ключ внутри container, "ns", "group", "subkey"}
_INDEX: dict = {}
_ORIGINAL: dict = {}
_GROUP: dict = {}
_APPLIED: set = set()        # какие ключи сейчас наложены в этом процессе
_LOADED_MTIME: float = -1.0  # mtime файла на момент последней синхронизации


def _enc_key(k) -> str:
    if isinstance(k, tuple):
        return "+".join(str(x) for x in k)
    return str(k)


def _is_pair(v) -> bool:
    return isinstance(v, tuple) and len(v) == 2 and all(isinstance(x, str) for x in v)


def _register_leaf(flat_key, container, fkey, ns, group):
    _INDEX[flat_key] = {"container": container, "fkey": fkey}
    _GROUP[flat_key] = group
    val = container[fkey]
    _ORIGINAL[flat_key] = (val[0], val[1])


def _build_index():
    _INDEX.clear()
    _ORIGINAL.clear()
    _GROUP.clear()
    for ns, mod, attr, shape, group in _REGISTRY:
        d = getattr(mod, attr, None)
        if d is None:
            continue
        if shape == "pair":
            for k, v in d.items():
                if _is_pair(v):
                    _register_leaf(f"{ns}.{_enc_key(k)}", d, k, ns, group)
        elif shape == "nested":
            for k, sub in d.items():
                if not isinstance(sub, dict):
                    continue
                for sk, v in sub.items():
                    if _is_pair(v):
                        _register_leaf(f"{ns}.{_enc_key(k)}.{sk}", sub, sk, ns, group)
        elif shape == "list":
            for i, v in enumerate(d):
                if _is_pair(v):
                    _register_leaf(f"{ns}.{i}", d, i, ns, group)


def _load_file() -> dict:
    try:
        with open(OVERRIDES_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}


def _save_file(data: dict):
    with open(OVERRIDES_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def _apply(flat_key, ru, en):
    entry = _INDEX.get(flat_key)
    if not entry:
        return False
    entry["container"][entry["fkey"]] = (ru, en)
    return True


def _sync_from(overrides: dict):
    """Привести «живые» словари в соответствие с overrides: наложить новые, снять убранные."""
    global _APPLIED
    new_keys = set()
    for key, val in overrides.items():
        if isinstance(val, (list, tuple)) and len(val) == 2:
            if _apply(key, val[0], val[1]):
                new_keys.add(key)
    # ключи, которые были наложены, но теперь убраны из файла — вернуть к оригиналу
    for key in _APPLIED - new_keys:
        orig = _ORIGINAL.get(key)
        if orig:
            _apply(key, orig[0], orig[1])
    _APPLIED = new_keys


def _file_mtime() -> float:
    try:
        return os.path.getmtime(OVERRIDES_FILE)
    except OSError:
        return 0.0


def init():
    """Построить индекс и применить сохранённые правки. Вызывается один раз при старте."""
    global _LOADED_MTIME
    _build_index()
    _sync_from(_load_file())
    _LOADED_MTIME = _file_mtime()


def refresh_if_changed():
    """Синхронизировать словари с файлом, если он изменился. Дёшево (один stat).
    Делает правки видимыми во всех воркерах uvicorn, а не только в том, где был POST."""
    global _LOADED_MTIME
    if not _INDEX:
        return
    mtime = _file_mtime()
    if mtime != _LOADED_MTIME:
        _sync_from(_load_file())
        _LOADED_MTIME = mtime


# ------- API для админки -------
def catalog(search: str = "", only_overridden: bool = False,
            offset: int = 0, limit: int = 60) -> dict:
    overrides = _load_file()
    s = (search or "").strip().lower()
    items = []
    for flat_key in sorted(_INDEX.keys()):
        entry = _INDEX[flat_key]
        cur = entry["container"][entry["fkey"]]
        ov = flat_key in overrides
        if only_overridden and not ov:
            continue
        if s and s not in flat_key.lower() and s not in cur[0].lower() and s not in cur[1].lower():
            continue
        items.append({
            "key": flat_key,
            "group": _GROUP.get(flat_key, ""),
            "ru": cur[0],
            "en": cur[1],
            "overridden": ov,
        })
    total = len(items)
    return {"total": total, "items": items[offset:offset + limit]}


def set_override(flat_key: str, ru: str, en: str) -> bool:
    global _LOADED_MTIME
    if flat_key not in _INDEX:
        return False
    _apply(flat_key, ru, en)
    _APPLIED.add(flat_key)
    overrides = _load_file()
    overrides[flat_key] = [ru, en]
    _save_file(overrides)
    _LOADED_MTIME = _file_mtime()
    return True


def reset(flat_key: str) -> bool:
    global _LOADED_MTIME
    if flat_key not in _INDEX:
        return False
    orig = _ORIGINAL.get(flat_key)
    if orig:
        _apply(flat_key, orig[0], orig[1])
    _APPLIED.discard(flat_key)
    overrides = _load_file()
    if flat_key in overrides:
        del overrides[flat_key]
        _save_file(overrides)
    _LOADED_MTIME = _file_mtime()
    return True
