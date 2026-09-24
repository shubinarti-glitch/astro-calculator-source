# Android editorial content — API only

The Android build does not consume private editorial packages. The former
generateAndroidEditorial task and its generated source directory are removed.
Stable Tarot IDs remain in code; names, meanings, advice and lunar editorial
texts are fetched from GET /api/mobile/editorial/v1.

The server reads data/editorial/android-v1.json privately and validates its
schema and the 78-card ID order before returning it. Never commit this file.
Production Android uses https://astrosmap.ru/; debug uses the local emulator
host. Deploy the API before distributing the updated application.

RemoteEditorial stores responses in process memory only, observed by Compose.
No editorial asset, local content database or HTTP disk cache is introduced.
Refreshing on Activity resume and before daily notification/widget work obtains
the current package. Failed or timed-out refresh clears the in-memory package
and exposes neutral localized messages, not an embedded interpretation.
Content already fetched may remain in memory until the next refresh/process exit;
this is not a promise of immediate erasure on network disconnection.

Existing saved user readings and OS-rendered notifications/widgets are not
deleted by this migration. Previously installed APKs and old private build
outputs may still contain historical texts. Do not redistribute those outputs.

Verification: unit tests check stable IDs/ranking, neutral fallback, language
switching and updating existing card objects from synthetic remote content.
Server tests use synthetic data and validate endpoint failure behavior and
no-store headers. Before release, inspect fresh APK/AAB contents, test network
loss and cold-start workers on a device, and audit other editorial sources.
Compilation alone is not an APK content audit or a licensing certification.

The public source exporter still requires a separate completeness review.
Do not include private content, credentials, signing keys or build outputs.
