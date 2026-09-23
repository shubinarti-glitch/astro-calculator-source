"""Private, non-destructive backup and inventory of potential editorial sources.

Candidates are not a copyright classification. No databases or credentials are read.
"""
import hashlib
import json
import re
import subprocess
import zipfile
from datetime import datetime, timezone
from pathlib import Path


def main():
    root = Path(__file__).resolve().parent.parent
    candidates = set()
    for folder in ("backend", "frontend", "android/app/src/main", "android/app/src/googleplay"):
        for path in (root / folder).rglob("*"):
            if path.is_file() and path.suffix in {".py", ".js", ".html", ".kt", ".xml", ".json"}:
                if "vendor" not in path.parts and "__pycache__" not in path.parts:
                    candidates.add(path)
    for name in ("authored_content.json", "authored_transit_content.json", "content_overrides.json"):
        path = root / "data" / name
        if path.is_file():
            candidates.add(path)
    candidates.update((root / "data" / "transit_en").glob("*.json"))
    public = root / ".source-publish-20260922"
    ref = "codex/clean-public-source"
    tracked = set(subprocess.check_output(
        ["git", "-C", str(public), "ls-tree", "-r", "--name-only", ref], encoding="utf-8"
    ).splitlines())
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    out = root / "data" / "content_evidence" / stamp
    out.mkdir(parents=True, exist_ok=False)
    records = []
    archive = out / "text-originals.zip"
    with zipfile.ZipFile(archive, "x", compression=zipfile.ZIP_DEFLATED) as bundle:
        for path in sorted(candidates):
            name = path.relative_to(root).as_posix()
            raw = path.read_bytes()
            source = raw.decode("utf-8-sig", errors="replace")
            # Only line numbers are recorded; the manifest does not repeat text.
            lines = [i for i, line in enumerate(source.splitlines(), 1)
                     if len(line.strip()) >= 100 and len(re.findall(r"[A-Za-zА-Яа-яЁё]+", line)) >= 12]
            records.append({"path": name, "sha256": hashlib.sha256(raw).hexdigest(),
                            "bytes": len(raw), "public_snapshot": name in tracked,
                            "long_text_candidate_lines": lines})
            bundle.writestr(name, raw)
    with zipfile.ZipFile(archive) as bundle:
        assert bundle.testzip() is None, "Archive CRC verification failed"
        for record in records:
            assert hashlib.sha256(bundle.read(record["path"])).hexdigest() == record["sha256"]
    manifest = {"created_utc": stamp, "public_ref": ref,
                "limitations": "Heuristic inventory, not a complete semantic or legal audit. Local snapshot, not live GitHub. Images/PDF binaries are not in this text backup.",
                "archive_sha256": hashlib.sha256(archive.read_bytes()).hexdigest(), "files": records}
    (out / "inventory.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    private = sum(not x["public_snapshot"] for x in records)
    suspects = sum(bool(x["long_text_candidate_lines"]) for x in records)
    print(json.dumps({"folder": str(out), "files_backed_up": len(records),
                      "not_in_public_snapshot": private, "files_needing_text_review": suspects,
                      "archive_verified": True}, ensure_ascii=True))


if __name__ == "__main__":
    main()
