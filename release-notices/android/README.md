# Android runtime notices

This directory is the deterministic legal inventory for the exact Community and Play **release runtime** dependency graphs.

- `*RuntimeClasspath.tsv` records every resolved Maven coordinate.
- `*RuntimeLicenses.tsv` records license names, URLs, and the SHA-256 of the resolved POM used as evidence.
- `license-overrides.tsv` resolves every coordinate whose POM omits a license. It uses exact versions so dependency drift cannot silently inherit an old decision.
- `Apache-2.0.txt` and `CheckerFramework-MIT.txt` are the full open-source license texts required by the current graph.
- `GOOGLE-COMPONENT-TERMS.md` records current official terms pages for proprietary Google SDK components; it does not replace online agreements.
- `MANIFEST.sha256` binds the complete pack.

Run `scripts/generate-android-release-notices.ps1` after dependency changes. The engineering gate reruns resolution, rejects stale graphs, requires an explicit override for every undeclared POM, and verifies this directory's manifest before any compiler starts.

This pack is an engineering attribution control, not a legal opinion. POM metadata can be incomplete. Public release still requires review of the actual APK, current Google/publisher agreements, notices embedded by AAR/JAR files, and any component-specific attribution requirements.
