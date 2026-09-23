"""Регрессии офлайн-справочника городов Android."""

from pathlib import Path
import sqlite3


CITY_DB = Path(__file__).parents[1] / "android/app/src/main/assets/db/cities.db"

EXPECTED_RUSSIAN_CITIES = {
    "ставрополь": "Ставрополь",
    "пугачев": "Пугачёв",
    "курортный": "Курортный",
    "обручево": "Обручево",
    "киселевск": "Киселёвск",
    "нарьян-мар": "Нарьян-Мар",
}


def test_corrected_russian_cities_are_found_by_prefix() -> None:
    with sqlite3.connect(CITY_DB) as db:
        for query, expected_name in EXPECTED_RUSSIAN_CITIES.items():
            row = db.execute(
                """SELECT name_ru FROM cities
                   WHERE country = 'RU'
                     AND (search_ru LIKE ? || '%' OR search_en LIKE ? || '%')
                   ORDER BY population DESC LIMIT 1""",
                (query, query),
            ).fetchone()
            assert row == (expected_name,), query


def test_all_russian_city_names_contain_cyrillic() -> None:
    with sqlite3.connect(CITY_DB) as db:
        rows = db.execute(
            "SELECT name_ru FROM cities WHERE country = 'RU'"
        ).fetchall()

    assert all(
        name and any("а" <= char.lower() <= "я" or char.lower() == "ё" for char in name)
        for (name,) in rows
    )
