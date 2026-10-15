# AlienFlow(chat) — app switching and cursor confirmation, 0.1.24-chat.1 (25)

Repository: IssisX/AlienFlow. Branch: AlienFlow(chat). Baseline f63ae375f00909a3bf5d7c4bce78ef7af23ba58d; installed baseline compiled from adda87cd9bc2e0f3cb491a35ab9a8d9877347c4f.

## Phone observation

The supplied Fold6 / Android 16 report at 2026-10-14T22:35:44Z identifies 0.1.23-chat.1 (24). Capture, server session and transcript saving succeed. Latest rejection is AF-E14 after dispatch: keyboard local cursor 2:2, offset=-1, absolute callback 0:0, evidence 11. This produces a negative inferred offset and rejects confirmation. The separate messaging unavailable-focus snapshot occurred during the preceding finishing session; it does not identify the latest rejection's app. No connection failure or new process crash was recorded.

## Implemented changes

Unknown-offset keyboard windows use cursor-relative ownership, independent of stale absolute callbacks and accessibility presentation. Validate local selection, every overlapping anchor and the complete replaced suffix. First insertion uses the current selection; revisions delete only observed owned text directly before the cursor, confirm that deletion, then commit once and confirm actual returned text/cursor. Known-offset edits retain their existing coordinate route. Cancellation and manual typing retain the ownership guards. Input lifecycle epochs reject operations from replaced connections; a same-editor restart preserves the binding while retiring old in-flight work.

While active, losing a field releases its writer while capture continues. Each focus boundary commits the preceding audio under the transport's capture lock. Startup frames retain their editor epoch across the handshake. Acknowledged audio items retain that epoch through deltas, finals and cleanup. The coordinator writes only the current field's segment. Speech captured with no eligible field and delayed old-field results remain in the complete history transcript. Returning to an app binds afresh and appends only its new segment. Termux receives only its current final segment once after Stop, without Enter. Switching while finishing preserves the pending result instead of adopting a new target. Cancellation can restore only the current field's still-owned span; earlier field segments stay untouched.

No microphone restart, keyboard replacement, app allowlist, timeout increase, automatic resending, AAB or provider/model change. Original signing identity, package, settings, artwork and stored user data are preserved.

## Verification status

Implementation is not yet runtime-verified. The release workflow must reproduce the stale-cursor and repeated-field cases against the shipped 0.1.23 source, run the current unit/lint/build gates, and exercise browser and actual installed Termux input IPC including an active app switch and delayed old-field result. Transcript/hardware fixtures remain explicitly labeled. Fold6 acceptance remains unverified until the owner installs the new APK.

Previous evidence: [0.1.23 checkpoint](history/IMPLEMENTATION_CHAT_0.1.23-chat.1.md).
