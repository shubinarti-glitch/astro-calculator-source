"""Contracts that prevent missing private content and accidental code extraction."""
import ast
import json
from pathlib import Path
import zipfile

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from backend import editorial_data as E
from backend import editorial_glossary as G

ROOT = Path(__file__).resolve().parents[1]


def test_required_manifest_covers_all_backend_references():
    found = {"texts": set(), "tables": set()}
    for path in (ROOT / "backend").glob("*.py"):
        for node in ast.walk(ast.parse(path.read_bytes())):
            if isinstance(node, ast.Call) and isinstance(node.func, ast.Name):
                kind = {"_editorial_text": "texts", "_editorial_table": "tables"}.get(node.func.id)
                if kind:
                    assert len(node.args) == 1 and isinstance(node.args[0], ast.Constant)
                    found[kind].add(node.args[0].value)
    manifest = json.loads((ROOT / "backend/editorial_required.json").read_text())
    assert set(manifest["texts"]) == found["texts"]
    assert set(manifest["tables"]) == found["tables"]
    E.validate_required()


def test_missing_lazy_text_is_caught_at_startup(monkeypatch):
    texts = dict(E._snippets())
    key = next(iter(texts))
    # Select a required key, not an unused historical entry in the package.
    key = json.loads((ROOT / "backend/editorial_required.json").read_text())["texts"][-1]
    texts.pop(key)
    monkeypatch.setattr(E, "_snippets", lambda: texts)
    with pytest.raises(RuntimeError, match="Required editorial text is missing"):
        E.validate_required()


def test_dictionary_keys_remain_code_literals():
    for module in ("interpretations", "transit_english", "vedic", "seo", "astrology"):
        for node in ast.walk(ast.parse((ROOT / "backend" / (module + ".py")).read_bytes())):
            if isinstance(node, ast.Dict):
                for key in node.keys:
                    assert not (isinstance(key, ast.Call) and isinstance(key.func, ast.Name)
                                and key.func.id == "_editorial_text"), module


@pytest.mark.parametrize("lang", ["ru", "en"])
def test_glossary_returns_only_requested_language(lang, tmp_path, monkeypatch):
    path = tmp_path / "glossary.json"
    path.write_text(json.dumps({"schema": 1, "ru": [["Термин", "Описание"]],
                                "en": [["Term", "Description"]]}), encoding="utf-8")
    monkeypatch.setattr(G, "GLOSSARY_FILE", path)
    app = FastAPI()
    app.include_router(G.router)
    response = TestClient(app).get("/api/glossary", params={"lang": lang})
    assert response.status_code == 200
    assert response.headers["content-language"] == lang
    assert response.json() == ([["Term", "Description"]] if lang == "en" else [["Термин", "Описание"]])


def test_glossary_missing_data_is_explicit(tmp_path, monkeypatch):
    monkeypatch.setattr(G, "GLOSSARY_FILE", tmp_path / "missing.json")
    app = FastAPI()
    app.include_router(G.router)
    response = TestClient(app).get("/api/glossary")
    assert response.status_code == 503
    assert response.headers["cache-control"] == "no-store"
    assert str(tmp_path) not in response.text


def test_glossary_matches_private_original():
    from scripts.extract_glossary import extract
    original = ROOT / "data/content_evidence/20260922T052929688291Z/text-originals.zip"
    if not original.exists():
        pytest.skip("Private migration baseline not installed")
    with zipfile.ZipFile(original) as archive:
        before = extract(archive.read("frontend/js/app.js").decode("utf-8-sig"))
    after = json.loads(G.GLOSSARY_FILE.read_text(encoding="utf-8"))
    assert after == before
