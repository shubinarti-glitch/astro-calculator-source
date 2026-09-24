# Local draft source export

`scripts/build_public_source.py` reads the **current working tree**, including
uncommitted and untracked allowlisted code. It never runs Git, contacts the
network, installs dependencies, deletes files, or publishes anything.

Inspect first (default is dry-run, JSON report goes to stdout):

```powershell
python scripts/build_public_source.py C:/exports/astro-draft
```

Only `--write` creates a local draft. The destination must not exist, must be
outside the source tree, and its parent must already exist. Existing files and
directories are rejected even in dry-run. Links/reparse points and hard-linked
source files are rejected. Use a quiescent source and destination parent: this
is not an atomic snapshot or a sandbox against concurrent hostile writers.
On failure after creation a partial draft is left in place, never cleaned up
automatically. Choose a different destination after investigating the failure.

The allowlist in the script covers backend Python, frontend JS/CSS/HTML,
tests, Python dependency manifests, Android Java/Kotlin/XML, Gradle files and
wrapper, build/generation scripts, and license/notice files. It preserves bytes,
including inline copyright comments. `android/swisseph/README.orig` is a specific
attribution-document exception, not permission to export package originals.
New build input types/locations require explicit allowlist review. The report
lists unallowlisted files and known missing build inputs; this is not exhaustive
dependency resolution.

Private data, media, Android assets/store files, originals, backups, caches,
internal documentation and Git history are excluded. Known forbidden files
(credentials, databases, keys and archives) encountered in candidate trees stop
the export before writing. Android signing files in the Android root are the sole
exception: only their names are recorded under `excluded_sensitive_files`; their
contents are never read or copied. Files in deliberately excluded trees are not read.
Selected files also receive a limited embedded-secret scan. Renaming private
content to an allowed source filename can evade these heuristics: manual review
of embedded Python/JS/Kotlin/XML/HTML text remains mandatory.

The JSON report identifies preserved and missing expected notices, omitted
runtime/build packages and known missing build inputs. The report is returned separately and is not copied into the public draft. Missing notices are review blockers, not fabricated
or silently replaced. The known-notice checklist is not a transitive license
audit; the Gradle wrapper and bundled html2pdf dependencies need separate review.

Every report marks `ready_to_build` and `ready_to_publish` false. In particular,
`data/editorial/text-tables-v1.json` is excluded although the backend requires
it. Other editorial content and excluded asset inputs also need review.
Android editorial data is a private server runtime package, not an APK build input. A separately authored neutral demo dataset is future
work; the exporter does not generate one or replace production data.

Successful export means only that the local draft passed these technical checks.
It does **not** assert AGPL compliance, corresponding-source completeness,
removal of all proprietary embedded text, or readiness to build/publish. The
existing `check_public_source.py` checks a Git index separately; this exporter
neither initializes nor modifies an index. No publication is authorized here.

Verification (temporary fixtures only, no application imports):

```powershell
python -B -m pytest tests/test_public_source_export.py -q -p no:cacheprovider
```

Android Java/Kotlin source packages named `data` under main/test source roots are included. This exception does not include runtime data, assets, or arbitrary files in those packages.
