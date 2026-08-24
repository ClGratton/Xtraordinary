# Interactive transport lifecycle

This is the canonical contract for any Android workflow that knows it will exchange data with X3 in both directions. It is deliberately feature-agnostic: Passes, a bounded transfer, firmware management, settings, or a future tool all use the same owner-based primitive.

## Why it exists

An open GATT connection is not necessarily responsive. In the battery-sensitive steady state, X3 may use the configured 1/2/4-second connection interval. A multi-command transaction that begins on that interval can take many seconds even though Android reports the device as connected.

The app therefore distinguishes:

- transport connected: encrypted GATT and notifications are available;
- interactive owner present: at least one caller currently requires responsive two-way traffic;
- lease applied for protocol session: X3 has been told to use the fast connection policy for the current capabilities sequence.

## Reusable ownership contract

`InteractiveTransportCoordinator` owns a set of opaque `InteractiveTransportOwner` values. It contains no feature names and no navigation rules.

| Event | Required behavior |
|---|---|
| First owner acquired | Keep/recover GATT, apply the interactive lease, and start the shared renewal watchdog. |
| Additional owner acquired | Reuse the same session; do not create another renewal loop. |
| Fresh capabilities sequence | Replay the lease once because this is a new protocol session. |
| Bounded transaction begins | Acquire a scoped owner and force one bounded balanced/intermediate lease reassertion before the first transaction command; bulk-transfer priority remains exclusive to bulk payloads. |
| One of several owners released | Keep the shared session for remaining owners. |
| Last owner released | Stop renewal, request balanced/slow connection parameters, then release GATT if no durable work needs it. |
| GATT disconnects | Firmware clears the session lease. Android replays it only if an owner still exists after reconnect. |

The current timing contract is a 120-second X3 watchdog renewed every 90 seconds while at least one owner exists. This is not four-second polling: renewal is one control message before watchdog expiry. Normal app/device commands and notifications remain event driven.

## Caller rules

Long-lived UI or mode callers translate their own visibility/runtime state into `acquireInteractiveTransport(owner)` and `releaseInteractiveTransport(owner)`. The shared coordinator must not import or inspect UI state.

Bounded operations use `withInteractiveTransport(owner) { transaction() }`. This guarantees that a transfer started after a screen has been open for a long time does not inherit the slow interval, and that temporary ownership is released even when the transaction fails.

Persistent slow modes are separate from interactive ownership. A displayed Live ticket or running Focus session keeps X3 awake and pulse-discoverable at the configured 30/60/120-second standby check-in; it does not by itself retain GATT. Pending settings, deletion, upload, control, or live-data changes reconnect at a pulse and use the separate connected interval. Those needs do not automatically imply a continuously fast link.

## Failure and reconciliation rules

- Android desired state, ACKed X3 state, and cached capabilities remain distinct.
- A lease has no durable feature state and is never treated as proof that a command succeeded.
- If lease delivery fails, its capabilities sequence is marked unapplied so a later attempt can retry.
- A disconnect clears the firmware lease and invalidates the old connection session.
- Bluetooth bond data is never reset as part of lease recovery.
- A visible state that implies device acceptance is committed only after its matching ACK. Before that, retain the last applied state and expose a pending state. Durable pending intent may replay after reconnect or process death, but replay must never make the phone appear active before X3 accepts it.
- A command that needs responsive two-way traffic acquires the shared fast lease before its first state-changing message. If X3 is asleep or unreachable, keep the command pending and tell the user to wake the device; do not relabel a bonded device as unpaired.
- USB receipt is itself device activity. Firmware restores full-speed processing and extends the shared inactivity deadline at the external-command boundary so USB operations do not depend on a physical wake press.

## Mandatory policy enforcement

`docs/engineering-policy.json` declares the machine-checkable rules. `scripts/check-engineering-policies.ps1` evaluates them before either canonical build wrapper starts Gradle or PlatformIO. The gate currently verifies the reusable coordinator boundary, lifecycle tests, reconnect replay, transaction-scoped ticket use, firmware disconnect cleanup, application-only firmware offset, bond-safe wrapper text, required docs, and wrapper integration.

The gate intentionally does not claim to prove subjective layout quality, runtime radio current, physical barcode scanning, or end-to-end ACK behavior. Those require screenshot tests, unit/integration tests, logs, and hardware acceptance respectively.
