# X3 companion diagnostic state matrix

The state meanings and timer ownership in the
[X3 companion connection and power contract](companion-connection-power-contract.md)
are authoritative for this matrix.

Use the diagnostic firmware and a debug Android build for power and connection
qualification. The app requests a snapshot only:

- after encrypted GATT setup;
- after it acknowledges a device-state revision;
- after a power-setting command is acknowledged; or
- when **Refresh diagnostics** is tapped.

There is no periodic diagnostic timer. Continuous serial logging is prohibited
because opening USB CDC can reset the X3 and logging changes the power result.

The `XtraordinaryDiag` Android log line and the debug-only Status card expose:

- activity and sync mode;
- connected, advertising, bonded-phone-present, first-pair-search-active,
  Home-pairing-notice-visible, state-confirmed, and low-power-reading flags;
- reset and wake reason;
- requested connection profile (`1` normal, `2` reading);
- normal and reading check-in settings plus home sleep timeout;
- uptime, CPU frequency, and free heap;
- connect, disconnect, state-transition, status-send, status-confirm,
  connection-parameter-request, command, NACK, and advertising-start counts.

## Required scenarios

| Scenario | X3 expected state | Android expected state | Diagnostic gate |
| --- | --- | --- | --- |
| Home, connected | `Awake / Fast` | Connected | State confirmed; profile `1` after the parameter delay |
| Unbonded first-phone search, before two minutes | `Awake / Fast`; Home and controls fully usable; connectable advertising | Foreground scan may discover and pair | No stored bond; first-pair search active; notice hidden |
| Unbonded first-phone search expires at two minutes | `Awake / Fast`; Home and controls remain fully usable; pairing-paused chip appears; first-discovery advertising stops | Does not claim Sleeping and does not pretend opening the app alone can connect | No stored bond; first-pair search inactive; notice visible; advertising inactive; controls and reader still work |
| Bonded phone absent beyond two minutes | `Awake / Fast`; Home remains fully usable and connectable; no unpaired chip | Remembered; one foreground attempt, then the configured normal retry cadence | Stored bond remains true; first-pair timeout never fires; reconnect succeeds without pairing again |
| Reading, connected | `Reading / Slow` | `Reading - low-power sync` | State confirmed; low-power-reading true; profile `2` |
| Reading, disconnected | Reader remains usable; sparse phone check-in policy does not change pages or wake the display | Remembered as Reading during configured grace, not immediately Disconnected | Reconnect snapshot increments disconnect/connect and remains Reading |
| Intentional sleep | Final `Sleeping / Off` revision is sent and acknowledged before bounded shutdown | Sleeping; no reconnect loop | No polling, advertising, or hourly wake after deep sleep |
| Power-button wake | Cold/deep-sleep boot, then connectable advertising | Finds X3 within 15 seconds and reconnects without pairing again | New snapshot reports reset/wake reason and a fresh advertising start |
| Settings write | Apply normal interval, reading interval, and home sleep timeout | Command ACK, then status/read-back | Diagnostic values exactly equal the selected values; NACK count unchanged |

## Sleep rule

True sleep has no poll interval. On battery, the X3 power latch removes MCU
power, so an hourly wake is neither free nor reliably available. Connected
shutdown sends `Sleeping / Off` and waits up to 750 ms for the phone to confirm
that revision. A normal timeout or long power press renders the configured
CrossPoint sleep screen first. The unbonded two-minute first-search timeout is
not a sleep trigger: it only stops first-discovery advertising and shows a
nonmodal chip on the still-live Home screen. After actual shutdown, the app
persists Sleeping and does not scan until the user physically wakes the X3.

## Pass order

Run the scenarios in table order, then repeat Home connected, power-button
shutdown, power-button wake, and reconnect three times. Only after all
functional gates pass may a timed battery measurement begin. Record starting
and ending battery percentage, elapsed time, activity, connection state, sync
settings, and the before/after diagnostic snapshots.
