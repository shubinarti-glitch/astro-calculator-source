"""Opt-in mobile feedback. Separate bounded storage; no account database changes."""
import json
import os
import threading
import time
from pathlib import Path
from typing import Literal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, ConfigDict, Field, ValidationError
from starlette.concurrency import run_in_threadpool


class MobileReport(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    id: UUID
    description: str = Field(min_length=5, max_length=2000)
    app_version: str = Field(pattern=r"^[0-9A-Za-z.+_-]{1,40}$")
    version_code: int = Field(ge=1, le=2147483647)
    store: Literal["googleplay", "standard"]
    android_api: int = Field(ge=26, le=100)
    consent: Literal[True]


class ReportStore:
    """Single-process store (same deployment model as the auth rate limiter).

    Raw IPs exist only in bounded in-memory rate buckets, never in saved reports.
    Retention is enforced on each read/write; at most 1000 records are retained.
    """
    def __init__(self, path: Path):
        self.path = path
        self.lock = threading.Lock()
        self.rates = {}

    def _read(self):
        if not self.path.exists():
            return []
        return json.loads(self.path.read_text(encoding="utf-8"))

    def _save(self, rows):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.path.with_suffix(".tmp")
        temporary.write_text(json.dumps(rows, ensure_ascii=False), encoding="utf-8")
        os.replace(temporary, self.path)

    def _live(self):
        cutoff = time.time() - 30 * 86400
        return [r for r in self._read() if r["received_at"] >= cutoff][-1000:]

    def submit(self, report: MobileReport, ip: str):
        with self.lock:
            now = time.time()
            self.rates = {k: [t for t in v if t > now - 3600]
                          for k, v in self.rates.items() if v and v[-1] > now - 3600}
            bucket = self.rates.get(ip, [])
            if len(bucket) >= 5 or (ip not in self.rates and len(self.rates) >= 10000):
                raise HTTPException(429, "Too many reports", headers={"Retry-After": "3600"})
            self.rates[ip] = bucket + [now]
            rows = self._live()
            report_id = str(report.id)
            if not any(r["id"] == report_id for r in rows):
                rows.append({**report.model_dump(mode="json"), "received_at": now})
            self._save(rows[-1000:])
            return {"id": report_id}

    def list_recent(self):
        with self.lock:
            rows = self._live()
            self._save(rows)
            return list(reversed(rows))[:100]


def create_router(store: ReportStore, require_admin):
    router = APIRouter()

    @router.post("/api/mobile/reports", status_code=201)
    async def submit(request: Request):
        body = bytearray()
        async for chunk in request.stream():
            if len(body) + len(chunk) > 16384:
                raise HTTPException(413, "Report is too large")
            body.extend(chunk)
        try:
            report = MobileReport.model_validate_json(body)
        except (ValidationError, ValueError):
            # Never reflect submitted descriptions, credentials or unknown fields.
            raise HTTPException(422, "Invalid report") from None
        try:
            return await run_in_threadpool(store.submit, report,
                                          request.client.host if request.client else "unknown")
        except (OSError, ValueError):
            raise HTTPException(503, "Report storage unavailable") from None

    @router.get("/api/admin/mobile-reports", dependencies=[Depends(require_admin)])
    async def list_reports():
        try:
            rows = await run_in_threadpool(store.list_recent)
        except (OSError, ValueError):
            raise HTTPException(503, "Report storage unavailable") from None
        from fastapi.responses import JSONResponse
        return JSONResponse({"reports": rows}, headers={"Cache-Control": "no-store"})

    return router
