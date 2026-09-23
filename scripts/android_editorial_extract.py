"""One-shot mechanical extraction; compare always reads the immutable originals.

Run from any directory: python scripts/android_editorial_extract.py extract|compare
No evaluation of Kotlin. Only literal constructor/map entries are accepted.
"""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / 'data/editorial'
BACKUP = PRIVATE / 'android-originals'
PACKAGE = PRIVATE / 'android-v1.json'
SOURCES = {
    'TarotDeck.kt': ROOT / 'android/app/src/main/java/ru/astrosmap/app/ui/tarot/TarotDeck.kt',
    'LunarTexts.kt': ROOT / 'android/app/src/main/java/ru/astrosmap/app/ui/tools/LunarTexts.kt',
}
LITERAL = r'"(?:[^"\\]|\\.)*"'


def region(text, name, expression):
    return re.search(r'    (?:private )?val ' + name + r'[^\n]*= ' + expression + r'\(\n.*?^    \)', text, re.M | re.S)


def parse(tarot, lunar):
    block = region(tarot, 'cards', 'listOf')
    assert block, 'Original cards block missing'
    cards = []
    for match in re.finditer(r'TarotCard\((.*?)\)', block.group(), re.S):
        args = match.group(1)
        assert not re.sub(LITERAL, '', args).replace(',', '').strip()
        values = [json.loads(s) for s in re.findall(LITERAL, args)]
        assert len(values) == 7 and all(values)
        cards.append(values)
    assert len(cards) == 78 and len({c[0] for c in cards}) == 78
    result = {'schemaVersion': 1, 'cards': cards}
    for name, count in [('phaseAdvice', 8), ('moonMood', 12)]:
        block = region(lunar, name, 'mapOf')
        assert block, name
        triples = re.findall('(' + LITERAL + r')\s+to\s+\((' + LITERAL + r')\s+to\s+(' + LITERAL + r')\)', block.group())
        rows = [[json.loads(s) for s in row] for row in triples]
        assert len(rows) == count and len({r[0] for r in rows}) == count
        result[name] = rows
    return result


def compare(generated=None):
    manifest = json.loads((BACKUP / 'sha256.json').read_text(encoding='utf-8'))
    for name, digest in manifest.items():
        assert hashlib.sha256((BACKUP / name).read_bytes()).hexdigest() == digest, name
    originals = [(BACKUP / name).read_text(encoding='utf-8') for name in SOURCES]
    expected = parse(*originals)
    assert json.loads(PACKAGE.read_text(encoding='utf-8')) == expected, 'Package parity failure'
    # The facade must differ only in the literal table initializers.
    for name, original in zip(SOURCES, originals):
        replaced = original
        for field, expr in ([('cards', 'listOf')] if name == 'TarotDeck.kt' else [('phaseAdvice', 'mapOf'), ('moonMood', 'mapOf')]):
            match = region(replaced, field, expr)
            prefix = '    val cards: List<TarotCard>' if field == 'cards' else '    private val ' + field
            replaced = replaced[:match.start()] + prefix + ' = ru.astrosmap.app.editorial.AndroidEditorial.' + field + replaced[match.end():]
        assert SOURCES[name].read_text(encoding='utf-8') == replaced, 'Unexpected logic change: ' + name
    if generated:
        source = Path(generated).read_text(encoding='utf-8')
        # Generated literals use Kotlin dollar escaping (not a JSON escape).
        source = source.replace('\\$', '$')
        assert parse(source, source) == expected, 'Generated Kotlin parity failure'
    print('Exact parity: 78 ordered IDs, 468 card fields, 40 lunar RU/EN fields; facade logic unchanged.')


def extract():
    assert not PACKAGE.exists() and not BACKUP.exists(), 'Refusing to overwrite private originals/package'
    for path in [PACKAGE, BACKUP / 'TarotDeck.kt']:
        subprocess.run(['git', 'check-ignore', '--quiet', str(path)], cwd=ROOT, check=True)
    originals = {name: path.read_bytes() for name, path in SOURCES.items()}
    texts = {name: raw.decode('utf-8').replace('\r\n', '\n') for name, raw in originals.items()}
    data = parse(texts['TarotDeck.kt'], texts['LunarTexts.kt'])
    BACKUP.mkdir(parents=True)
    for name, raw in originals.items():
        (BACKUP / name).write_bytes(raw)
    (BACKUP / 'sha256.json').write_text(json.dumps({n: hashlib.sha256(b).hexdigest() for n, b in originals.items()}, indent=2), encoding='utf-8')
    PACKAGE.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    for name, text in texts.items():
        for field, expr in ([('cards', 'listOf')] if name == 'TarotDeck.kt' else [('phaseAdvice', 'mapOf'), ('moonMood', 'mapOf')]):
            match = region(text, field, expr)
            prefix = '    val cards: List<TarotCard>' if field == 'cards' else '    private val ' + field
            text = text[:match.start()] + prefix + ' = ru.astrosmap.app.editorial.AndroidEditorial.' + field + text[match.end():]
        SOURCES[name].write_text(text, encoding='utf-8')
    compare()


if __name__ == '__main__':
    if sys.argv[1:] == ['extract']:
        extract()
    elif sys.argv[1:2] == ['compare']:
        compare(sys.argv[2] if len(sys.argv) > 2 else None)
    else:
        raise SystemExit('Usage: android_editorial_extract.py extract|compare [generated.kt]')
