# Xtraordinary authoritative TODO

**Updated:** 2026-08-22  
**Scope:** normalized remaining work from the 175 user-prompt blocks in the current Codex task, reconciled against `HANDOFF.md`, `docs/x3-takeover-tracker.md`, the current checkout, and recorded physical evidence.

This is the actionable backlog. Older unchecked items in `docs/x3-takeover-tracker.md` remain incident history and must not be treated as current work unless they are represented here. Do not reopen a completed item without new contradictory runtime evidence.

## Status and evidence rules

- `[ ]` — locally actionable and not complete.
- `[~]` — implemented in source, but the current source-matching build or physical acceptance is missing.
- `[!]` — blocked by an external credential, contract, permission, legal identity, or unavailable physical facility.
- Source presence is not a build; a build is not an install; an install is not physical acceptance.
- Existing measured behavior is a constraint. Before changing power, radio, pairing, acknowledgement, persistence, USB, reader-refresh, or legal behavior, compare the proposed change with the linked policy and hardware evidence. If they conflict, document the evidence-backed decision before editing.
- Preserve Pixel app data, the Android/X3 Bluetooth bond, X3 NVS, SD books, reading history, and current device settings unless the user explicitly authorizes a targeted reset.

## Execution order

### P0 — Build, deploy, and accept the current source

- [x] Finish and push the current milestone's dirty documentation/policy changes; require a clean named branch with matching upstream before compilation.
- [ ] Start the compiler/device phase in a fresh task with a bounded regression-impact audit against `docs/x3-power-sync-flow.md`, `docs/interactive-transport-lifecycle.md`, `docs/usb-firmware-maintenance.md`, `docs/legal-release-audit.md`, and the physical evidence in `HANDOFF.md`.
- [~] Final release-artifact attempt: Android and firmware notice portability checks are pushed through `586cbac`, but the one canonical Android wrapper invocation stopped before compiler entry because the task-bound replay/task-growth gate rejected the weekly waiver. No APK, install, flash, upload, handshake, or device mutation occurred; resume only from a fresh passing audit.
- [x] 2026-08-24 fresh deployment audit and regression-impact checkpoint: this task's bound gate passed at 4% weekly use, 0-point task growth, 30,915 replay median, 41,093 latest input, and 10% recent growth. The prior 145,461-token `compaction-required` result belongs to older task `01a02a88-cd91-7361-a6e3-1941183387f4` and remains history; no protected deployment stage started in either task. The current committed/pushed checkpoint authorizes the one permitted build attempt.
- [~] Canonical Android build attempted once from pushed `e020299` using only `-AllowDeferredUiReviewDebt`; policy/provenance/notice gates passed, but both CommunityDebug and PlayDebug compile failed before APK creation because `CompanionViewModel.kt:1210` dereferences `finalSnapshot` even though lines 1166-1175 assign it the digest pass's `Unit` result. Correct the bounded reconciliation-result binding, commit/push it, then retry only in a newly authorized build milestone.
- [ ] Rediscover the wireless-debug Pixel through the repository ADB/mDNS workflow and install with `adb install -r`, retaining app data.
- [ ] Compare the pushed firmware source with the installed X3 dev36 artifact. Build and application-only flash only if firmware actually changed; preserve NVS, bond, and SD.
- [ ] Record exact app/firmware versions, commits, artifact hashes, install/flash results, retained-data checks, and the fresh capabilities/status/policy acknowledgement chain.
- [ ] Restore the Pixel screen timeout to `1800000` ms after device work if it is temporarily extended.

### P0 — Book import, transfer, background, and library truth

