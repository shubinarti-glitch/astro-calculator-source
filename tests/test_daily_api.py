import pytest
from datetime import datetime, timezone
from fastapi.testclient import TestClient

from backend import main as main_module


def test_profile_local_date_uses_profile_timezone():
    now = datetime(2030, 1, 1, 22, 30, tzinfo=timezone.utc)

    assert main_module._profile_local_date(
        {"tz_str": "Asia/Krasnoyarsk", "lat": 56.0153, "lng": 92.8932}, now
    ).isoformat() == "2030-01-02"
    assert main_module._profile_local_date(
        {"tz_str": "America/New_York", "lat": 40.7128, "lng": -74.006}, now
    ).isoformat() == "2030-01-01"


@pytest.fixture
def daily_client(monkeypatch):
    captured = []

    main_module.app.dependency_overrides[main_module.current_user_id] = lambda: 42
    monkeypatch.setattr(
        main_module.db,
        "get_primary_profile",
        lambda uid: {
            "label": "Test person",
            "data": {
                "name": "Test person",
                "year": 1990,
                "month": 5,
                "day": 15,
                "hour": 14,
                "minute": 30,
                "lat": 55.7558,
                "lng": 37.6173,
                "tz_str": "Europe/Moscow",
                "city": "Moscow",
            },
        },
    )
    monkeypatch.setattr(main_module.db, "is_premium", lambda uid: False)

    def fake_transit_report(natal_params, transit_dt, with_svg, transit_location=None):
        captured.append((dict(natal_params), dict(transit_dt), with_svg, transit_location))
        lang = natal_params["lang"]
        return {
            "aspects": [
                {
                    "p1": "Sun", "p1_ru": "Солнце" if lang == "ru" else "Sun",
                    "p1_symbol": "☉", "p2": "Mercury",
                    "p2_ru": "Меркурий" if lang == "ru" else "Mercury",
                    "p2_symbol": "☿", "aspect": "conjunction",
                    "aspect_ru": "соединение" if lang == "ru" else "conjunction",
                    "aspect_symbol": "☌", "nature": "neutral",
                    "nature_label": "нейтральный" if lang == "ru" else "neutral",
                    "orbit": 0.1, "movement": "", "interp": f"text-{lang}-mercury",
                },
                {
                    "p1": "Moon", "p1_ru": "Луна" if lang == "ru" else "Moon",
                    "p1_symbol": "☽", "p2": "Jupiter",
                    "p2_ru": "Юпитер" if lang == "ru" else "Jupiter",
                    "p2_symbol": "♃", "aspect": "square",
                    "aspect_ru": "квадратура" if lang == "ru" else "square",
                    "aspect_symbol": "□", "nature": "tense",
                    "nature_label": "напряжённый" if lang == "ru" else "tense",
                    "orbit": 1.0, "movement": "", "interp": f"text-{lang}-jupiter",
                },
                {
                    "p1": "Venus", "p1_ru": "Венера" if lang == "ru" else "Venus",
                    "p1_symbol": "♀", "p2": "Saturn",
                    "p2_ru": "Сатурн" if lang == "ru" else "Saturn",
                    "p2_symbol": "♄", "aspect": "sextile",
                    "aspect_ru": "секстиль" if lang == "ru" else "sextile",
                    "aspect_symbol": "⚹", "nature": "harmonious",
                    "nature_label": "гармоничный" if lang == "ru" else "harmonious",
                    "orbit": 0.0, "movement": "applying", "interp": f"text-{lang}-saturn",
                },
                {
                    "p1": "Mars", "p1_ru": "Марс" if lang == "ru" else "Mars",
                    "p1_symbol": "♂", "p2": "Moon",
                    "p2_ru": "Луна" if lang == "ru" else "Moon",
                    "p2_symbol": "☽", "aspect": "opposition",
                    "aspect_ru": "оппозиция" if lang == "ru" else "opposition",
                    "aspect_symbol": "☍", "nature": "tense",
                    "nature_label": "напряжённый" if lang == "ru" else "tense",
                    "orbit": 0.0, "movement": "", "interp": f"text-{lang}-moon",
                },
            ]
        }

    monkeypatch.setattr(main_module.astrology, "transit_report", fake_transit_report)
    try:
        yield TestClient(main_module.app), captured
    finally:
        main_module.app.dependency_overrides.pop(main_module.current_user_id, None)


def test_daily_defaults_to_russian(daily_client):
    client, captured = daily_client
    response = client.get("/api/daily")

    assert response.status_code == 200
    assert captured[-1][0]["lang"] == "ru"
    assert response.json()["aspects"][0]["p2_ru"] == "Юпитер"


def test_daily_supports_english_without_forcing_russian(daily_client):
    client, captured = daily_client
    response = client.get("/api/daily?lang=en")

    assert response.status_code == 200
    assert captured[-1][0]["lang"] == "en"
    assert response.json()["aspects"][0]["p2_ru"] == "Jupiter"


def test_daily_uses_requested_date_in_calculation_and_response(daily_client):
    client, captured = daily_client
    response = client.get("/api/daily?date=2030-02-03")

    assert response.status_code == 200
    assert response.json()["date"] == "2030-02-03"
    assert captured[-1][1] == {"year": 2030, "month": 2, "day": 3, "hour": 12, "minute": 0}
    assert captured[-1][2] is False


def test_daily_uses_current_location_and_its_calendar_date(daily_client):
    client, captured = daily_client
    response = client.get(
        "/api/daily?date=2030-02-03&current_lat=40.7128&current_lng=-74.006"
        "&current_tz=America%2FNew_York&current_city=New%20York"
    )

    assert response.status_code == 200
    assert captured[-1][3]["tz_str"] == "America/New_York"
    assert captured[-1][3]["city"] == "New York"


def test_daily_sorts_by_today_strength_and_only_returns_real_movement(daily_client):
    client, _ = daily_client
    response = client.get("/api/daily?lang=en&date=2030-02-03")

    assert response.status_code == 200
    aspects = response.json()["aspects"]
    assert [aspect["p2_ru"] for aspect in aspects] == ["Jupiter", "Saturn", "Mercury"]
    assert "movement" not in aspects[0]
    assert aspects[1]["movement"] == "applying"
    assert "movement" not in aspects[2]


def test_daily_rejects_unsupported_language_and_bad_date(daily_client):
    client, _ = daily_client

    assert client.get("/api/daily?lang=de").status_code == 422
    assert client.get("/api/daily?date=03-02-2030").status_code == 422
