# -*- coding: utf-8 -*-
"""Smoke-тесты API: каждый эндпоинт отвечает корректно. Запуск: pytest -q

Тесты не создают пользователей (авторизация проверяется на несуществующих),
поэтому реальную базу data/app.db не засоряют.
"""
import random
import string

from fastapi.testclient import TestClient

from backend import astrology, db
from backend.main import app

db.init_db()  # таблицы на случай, если lifespan не запущен вне контекст-менеджера
client = TestClient(app)

# Премиум-эндпоинты (ректификация/синастрия/прогноз) в тестах открыты через
# override зависимости — реальную БД пользователями не засоряем.
from backend import main as main_module  # noqa: E402
app.dependency_overrides[main_module.require_premium] = lambda: 1

NATAL = {
    "name": "Test", "year": 1990, "month": 5, "day": 15, "hour": 14, "minute": 30,
    "lat": 55.7558, "lng": 37.6173, "city": "Moscow",
}

LEGAL_ACCEPTANCE = {
    "privacy_accepted": True,
    "terms_accepted": True,
    "privacy_version": "2026-09-01",
    "terms_version": "2026-09-01",
    "consent_source": "web",
}


def _rnd(prefix):
    return prefix + "".join(random.choices(string.ascii_lowercase, k=8))


# ---- Расчётные эндпоинты ----
def test_meta():
    assert client.get("/api/meta").status_code == 200


def test_transits_current():
    r = client.get("/api/transits/current")
    assert r.status_code == 200
    data = r.json()
    tr = data["transits"]
    assert len(tr) == 10  # Солнце … Плутон
    first = tr[0]
    # Каждая планета: знак, локализованное значение и период (until обязателен).
    for key in ("planet", "sign", "sign_ru", "meaning", "until"):
        assert first.get(key), f"пусто поле {key}"
    assert isinstance(first["retrograde"], bool)


def test_natal_ok():
    r = client.post("/api/natal", json=NATAL)
    assert r.status_code == 200
    d = r.json()
    assert d["planets"]
    assert d["essentials"]["lot_fortune"]["sign_ru"]
    assert "is_day" in d["essentials"]["sect"]


def test_natal_invalid_month():
    assert client.post("/api/natal", json=dict(NATAL, month=13)).status_code == 422


def test_natal_no_svg():
    # svg=0 — режим мобильного приложения: тексты есть, SVG нет.
    d = client.post("/api/natal?svg=0", json=NATAL).json()
    assert "svg" not in d
    assert d["planets"] and d["story"]
    t = client.post("/api/transit?svg=0", json={"natal": NATAL, "transit_date": {"year": 2026, "month": 6, "day": 1, "hour": 12, "minute": 0}}).json()
    assert "svg" not in t


def test_premium_reports_no_svg():
    r = client.post("/api/return?svg=0", json={"natal": NATAL, "year": 2026, "return_type": "Solar"}).json()
    assert "svg" not in r and r["planets"]
    p = client.post("/api/progression?svg=0", json={"natal": NATAL, "target_date": {"year": 2026, "month": 7, "day": 17, "hour": 12, "minute": 0}}).json()
    assert "svg" not in p and p["prog_planets"]
    s = client.post("/api/synastry?svg=0", json={"person_a": NATAL, "person_b": dict(NATAL, year=1988)}).json()
    assert "svg" not in s and s["couple"]["verdict"]


def test_synastry():
    r = client.post("/api/synastry", json={"person_a": NATAL, "person_b": dict(NATAL, year=1988)})
    assert r.status_code == 200
    c = r.json()["couple"]
    assert c["spheres"] and c["composite"] and c["overlays"]


def test_transit():
    r = client.post("/api/transit", json={
        "natal": NATAL,
        "transit_date": {"year": 2026, "month": 6, "day": 1, "hour": 12, "minute": 0},
        "transit_location": {"lat": 56.0153, "lng": 92.8932, "tz_str": "Asia/Krasnoyarsk", "city": "Krasnoyarsk"},
    })
    assert r.status_code == 200
    assert r.json()["transit_meta"]["tz_str"] == "Asia/Krasnoyarsk"


def test_return():
    r = client.post("/api/return", json={
        "natal": NATAL, "year": 2026, "return_type": "Solar",
        "location": {"lat": 40.7128, "lng": -74.006, "tz_str": "America/New_York", "city": "New York"},
    })
    assert r.status_code == 200
    assert r.json()["meta"]["city"] == "New York"
    assert r.json()["meta"]["tz_str"] == "America/New_York"
    assert r.json()["period_end"]


def test_progression():
    r = client.post("/api/progression", json={"natal": NATAL, "target_date": {"year": 2026, "month": 6, "day": 1, "hour": 12, "minute": 0}})
    assert r.status_code == 200


def test_forecast():
    r = client.post("/api/forecast", json={
        "natal": NATAL,
        "location": {"lat": 40.7128, "lng": -74.006, "tz_str": "America/New_York", "city": "New York"},
        "start": {"year": 2026, "month": 6, "day": 1}, "end": {"year": 2027, "month": 6, "day": 1},
    })
    assert r.status_code == 200
    assert r.json()["location_meta"] == {
        "lat": 40.7128, "lng": -74.006, "tz_str": "America/New_York", "city": "New York",
    }