- [~] Physically accept the current source-matching build for the complete transfer lifecycle; the source corrections are pushed through `e743bc8`, but this candidate has not been installed or accepted.
- [ ] Verify the system picker exposes only EPUB-compatible documents and never offers arbitrary Pocket/files content; reject an OPF whose spine contains no readable XHTML.
- [ ] For a single selected book, close the picker/selection state immediately when upload starts and show progress plus Stop in that book's row.
- [ ] For one or multiple newly imported books, immediately offer direct transfer to X3 with copy that reflects the actual count.
- [ ] Keep an active upload alive through brief and long Android background periods. Backgrounding alone must keep the top chip at **Paired** or the truthful active-transfer state, not **Reconnecting**, and must not drop the required fast/persistent transport until the operation finishes.
- [ ] Treat explicit Stop, task swipe/removal, source/validation failure, and terminal X3 rejection as final: cancel and join the old worker, clear active UI and durable replay state, and never restart the transfer on foreground/relaunch.
- [ ] Treat only transient transport interruption as replayable durable intent; reconnect must resume from a fresh transaction boundary without creating a second concurrent worker.
- [ ] On rejection or failure, leave no zero-chapter/corrupt book, partial destination, stale **Upload rejected/stopped** chip, or instantly re-rejected phantom job after force-close/relaunch.
- [ ] Accept both BLE and USB success using real EPUBs, including a long background transfer and sequential multi-book queue.
- [ ] Accept same-name replacement atomically: publish only after verification and restore the previous book if replacement fails.
- [ ] Reconcile upload and delete only from the final revisioned X3 library snapshot. After the acknowledgement, the phone row and X3 menu must agree without a second user action.
- [ ] Verify Project Hail Mary cover parity: the phone recovers/displays the cover already present on X3.
- [ ] Verify the X3 library shown in the app contains books only and does not expose unrelated filesystem entries.
- [ ] Open `test_tables.epub` on X3 and prove real multi-page pagination rather than a one-page/zero-chapter shell.

### P1 — Connection truth, power, sleep, and wake

- [ ] Finish the dev36 sleep-boundary acceptance: wake/replug X3, retrieve the retained trace for previous boot 4, require ordinary reset `8` with no new panic marker, then complete repeated sleep/wake/reconnect cycles.
- [ ] Reopen the exact book/chapter associated with the old crash and run a sustained page-turn smoke test while retaining crash evidence.
- [ ] Physically measure Home current between configured 30/60/120-second standby advertising pulses and compare Reading drain with the original eight-percentage-point baseline.
- [ ] Verify the complete reusable radio policy on hardware: fast discovery after boot/Home/button activity; sparse standby pulse at the configured interval; separate 1/2/4-second connected-background cadence; inactivity sleep at the configured deadline.
- [ ] Verify Reading and acknowledged Static ticket are radio-quiet, while Focus and Live ticket remain awake and pulse-discoverable without retaining GATT merely because their screen is visible.
- [ ] Verify a physical button leaves sparse standby and restores fast discovery; a new command during sparse standby must be delivered on a permitted pulse without pretending the device is continuously connected.
- [ ] Verify the X3 low-power chip derives **up to N seconds** from the applied standby setting rather than hard-coded copy.
- [ ] Verify Bluetooth-off is shown as **Bluetooth off**, remembered idle is **Paired/Available**, an active attempt is **Reconnecting**, and **Connected** appears only after advertisement, status-0 GATT, services, notifications, and protocol capabilities.
- [ ] Verify foregrounding the app does not retain GATT or worsen X3 battery when no work is pending; foreground pending work must begin its bounded recovery promptly.
- [ ] Verify rapid Settings edits still coalesce to the newest desired values, remain pending while offline/Reading, apply after an allowed transition, and become **Synced to X3** only after matching acknowledgements.
- [ ] Visually accept the configured instant/1-second/2-second Power behavior, immediate post-sleep wake cancellation, clean full-refresh restoration, and absence of transition ghosting.

### P1 — Tickets, live data, Focus, and scanner behavior

- [~] The source implements photo, `.pkpass`, Google Wallet FlightObject JSON, shared/save link input, QR/PDF417/Aztec handling, Static/Live modes, arrival time/delay, and X3 payload v2; the current source-matching app/X3 combination still needs physical acceptance.
- [ ] Verify selecting and sending a pass keeps the action inside the horizontally expanding Static/Live choice and morphs to Remove only after the matching X3 acknowledgement.
- [ ] Verify immutable pending identity: paging to another pass or changing desired next-send state cannot rename or overwrite the operation already in flight; send and clear cannot overlap.
- [ ] Verify the X3 Home **Ticket** item appears after acknowledged upload, survives leaving the ticket, and disappears immediately after acknowledged removal with a refreshed menu.
- [ ] Verify entering and leaving the ticket performs the required full refresh and leaves no ghosting.
- [ ] Verify Static remains displayed while radio-quiet/asleep; returning Home restores a fresh fast window but must not cause an already-cancelled phone intent to remove the pass unexpectedly.
- [ ] Verify Live ticket and Pomodoro never enter inactivity sleep. Live uses provider polling plus the generic pulse/interactive lifecycle rather than an always-open GATT link.
- [ ] Measure send/remove acknowledgement latency from Home and sparse standby; confirm the generic interactive lease removes the former 10–13-second avoidable delay without increasing idle drain.
- [ ] On Android, verify magnetic horizontal paging, adjacent-route edge peek, haptics, stable front/back bounds, no redundant black banners, no import-provenance clutter, and no detached/floating chevron.
- [ ] On Android, verify the centred **Show code** / **Show details** control is discoverable, uses only the restrained QR cue where space permits, and reaches the same final state with reduced motion.
- [ ] On X3, accept both matrix and linear ticket compositions, operational hierarchy, larger but bounded code use, arrival/departure/delay fields, bottom code placement, button outlines, and no rendered comments/debug text.
- [ ] For linear codes, verify the lower-right mapped control opens the rotated full-width scanner view and the mapped Back/Ticket control is correctly labelled. Preserve integer module scaling and scanner quiet zones.
- [!] Validate QR, PDF417, and Aztec output at real airport/airline scanners; local camera scanning is useful evidence but not a substitute.
- [!] Configure and deploy the external live-flight provider/proxy with production credentials and contract. The APK must contain no provider secret; strict flight/date/origin matching and freshness/backoff remain mandatory.
- [ ] Visually accept the revised Focus timer on X3: dominant minute value with smaller `min`, coherent e-ink hierarchy, and no `mm:ss` presentation.

