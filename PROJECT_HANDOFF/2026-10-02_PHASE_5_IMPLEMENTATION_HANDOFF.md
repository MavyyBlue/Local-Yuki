# Local Yuki — Phase 5 completion implementation handoff

**Date:** 2026-10-02

**Phase / slice:** Phase 5 — living memory and semantic-recall socket completion

**Status:** CANDIDATE. Developer verification is not independent certification.

## BASELINE IMPLEMENTED AGAINST

Live main overlay `59eb7f8cb449ca47b9d17e66be4dbe877f766abb`, which already contains
schema 4 and a Phase 5 implementation. Certified Phase 4 remains
`12f8654c4809a1bb1a94cb1e0bda7ebd6d00dc21` / Android CI #22 / phone PASS.
The [architecture handoff](2026-10-02_PHASE_5_ARCHITECTURE_HANDOFF.md) records the
owner-authorized scope after fresh source inspection.

## FILES CHANGED

- `LivingMemoryStore.kt`: transactional projection refresh, source validation,
  revision-local recall metadata and reliable replay.
- `DeterministicLexicalRecallEngine.kt`: current-head joins, bounded coverage,
  validated hints, optional-adapter fallback and detached reranker inputs.
- `LivingMemoryContracts.kt`: canonical Unicode term validation.
- `LivingMemoryStoreTest.kt`: eight additional behavioral regression tests.
- `scripts/check.py`: proxy-aware Gradle/test JVM launcher.
- `.github/workflows/android-build.yml`: run the existing fixed-signing workflow
  on all `phase-*` candidate branches rather than only `phase-1a-*`.
- README, handoff index, active slice, current state and roadmap: distinguish live
  candidate from certified baseline and document reproducible verification.

`CHANGELOG.md` remains reserved for certified changes, as its own policy requires.

## MIGRATIONS

No additional physical migration in this completion patch. The inherited candidate
already advances schema 3 → 4 via `2026-09-25-living-memory-v1`. It adds surface
metadata/state/terms and append-only meaningful-recall events without replacing
Phase 4 tables. Existing tests exercise populated v3 → v4, v2 → v4, full state
migration and transaction rollback on failure.

## NEW / CHANGED INTERFACES

`recall(query, reranker, candidateEngine)` accepts an optional model-neutral
`SemanticCandidateEngine`. Throwing, unavailable or structurally invalid advisory
results fall back to lexical lookup. A valid adapter's kind mismatch is filtered
against authoritative memory, and its coverage is conservatively partial.

`LivingMemoryPolicyV1.validTerm` validates bounded, lower-case Unicode alphanumeric
terms. Existing cognition-facing readers retain no mutation route.

## BEHAVIOR IMPLEMENTED

Importance and meaningful reinforcement refresh abstraction and lexical terms in
the same transaction as their state revision. The trusted deep read happens
before the write lock; the source head is checked inside that lock. This avoids
cross-helper exclusive-lock waits while retaining optimistic conflict checks.

When an owner correction/restore advances the source head, reconciliation resets
that revision's reinforcement, recall count and last-recall timestamp. Importance
remains attached to the durable memory. Old recall events remain immutable.

Exact event replay works after source changes and without a clock. Conflicting ID
reuse fails. A transaction rechecks event replay to handle an intervening commit.
Last-meaningful-recall time never moves backward after clock rollback; overflowing
state revisions fail before any write.

Surface terms are checked against their bounded source revision, including
historical heads. Corruption fails closed; reconciliation does not silently repair
a corrupted projection or alter deep evidence.

Lexical queries join current durable heads before limiting results, so stale terms
cannot starve valid candidates. Each term probes at most 201 rows: 200 candidates
plus one truncation sentinel. Truncated lookup reports PARTIAL even when persisted
index coverage is COMPLETE. Grounding ignores absent/stale IDs but propagates a
failed read of an existing current authority, including evidence corruption.

Rerankers receive detached collections, including nested evidence, surface terms
and matched terms. Only a valid identity permutation changes ordering; returned
content is always reconstructed from the untouched grounded inputs. Invalid or
throwing rankers fall back to the existing lexical order.

## AUTHORITY BOUNDARIES PRESERVED

No raw evidence, durable content, provenance, history, identity, personality,
permissions, signing certificate or model-freeze boundary is changed. Search does
not strengthen memories. Trusted app plumbing alone invokes meaningful-consumption
and importance mutation. Semantic hints/ranking remain advisory.

## TESTS ADDED / CHANGED