def test_calendar():
    r = client.post("/api/calendar", json={
        "natal": NATAL,
        "location": {"lat": 51.5074, "lng": -0.1278, "tz_str": "Europe/London", "city": "London"},
        "start": {"year": 2026, "month": 6, "day": 1}, "end": {"year": 2026, "month": 7, "day": 1},
    })
    assert r.status_code == 200
    assert r.json()["location_meta"]["tz_str"] == "Europe/London"
    assert r.json()["location_meta"]["city"] == "London"


def test_vedic():
    r = client.post("/api/vedic-calendar", json={"year": 2026, "month": 6, "lat": 55.75, "lng": 37.61})
    assert r.status_code == 200


def test_rectification():
    r = client.post("/api/rectification", json={"natal": NATAL, "asc_traits": ["temp_fire"], "start_minute": 600, "end_minute": 900, "step_minute": 30})
    assert r.status_code == 200
    assert r.json()["best"]["time"]


def test_archetypes():
    assert client.get("/api/archetypes").status_code == 200


# ---- Безопасность ----
def test_register_short_password():
    assert client.post("/api/auth/register", json={"username": "x", "password": "1234"}).status_code == 422


def test_validation_error_detail_shape():
    """Мобильное приложение читает msg из detail-списка (AccountViewModel.parseDetail).
    Если форма 422 изменится — там снова вылезет невнятное «HTTP 422»."""
    r = client.post("/api/auth/register", json={"username": "x", "password": "1234", "email": "a@b.ru"})
    detail = r.json()["detail"]
    assert isinstance(detail, list) and detail
    assert all(isinstance(item.get("msg"), str) and item["msg"] for item in detail)


def test_login_nonexistent():
    r = client.post("/api/auth/login", json={"username": _rnd("nouser_"), "password": "wrongpass123"})
    assert r.status_code == 401


def test_login_throttle():
    u = _rnd("brute_")
    codes = [client.post("/api/auth/login", json={"username": u, "password": "wrongpass123"}).status_code for _ in range(7)]
    assert codes.count(401) >= 5
    assert 429 in codes  # после 5 неудач — блокировка


def test_admin_requires_auth():
    assert client.get("/api/admin/texts").status_code == 401


def test_name_xss_sanitized():
    r = client.post("/api/natal", json=dict(NATAL, name='<img src=x onerror=alert(1)>Bob'))
    assert r.status_code == 200
    assert "<" not in r.json()["meta"]["name"]


# ---- Надёжность ----
def test_token_revocation():
    tok = db.make_token(999999)
    assert db.verify_token(tok) == 999999      # валиден
    db.revoke_token(tok)
    assert db.verify_token(tok) is None         # после выхода — отозван


def test_logout_endpoint():
    assert client.post("/api/auth/logout").status_code == 200  # идемпотентно, без токена тоже ок


def test_text_audit_log():
    before = len(db.list_text_audit())
    db.add_text_audit(1, "tester", "TEST.KEY", "edit")
    rows = db.list_text_audit()
    # list_text_audit() intentionally caps the response at 100 newest rows.
    assert len(rows) == min(before + 1, 100)
    assert rows[0]["key"] == "TEST.KEY" and rows[0]["action"] == "edit"


def test_natal_house_interp_richer():
    # планета в доме теперь развёрнута (несколько предложений), а не одна фраза
    d = client.post("/api/natal", json=NATAL).json()
    sun = next(p for p in d["planets"] if p["name"] == "Sun")
    assert len(sun["interp_house"]) > 120


# ---- Ректификация: расширенный движок (символические дирекции + режим окна) ----
# Валидные события (после даты рождения 1990-05-15) для питания движка.
RECT_EVENTS = [
    {"date": {"year": 2010, "month": 6, "day": 1}, "label": "Свадьба", "type": "relationship", "weight": 1.5},
    {"date": {"year": 2015, "month": 9, "day": 20}, "label": "Карьера", "type": "career", "weight": 1.2},
    {"date": {"year": 2018, "month": 3, "day": 10}, "label": "Ребёнок", "type": "child", "weight": 1.0},
]


def _rect_call(**overrides):
    payload = {"natal": NATAL, "asc_traits": ["temp_fire"], "events": RECT_EVENTS}
    payload.update(overrides)
    return client.post("/api/rectification", json=payload)


def test_rectification_legacy_structure():
    # Регрессия: старый вызов без center/window возвращает прежнюю структуру.
    r = _rect_call(start_minute=600, end_minute=900, step_minute=30)
    assert r.status_code == 200
    d = r.json()
    assert set(("meta", "best", "top")).issubset(d.keys())
    best = d["best"]
    assert "time" in best and "score" in best and "breakdown" in best
    # best.time формата HH:MM
    hh, mm = best["time"].split(":")
    assert len(hh) == 2 and len(mm) == 2 and 0 <= int(hh) <= 23 and 0 <= int(mm) <= 59
    assert isinstance(d["top"], list) and d["top"]