### P1 — Reader behavior and reading statistics

- [ ] Physically time image-heavy and text page turns on the current firmware, including cached-next-page and rapid-turn cases. Compare with the implemented display-sized 2-bit cache, coarse JPEG scaling, streamed PNG rows, and 160/120/80-row safe-strip fallback before proposing another optimization.
- [ ] Physically validate the configurable 1/5/10/15/30-page cleanup cadence, the X3 HALF/forced-resync behavior, ghosting, and the observed black transition. Do not remove controller waits without hardware evidence that first-differential quality remains correct.
- [x] Reading statistics are implemented and physically accepted: X3 SD journal, page time and word counts, durable chunk delivery, Android persistence-before-ACK, cumulative/session UI, and the two-session/eight-sample import. Keep this closed unless new runtime evidence contradicts it.
- [x] Charging-limit feasibility is resolved: the firmware can read BQ27220 state of charge, but no verified charger-enable/charge-path control is exposed. Do not claim a firmware charge cutoff; a percentage warning is the only locally justified feature without a schematic/control pin.

### P1 — Setup, appearance, responsive layout, and user-facing state

- [~] Source contains the shared measured bottom action slot and first-viewport space policy; install and physically verify it rather than reopening the prior pixel-moving review loop.
- [ ] Complete the first-run carousel and run the full production-phone setup path, including firmware-source selection and recovery without losing pairing unexpectedly.
- [ ] On 412×915 and compact-height devices, keep all required first-page content visible; on Library and Device pages, keep equivalent actions in the same lower zone and assign tall-screen slack deliberately instead of leaving ownerless white bands.
- [ ] Verify large text/localization reflows or scrolls without clipping, while normal pages adapt to available height rather than using fixed short cards surrounded by unused space.
- [ ] Physically accept independent appearance axes: drag/tap the sea/astronaut carousel for Light/Dark, select Expressive/Minimal separately in Settings, restart in every combination, and prove Minimal changes shape/density/motion rather than only color.
- [ ] Verify the device chip never simultaneously says **Paired** and **No companion device found**, and passive discovery misses do not add redundant failure copy for a remembered X3.
- [ ] Verify ordinary setup, status, discovery, library, and Focus-preview surfaces say **X3/X3 reader**, not decorative **XTEINK**. Preserve the internal Bluetooth/protocol identity until a coordinated bond-safe migration exists.
- [ ] Verify app bars, ticket controls, selection cards, status/timeout feedback, and bottom navigation use the documented hierarchy and stable target sizes without redundant banners or controls stranded in dead space.
- [ ] Record compact, 1.3×, reduced-motion, TalkBack, restart, and real dynamic-color evidence. The user-deferred eight-role UI receipt wave remains release debt; do not restart it during functional/device milestones.

### P2 — Google backup, monetization, and production services

