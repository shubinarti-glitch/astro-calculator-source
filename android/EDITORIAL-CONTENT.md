# Android editorial data (bounded separation)

The production build requires the private, versioned
`../data/editorial/android-v1.json`. The root `/data/` ignore rule excludes it.
An alternative build-machine location can be selected with
`-PandroidEditorialFile=<absolute path>`. Provision the file privately before
building either `standard` or `googleplay`; never commit it or the backups.

`generateAndroidEditorial` validates schema, completeness, the 78 stable card
IDs/order, eight phases and twelve signs. It emits Kotlin beneath
`app/build/generated/androidEditorial/kotlin/` (ignored). All app variants depend
on generation. Missing/invalid production data fails the build, even if generated
output from a previous build exists. The generator does not use the shared build
cache. Other compiler/build caches and APKs may still contain the texts: keep
those artifacts private too.

The existing `TarotCard`, `TarotDeck` and `LunarTexts` APIs and gameplay methods
are unchanged. Initialization is synchronous JVM object initialization, with no
Context, assets, network, coroutine or Activity dependency. Both languages remain
embedded; getters choose the current locale on each access. Saved IDs, ordering,
unknown-key fallbacks, phase labels and emoji retain their original behavior.

## Verification

Before extraction, byte-exact originals plus SHA-256 hashes were saved under
`../data/editorial/android-originals/`. The one-shot migration refuses overwrite:

```
python scripts/android_editorial_extract.py extract
python scripts/android_editorial_extract.py compare android/app/build/generated/androidEditorial/kotlin/ru/astrosmap/app/editorial/AndroidEditorial.kt
```

Run these from the repository root. `compare` checks backup hashes, all data
against the originals (including order), generated literals, and that facades
changed only in table initializers. For future intentional editorial changes,
retain this baseline and approve/version new parity evidence explicitly.

From `android/`, run both `:app:testStandardDebugUnitTest` and
`:app:testGoogleplayDebugUnitTest`; `AndroidEditorialTest` exercises access on a
plain JVM without Android initialization, saved ID lookup, draw/rank invariants,
and RU→EN→RU switching. This does not replace on-device cold-start testing of
DailyWorker/WidgetUpdateWorker or release/minified APK testing. Do not claim those
runtime scenarios verified solely from unit tests.

## Public/demo and licensing limits

A source-only public checkout intentionally cannot build without a separately
provisioned editorial package. No demo package or silent production fallback is
provided in this change. A future public demo needs explicitly reviewed neutral
data matching the schema/IDs, clear demo labeling, and safeguards against shipping
placeholder content in production. The override property is a private provisioning
mechanism, not such a demo safeguard.

This removes editorial text from current tracked Kotlin files only. Offline APKs
still embed readable/extractable text. It neither removes past Git history/copies
nor guarantees legal protection or revokes any previously granted license. Tarot
artwork, other Android text sources and repository-history cleanup are outside
this bounded change. No publishing, deployment, database or secret changes.
