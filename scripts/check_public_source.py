"""Reject tracked private/editorial files in a source-publication checkout.

Usage: python scripts/check_public_source.py PATH
This checks the index, not just ignore rules. It does not certify licensing.
"""
import subprocess
import sys
from pathlib import PurePosixPath


def prohibited(name):
    path = PurePosixPath(name)
    parts = path.parts
    return (
        name.startswith(("data/", "frontend/media/", "android/store/",
                         ".claude/", ".codex/", ".source-publish-", "release_backups/"))
        or path.name in {"secret.key", "signing.properties", "local.properties", "yookassa.json"}
        or path.name == ".env" or path.name.startswith(".env.")
        or path.suffix.lower() in {".jks", ".keystore", ".pem", ".apk", ".aab"}
        or (path.suffix.lower() in {".db", ".sqlite", ".sqlite3"}
            and name != "android/app/src/main/assets/db/cities.db")
    )


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: check_public_source.py PATH")
    result = subprocess.run(["git", "-C", sys.argv[1], "ls-files", "-z"],
                            check=True, stdout=subprocess.PIPE)
    names = result.stdout.decode("utf-8").split("\0")
    blocked = [name for name in names if name and prohibited(name)]
    for name in blocked:
        print("BLOCKED:", name)
    if blocked:
        raise SystemExit(1)
    print("PASS: no prohibited paths in the index. Embedded content and history require separate review.")


if __name__ == "__main__":
    main()
