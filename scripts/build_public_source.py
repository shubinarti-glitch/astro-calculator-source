"""Offline, allowlisted working-tree export. Default: inspect only, never publish.

This is a draft source snapshot, NOT a licensing or completeness certification.
No Git commands, deletion, dependency installation, or application imports.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat


ROOT_FILES = {
    "run.py", "requirements.txt", "requirements-dev.txt", "pyproject.toml",
    "poetry.lock", "uv.lock", "package.json", "package-lock.json",
    "Dockerfile", "compose.yaml", "Makefile", "README.md",
    "CONTENT-NOTICE.md",
}
TREES = {
    "backend": {".py"}, "frontend": {".js", ".css", ".html", ".webmanifest"},
    "scripts": {".py", ".sh", ".ps1", ".bat"}, "tests": {".py"},
    "android/app/src": {".kt", ".java", ".xml"},
    "android/astrocore/src": {".kt", ".java", ".xml"},
    "android/swisseph/src": {".java"},
    "android/gradle": {".toml", ".properties"},
    "android/scripts": {".py", ".sh", ".ps1", ".kts"},
    "android/buildSrc": {".kt", ".kts", ".java", ".properties"},
}
EXACT = ROOT_FILES | {
    "backend/editorial_required.json",
    "android/gradlew", "android/gradlew.bat", "android/gradle.properties",
    "android/settings.gradle.kts", "android/build.gradle.kts",
    "android/app/build.gradle.kts", "android/app/proguard-rules.pro",
    "android/astrocore/build.gradle.kts", "android/swisseph/build.gradle.kts",
    "android/gradle/wrapper/gradle-wrapper.jar", "android/run-tests.ps1",
    "android/test-ascii.init.gradle", "android/swisseph/README.orig",
    "android/EDITORIAL-CONTENT.md",
    "frontend/js/vendor/html2pdf.bundle.min.js.LICENSE.txt",
    "docs/PUBLIC-SOURCE-EXPORT.md",
}
SKIP_DIRS = {
    "data", "media", "store", ".git", ".claude", ".codex", ".codex-artifacts",
    "__pycache__", ".pytest_cache", ".gradle", "build", "node_modules",
    "venv", "release_backups", "originals", "package-originals", "content_evidence", "assets",
}
FORBIDDEN_SUFFIXES = {
    ".db", ".sqlite", ".sqlite3", ".pem", ".key", ".jks", ".keystore",
    ".p12", ".pfx", ".apk", ".aab", ".zip", ".gz", ".tar", ".7z",
    ".bundle", ".bak", ".orig", ".rar", ".tgz", ".bz2", ".xz", ".sql",
}
NOTICE = re.compile(r"^(license|licence|copying|notice|copyright|third[-_]party)([._-].*)?$", re.I)
SECRET = re.compile(rb"-----BEGIN (?:[A-Z ]*PRIVATE KEY)-----|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{36,}")
LOCAL_ANDROID_SECRETS = re.compile(
    r"^android/(?:local\.properties|signing\.properties|[^/]+\.(?:jks|keystore|p12|pfx))$",
    re.I,
)
MISSING_PACKAGES = [
    "data/editorial/text-snippets-v1.json and glossary-v1.json: required private runtime content; excluded",
    "data/editorial/text-tables-v1.json: required backend runtime package; excluded",
    "data/authored_content.json, data/authored_transit_content.json, data/transit_en/: excluded editorial content",
    "Android private editorial build packages: not supplied or validated; neutral demo data must be prepared separately",
    "Media, ephemerides and Android asset databases: excluded; runtime/build replacements require separate review",
]


class ExportError(ValueError):
    """Unsafe export request; nothing should be published."""


def forbidden(name):
    path = PurePosixPath(name.lower())
    return (any(p in SKIP_DIRS or p.startswith(".source-publish-") for p in path.parts)
            or path.name.startswith(".env")
            or path.name in {"signing.properties", "local.properties", "yookassa.json", "credentials.json"}
            or (path.suffix in FORBIDDEN_SUFFIXES and name != "android/swisseph/README.orig"))


def allowed(name):
    path = PurePosixPath(name)
    if forbidden(name):
        return False
    return (name in EXACT or bool(NOTICE.fullmatch(path.name))
            or (path.parent == PurePosixPath("android") and name.endswith(".gradle.kts"))
            or any(name.startswith(root + "/") and path.suffix in suffixes
                   for root, suffixes in TREES.items()))


def _regular(path):
    info = path.lstat()
    if (not stat.S_ISREG(info.st_mode) or info.st_nlink != 1
            or getattr(info, "st_file_attributes", 0) & 0x400):
        raise ExportError("Not an independent regular file: " + str(path))


def _walk(root, directory):
    for path in sorted(directory.iterdir()):
        name = path.relative_to(root).as_posix()
        info = path.lstat()
        if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400:
            raise ExportError("Link/reparse point in selected tree: " + name)
        if path.is_dir():
            if path.name.lower() not in SKIP_DIRS and not path.name.startswith("."):
                yield from _walk(root, path)
        else:
            yield name


def plan(source):
    """Validate candidates without reading excluded private directories."""
    source = Path(source).resolve(strict=True)
    candidates = set()
    # Only these top-level trees are traversed. Internal docs and archives are not.
    for root in ("backend", "frontend", "scripts", "tests", "android"):
        directory = source / root
        if directory.is_symlink() or (directory.exists() and getattr(directory.lstat(), "st_file_attributes", 0) & 0x400):
            raise ExportError("Linked source tree: " + root)
        if directory.is_dir():
            candidates.update(_walk(source, directory))
    candidates.update(p.name for p in source.iterdir() if not p.is_dir() and NOTICE.fullmatch(p.name))
    candidates.update(name for name in EXACT if os.path.lexists(source / name))
    files, excluded, excluded_sensitive = {}, [], []
    for name in sorted(candidates):
        if forbidden(name):
            # These local Android files are expected beside Gradle files. Keep
            # them out of the snapshot and do not open their contents.
            if LOCAL_ANDROID_SECRETS.fullmatch(name):
                excluded_sensitive.append(name)
                continue
            raise ExportError("Forbidden file in candidate tree: " + name)
        if not allowed(name):
            excluded.append(name)
            continue
        path = source / name
        # Check every ancestor too, including explicitly selected files.
        for parent in path.parents:
            if parent == source:
                break
            if parent.is_symlink() or getattr(parent.lstat(), "st_file_attributes", 0) & 0x400:
                raise ExportError("Linked ancestor: " + name)
        _regular(path)
        payload = path.read_bytes()
        if SECRET.search(payload):
            raise ExportError("Potential embedded secret in: " + name)
        files[name] = hashlib.sha256(payload).hexdigest()
    notices = [name for name in files if NOTICE.fullmatch(PurePosixPath(name).name)]
    missing = []
    for required in ("LICENSE", "android/swisseph/LICENSE",
                     "frontend/js/vendor/html2pdf.bundle.min.js.LICENSE.txt",
                     "android/gradle/wrapper/LICENSE"):
        if required not in files or not (source / required).stat().st_size:
            missing.append(required)
    return {
        "files": files, "excluded_unallowlisted": excluded,
        "excluded_sensitive_files": excluded_sensitive,
        "preserved_notices": notices,
        "excluded_private_directories": sorted(SKIP_DIRS),
        "missing_notices": missing, "missing_runtime_build_packages": MISSING_PACKAGES,
        "missing_build_inputs": [name for name in (
            "run.py", "requirements.txt", "backend/main.py", "frontend/index.html",
            "android/settings.gradle.kts", "android/build.gradle.kts",
            "android/app/build.gradle.kts", "android/astrocore/build.gradle.kts",
            "android/swisseph/build.gradle.kts", "android/gradlew", "android/gradlew.bat",
            "android/gradle/libs.versions.toml", "android/gradle/wrapper/gradle-wrapper.jar",
            "android/gradle/wrapper/gradle-wrapper.properties") if name not in files],
        "ready_to_build": False, "ready_to_publish": False,
        "limitations": ["No assertion of AGPL compliance or corresponding-source completeness.",
                        "Embedded Python/JS/Kotlin/editorial and legal text requires manual review.",
                        "Secret detection is heuristic, not a credential audit.",
                        "Third-party transitive notices and dependency sources require review."],
    }


def export(source, destination, *, write=False):
    source = Path(source).resolve(strict=True)
    destination = Path(destination).absolute()
    if os.path.lexists(destination):
        raise ExportError("Destination already exists: " + str(destination))
    for parent in destination.parents:
        if parent.is_symlink() or (parent.exists() and getattr(parent.lstat(), "st_file_attributes", 0) & 0x400):
            raise ExportError("Linked destination ancestor: " + str(parent))
    if destination.resolve().is_relative_to(source):
        raise ExportError("Destination must be outside the source tree")
    if not destination.parent.is_dir():
        raise ExportError("Destination parent must already exist")
    report = plan(source)
    if not write:
        return report
    destination.mkdir()  # Exclusive creation; never merge or remove anything.
    for name, digest in report["files"].items():
        origin = source / name
        _regular(origin)
        if origin.resolve() != source / name:
            raise ExportError("Source path changed: " + name)
        payload = origin.read_bytes()
        if hashlib.sha256(payload).hexdigest() != digest:
            raise ExportError("Source changed during export: " + name)
        target = destination / name
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("xb") as stream:
            stream.write(payload)
        target.chmod(stat.S_IMODE(origin.stat().st_mode))
    # Revalidate output. Failures deliberately leave a partial draft, never delete it.
    for path in destination.rglob("*"):
        if path.is_file():
            name = path.relative_to(destination).as_posix()
            _regular(path)
            if not allowed(name) or name not in report["files"]:
                raise ExportError("Forbidden/unplanned output: " + name)
            if hashlib.sha256(path.read_bytes()).hexdigest() != report["files"][name]:
                raise ExportError("Output mismatch: " + name)
    # The audit report is returned to the operator (and printed by the CLI),
    # but is deliberately not copied into the public tree: it contains an
    # internal inventory of excluded files and runtime packages.
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--source", type=Path, default=Path(__file__).resolve().parents[1])
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--write", action="store_true", help="Create a new local draft directory")
    mode.add_argument("--dry-run", action="store_true", help="Default; no filesystem writes")
    args = parser.parse_args(argv)
    try:
        report = export(args.source, args.destination, write=args.write)
    except (OSError, ExportError) as error:
        parser.exit(1, "BLOCKED: " + str(error) + "\n")
    print(json.dumps(report, ensure_ascii=True, indent=2))
    print("DRAFT ONLY: not build-ready or publication-ready; no compliance certification.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
