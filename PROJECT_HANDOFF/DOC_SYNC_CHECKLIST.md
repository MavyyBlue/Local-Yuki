# Local Yuki — Doc Sync Checklist

Run after a slice is accepted/certified.

- [ ] Record accepted commit SHA / candidate identifier.
- [ ] Record latest successful relevant CI run.
- [ ] Record app version/versionCode if changed.
- [ ] Record database/schema version if changed.
- [ ] Record signing lineage status if relevant.
- [ ] Update `CURRENT_STATE.md` with only verified implementation facts.
- [ ] Update `ACTIVE_SLICE.md` to the next authorized bounded slice.
- [ ] Update `ROADMAP.md` phase/gate status.
- [ ] Add a concise certified entry to `CHANGELOG.md`.
- [ ] Archive Yuki/Akari/Mio slice handoffs under `PROJECT_HANDOFF/HISTORY/`.
- [ ] Confirm the Model Freeze Gate remains correctly OPEN/CLOSED.
- [ ] Confirm no model-specific implementation detail accidentally became app-owned architecture.
- [ ] Confirm autonomy/capability rules remain consistent with the North Star.
- [ ] Confirm substrate opacity remains intact.
- [ ] Confirm unresolved defects/risks are explicitly carried forward.