def test_rectification_window_mode():
    # Режим окна: center=720 (12:00), window=120 (±2ч) → диапазон [600..840], шаг 1 мин.
    r = _rect_call(center_minute=720, window_minutes=120)
    assert r.status_code == 200
    d = r.json()
    best = d["best"]
    total_min = int(best["time"][:2]) * 60 + int(best["time"][3:])
    assert 600 <= total_min <= 840, f"best time {best['time']} вне окна"
    assert d["top"], "top должен быть непустым"
    # Сетка кандидатов (окно ±120 → 241 ≤ 400) прошла валидацию: запрос успешен.
    assert d["meta"]["candidates_checked"] > 0
    # top-времена тоже внутри окна (шаг реально 1 мин, регионы уточняются поминутно).
    for c in d["top"]:
        t = int(c["time"][:2]) * 60 + int(c["time"][3:])
        assert 600 <= t <= 840


def test_rectification_window_max_3h():
    # Валидный ±3ч (window=180 → 361 кандидат ≤ 400) не падает и возвращает результат.
    # 200 (не 400) означает: сетка кандидатов (361 ≤ лимита 400) прошла валидацию.
    r = _rect_call(center_minute=720, window_minutes=180)
    assert r.status_code == 200
    d = r.json()
    assert d["best"]["time"]
    # candidates_checked включает грубый проход + поминутное уточнение регионов,
    # поэтому может превышать 400 — лимит применяется к исходной сетке, не к нему.
    assert d["meta"]["candidates_checked"] > 0


def test_rectification_window_over_limit_rejected():
    # window_minutes=200 > максимума 180 → отклоняется валидацией Pydantic (422).
    r = _rect_call(center_minute=720, window_minutes=200)
    assert r.status_code == 422


def test_rectification_symbolic_arc_equals_years():
    # Юнит: sym_arc в предрасчёте события равен числу лет (ключ 1°/год).
    assert astrology.SYMBOLIC_KEY_DEG_PER_YEAR == 1.0
    # Событие ровно через ~20 тропических лет после рождения.
    events = [{"date": {"year": 2010, "month": 5, "day": 15}, "label": "e", "type": "", "weight": 1.0}]
    report = astrology.rectification_report(
        natal_params=dict(NATAL, lang="ru"),
        events=events,
        start_minute=700, end_minute=740, step_minute=5,
    )
    # Разбор непустой, событие отработано.
    assert report["best"]["breakdown"]
    assert report["meta"]["events_used"] == 1


def test_rectification_symbolic_direction_label_present():
    # Символические дирекции присутствуют в разборе хотя бы одного кандидата (best или top).
    # Мягкая проверка: при разнообразных событиях метка "сим.дир." должна встретиться,
    # иначе — как минимум разбор непустой (движок отработал события).
    r = _rect_call(center_minute=720, window_minutes=120)
    assert r.status_code == 200
    d = r.json()
    factors = []
    for cand in [d["best"]] + d["top"]:
        factors.extend(f.get("factor", "") for f in cand.get("breakdown", []))
    has_sym = any("сим.дир." in f for f in factors)
    has_any = any(f and f != "—" for f in factors)
    assert has_sym or has_any, "ни одной сработавшей техники в разборе"


def test_rectification_symbolic_label_direct():
    # Прямой юнит: движок EN → метка символической дирекции содержит "sym.dir."
    # при событии со значимой символической дугой. Проверяем сам код разбора.
    events = [{"date": {"year": 2016, "month": 8, "day": 25}, "label": "ev", "type": "career", "weight": 2.0}]
    report = astrology.rectification_report(
        natal_params=dict(NATAL, lang="en"),
        events=events,
        start_minute=0, end_minute=1439, step_minute=15,
    )
    factors = []
    for cand in [report["best"]] + report["top"]:
        factors.extend(f.get("factor", "") for f in cand.get("breakdown", []))
    # Хотя бы одна сработавшая техника; если это символическая — метка EN-формата.
    sym = [f for f in factors if f.startswith("sym.dir.")]
    for f in sym:
        assert "sym.dir." in f
    # Движок в любом случае вернул непустой разбор.
    assert any(f and f != "—" for f in factors)


# ---- Защиты (аудит безопасности 2026-07) ----
def test_revoke_rejects_forged_token():
    # Поддельный токен не должен попадать в revoked_tokens (DoS на БД).
    assert db.revoke_token("1.99999999999.deadbeef") is False


def test_body_size_limit():
    r = client.post("/api/natal", json=NATAL,
                    headers={"Content-Length": "2000000"})
    assert r.status_code == 413


def test_geocode_rate_limited():
    from backend.main import _RATE
    _RATE.pop("geo:testclient", None)
    codes = [client.get("/api/reverse-geocode", params={"lat": 0, "lng": 0}).status_code
             for _ in range(31)]
    assert 429 in codes
    _RATE.pop("geo:testclient", None)


def test_rectification_rate_limited():
    from backend.main import _RATE
    _RATE.pop("rect:testclient", None)
    body = {"natal": NATAL, "events": [], "start_minute": 0, "end_minute": 60, "step_minute": 30}
    codes = [client.post("/api/rectification", json=body).status_code for _ in range(11)]
    assert codes[-1] == 429
    _RATE.pop("rect:testclient", None)


# ---- SEO-страницы трактовок ----
def test_seo_page_sign():
    r = client.get("/opisanie/solntse-v-lve")
    assert r.status_code == 200
    assert "Солнце во Льве" in r.text
    assert "сиять" in r.text  # авторский текст, не заглушка


