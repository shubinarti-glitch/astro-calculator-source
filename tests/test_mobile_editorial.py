import json
from fastapi import FastAPI
from fastapi.testclient import TestClient
from backend import mobile_editorial


def test_unavailable_does_not_reveal_paths(tmp_path, monkeypatch):
    monkeypatch.setattr(mobile_editorial, "PACKAGE", tmp_path / "private.json")
    app = FastAPI()
    app.include_router(mobile_editorial.router)
    response = TestClient(app).get("/api/mobile/editorial/v1")
    assert response.status_code == 503
    assert response.json() == {"detail": "Editorial content unavailable"}


def test_invalid_package_is_rejected(tmp_path, monkeypatch):
    path = tmp_path / "invalid.json"
    path.write_text(json.dumps({"schemaVersion": 1}))
    monkeypatch.setattr(mobile_editorial, "PACKAGE", path)
    app = FastAPI()
    app.include_router(mobile_editorial.router)
    assert TestClient(app).get("/api/mobile/editorial/v1").status_code == 503


def test_package_contract_and_no_cache(tmp_path, monkeypatch):
    majors = "fool magician priestess empress emperor hierophant lovers chariot strength hermit wheel justice hanged death temperance devil tower star moon sun judgement world".split()
    ids = [f"major_{i:02}_{name}" for i, name in enumerate(majors)]
    ids += [f"{suit}_{i:02}" for suit in ("wands", "cups", "swords", "pents") for i in range(1, 15)]
    data = {"schemaVersion": 1, "cards": [[key] + ["test"] * 6 for key in ids],
            "phaseAdvice": [[str(i), "test", "test"] for i in range(8)],
            "moonMood": [[str(i), "test", "test"] for i in range(12)]}
    path = tmp_path / "synthetic.json"
    path.write_text(json.dumps(data))
    monkeypatch.setattr(mobile_editorial, "PACKAGE", path)
    assert mobile_editorial.load_package(path) == data
    assert len(data["cards"]) == 78
    app = FastAPI()
    app.include_router(mobile_editorial.router)
    response = TestClient(app).get("/api/mobile/editorial/v1")
    assert response.status_code == 200
    assert response.headers["cache-control"] == "no-store"
    assert response.json() == data
