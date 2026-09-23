from fastapi.testclient import TestClient
from backend.main import app
from backend import api_language


def test_auth_error_language_preserves_status_and_default():
    client = TestClient(app)
    ru = client.get("/api/auth/me")
    en = client.get("/api/auth/me", headers={"Accept-Language": "en"})
    explicit = client.get("/api/auth/me?lang=ru", headers={"Accept-Language": "en"})
    assert ru.status_code == en.status_code == explicit.status_code == 401
    assert en.json()["detail"] == "Please sign in."
    assert ru.json()["detail"] == explicit.json()["detail"] == "Требуется вход"


def test_time_errors_are_translated_without_changing_russian():
    text = "Такого местного времени не существовало из-за перевода часов. Укажите время до или после перехода."
    assert "clocks moved forward" in api_language.translate(text, "en")
    assert api_language.translate(text, "ru") == text
    assert api_language.translate("Неизвестный часовой пояс: Example/Zone", "en") == "Unknown time zone: Example/Zone"
    assert api_language.translate("Value error, Некорректный адрес почты", "en") == "Value error, Invalid email address."


def test_english_geocoding_passes_language_to_provider(monkeypatch):
    from backend import main
    requests = []
    class Response:
        def raise_for_status(self):
            pass
        def json(self):
            return []
    def fake_get(url, **kwargs):
        requests.append(kwargs)
        return Response()
    monkeypatch.setattr(main.requests, "get", fake_get)
    client = TestClient(app)
    assert client.get("/api/geocode?q=London&lang=en").status_code == 200
    assert requests[0]["params"]["accept-language"] == "en"
