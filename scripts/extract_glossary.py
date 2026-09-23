"""Extract the historical GLOSSARY literal without executing JavaScript.

Usage: python scripts/extract_glossary.py path/to/original-app.js
Prints JSON to stdout; accepts only the specific ru/en string-pair grammar.
"""
import argparse
import json
import re
from pathlib import Path

STRING = r'"(?:[^"\\\x00-\x1f]|\\(?:["\\/bfnrt]|u[0-9a-fA-F]{4}))*"'
ENTRY = re.compile(
    r'\s*\{\s*ru\s*:\s*\[\s*(' + STRING + r')\s*,\s*(' + STRING
    + r')\s*\]\s*,\s*en\s*:\s*\[\s*(' + STRING + r')\s*,\s*('
    + STRING + r')\s*\]\s*\}\s*'
)


def extract(source):
    marker = "const GLOSSARY = ["
    if source.count(marker) != 1:
        raise ValueError("Expected exactly one GLOSSARY declaration")
    pos = source.index(marker) + len(marker)
    result = {"schema": 1, "ru": [], "en": []}
    while True:
        match = ENTRY.match(source, pos)
        if not match:
            raise ValueError("Unsupported glossary syntax (only literal string pairs allowed)")
        values = [json.loads(value) for value in match.groups()]
        result["ru"].append(values[:2])
        result["en"].append(values[2:])
        pos = match.end()
        if source[pos:pos + 1] == ',':
            pos += 1
            if re.match(r'\s*\];', source[pos:]):
                return result
        elif re.match(r'\s*\];', source[pos:]):
            return result
        else:
            raise ValueError("Expected comma or end of glossary")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    args = parser.parse_args()
    print(json.dumps(extract(args.source.read_text(encoding="utf-8-sig")), ensure_ascii=False, indent=2))
