"""Public glossary API; its bilingual source is stored outside static assets.

Integration: from .editorial_glossary import router as glossary_router
then app.include_router(glossary_router) before any catch-all routes.
"""
import json
from functools import lru_cache
from pathlib import Path
from typing import Literal

from fastapi import APIRouter, HTTPException, Response

router = APIRouter()
GLOSSARY_FILE = Path(__file__).resolve().parent.parent / "data/editorial/glossary-v1.json"


@lru_cache(maxsize=2)
def _load(path: Path, mtime_ns: int, size: int):
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or data.get("schema") != 1:
        raise ValueError("Unsupported glossary schema")
    for lang in ("ru", "en"):
        entries = data.get(lang)
        if not isinstance(entries, list) or not entries:
            raise ValueError("Missing glossary language")
        if not all(isinstance(row, list) and len(row) == 2
                   and all(isinstance(value, str) for value in row) for row in entries):
            raise ValueError("Invalid glossary entry")
    if len(data["ru"]) != len(data["en"]):
        raise ValueError("Glossary language counts differ")
    return data


@router.get("/api/glossary")
def glossary(response: Response, lang: Literal["ru", "en"] = "ru"):
    try:
        stat = GLOSSARY_FILE.stat()
        data = _load(GLOSSARY_FILE, stat.st_mtime_ns, stat.st_size)
    except (OSError, ValueError):
        raise HTTPException(status_code=503, detail="Glossary temporarily unavailable",
                            headers={"Cache-Control": "no-store"}) from None
    response.headers["Cache-Control"] = "public, max-age=300"
    response.headers["Content-Language"] = lang
    return data[lang]
