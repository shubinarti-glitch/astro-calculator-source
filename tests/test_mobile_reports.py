import json
import time
from uuid import uuid4

import pytest
from fastapi import FastAPI, Header, HTTPException
from fastapi.testclient import TestClient
from backend.mobile_reports import ReportStore, create_router


@pytest.fixture
def reports(tmp_path):
    store = ReportStore(tmp_path / "reports.json")
    def admin(authorization: str = Header(default="")):
        if authorization != "Bearer test-admin":
            raise HTTPException(403)
    app = FastAPI()
    app.include_router(create_router(store, admin))
    return TestClient(app), store


def payload():
    return dict(id=str(uuid4()), description="Test steps only", app_version="1.7.3",
                version_code=11, store="googleplay", android_api=36, consent=True)


def test_submit_and_retry(reports):
    client, store = reports
    body = payload()
    for _ in range(2):
        response = client.post("/api/mobile/reports", json=body)
        assert response.status_code == 201
        assert response.json() == {"id": body["id"]}
    rows = store.list_recent()
    assert len(rows) == 1
    assert set(rows[0]) == set(body) | {"received_at"}


def test_admin_access(reports):
    client, _ = reports
    assert client.get("/api/admin/mobile-reports").status_code == 403
    response = client.get("/api/admin/mobile-reports", headers={"Authorization": "Bearer test-admin"})
    assert response.status_code == 200
    assert response.headers["cache-control"] == "no-store"


@pytest.mark.parametrize("change", [{"consent": False}, {"password": "secret"},
                                  {"description": " "}, {"description": "x" * 2001},
                                  {"store": "unknown"}, {"android_api": 1}])
def test_validation_does_not_echo_input(reports, change):
    client, store = reports
    response = client.post("/api/mobile/reports", json={**payload(), **change})
    assert response.status_code == 422
    assert response.json() == {"detail": "Invalid report"}
    assert not store.path.exists()


def test_limits(reports):
    client, _ = reports
    assert client.post("/api/mobile/reports", content=b"x" * 16385).status_code == 413
    for _ in range(5):
        assert client.post("/api/mobile/reports", json=payload()).status_code == 201
    assert client.post("/api/mobile/reports", json=payload()).status_code == 429


def test_retention(reports):
    _, store = reports
    store.path.write_text(json.dumps([{**payload(), "received_at": time.time() - 31 * 86400}]))
    assert store.list_recent() == []
    assert json.loads(store.path.read_text()) == []


def test_storage_failure_is_not_success(reports, monkeypatch):
    client, store = reports
    def fail(rows):
        raise OSError("private server path")
    monkeypatch.setattr(store, "_save", fail)
    response = client.post("/api/mobile/reports", json=payload())
    assert response.status_code == 503
    assert "private server path" not in response.text


def test_capacity_is_bounded(reports):
    client, store = reports
    rows = [{**payload(), "received_at": time.time()} for _ in range(1000)]
    store.path.write_text(json.dumps(rows))
    assert client.post("/api/mobile/reports", json=payload()).status_code == 201
    saved = json.loads(store.path.read_text())
    assert len(saved) == 1000
    assert saved[0]["id"] == rows[1]["id"]
