"""Restore structural literals and stable dictionary keys after mechanical extraction."""
import ast
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODULES = ("interpretations", "transit_english", "vedic", "seo", "astrology")


def main():
    package = json.loads((ROOT / "data/editorial/text-snippets-v1.json").read_text(encoding="utf-8"))["texts"]
    def key(node):
        if (isinstance(node, ast.Call) and isinstance(node.func, ast.Name)
                and node.func.id == "_editorial_text" and len(node.args) == 1
                and isinstance(node.args[0], ast.Constant)):
            return node.args[0].value
        return None
    technical = set()
    sources = {}
    for module in MODULES:
        path = ROOT / "backend" / (module + ".py")
        source = path.read_bytes()
        tree = ast.parse(source)
        sources[path] = source
        for node in ast.walk(tree):
            if isinstance(node, ast.Dict):
                for k in node.keys:
                    if key(k):
                        technical.add(package[key(k)])
            if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "_STYLE" for t in node.targets):
                if key(node.value):
                    technical.add(package[key(node.value)])
    # Dictionary keys remain stable wherever used, including lookup arguments.
    def restore(node):
        k = key(node)
        if not k:
            return False
        value = package[k]
        return value in technical or bool(re.search(r"<[!/?A-Za-z]|\bxmlns=", value))
    class Restore(ast.NodeTransformer):
        def visit_JoinedStr(self, node):
            self.generic_visit(node)
            values = []
            for item in node.values:
                if (isinstance(item, ast.FormattedValue) and item.conversion == -1
                        and item.format_spec is None and isinstance(item.value, ast.Constant)
                        and isinstance(item.value.value, str)):
                    item = ast.Constant(value=item.value.value)
                if values and isinstance(item, ast.Constant) and isinstance(values[-1], ast.Constant):
                    values[-1].value += item.value
                else:
                    values.append(item)
            node.values = values
            return node

        def visit_Call(self, node):
            return ast.copy_location(ast.Constant(value=package[key(node)]), node) if restore(node) else self.generic_visit(node)
    count = 0
    for path, source in sources.items():
        offsets, total = [], 0
        for line in source.splitlines(keepends=True):
            offsets.append(total)
            total += len(line)
        edits = []
        def walk(node):
            nonlocal count
            if isinstance(node, ast.JoinedStr):
                n = sum(restore(c) for c in ast.walk(node))
                if not n:
                    return
                count += n
                replacement = ast.unparse(ast.fix_missing_locations(Restore().visit(node)))
            elif restore(node):
                count += 1
                replacement = repr(package[key(node)])
            else:
                for child in ast.iter_child_nodes(node):
                    walk(child)
                return
            edits.append((offsets[node.lineno-1]+node.col_offset,
                          offsets[node.end_lineno-1]+node.end_col_offset, replacement.encode("utf-8")))
        walk(ast.parse(source))
        for start, end, value in sorted(edits, reverse=True):
            source = source[:start] + value + source[end:]
        try:
            compile(source, str(path), "exec")
        except SyntaxError:
            (ROOT / "data/content_evidence" / (path.stem + "-refine-debug.py")).write_bytes(source)
            raise
        path.write_bytes(source)
    required = {"schema": 1, "texts": [], "tables": []}
    for path in sources:
        for node in ast.walk(ast.parse(path.read_bytes())):
            if key(node):
                required["texts"].append(key(node))
            if (isinstance(node, ast.Call) and isinstance(node.func, ast.Name)
                    and node.func.id == "_editorial_table" and len(node.args) == 1):
                required["tables"].append(ast.literal_eval(node.args[0]))
    required["texts"] = sorted(set(required["texts"]))
    required["tables"] = sorted(set(required["tables"]))
    (ROOT / "backend/editorial_required.json").write_text(json.dumps(required, indent=2), encoding="utf-8")
    print(f"Restored {count} structural/key references; manifest: {len(required['texts'])} texts, {len(required['tables'])} tables")


if __name__ == "__main__":
    main()
