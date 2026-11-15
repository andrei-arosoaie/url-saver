# Know Android — Phase 13

| Folder | Role |
| --- | --- |
| [`capacitor/`](capacitor/README.md) | **Active implementation.** Capacitor Android shell reusing the existing React application and its styling. |
| [`kotlin/`](kotlin/README.md) | Preserved Kotlin/Compose prototype from the initial implementation. Reference only; no further UI duplication is planned. |

The Android app must preserve the web application's design and full functionality. Capacitor is now the selected approach. Android-only integrations can be written in Kotlin/Java behind native plugins; they do not require rebuilding the React screens.

This is an implementation checkpoint, not completion of Phase 13. See the active project's README for build commands, verified capabilities and remaining work.
