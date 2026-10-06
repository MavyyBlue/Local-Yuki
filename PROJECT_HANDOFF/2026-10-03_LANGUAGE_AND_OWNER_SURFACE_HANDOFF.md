> Historical handoff. Current source status, owner-directed verification and freeze removal are recorded in [CURRENT_STATE](CURRENT_STATE.md). Stale CI/assistant requirements below are historical, not active gates.

# Local Yuki — Language role and adaptive owner surface

Owner clarification, 2026-10-03: the subsystems collectively form the brain; the
phone is the body. The language model handles language expression. Reasoning,
meaning selection, decisions and action plans belong to the cognitive subsystems.
A later voice model turns the accepted words into sound. This supersedes older
North Star/Phase 17 wording assigning creative reasoning or tool plans to the
Language & Expression engine. Those architecture documents have been corrected.

## Implementation

The language socket now accepts `ExpressionRequest(PreparedMeaning)` instead of
`YukiTurnContext`. Prepared meaning includes bounded points, grounded evidence
references and explicit uncertainty. `MeaningComposer` runs after the decision /
optional reasoning stages and before language expression. The coordinator rejects
invented composition evidence before invoking language and limits expression
references to the composed sources. Language receives no full thinking workspace,
raw user message, capabilities or execution handles. Action plans continue through
Executive Control rather than expression. The default composer is an honest
receipt acknowledgement, not an implemented general thinking/answer engine.

The home screen now contains a compact header/status, message editor, save action
and memory/note entry point. Grouped dialogs contain local-note permissions, rest /
maintenance, Vault, models, detailed status and developer checks. Search results
appear only when requested and can be cleared. Existing authority/storage behavior
is preserved; no schema, signing, permission or model runtime change is needed.

The activity explicitly handles API 30+ system bars, cutouts and IME insets,
using their maximum rather than adding overlapping navigation and keyboard space.
A scrollable safe viewport and measurement-time maximum width adapt to reduced
height, narrow screens and landscape/tablets. API 26–29 use the fitted legacy decor
and `adjustResize`. The focused editor requests visibility after keyboard changes.
There are no fixed-height content panels; text can wrap and content can scroll.
The message editor retains its text through Android view-state restoration.

## Verification and limits

Behavioral regression tests cover decision → reasoning → meaning → wording order,
rejection before expression for invented sources, and preserved original evidence.
An API 35 layout test covers a narrow viewport, cutout/navigation exclusion,
keyboard opening/closing, scrolling and wide-screen width constraints. Full developer verification passed: 32 foundation + 51 Android tests
(83 total), zero failures/skips, boundary verification, unsigned assembly and lint
with zero errors. Real Galaxy S26+ layout/keyboard acceptance and independent QA remain
pending; JVM layout tests do not prove every OEM keyboard or animation behavior.

No real model is connected yet. Reference checks do not prove generated wording
semantically preserves all meaning; future language adapters need evaluation for
omissions, uncertainty preservation and invented claims. Speech synthesis and
incoming-language interpretation remain later separate sockets.

## Phone check

Upgrade with the fixed-signed CI APK and preserve data. Check the compact home
screen with gesture navigation and/or three-button navigation, portrait/landscape,
keyboard open/closed, multiline input and a larger system font. The editor should
remain reachable by scrolling inside the safe viewport; menus/dialogs should stay
within Android's usable window. Find memory/note actions below the editor; open
Menu for permissions, rest, Vault, models and detailed status. Verify existing
memories and Vault functions remain accessible.
