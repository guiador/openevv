# Android and NVDA distributions — 0.4.6

[Downloads / Descargas](https://github.com/guiador/openevv/releases/tag/dist-v0.4.6)

This is an experimental downstream distribution of OpenEVV. The engine and original Android/NVDA work retain their attribution and licensing boundaries; see the component LICENSE and NOTICE files. The MIT license does not license third-party language data. The original engine is [Mudb0y/openevv](https://github.com/Mudb0y/openevv); the runtime corrections were also proposed in [upstream PR #55](https://github.com/Mudb0y/openevv/pull/55).

## Packages

| File | Purpose |
| --- | --- |
| `openevv-principal-0.4.6-es.apk` | Main Android TTS installation, package `org.openevv.android.principal` |
| `openevv-auxiliar-0.4.6-es.apk` | Optional second Android TTS installation, package `org.openevv.android.auxiliar` |
| `openevv-es-0.4.6-nvda2026.3-x64.nvda-addon` | Add-on targeting 64-bit NVDA 2026.3; manifest range 2026.1–2026.3 |
| Windows ECI library archive | Standalone DLLs for applications; not an NVDA add-on installer |
| `SHA256SUMS.txt` | SHA-256 integrity hashes for the published packages |

Android contains ARM64, ARMv7 and x86_64 native libraries and requires Android 6/API 23 or later. These APKs preserve the existing debug signing identity for updates to this distribution. They are debug variants, not store-signed releases. Building from source with your own signing key produces a different identity and does not automatically update an existing installation.

The two Android package IDs can coexist. Most users need only Principal. Auxiliar is an optional separate installation, not a required dependency or an additional language pack. Neither APK updates the other. The original automatic updater remains disabled; download updates from this repository's releases.

The product offers eight language variants: US/British English, Spain/Latin American Spanish, France/Canadian French, German and Italian, with eight voice presets each. The interface and installed NVDA documentation support English, Spanish, German, French and Italian. Interface languages and engine dialects are different lists; no additional speech language is implied by a translated label.

## Install and use

On Android, download the appropriate APK, allow that installation source when Android requests it, and select OpenEVV in system text-to-speech settings. An installation with the same package and signer can be updated without uninstalling it. Your screen reader remains a separate application. Voice, dictionaries, audio settings and the optional experimental response controls are in OpenEVV's settings.

For NVDA, open the `.nvda-addon`, complete installation and restart NVDA when ready. Select OpenEVV in the synthesizer dialog. Extended settings are under Settings → OpenEVV. The settings panel follows the voice language when opened; the synthesizer selection labels and translated add-on metadata follow NVDA's interface language. Settings and voice identifiers are preserved across this localization update. Apply/OK saves panel changes and Cancel discards them.

Classic synthesis is the default. Wideband and the native 16 kHz base are experimental options; response experiments are disabled by default. Their tradeoffs and warnings are translated in all five languages. Higher output rates alone do not establish better sound or lower audible latency.

## Construction and validation

The fixed native snapshot is included in `free-eloquence-tts-main/native/openevv`, with its pinned revision and six local patches. It is not necessary to recover a private nested Git repository. `tools/verify-engine-20261012.py` checks the snapshot against the recorded upstream hashes and exact documented differences, and verifies that the patches remain reversible.

Android: copy `free-eloquence-tts-main/app/build.gradle.kts.example` to `app/build.gradle.kts` inside that project. Configure Android SDK/NDK, JDK and the native tools described in its README. Signing secrets are supplied through local properties/environment variables, never committed. On Windows the helper `tools/build-android-current.ps1` in that project accepts `-ToolRoot`; `-IncludeAuxiliary -BuildTests` includes the optional APK and instrumentation package. Its per-user Codex tool/runtime defaults describe the maintainer's setup, not tools automatically present on another machine. Gradle/native build instructions remain the portable starting point.

NVDA: `free-eloquence-tts-main/nvda-es/prepare.py` reproduces the customized driver and localized metadata, `make-docs.py` builds installed documentation, and the build/package scripts produce the DLL and add-on. Build outputs, signing files and local reports are excluded from source control. GitHub Releases carry downloadable binaries separately.

The 0.4.6 changes complete missing interface translations and preserve the previously verified engine. Tests distinguish native synthesis, real Android instrumentation, simulated NVDA API/panel behavior and user observations. The source validation, localization checks, controller regressions, native matrices and their material limits are recorded with the release. User-reported faster NVDA execution is qualitative feedback, not a comparative latency benchmark. No claim of complete stability or exhaustive accessibility validation is made.

## Instalación y alcance

Para Android, descarga normalmente el APK Principal. Auxiliar permite una segunda instalación independiente y es opcional. Ambos incluyen las mismas ocho variantes de idioma y sus propias preferencias. Conservan la firma de esta distribución para poder actualizarla sin desinstalar. Son compilaciones debug; el actualizador original está desactivado.

Para NVDA, abre el complemento, instálalo y reinicia el lector cuando te convenga. Selecciona OpenEVV como sintetizador y abre Ajustes → OpenEVV para las opciones ampliadas. El panel sigue el idioma de la voz elegida; las etiquetas generales y los datos del complemento siguen el idioma de NVDA. Aplicar/Aceptar guarda los cambios y Cancelar los descarta.

La versión 0.4.6 completa textos de Android y NVDA en los cinco idiomas, incluidos mensajes de error, etiquetas accesibles, controles y advertencias experimentales, créditos y documentación. Mantiene las correcciones del cierre provocado por el mensaje de archivo/versiones. Los resultados automáticos y las pruebas reales del dispositivo se describen con su alcance; las simulaciones no equivalen a una sesión completa con un lector de pantalla.
