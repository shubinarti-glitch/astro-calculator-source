"""Serve the private Android editorial package; never copy it into app sources."""
import hashlib
import json
from pathlib import Path
from fastapi import APIRouter, HTTPException
from fastapi.responses import JSONResponse

PACKAGE = Path(__file__).resolve().parents[1] / "data/editorial/android-v1.json"
router = APIRouter()


def load_package(path: Path):
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or data.get("schemaVersion") != 1:
        raise ValueError("Invalid schema")
    if set(data) != {"schemaVersion", "cards", "phaseAdvice", "moonMood"}:
        raise ValueError("Invalid fields")
    for key, size, width in (("cards", 78, 7), ("phaseAdvice", 8, 3), ("moonMood", 12, 3)):
        rows = data[key]
        if not isinstance(rows, list) or len(rows) != size:
            raise ValueError("Invalid table")
        if any(not isinstance(r, list) or len(r) != width or
               any(not isinstance(v, str) or not v.strip() or len(v) > 20000 for v in r) for r in rows):
            raise ValueError("Invalid row")
        if len({r[0] for r in rows}) != size:
            raise ValueError("Duplicate ID")
    digest = hashlib.sha256("\n".join(r[0] for r in data["cards"]).encode()).hexdigest()
    if digest != "25e07797a22b4977a67a24113c22c12d3a22aedc85636e605a4d51e49c8b5c53":
        raise ValueError("Invalid card IDs")
    return data


@router.get("/api/mobile/editorial/v1")
def editorial():
    try:
        data = load_package(PACKAGE)
    except (OSError, ValueError, TypeError, KeyError):
        raise HTTPException(503, "Editorial content unavailable") from None
    return JSONResponse(data, headers={"Cache-Control": "no-store"})