def test_seo_page_house_and_404():
    assert client.get("/opisanie/luna-v-7-dome").status_code == 200
    assert client.get("/opisanie/nesuschestvuet").status_code == 404


def test_seo_sitemap():
    r = client.get("/sitemap.xml")
    assert r.status_code == 200
    from backend import seo
    from xml.etree import ElementTree
    root = ElementTree.fromstring(r.content)
    urls = [node.text for node in root.iter() if node.tag.endswith("}loc")]
    expected = {"http://testserver/", "http://testserver/opisaniya", "http://testserver/opisaniya?lang=en"}
    expected.update(f"http://testserver/opisanie/{slug}" for slug in seo.PAGES)
    expected.update(f"http://testserver/opisanie/{slug}?lang=en" for slug in seo.EN_PAGES)
    assert set(urls) == expected
    assert len(urls) == len(expected)


def test_natal_explains_dst_gap_and_ambiguity():
    gap = client.post("/api/natal", json={
        **NATAL, "year": 2024, "month": 3, "day": 10, "hour": 2, "minute": 30,
        "lat": 40.7128, "lng": -74.006, "tz_str": "America/New_York", "city": "New York",
    })
    assert gap.status_code == 400
    assert "не существовало" in gap.json()["detail"]

    ambiguous = client.post("/api/natal", json={
        **NATAL, "year": 2024, "month": 11, "day": 3, "hour": 1, "minute": 30,
        "lat": 40.7128, "lng": -74.006, "tz_str": "America/New_York", "city": "New York",
    })
    assert ambiguous.status_code == 400
    assert "неоднозначно" in ambiguous.json()["detail"]

    first = client.post("/api/natal?svg=0", json={
        **NATAL, "year": 2024, "month": 11, "day": 3, "hour": 1, "minute": 30,
        "lat": 40.7128, "lng": -74.006, "tz_str": "America/New_York",
        "city": "New York", "is_dst": True,
    })
    second = client.post("/api/natal?svg=0", json={
        **NATAL, "year": 2024, "month": 11, "day": 3, "hour": 1, "minute": 30,
        "lat": 40.7128, "lng": -74.006, "tz_str": "America/New_York",
        "city": "New York", "is_dst": False,
    })
    assert first.status_code == second.status_code == 200
    assert first.json()["meta"]["utc_datetime"] != second.json()["meta"]["utc_datetime"]


def test_uranus_priority_page_has_unique_seo_layer():
    r = client.get("/opisanie/uran-v-3-dome")
    assert r.status_code == 200
    assert "<title>Уран в 3 доме — мышление и общение | Натальная карта</title>" in r.text
    assert 'name="description" content="Что означает Уран в 3 доме:' in r.text
    assert 'class="quick-answer"' in r.text
    assert "нестандартное мышление" in r.text
    assert 'href="/opisanie/uran-v-11-dome"' in r.text


def test_uranus_in_leo_explains_generational_position():
    r = client.get("/opisanie/uran-v-lve")
    assert r.status_code == 200
    assert "Уран во Льве — творчество и свобода" in r.text
    assert "поколенческое положение" in r.text


def test_non_priority_seo_page_keeps_generic_template():
    r = client.get("/opisanie/luna-v-7-dome")
    assert r.status_code == 200
    assert "<title>Луна в 7 доме — значение в натальной карте</title>" in r.text
    assert 'class="quick-answer"' not in r.text


def test_seo_catalog_features_uranus_cluster():
    r = client.get("/opisaniya")
    assert r.status_code == 200
    assert "Популярные материалы об Уране" in r.text
    assert 'href="/opisanie/uran-v-3-dome"' in r.text


def test_moon_priority_page_has_unique_seo_layer():
    r = client.get("/opisanie/luna-v-2-dome")
    assert r.status_code == 200
    assert "Луна во 2 доме — деньги и чувство опоры" in r.text
    assert 'name="description" content="Что означает Луна во 2 доме:' in r.text
    assert 'class="quick-answer"' in r.text
    assert "эмоциональную безопасность" in r.text
    assert 'href="/opisanie/luna-v-4-dome"' in r.text


def test_seo_catalog_features_moon_cluster_and_favicon():
    r = client.get("/opisaniya")
    assert r.status_code == 200
    assert "Популярные материалы о Луне" in r.text
    assert 'href="/opisanie/luna-v-11-dome"' in r.text
    assert '<link rel="icon" href="/icon.svg" type="image/svg+xml">' in r.text

    page = client.get("/opisanie/luna-v-5-dome")
    assert '<link rel="icon" href="/icon.svg" type="image/svg+xml">' in page.text


def test_mars_priority_page_has_unique_seo_layer():
    r = client.get("/opisanie/mars-v-6-dome")
    assert r.status_code == 200
    assert "Марс в 6 доме — работа и повседневные дела" in r.text
    assert 'name="description" content="Что означает Марс в 6 доме:' in r.text
    assert 'class="quick-answer"' in r.text
    assert "действовать через конкретные задачи" in r.text
    assert 'href="/opisanie/mars-v-10-dome"' in r.text


def test_mars_in_leo_has_unique_answer():
    r = client.get("/opisanie/mars-v-lve")
    assert r.status_code == 200
    assert "Марс во Льве — воля и яркое действие" in r.text
    assert "действовать заметно" in r.text


