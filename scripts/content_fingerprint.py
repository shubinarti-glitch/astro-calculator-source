"""Record local SHA-256 fingerprints of editorial originals (not proof of authorship).

Does not include databases, credentials, or the text itself in the manifest.
"""
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path


def main():
    root = Path(__file__).resolve().parent.parent
    files = set()
    for name in ("authored_content.json", "authored_transit_content.json", "content_overrides.json"):
        path = root / "data" / name
        if path.is_file():
            files.add(path)
    files.update((root / "data" / "transit_en").glob("*.json"))
    for name in ("interpretations.py", "transit_english.py", "vedic.py"):
        files.add(root / "backend" / name)
    stamp = datetime.now(timezone.utc)
    records = []
    for path in sorted(files):
        content = path.read_bytes()
        records.append({"path": path.relative_to(root).as_posix(), "bytes": len(content),
                        "sha256": hashlib.sha256(content).hexdigest()})
    output = root / "data" / "content_evidence"
    output.mkdir(exist_ok=True)
    target = output / (stamp.strftime("%Y%m%dT%H%M%S%fZ") + ".json")
    with target.open("x", encoding="utf-8") as handle:
        json.dump({"recorded_at_utc": stamp.isoformat(),
                   "note": "Local integrity record; not an independent timestamp or proof of authorship.",
                   "files": records}, handle, ensure_ascii=False, indent=2)
    # Verify the saved manifest against originals before reporting success.
    saved = json.loads(target.read_text(encoding="utf-8"))
    for record in saved["files"]:
        actual = hashlib.sha256((root / record["path"]).read_bytes()).hexdigest()
        if actual != record["sha256"]:
            raise RuntimeError("Content changed during verification: " + record["path"])
    print(f"Verified {len(records)} fingerprints: {target}")


if __name__ == "__main__":
    main()
