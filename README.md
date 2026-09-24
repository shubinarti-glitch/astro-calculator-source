# AstroSMap

Source code for the AstroSMap web service and Android application.

The program source in this repository is provided under the GNU Affero
General Public License v3.0. See `LICENSE`.

AstroSMap's original interpretations, forecasts, Tarot texts, translations,
illustrations, reports, databases, credentials, signing keys and production
configuration are not part of this repository and are not licensed under the
AGPL. See `CONTENT-NOTICE.md`.

This source distribution requires separately supplied runtime data and
third-party resources. It is not a ready-to-deploy copy of astrosmap.ru.

Copyright © 2026 AstroSMap.

## Release 1.7.4 (Android versionCode 12)

This snapshot contains the application and server changes released on
24 September 2026: explicit registration consent, event-specific forecast
summaries, optional mobile feedback/crash diagnostics, remote editorial
content, and serialized access to the shared ephemeris engine.

Android `data` packages contain program source (API models, persistence,
synchronization and tests); they are included. They are distinct from the
private root `data/` directory, which is excluded.

Android editorial text is served by `/api/mobile/editorial/v1` and is no
longer a generated Kotlin build input. See `android/EDITORIAL-CONTENT.md`.
Release signing credentials are never distributed. Google Play uses
`:app:bundleGoogleplayRelease`; standard APK uses `:app:assembleStandardRelease`.

This is a source distribution, not a standalone production deployment.
The private runtime datasets, artwork, city database, ephemerides and some
test fixtures are not included. The production tests passed in the provisioned
release workspace; a complete build/test of this public checkout has not been
verified. No claim of bit-for-bit reproducibility is made.