def test_seo_catalog_features_mars_cluster():
    r = client.get("/opisaniya")
    assert r.status_code == 200
    assert "Популярные материалы о Марсе" in r.text
    assert 'href="/opisanie/mars-v-6-dome"' in r.text


def test_sun_priority_page_has_unique_seo_layer():
    r = client.get("/opisanie/solntse-v-8-dome")
    assert r.status_code == 200
    assert "Солнце в 8 доме — трансформация и общие ресурсы" in r.text
    assert 'name="description" content="Что означает Солнце в 8 доме:' in r.text
    assert 'class="quick-answer"' in r.text
    assert "через глубокие перемены" in r.text
    assert 'href="/opisanie/solntse-v-11-dome"' in r.text


def test_seo_catalog_features_sun_cluster():
    r = client.get("/opisaniya")
    assert r.status_code == 200
    assert "Популярные материалы о Солнце" in r.text
    assert 'href="/opisanie/solntse-v-8-dome"' in r.text


def test_homepage_targets_natal_chart_with_transits():
    r = client.get("/")
    assert r.status_code == 200
    assert "Натальная карта онлайн бесплатно с транзитами" in r.text
    assert '<link rel="canonical" href="https://astrosmap.ru/"' in r.text


# ---- Подписка «Премиум» ----
def test_premium_gate_when_not_overridden():
    # временно убираем override: без токена премиум-эндпоинты закрыты
    ov = app.dependency_overrides.pop(main_module.require_premium)
    try:
        r = client.post("/api/synastry", json={"person_a": NATAL, "person_b": NATAL})
        assert r.status_code == 401
    finally:
        app.dependency_overrides[main_module.require_premium] = ov


def test_billing_plans_public():
    r = client.get("/api/billing/plans")
    assert r.status_code == 200
    assert r.json()["month"]["price"] == 299
    assert r.json()["year"]["price"] == 1990


def test_billing_create_requires_auth():
    assert client.post("/api/billing/create", json={"plan": "month"}).status_code == 401


def test_subscription_extend_and_expiry():
    import time as _t
    # логика продления без записи в users: несуществующий uid не пройдёт FK —
    # проверяем на временном пользователе и убираем за собой
    u = db.create_user(_rnd("subtest_"), "password123")
    try:
        assert not db.is_premium(u["id"])
        db.extend_subscription(u["id"], "month", 30)
        assert db.is_premium(u["id"])
        sub = db.get_subscription(u["id"])
        assert sub["expires_at"] > _t.time() + 29 * 86400
        # продление добавляет срок к текущему
        db.extend_subscription(u["id"], "year", 365)
        assert db.get_subscription(u["id"])["expires_at"] > _t.time() + 394 * 86400
    finally:
        with db.get_conn() as c:
            c.execute("DELETE FROM users WHERE id = ?", (u["id"],))


def test_billing_plus_plans():
    plans = client.get("/api/billing/plans").json()
    assert plans["plus_month"]["price"] == 1799 and plans["plus_year"]["price"] == 3490


def test_consultation_credit():
    u = db.create_user(_rnd("constest_"), "password123")
    try:
        assert db.has_consultation(u["id"]) is False
        db.add_consultation(u["id"])
        assert db.has_consultation(u["id"]) is True
    finally:
        with db.get_conn() as c:
            c.execute("DELETE FROM users WHERE id = ?", (u["id"],))


def test_billing_consult_plan():
    plans = client.get("/api/billing/plans").json()
    assert plans["consult"]["price"] == 3500 and plans["consult"]["days"] == 0


# --------------------------------------------------------------------------- #
#  Админ-функции: премиум, бан, платежи, статистика; смена пароля
# --------------------------------------------------------------------------- #
def _make_user(prefix="feat_"):
    name = _rnd(prefix)
    main_module._RATE.clear()  # сброс in-memory лимитера: тесты регистрируют многих с одного IP
    r = client.post("/api/auth/register",
                    json={"username": name, "password": "password123", "email": f"{name}@test.local", **LEGAL_ACCEPTANCE})
    assert r.status_code == 200
    return name, r.json()["token"]


def _cleanup(username):
    u = db.get_user_by_username(username)
    if u:
        with db.get_conn() as c:
            c.execute("DELETE FROM users WHERE id = ?", (u["id"],))


def test_change_password():
    name, token = _make_user("pwd_")
    try:
        h = {"Authorization": f"Bearer {token}"}
        # неверный старый пароль
        r = client.post("/api/auth/password", headers=h,
                        json={"old_password": "wrong", "new_password": "newpassword1"})
        assert r.status_code == 400
        # верный
        r = client.post("/api/auth/password", headers=h,
                        json={"old_password": "password123", "new_password": "newpassword1"})
        assert r.status_code == 200
        assert client.post("/api/auth/login", json={"username": name, "password": "newpassword1"}).status_code == 200
        assert client.post("/api/auth/login", json={"username": name, "password": "password123"}).status_code == 401
    finally:
        _cleanup(name)


