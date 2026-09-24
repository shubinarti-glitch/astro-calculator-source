"""Isolated local QA: never use the account DB, SMTP or payment configuration."""
import argparse
import secrets
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["test", "serve"])
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()
    # db currently reads its key at import. Refuse import if that could create a production key.
    if not (ROOT / "data/secret.key").is_file():
        raise SystemExit("Existing local key required for safe module import; no key will be created")
    with tempfile.TemporaryDirectory(prefix="astrosmap-mobile-qa-") as directory:
        from backend import db, emailer, payments, content_store
        temporary = Path(directory)
        db.DATA_DIR = temporary
        db.DB_PATH = temporary / "qa.db"
        db.SECRET_FILE = temporary / "unused.key"
        db._SECRET = secrets.token_bytes(32)
        emailer.CONFIG_PATH = temporary / "smtp.json"
        emailer.send = lambda *a, **k: None
        payments.CONFIG_FILE = temporary / "payments.json"
        content_store.OVERRIDES_FILE = str(temporary / "overrides.json")
        from backend.main import app
        if args.mode == "test":
            # Existing API tests assume an administrator already exists, as in production.
            # Seed only the disposable database so ordinary test users stay non-admin.
            db.init_db()
            db.create_user("qa-seeded-admin", secrets.token_urlsafe(32))
            import pytest
            return pytest.main(["tests/test_api.py", "tests/test_mobile_reports.py", "tests/test_mobile_editorial.py", "-q", "--tb=short"])
        import uvicorn
        uvicorn.run(app, host="127.0.0.1", port=args.port, log_level="warning")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
