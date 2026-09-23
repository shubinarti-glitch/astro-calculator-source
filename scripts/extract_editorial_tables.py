"""One-time mechanical extraction of explicitly selected literal tables.

Run locally before distributing modified code. Never publishes data or Git history.
"""
import ast
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SELECTED = {
    "transit_english": {"ASPECTS", "TIMING", "ROLES"},
    "vedic": {"NAKSHATRA_GUIDE", "_PAKSHA_ADVICE", "_DAY_ADVICE"},
}


def encode(value):
    if isinstance(value, dict):
        return {"type": "dict", "value": [[encode(k), encode(v)] for k, v in value.items()]}
    if isinstance(value, (list, tuple)):
        return {"type": "tuple" if isinstance(value, tuple) else "list", "value": [encode(v) for v in value]}
    if value is None or type(value) in (str, int, float, bool):
        return {"type": "scalar", "value": value}
    raise TypeError(type(value))


def main():
    output = ROOT / "data" / "editorial" / "text-tables-v1.json"
    if output.exists():
        raise SystemExit("Package already exists; refusing to overwrite originals")
    tables, changes = {}, {}
    for module, names in SELECTED.items():
        path = ROOT / "backend" / (module + ".py")
        source = path.read_text(encoding="utf-8")
        lines = source.splitlines(keepends=True)
        found, edits = set(), []
        for node in ast.parse(source).body:
            if isinstance(node, ast.Assign) and len(node.targets) == 1 and isinstance(node.targets[0], ast.Name):
                name = node.targets[0].id
                if name in names:
                    value = ast.literal_eval(node.value)
                    key = module + "." + name
                    tables[key] = encode(value)
                    found.add(name)
                    edits.append((node.lineno - 1, node.end_lineno,
                                  f'{name} = __import__("backend.editorial_data", fromlist=["table"]).table("{key}")\n'))
        if found != names:
            raise RuntimeError(f"Missing expected literal tables: {module}: {names - found}")
        for start, end, replacement in reversed(edits):
            lines[start:end] = [replacement]
        updated = "".join(lines)
        ast.parse(updated)
        changes[path] = updated
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("x", encoding="utf-8") as handle:
        json.dump({"schema": 1, "tables": tables}, handle, ensure_ascii=False, indent=2)
    for path, source in changes.items():
        path.write_text(source, encoding="utf-8")
    print(f"Extracted {len(tables)} tables to private package; source calculations preserved")


if __name__ == "__main__":
    main()