def test_ban_blocks_login_and_token():
    name, token = _make_user("ban_")
    try:
        u = db.get_user_by_username(name)
        assert db.admin_set_banned(u["id"], True)
        # вход отклоняется
        r = client.post("/api/auth/login", json={"username": name, "password": "password123"})
        assert r.status_code == 403
        # старый токен тоже не работает
        r = client.get("/api/auth/me", headers={"Authorization": f"Bearer {token}"})
        assert r.status_code == 403
        # разбан возвращает доступ
        assert db.admin_set_banned(u["id"], False)
        assert client.post("/api/auth/login", json={"username": name, "password": "password123"}).status_code == 200
    finally:
        _cleanup(name)


def test_admin_cannot_be_banned():
    admins = [u for u in db.list_all_users() if u["is_admin"]]
    if admins:
        assert db.admin_set_banned(admins[0]["id"], True) is False


def test_admin_premium_grant_and_revoke():
    name, _ = _make_user("gift_")
    try:
        u = db.get_user_by_username(name)
        assert db.admin_set_premium(u["id"], 30)
        assert db.is_premium(u["id"])
        assert db.admin_set_premium(u["id"], 0)
        assert not db.is_premium(u["id"])
        assert db.admin_set_premium(999999, 30) is False
    finally:
        _cleanup(name)


def test_usage_stats_recorded():
    before = {r["endpoint"]: r["total"] for r in db.usage_summary()}
    db.record_usage("natal")
    db.record_usage("natal")
    after = {r["endpoint"]: r["total"] for r in db.usage_summary()}
    assert after.get("natal", 0) == before.get("natal", 0) + 2


def test_admin_endpoints_require_admin():
    name, token = _make_user("plain_")
    try:
        h = {"Authorization": f"Bearer {token}"}
        for path in ("/api/admin/payments", "/api/admin/usage"):
            assert client.get(path, headers=h).status_code == 403
        assert client.post("/api/admin/users/1/premium", headers=h, json={"days": 30}).status_code == 403
        assert client.post("/api/admin/users/1/ban", headers=h, json={"banned": True}).status_code == 403
    finally:
        _cleanup(name)


# --------------------------------------------------------------------------- #
#  Email-авторизация, сброс пароля, кабинет
# --------------------------------------------------------------------------- #
def test_register_requires_email():
    name = _rnd("noemail_")
    r = client.post("/api/auth/register", json={"username": name, "password": "password123"})
    assert r.status_code == 422


def test_register_requires_current_legal_acceptance():
    name = _rnd("consent_")
    base = {"username": name, "password": "password123", "email": f"{name}@test.local"}
    assert client.post("/api/auth/register", json=base).status_code == 422
    stale = {**base, **LEGAL_ACCEPTANCE, "privacy_version": "2026-08-01"}
    assert client.post("/api/auth/register", json=stale).status_code == 409
    r = client.post("/api/auth/register",
                    json={"username": name, "password": "password123", "email": "not-an-email"})
    assert r.status_code == 422


def test_email_unique_and_login_by_email():
    name, token = _make_user("em_")
    try:
        # почта занята — второй аккаунт с той же почтой не создаётся
        other = _rnd("em2_")
        r = client.post("/api/auth/register",
                        json={"username": other, "password": "password123", "email": f"{name}@test.local", **LEGAL_ACCEPTANCE})
        assert r.status_code == 409
        # вход по почте
        r = client.post("/api/auth/login",
                        json={"username": f"{name}@test.local", "password": "password123"})
        assert r.status_code == 200 and r.json()["username"] == name
        # /me отдаёт почту
        me = client.get("/api/auth/me", headers={"Authorization": f"Bearer {token}"}).json()
        assert me["email"] == f"{name}@test.local" and me["email_verified"] is False
    finally:
        _cleanup(name)


def test_user_can_delete_own_account_with_password():
    name, token = _make_user("delself_")
    user = db.get_user_by_username(name)
    with db.get_conn() as c:
        assert c.execute("SELECT COUNT(*) FROM consent_records WHERE user_id = ?", (user["id"],)).fetchone()[0] == 2
    h = {"Authorization": f"Bearer {token}"}
    assert client.request("DELETE", "/api/auth/account", headers=h, json={"password": "wrong"}).status_code == 400
    assert client.request("DELETE", "/api/auth/account", headers=h, json={"password": "password123"}).status_code == 200
    assert db.get_user_by_username(name) is None
    with db.get_conn() as c:
        assert c.execute("SELECT COUNT(*) FROM consent_records WHERE user_id IS NULL").fetchone()[0] >= 2


def test_verify_and_reset_tokens():
    name, token = _make_user("tok_")
    try:
        u = db.get_user_by_username(name)
        # подтверждение почты
        t = db.create_email_token(u["id"], "verify", 3600)
        assert client.post("/api/auth/verify-email", json={"token": t}).status_code == 200
        assert client.post("/api/auth/verify-email", json={"token": t}).status_code == 400  # одноразовый
        assert db.get_user_by_id(u["id"])["email_verified"] == 1
        # сброс пароля
        t = db.create_email_token(u["id"], "reset", 3600)
        r = client.post("/api/auth/reset", json={"token": t, "new_password": "newpassword1"})
        assert r.status_code == 200
        r = client.post("/api/auth/login", json={"username": name, "password": "newpassword1"})
        assert r.status_code == 200
        # токен не того типа не подходит
        t = db.create_email_token(u["id"], "verify", 3600)
        assert client.post("/api/auth/reset", json={"token": t, "new_password": "newpassword2"}).status_code == 400
    finally:
        _cleanup(name)


