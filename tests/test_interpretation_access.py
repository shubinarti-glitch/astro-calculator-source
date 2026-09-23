from backend import main


def test_brief_natal_limits_both_rendering_modes_without_changing_calculations():
    import copy
    planet = {
        "name": "Sun", "longitude": 123.456, "house_num": 6,
        "interp_sign": "sign " * 200, "interp_house": "house " * 200,
        "interp_plain": "plain " * 200,
        "interp_full": [{"label": "Reading", "text": "full " * 200}] * 3,
        "interp_full_plain": [{"label": "Reading", "text": "plain full " * 200}] * 3,
    }
    source = {"planets": [planet]}
    original = copy.deepcopy(source)
    result = main._brief_report(source, "natal")["planets"][0]
    for key in ("interp_sign", "interp_house", "interp_plain"):
        assert len(result[key]) <= 261
    for key in ("interp_full", "interp_full_plain"):
        assert len(result[key]) == 1
        assert len(result[key][0]["text"]) <= 261
    assert result["longitude"] == planet["longitude"]
    assert result["house_num"] == planet["house_num"]
    assert source == original


def test_paid_natal_retains_complete_alternative_text(monkeypatch):
    monkeypatch.setattr(main.db, "get_access_state", lambda uid: {"premium": True})
    source = {"planets": [{"interp_full_plain": [{"text": "word " * 500}]}]}
    result = main._protect_interpretations(source, "natal", "detailed", 1)
    assert result["planets"] == source["planets"]


def test_free_user_cannot_request_paid_modes(monkeypatch):
    monkeypatch.setattr(main.db, "get_access_state", lambda _uid: {"premium": False, "entitlements": []})
    assert main._effective_interpretation_mode("detailed", 1) == "brief"
    assert main._effective_interpretation_mode("technical", 1) == "brief"


def test_premium_and_professional_modes(monkeypatch):
    monkeypatch.setattr(
        main.db,
        "get_access_state",
        lambda uid: {"premium": True, "entitlements": ["professional_tools"] if uid == 2 else []},
    )
    assert main._effective_interpretation_mode(None, 1) == "detailed"
    assert main._effective_interpretation_mode("technical", 1) == "detailed"
    assert main._effective_interpretation_mode("technical", 2) == "technical"


def test_brief_report_is_reduced_without_mutating_source():
    source = {
        "summary": "word " * 200,
        "sphere_forecast": [{"text": "sphere " * 100} for _ in range(4)],
        "events": [{"text": "event " * 100} for _ in range(5)],
        "profection": {"full": "paid"},
        "progressed_moon": {"full": "paid"},
    }
    result = main._brief_report(source, "forecast")

    assert result["access_mode"] == "brief"
    assert len(result["sphere_forecast"]) == 2
    assert len(result["events"]) == 3
    assert "profection" not in result
    assert "progressed_moon" not in result
    assert len(source["sphere_forecast"]) == 4
    assert "profection" in source
