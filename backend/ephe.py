"""
Настройка пути к эфемеридам Swiss Ephemeris.

Проблема: проект может лежать по пути с не-ASCII символами (кириллица).
C-библиотека swisseph на Windows не умеет открывать файлы .se1 по таким путям
и молча падает на встроенный аналитический режим (Moshier), а для Хирона и
астероидов запасного варианта нет вовсе.

Решение: копируем файлы эфемерид из пакета kerykeion в гарантированно ASCII-папку
и подменяем (monkeypatch) swe.set_ephe_path, чтобы Kerykeion всегда смотрел туда.
"""
from __future__ import annotations

import os
import shutil
from pathlib import Path

import swisseph as swe

_initialized_path: str | None = None


def _package_sweph_dir() -> Path:
    import kerykeion

    return Path(kerykeion.__file__).resolve().parent / "sweph"


def _ascii_candidates() -> list[Path]:
    candidates: list[Path] = []
    home = Path.home()
    if str(home).isascii():
        candidates.append(home / ".astro_ephe")
    candidates.append(Path("C:/sweph"))
    temp = os.environ.get("TEMP") or os.environ.get("TMP") or "C:/Temp"
    if str(temp).isascii():
        candidates.append(Path(temp) / "astro_ephe")
    return candidates


def _copy_tree(src: Path, dst: Path) -> None:
    dst.mkdir(parents=True, exist_ok=True)
    for item in src.iterdir():
        target = dst / item.name
        if item.is_dir():
            _copy_tree(item, target)
        elif item.is_file():
            if (not target.exists()) or target.stat().st_size != item.stat().st_size:
                shutil.copy2(item, target)


def ensure_ephemeris() -> str:
    """Гарантирует ASCII-путь к эфемеридам и настраивает swisseph. Идемпотентна."""
    global _initialized_path
    if _initialized_path is not None:
        return _initialized_path

    src = _package_sweph_dir()

    # Если путь пакета и так ASCII — используем его напрямую.
    if str(src).isascii():
        swe.set_ephe_path(str(src))
        _initialized_path = str(src)
        return _initialized_path

    # Иначе копируем эфемериды в первую доступную ASCII-папку.
    ascii_path: str | None = None
    for tgt in _ascii_candidates():
        try:
            _copy_tree(src, tgt)
            ascii_path = str(tgt)
            break
        except Exception:
            continue

    if ascii_path is None:
        ascii_path = str(src)  # крайний случай — пусть будет хоть что-то

    # Подменяем set_ephe_path: Kerykeion жёстко зашивает путь пакета (см. его
    # astrological_subject_factory.py), поэтому перехватываем вызов и
    # перенаправляем на ASCII-папку.
    _orig = swe.set_ephe_path

    def _patched(path: str | None = None) -> None:  # noqa: ANN001
        _orig(ascii_path)

    swe.set_ephe_path = _patched  # type: ignore[assignment]
    swe.set_ephe_path()

    _initialized_path = ascii_path
    return ascii_path
