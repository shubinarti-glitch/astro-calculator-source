"""Load separately supplied editorial data without executing content as code."""
import json
from functools import lru_cache
from pathlib import Path

_PACKAGE = Path(__file__).resolve().parent.parent / "data" / "editorial" / "text-tables-v1.json"


def _decode(node):
    if not isinstance(node, dict) or set(node) != {"type", "value"}:
        raise ValueError("Invalid editorial data node")
    kind, value = node["type"], node["value"]
    if kind == "scalar" and (value is None or type(value) in (str, int, float, bool)):
        return value
    if kind in ("list", "tuple") and isinstance(value, list):
        items = [_decode(item) for item in value]
        return tuple(items) if kind == "tuple" else items
    if kind == "dict" and isinstance(value, list):
        result = {}
        for pair in value:
            if not isinstance(pair, list) or len(pair) != 2:
                raise ValueError("Invalid editorial dictionary entry")
            key, item = _decode(pair[0]), _decode(pair[1])
            if key in result:
                raise ValueError("Duplicate editorial dictionary key")
            result[key] = item
        return result
    raise ValueError("Invalid editorial data type")


@lru_cache(maxsize=1)
def _package():
    try:
        raw = json.loads(_PACKAGE.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise RuntimeError("Editorial data package is missing or invalid; configure it before starting the service") from error
    if not isinstance(raw, dict) or raw.get("schema") != 1 or not isinstance(raw.get("tables"), dict):
        raise ValueError("Unsupported editorial package schema")
    return raw["tables"]


def table(key):
    """Return a fresh mutable table, preserving tuples and non-string keys for CMS."""
    try:
        node = _package()[key]
    except KeyError as error:
        raise RuntimeError("Required editorial table is missing: " + key) from error
    return _decode(node)


@lru_cache(maxsize=1)
def _snippets():
    path = _PACKAGE.with_name("text-snippets-v1.json")
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise RuntimeError("Editorial snippets package is missing or invalid") from error
    if not isinstance(raw, dict) or raw.get("schema") != 1 or not isinstance(raw.get("texts"), dict):
        raise ValueError("Unsupported editorial snippets schema")
    if not all(isinstance(k, str) and isinstance(v, str) for k, v in raw["texts"].items()):
        raise ValueError("Editorial snippets must contain only text")
    return raw["texts"]


def text(key):
    try:
        return _snippets()[key]
    except KeyError as error:
        raise RuntimeError("Required editorial text is missing: " + key) from error


def validate_required():
    """Fail before serving requests if any lazy function text is missing."""
    manifest = json.loads(Path(__file__).with_name("editorial_required.json").read_text(encoding="utf-8"))
    if manifest.get("schema") != 1:
        raise RuntimeError("Unsupported editorial requirements manifest")
    for key in manifest["texts"]:
        text(key)
    for key in manifest["tables"]:
        table(key)
