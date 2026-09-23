"""Mechanical extraction preserving expression evaluation and literal text exactly.

Scope is explicit editorial backend modules. No eval or executable templates.
Docstrings and source comments are preserved. Verify output snapshots afterwards.
"""
import ast
import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODULES = ("interpretations", "transit_english", "vedic", "seo")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--module", action="append", choices=(*MODULES, "astrology"))
    args = parser.parse_args()
    modules = tuple(args.module) if args.module else MODULES
    target = ROOT / "data" / "editorial" / "text-snippets-v1.json"
    if target.exists():
        if not args.module:
            raise SystemExit("Existing package: specify a new module explicitly")
        original_package = target.read_bytes()
        raw = json.loads(original_package)
        texts = raw["texts"]
    else:
        original_package = None
        texts = {}
    plans = {}
    for module in modules:
        path = ROOT / "backend" / (module + ".py")
        source = path.read_bytes()
        if b"from .editorial_data import text as _editorial_text" in source:
            raise SystemExit(f"Module already extracted: {module}")
        tree = ast.parse(source)
        lines = source.splitlines(keepends=True)
        offsets, offset = [], 0
        for line in lines:
            offsets.append(offset)
            offset += len(line)
        docstrings = set()
        for node in ast.walk(tree):
            if isinstance(node, (ast.Module, ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
                if node.body and isinstance(node.body[0], ast.Expr) and isinstance(node.body[0].value, ast.Constant):
                    docstrings.add(id(node.body[0].value))

        def eligible(node):
            return (isinstance(node, ast.Constant) and isinstance(node.value, str)
                    and id(node) not in docstrings and bool(re.search(r"\s", node.value))
                    and bool(re.search(r"[A-Za-zА-Яа-яЁё]", node.value)))

        def lookup(value):
            key = module + "." + hashlib.sha256(value.encode("utf-8")).hexdigest()
            texts[key] = value
            return ast.Call(func=ast.Name(id="_editorial_text", ctx=ast.Load()),
                            args=[ast.Constant(value=key)], keywords=[])

        class FstringRewriter(ast.NodeTransformer):
            def visit_JoinedStr(self, node):
                result = []
                for item in node.values:
                    if eligible(item):
                        result.append(ast.FormattedValue(value=lookup(item.value), conversion=-1))
                    else:
                        result.append(self.visit(item))
                node.values = result
                return node

            def visit_Constant(self, node):
                return lookup(node.value) if eligible(node) else node

        edits = []
        def walk(node):
            if isinstance(node, ast.JoinedStr):
                replacement = ast.unparse(ast.fix_missing_locations(FstringRewriter().visit(node)))
            elif eligible(node):
                replacement = ast.unparse(lookup(node.value))
            else:
                for child in ast.iter_child_nodes(node):
                    walk(child)
                return
            start = offsets[node.lineno - 1] + node.col_offset
            end = offsets[node.end_lineno - 1] + node.end_col_offset
            edits.append((start, end, replacement.encode("utf-8")))
        walk(tree)
        updated = source
        for start, end, replacement in sorted(edits, reverse=True):
            updated = updated[:start] + replacement + updated[end:]
        # Insert import after docstring/future imports, leaving encoding and comments intact.
        parsed = ast.parse(updated)
        anchor = 0
        for node in parsed.body:
            if (isinstance(node, ast.Expr) and isinstance(node.value, ast.Constant) and isinstance(node.value.value, str)) or (isinstance(node, ast.ImportFrom) and node.module == "__future__"):
                anchor = node.end_lineno
            else:
                break
        updated_lines = updated.splitlines(keepends=True)
        updated_lines.insert(anchor, b"from .editorial_data import text as _editorial_text\n")
        updated = b"".join(updated_lines)
        compile(updated, str(path), "exec")
        plans[path] = updated
    target.parent.mkdir(parents=True, exist_ok=True)
    if original_package is not None:
        backup = target.with_name("text-snippets-v1.before-" + "-".join(modules) + ".json")
        with backup.open("xb") as handle:
            handle.write(original_package)
    with target.open("w" if original_package is not None else "x", encoding="utf-8") as handle:
        json.dump({"schema": 1, "texts": texts}, handle, ensure_ascii=False, indent=2)
    for path, updated in plans.items():
        path.write_bytes(updated)
    print(f"Extracted {len(texts)} exact snippets in {len(plans)} modules")


if __name__ == "__main__":
    main()
