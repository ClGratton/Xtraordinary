# ADR 0001: Optional reading-history backup in Google Drive app data

- Status: Accepted for development; production OAuth configuration pending
- Date: 2026-08-08

## Context

Reading statistics must remain useful without an Xtraordinary-operated server. Android already persists sessions before acknowledging and deleting the X3 transfer copy. Users may optionally want that phone history restored on another Android device.

## Decision

Local-only remains the default and requires no account. When the user explicitly enables **Back up with Google**, Android requests Google authorization for `drive.appdata` and basic account identity, then merges a versioned `xtraordinary-reading-v1.json` file in Drive's private `appDataFolder`.

The cloud payload contains reading sessions and the minimum-page-time preference only. It excludes books, covers, passes, notifications, Bluetooth identifiers, firmware data, and Gemini content. Short-lived OAuth access tokens stay in memory and are never persisted. The app provides sync, delete-cloud-copy, and revoke/disconnect controls.

Google is the identity and storage provider; this does not create an Xtraordinary server account. Setup therefore keeps its three-page Welcome / Library / Device structure and embeds the optional consent on Welcome instead of adding a mandatory account page.

## Consequences

- Reading works fully offline and an unavailable Google service cannot block X3 acknowledgement or local history.
- Merge uses deterministic content fingerprints so a reflashed X3 reusing numeric session IDs cannot overwrite older history.
- A production release still needs an Android OAuth client for the final package/signing certificate, a verified public homepage/privacy-policy domain, consent-screen configuration, and jurisdiction-specific legal review.
- Because there is no Xtraordinary account, there is no separate web account-deletion endpoint. Users can delete the app-data file and revoke Google access in-app; Google Account controls remain available independently.