Additional regression cases cover atomic importance/reinforcement projection
refresh; clock rollback and replay after clock loss/head changes; stale-index
starvation; throwing/unavailable/invalid/mutating/forging adapters; malformed hints
and corrupt projection isolation; deep evidence corruption propagation; truncated
coverage with 201 matches; and episode lookup beyond 100 historical revisions.

Existing regressions cover migration/rollback, process reconstruction, evidence
immutability, aging, optimistic conflicts, ordered reconciliation and exact query
plans using `idx_living_term_lookup` without temporary sorting.

## TEST RESULTS

Developer checks in the managed Linux workspace, JDK 17 / Android SDK 35:

- `:foundation-contracts:check`: PASS — 22 tests, zero failures/errors/skips,
  plus the pure JVM source/dependency boundary guard.
- `:app:testDebugUnitTest`: PASS — 35 tests, zero failures/errors/skips.
- `:app:assembleDebug`: PASS — unsigned development APK.
- `:app:lintDebug`: PASS — zero errors; nine existing warnings about SDK/dependency
  freshness, bootstrap text internationalization and missing application icon.

The initial test runtime download failed because Gradle-only proxy settings did
not reach the Robolectric JVM. The checked-in launcher forwards the inherited
proxy through `JAVA_TOOL_OPTIONS`; the actual Android tests then ran successfully.

## RESOURCE / PROFILE NOTES

Explicit reconciliation pages remain capped at 50. Query input remains capped at
512 UTF-8 bytes, 12 terms and 20 grounded outputs; each term lookup is capped at
200 candidates plus a sentinel. Source validation uses exact revision lookups and
at most 48 terms. No startup scan, worker, service, receiver, model residency or
polling was introduced. Real-device battery/thermal measurements remain pending.

## MODEL COUPLING INTRODUCED OR REMOVED

None. No neural runtime, weights, embedding dependency, tokenizer, prompts or
network dependency was added to application/foundation code. Pure JVM interfaces
accept deterministic and mock adapters. Model Freeze Gate remains CLOSED.

## KNOWN LIMITATIONS

The application is still a foundation bootstrap, without conversational cognition
or a final model. Lexical abstraction is deterministic term reduction, not a
language-generated summary. Semantic fallback searches only reconciled surface
terms; original content/history remains available through the separate exact
Phase 4 reader. Grounding is a point-in-time read, not a promise that a source head
can never advance after returning a candidate. Maintenance remains explicit.

No fixed-signing secrets, emulator or target phone were supplied to this workspace.
The local unsigned APK is build evidence and must not replace the fixed-signed
installation. Independent Mio review, exact-candidate signed CI and real-phone
acceptance remain pending; no self-issued Mio PASS or certification is claimed.

## CI EXPECTATION / RESULT

The candidate branch is eligible for the existing Android workflow, including
fixed-key restoration, tests, build, signing-certificate verification and APK upload.
Remote evidence must identify the exact candidate commit. Local test results above
do not stand in for that signed CI run.

Publication was attempted through both the connected GitHub Git-tree API and
`git push`; both returned HTTP 403. The connected app can read repository metadata
but could not write this candidate. No remote branch or PR was created, and no
remote CI result is claimed. The candidate is committed on the local
`phase-5-completion` branch with a clean working tree.

Delivery workaround: `local-yuki-source.zip` is a manifest-bearing changed-file
overlay for the existing mobile importer. Upload it at the repository root to
import the source through that established owner workflow. The importer preserves
protected `.github/workflows/` files, so the branch-trigger expansion is delivered
only in the full-source archive and Git patch, not in the mobile overlay. Importing
to `main` still uses the existing `workflow_run` signed-build path. Verify the
expanded commit's CI run and signing certificate before phone installation.

## PHONE-TEST ITEMS

1. Install the candidate's fixed-signed CI artifact as an update to the accepted
   Phase 4 APK. Preserve existing app data; do not uninstall/reinstall.
2. Launch: Continuity, Yuki State and Memory should report restored; Living Memory
   initializes on first v3 → v4 upgrade; Semantic Recall reports lexical-ready.
3. Force-stop and relaunch: Living Memory should report restored, other authorities
   remain restored, and temporal grounding shows the device's current date/zone.
4. Rotate/recreate and relaunch again; check for crashes, hangs or repeated resets.
5. Report exact CI commit/run and observed statuses to complete acceptance.

After independent review, signed CI and phone acceptance, Yuki may promote Phase 5
to the certified documents/changelog and issue a bounded Phase 6 architecture
handoff. Until then, Phase 4 remains the certified baseline.
