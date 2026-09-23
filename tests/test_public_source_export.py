"""All exporter fixtures and outputs live in pytest temporary directories."""
import importlib.util
import json
import os
from pathlib import Path

import pytest


spec = importlib.util.spec_from_file_location(
    "public_export", Path(__file__).resolve().parents[1] / "scripts/build_public_source.py")
exporter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(exporter)


def put(root, name, content=b"fixture\n"):
    path = root / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(content)
    return path


@pytest.fixture
def source(tmp_path):
    root = tmp_path / "source"
    root.mkdir()
    for name in ("LICENSE", "backend/main.py", "requirements.txt", "run.py",
                 "frontend/js/app.js", "scripts/generate_editorial.py",
                 "android/scripts/build_content.py", "android/app/build.gradle.kts",
                 "android/gradlew", "android/gradle/wrapper/gradle-wrapper.jar",
                 "android/swisseph/LICENSE", "android/swisseph/README.orig",
                 "frontend/js/vendor/NOTICE.txt"):
        put(root, name)
    return root


def test_default_dry_run_is_read_only(source, tmp_path):
    before = {p: p.read_bytes() for p in source.rglob("*") if p.is_file()}
    destination = tmp_path / "draft"
    report = exporter.export(source, destination)
    assert not destination.exists()
    assert before == {p: p.read_bytes() for p in source.rglob("*") if p.is_file()}
    assert not report["ready_to_build"] and not report["ready_to_publish"]
    assert any("text-tables-v1.json" in item for item in report["missing_runtime_build_packages"])
    assert "frontend/js/vendor/html2pdf.bundle.min.js.LICENSE.txt" in report["missing_notices"]


def test_allowlist_exclusions_and_notices(source, tmp_path):
    private = ("data/editorial/text-tables-v1.json", ".git/config", "docs/CONTENT-SOURCE-MAP.md",
               "frontend/media/private.js", "release_backups/original.zip",
               "android/app/src/main/assets/editorial.json", "android/store/release.apk",
               "backend/originals/source.py", "frontend/photo.png", "android/INTERNAL.md")
    for name in private:
        put(source, name)
    destination = tmp_path / "draft"
    report = exporter.export(source, destination, write=True)
    for name in private:
        assert not (destination / name).exists()
        assert (source / name).exists()
    for name in report["files"]:
        assert (destination / name).read_bytes() == (source / name).read_bytes()
    assert "scripts/generate_editorial.py" in report["files"]
    assert "android/scripts/build_content.py" in report["files"]
    assert "android/gradle/wrapper/gradle-wrapper.jar" in report["files"]
    assert "frontend/js/vendor/NOTICE.txt" in report["preserved_notices"]
    assert "android/swisseph/README.orig" in report["files"]
    assert json.loads((destination / "PUBLIC-SOURCE-REPORT.json").read_text()) == report


def test_local_android_signing_files_are_recorded_without_being_read(source, tmp_path):
    signing = put(source, "android/astrosmap-release.jks", b"private signing material")
    properties = put(source, "android/signing.properties", b"storePassword=secret")
    report = exporter.export(source, tmp_path / "draft", write=True)
    assert report["excluded_sensitive_files"] == [
        "android/astrosmap-release.jks", "android/signing.properties"
    ]
    assert signing.read_bytes() == b"private signing material"
    assert properties.read_bytes() == b"storePassword=secret"
    assert not (tmp_path / "draft/android/astrosmap-release.jks").exists()
    assert not (tmp_path / "draft/android/signing.properties").exists()


@pytest.mark.parametrize("name", ["backend/.env", "backend/SECRET.KEY", "scripts/original.zip",
    "android/app/signing.properties", "backend/users.sqlite3", "frontend/js/private.pem",
    "backend/module.py.orig", "backend/credentials.json"])
def test_forbidden_candidates_fail_before_write(source, tmp_path, name):
    put(source, name)
    with pytest.raises(exporter.ExportError, match="Forbidden"):
        exporter.export(source, tmp_path / "draft", write=True)
    assert not (tmp_path / "draft").exists()


@pytest.mark.parametrize("write", [False, True])
def test_existing_destination_untouched(source, tmp_path, write):
    destination = tmp_path / "draft"
    put(destination, "keep", b"unchanged")
    with pytest.raises(exporter.ExportError, match="already exists"):
        exporter.export(source, destination, write=write)
    assert (destination / "keep").read_bytes() == b"unchanged"


def test_destination_inside_source_rejected(source):
    with pytest.raises(exporter.ExportError, match="outside"):
        exporter.export(source, source / "draft", write=True)


def test_embedded_key_rejected(source, tmp_path):
    marker = b"-----BEGIN RSA " + b"PRIVATE KEY-----"
    put(source, "backend/config.py", marker)
    with pytest.raises(exporter.ExportError, match="embedded secret"):
        exporter.export(source, tmp_path / "draft", write=True)
    assert not (tmp_path / "draft").exists()


def test_symlink_rejected(source, tmp_path):
    target = put(tmp_path, "private", b"private")
    try:
        (source / "backend/leak.py").symlink_to(target)
    except OSError:
        pytest.skip("Symlink creation unavailable")
    with pytest.raises(exporter.ExportError, match="Link"):
        exporter.export(source, tmp_path / "draft", write=True)


def test_hardlink_rejected(source, tmp_path):
    target = put(tmp_path, "private", b"private")
    os.link(target, source / "backend/leak.py")
    with pytest.raises(exporter.ExportError, match="regular file"):
        exporter.export(source, tmp_path / "draft", write=True)


def test_cli_default_and_explicit_write(source, tmp_path, capsys):
    destination = tmp_path / "draft"
    assert exporter.main([str(destination), "--source", str(source)]) == 0
    assert not destination.exists()
    assert "not build-ready" in capsys.readouterr().out
    assert exporter.main([str(destination), "--source", str(source), "--write"]) == 0
    assert destination.is_dir()


def test_changed_input_leaves_partial_draft_without_deletion(source, tmp_path, monkeypatch):
    original_plan = exporter.plan
    def changed_plan(root):
        report = original_plan(root)
        put(source, "backend/main.py", b"changed")
        return report
    monkeypatch.setattr(exporter, "plan", changed_plan)
    with pytest.raises(exporter.ExportError, match="changed during export"):
        exporter.export(source, tmp_path / "draft", write=True)
    assert (tmp_path / "draft").exists()
    assert not (tmp_path / "draft/PUBLIC-SOURCE-REPORT.json").exists()
