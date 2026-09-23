"""Capture/compare private deterministic interpretation output before migration."""
import argparse
import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
from backend import interpretations as I


def outputs(extended=False):
    planets = ("Sun", "Moon", "Mercury", "Venus", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune", "Pluto")
    signs = ("Ari", "Tau", "Gem", "Can", "Leo", "Vir", "Lib", "Sco", "Sag", "Cap", "Aqu", "Pis")
    aspects = ("conjunction", "sextile", "square", "trine", "opposition")
    result = {}
    def record(key, value):
        raw = json.dumps(value, ensure_ascii=False, sort_keys=True).encode("utf-8")
        result[key] = hashlib.sha256(raw).hexdigest()
    for lang in ("ru", "en"):
        for planet in planets:
            for sign in signs:
                for retro in (False, True):
                    record(f"sign/{lang}/{planet}/{sign}/{retro}", I.interpret_sign_full(planet, sign, lang, retro=retro))
            for house in range(1, 13):
                record(f"house/{lang}/{planet}/{house}", I.interpret_house(planet, house, lang))
            for other in planets:
                if planet == other:
                    continue
                for aspect in aspects:
                    record(f"aspect/{lang}/{planet}/{other}/{aspect}", I.interpret_aspect(planet, aspect, other, lang))
                    record(f"progression/{lang}/{planet}/{other}/{aspect}", I.interpret_progression(planet, aspect, other, lang))
    if extended:
        from backend import vedic as V
        points = list(dict.fromkeys(list(I._TRANSIT_DEEP_NAME) + list(I._TRANSIT_ANGLES)))
        for lang in ("ru", "en"):
            for moving in points:
                for target in points:
                    for aspect in aspects:
                        for orbit, phase in ((0.5, "applying"), (2.0, "separating")):
                            record(f"transit/{lang}/{moving}/{target}/{aspect}/{phase}",
                                   I.interpret_transit(moving, aspect, target, lang, orbit, phase))
            for month in (2, 6, 9, 12):
                for zone, lat, lng in (("UTC", 0, 0), ("Europe/Moscow", 55.75, 37.62),
                                       ("America/New_York", 40.71, -74.0)):
                    record(f"vedic/{lang}/{month}/{zone}", V.vedic_calendar(2026, month, lat, lng, zone, lang=lang))
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("capture", "compare"))
    parser.add_argument("file", type=Path)
    parser.add_argument("--extended", action="store_true")
    args = parser.parse_args()
    result = outputs(args.extended)
    if args.mode == "capture":
        args.file.parent.mkdir(parents=True, exist_ok=True)
        with args.file.open("x", encoding="utf-8") as handle:
            json.dump(result, handle, ensure_ascii=False, indent=2)
        print(f"Captured {len(result)} output fingerprints")
    else:
        expected = json.loads(args.file.read_text(encoding="utf-8"))
        changed = [key for key in expected.keys() | result.keys() if expected.get(key) != result.get(key)]
        if changed:
            print("Changed output keys:", *sorted(changed)[:20], sep="\n")
            raise SystemExit(1)
        print(f"PASS: {len(result)} outputs match")


if __name__ == "__main__":
    main()