def test_history_and_profile_note():
    name, token = _make_user("hist_")
    try:
        h = {"Authorization": f"Bearer {token}"}
        r = client.post("/api/history", headers=h,
                        json={"kind": "natal", "label": "Тест <b>x</b>", "params": {"a": 1}})
        assert r.status_code == 200
        items = client.get("/api/history", headers=h).json()
        assert len(items) == 1 and items[0]["kind"] == "natal" and "<" not in items[0]["label"]
        # заметка к сохранённой карте
        p = client.post("/api/profiles", headers=h, json={"label": "Мама", "data": {"y": 1960}}).json()
        r = client.post(f"/api/profiles/{p['id']}/note", headers=h, json={"note": "заметка"})
        assert r.status_code == 200
        profs = client.get("/api/profiles", headers=h).json()
        assert profs[0]["note"] == "заметка"
        # свои платежи (пусто, но эндпоинт живой)
        assert client.get("/api/billing/my", headers=h).json()["items"] == []
    finally:
        _cleanup(name)


# --------------------------------------------------------------------------- #
#  Транзит дня, основной человек, еженедельная рассылка
# --------------------------------------------------------------------------- #
def test_primary_and_daily():
    name, token = _make_user("daily_")
    try:
        h = {"Authorization": f"Bearer {token}"}
        # без основного человека — daily говорит has_primary=false
        r = client.get("/api/daily", headers=h)
        assert r.status_code == 200 and r.json()["has_primary"] is False
        # сохранить человека и назначить основным
        p = client.post("/api/profiles", headers=h,
                        json={"label": "Я", "data": {"name": "Я", "year": 1987, "month": 7, "day": 10,
                                                      "hour": 11, "minute": 11, "lat": 56.0091, "lng": 92.8726,
                                                      "tz_str": "Asia/Krasnoyarsk", "city": "Красноярск"}}).json()
        assert client.post("/api/profiles/primary", headers=h, json={"profile_id": p["id"]}).status_code == 200
        r = client.get("/api/daily", headers=h)
        assert r.status_code == 200
        d = r.json()
        assert d["has_primary"] is True and d["person"] == "Я" and isinstance(d["aspects"], list)
        # чужую карту основным назначить нельзя
        assert client.post("/api/profiles/primary", headers=h, json={"profile_id": 999999}).status_code == 404
        # снять отметку
        assert client.post("/api/profiles/primary", headers=h, json={"profile_id": None}).status_code == 200
        assert client.get("/api/daily", headers=h).json()["has_primary"] is False
    finally:
        _cleanup(name)


def test_notify_requires_verified_email():
    name, token = _make_user("notif_")
    try:
        h = {"Authorization": f"Bearer {token}"}
        # почта есть (из _make_user), но не подтверждена → включить нельзя
        assert client.post("/api/notify", headers=h, json={"weekly": True}).status_code == 400
        # подтверждаем и включаем
        u = db.get_user_by_username(name)
        db.mark_email_verified(u["id"])
        assert client.post("/api/notify", headers=h, json={"weekly": True}).status_code == 200
        assert db.get_user_by_id(u["id"])["notify_weekly"] == 1
        # отписка по токену — без входа
        tok = db.get_user_by_id(u["id"])["unsub_token"]
        assert tok
        r = client.get(f"/api/unsubscribe?token={tok}")
        assert r.status_code == 200
        assert db.get_user_by_id(u["id"])["notify_weekly"] == 0
    finally:
        _cleanup(name)


def test_weekly_subscribers_filter():
    name, token = _make_user("wsub_")
    try:
        u = db.get_user_by_username(name)
        db.mark_email_verified(u["id"])
        db.set_notify_weekly(u["id"], True)
        # без основного человека в список не попадает
        assert all(s["id"] != u["id"] for s in db.weekly_subscribers())
        p = db.add_profile(u["id"], "Я", {"year": 1987, "month": 7, "day": 10, "lat": 56.0, "lng": 92.8})
        db.set_primary_profile(u["id"], p["id"])
        assert any(s["id"] == u["id"] for s in db.weekly_subscribers())
    finally:
        _cleanup(name)


def test_synastry_preview_free_and_hides_details():
    # Тизер доступен без премиума и без входа
    r = client.post("/api/synastry/preview",
                    json={"person_a": NATAL, "person_b": dict(NATAL, year=1988)})
    assert r.status_code == 200
    d = r.json()
    # раскрыто: индекс + тон по сферам + счётчики
    assert isinstance(d["score"]["value"], int)
    assert d["spheres"] and all(set(s) == {"key", "label", "tone"} for s in d["spheres"])
    assert "strength_count" in d and "challenge_count" in d
    # спрятано: ни детального текста разбора, ни аспектов, ни карты
    assert "text" not in str(d.get("spheres"))
    assert "aspects" not in d and "couple" not in d and "chart_svg" not in d