- [!] Configure a production Google OAuth client for the final package/signing certificate and verified domain, then accept Drive create, merge/restore, delete, revoke, and reconnect. The current real-device boundary is `DEVELOPER_ERROR`.
- [!] Provide the production identity/entitlement backend and Play Developer API verification so trial and lifetime ad-free state survive reinstall/clear-data for the same identity and recover refunds/revocations.
- [!] Configure the final Play one-time product, license-test accounts, production AdMob identifiers, published UMP consent messages, and Data Safety declarations.
- [ ] From a Play-installed test build, physically accept purchase, pending purchase, restore, consent/privacy options, official test adaptive banner, **Continue with free**, expiry, offline/unknown state, and no-ad surfaces.
- [x] Keep the Community distribution SDK-free and ad-free; no Billing, Mobile Ads, or UMP classes/manifest entries may enter it.

### P2 — Legal, licensing, branding, and release readiness

- [ ] Resolve the confirmed statically linked ArduinoWebSockets 2.7.3 LGPL obligation: replace it or implement the exact compliant distribution/linking path after legal review.
- [!] Replace every `MISSING` artwork-provenance field with dated generator account, prompt/source chain, commercial-output terms, and human approval evidence, or remove the affected asset.
- [!] Obtain written XTEINK name/logo/product-image/OEM-firmware permission. If permission is not obtained, keep public builds free of OEM art/branding and describe compatibility without endorsement.
- [!] Finalize publisher/legal identity, jurisdiction/consumer terms, privacy contact, age/territory requirements, and verified public Terms and Privacy URLs.
- [ ] Recheck the in-app Terms, Privacy, and Notices against the exact enabled endpoints/features in the final Community and Play builds.
- [ ] Review the exact signed APK/AAB notice assets and generated firmware release record against Android dependencies, CrossPoint/CrossInk/Open X4 obligations, fonts/icons/art, source offers, and binary/source hashes.
- [!] Close the production OAuth, flight-provider, Play, AdMob, UMP, and entitlement contracts before calling the release legally or commercially ready.

## Workflow and continuity requirements

- [ ] Commit and push the current durable workflow edits that were left dirty while this TODO was created.
- [ ] One user-visible Codex task owns one substantial milestone. It checkpoints source, evidence, `TODO.md`, tracker, and handoff before ending.
- [ ] The next milestone starts in a fresh user-visible task with a compact packet derived from this file and directly relevant policy/evidence only; automatically open that task in the Codex app so the user never has to inspect Subagents or say “go on”.
- [ ] Within a milestone, use one fresh Terra execution owner for implementation, debugging, build, and acceptance. Do not create agents per microtask, keep one agent for the entire project, or launch reviewer waves.
- [ ] The Sol coordinator must not repeat source inspection already owned by the milestone worker. It reports only completed product work, current phase, phone/X3 mutations, blockers, and the next verifiable outcome.
- [ ] Check signed-in weekly usage and replay growth at task entry and protected stage boundaries. Usage is an advisory planning constraint after explicit user continuation; it must trigger compaction/fresh handoff, not silently stop the product objective.
- [ ] When a workflow correction is discovered, update `docs/codex-usage-workflow.md`, `AGENTS.md`, the dated usage ledger, and a machine-checkable engineering-policy rule. Do not leave it only in chat memory.

## Prompt coverage map

| Prompt family from this chat | Normalized location |
| --- | --- |
| Reading drain, sparse/fast radio, sleep, button wake, battery telemetry, configurable timings, Settings desync | P1 Connection truth, power, sleep, and wake |
| Reader refresh cadence, black pause, image-render speed/cache, charging limit | P1 Reader behavior and reading statistics |
| Static/Live passes, Google Wallet/photo import, API updates, ACK/menu/ghosting, scanner, barcode/QR layouts, timer | P1 Tickets, live data, Focus, and scanner behavior |
| Reading session statistics and offline journal | Closed item under P1 Reader behavior |
| EPUB filtering, row progress, direct-send prompt, background service, Stop/no replay, corrupt partials, covers, deletion/library truth | P0 Book import, transfer, background, and library truth |
| Paired/Available/Reconnecting/Bluetooth-off truth and wireless-debug discovery | P1 Connection truth plus P0 deployment |
| Setup bottom actions, unused whitespace, adaptive sizing, sea/astronaut Light/Dark, Expressive/Minimal | P1 Setup, appearance, responsive layout, and user-facing state |
| Play purchase, seven-day trial, ads/consent, Community build | P2 Google backup and monetization |
| CrossPoint/CrossInk/XTEINK licensing, firmware distribution, art/brand rights, Terms/Privacy | P2 Legal, licensing, branding, and release readiness |
| Canonical paths, policy gate, token accounting, compaction, bounded agents, automatic task opening/continuation | Workflow and continuity requirements |