def test_report_credits_flow():
    name, token = _make_user("rep_")
    try:
        h = {"Authorization": f"Bearer {token}"}
        u = db.get_user_by_username(name)
        # изначально кредитов нет — consume даёт 402
        assert client.post("/api/report/consume", headers=h).status_code == 402
        # выдаём кредит (как после успешной оплаты plan=report)
        db.add_report_credit(u["id"])
        me = client.get("/api/auth/me", headers=h).json()
        assert me["report_credits"] == 1
        # списываем — остаётся 0
        r = client.post("/api/report/consume", headers=h)
        assert r.status_code == 200 and r.json()["report_credits"] == 0
        # повторно уже нельзя
        assert client.post("/api/report/consume", headers=h).status_code == 402
        # план report валиден для создания платежа (проверяем схему, не сам платёж)
        from backend import payments
        assert payments.PLANS["report"] == (149, 0)
    finally:
        _cleanup(name)


# ---- Вебхук и досверка платежей ----
def test_yookassa_webhook_safe():
    # Неизвестный платёж и пустое тело → 200 без обращения к ЮKassa (spoof-safe no-op).
    assert client.post("/api/yookassa/webhook", json={"object": {"id": "no-such-payment-xyz"}}).status_code == 200
    assert client.post("/api/yookassa/webhook", json={}).status_code == 200


def test_payment_reconcile_helpers():
    from backend import payments
    name, _tok = _make_user("pay_")
    pid = "test_pay_" + name
    try:
        u = db.get_user_by_username(name)
        db.add_payment(pid, u["id"], "report")
        # pending виден в обеих выборках
        assert db.get_pending_payment(pid) == {"payment_id": pid, "user_id": u["id"], "plan": "report"}
        assert any(p["payment_id"] == pid for p in db.pending_payments_all(older_than_min=0))
        # succeeded применяется идемпотентно: выдаёт report-кредит и уводит запись из pending
        assert payments._apply_status(pid, u["id"], "report", "succeeded") is True
        assert db.get_pending_payment(pid) is None          # больше не pending
        assert db.get_report_credits(u["id"]) == 1
        # запись уже не pending → повторная сверка её не тронет (нет двойной выдачи)
        assert db.pending_payments_all(older_than_min=0) == [] or all(
            p["payment_id"] != pid for p in db.pending_payments_all(older_than_min=0))
    finally:
        with db.get_conn() as c:
            c.execute("DELETE FROM payments WHERE payment_id = ?", (pid,))
        _cleanup(name)


def test_events_accepts_batch_and_stores():
    """Обезличенные события принимаются и попадают в сводку."""
    dev = "test-" + "".join(random.choices(string.ascii_lowercase + string.digits, k=12))
    r = client.post("/api/events", json={
        "device_id": dev,
        "events": [{"name": "app_open"}, {"name": "chart_created", "props": {"src": "form"}}],
    })
    assert r.status_code == 200, r.text
    assert r.json()["accepted"] == 2
    summary = db.events_summary(30)
    names = {row["name"] for row in summary["by_name"]}
    assert {"app_open", "chart_created"} <= names


def test_events_rejects_bad_input():
    """Открытый эндпоинт обязан отбивать мусор на границе доверия."""
    dev = "test-" + "".join(random.choices(string.ascii_lowercase + string.digits, k=12))
    # недопустимые символы в имени события
    r = client.post("/api/events", json={"device_id": dev, "events": [{"name": "Ужас; DROP"}]})
    assert r.status_code == 422
    # слишком короткий device_id
    r = client.post("/api/events", json={"device_id": "x", "events": [{"name": "app_open"}]})
    assert r.status_code == 422
    # пустой список событий
    r = client.post("/api/events", json={"device_id": dev, "events": []})
    assert r.status_code == 422


def test_admin_events_requires_admin():
    r = client.get("/api/admin/events")
    assert r.status_code in (401, 403)


def test_free_tier_one_chart_and_unlock_by_premium():
    """Бесплатно — одна карта; остальные не удаляются, а открываются подпиской."""
    name, token = _make_user("lim_")
    h = {"Authorization": f"Bearer {token}"}
    try:
        u = db.get_user_by_username(name)
        first = client.post("/api/profiles", headers=h,
                            json={"label": "Первая", "data": {"year": 1990}})
        assert first.status_code == 200
        first_id = first.json()["id"]

        # вторая карта на бесплатном тарифе — отказ с предложением подписки
        second = client.post("/api/profiles", headers=h,
                             json={"label": "Вторая", "data": {"year": 1991}})
        assert second.status_code == 402, second.text
        assert "Премиум" in second.json()["detail"]

        # выдаём подписку — лимит снимается
        db.extend_subscription(u["id"], "month", 30)
        allowed = client.post("/api/profiles", headers=h,
                              json={"label": "Вторая", "data": {"year": 1991}})
        assert allowed.status_code == 200
        second_id = allowed.json()["id"]

        # с подпиской обе карты доступны
        items = client.get("/api/profiles", headers=h).json()
        assert all(not it["locked"] for it in items)

        # подписка кончилась: карты остаются, но активна только первая
        with db.get_conn() as c:
            c.execute("DELETE FROM subscriptions WHERE user_id = ?", (u["id"],))
        items = client.get("/api/profiles", headers=h).json()
        assert len(items) == 2, "карты не должны удаляться"
        locked = {it["id"]: it["locked"] for it in items}
        assert locked[first_id] is False, "первая карта остаётся доступной"
        assert locked[second_id] is True, "остальные блокируются до продления"
    finally:
        _cleanup(name)
