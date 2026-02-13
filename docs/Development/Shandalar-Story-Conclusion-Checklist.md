# Shandalar Story Conclusion — Implementation Checklist

## 0) Scope Lock
- [ ] Target **Shandalar** only.
- [ ] Keep existing story through quest `52` intact.
- [ ] Use captive mapping: **Liliana (B), Chandra (R), Garruk (G), Jace (U), Ajani (W)**.
- [ ] Keep castle arc as captives; use separate attunement quests/dungeons.
- [ ] Accept legacy shared-data layout (`.../common/world/*`) as an engine constraint. This checklist may update shared resources where required.
- [ ] Scope guard for this PR: implement Shandalar story flow (`quests.json`, Spawn dialogue flow, story flag contract, quest-stage wiring). Cross-plane balancing/cleanup side effects from shared resources are out of scope for this PR.

## 1) Quest Chain (`forge-gui/res/adventure/Shandalar/world/quests.json`)
- [ ] Add quest `54`: debrief + captive identity reveal.
- [ ] Add quest `55`: Black attunement (`Liliana's Stone`).
- [ ] Add quest `56`: Red attunement (`Chandra's Stone`).
- [ ] Add quest `57`: Green attunement (`Garruk's Stone`).
- [ ] Add quest `58`: Blue attunement (`Jace's Stone`).
- [ ] Add quest `59`: White attunement (`Ajani's Stone`).
- [ ] Add quest `60`: Fivefold convergence finale.
- [ ] In Spawn NPC post-castle continuation (`forge-gui/res/adventure/common/maps/map/main_story/spawn.tmx`), set `mainQuest=3`, `post52_started=1`, and `issueQuest: "54"` in the same action block.
- [ ] Gate that Spawn continuation branch on `post52_started` not set, so quest `54` is not re-issued on repeat dialogue.
- [ ] Set `q54_complete=1` in quest `54` epilogue/progression completion step.
- [ ] Ensure exact handoff: `52 -> 54 -> 55 -> 56 -> 57 -> 58 -> 59 -> 60`.
- [ ] Add explicit out-of-order reconciliation stages for `55–59` (no implicit engine auto-complete assumptions).
- [ ] For each attunement quest (`55–59`), include a Donovan reconciliation step that:
- [ ] checks if `attune_<color>=1` and immediately advances if true;
- [ ] else checks if player already owns the required stone and sets `attune_<color>=1` if true;
- [ ] else sends player to the normal encounter/POI flow.
- [ ] Ensure reconciliation is idempotent: re-entering dialogue after completion does not re-award progression flags.

## 2) Flag Contract (must match exactly)
- [ ] **Authoritative flag set for this arc**: `post52_started`, `q54_complete`, `attune_black`, `attune_red`, `attune_green`, `attune_blue`, `attune_white`, `fivefold_ready`, `convergence_complete`, optional `convergence_enhanced`, and one-time recovery flags `stone_recover_black`, `stone_recover_red`, `stone_recover_green`, `stone_recover_blue`, `stone_recover_white`.
- [ ] `55` writes only `attune_black=1`.
- [ ] `56` writes only `attune_red=1`.
- [ ] `57` writes only `attune_green=1`.
- [ ] `58` writes only `attune_blue=1`.
- [ ] `59` writes only `attune_white=1`.
- [ ] `59` sets `fivefold_ready=1` only if all five `attune_*` are true.
- [ ] `60` requires `fivefold_ready=1`.
- [ ] `60` sets `convergence_complete=1`.
- [ ] Optional branch in `60` may set `convergence_enhanced=1`.
- [ ] No quest writes multiple `attune_*` flags.
- [ ] `post52_started` is set exactly once at first post-`52` bootstrap/issue of quest `54`.
- [ ] `q54_complete` is set exactly once when quest `54` completion resolves.
- [ ] Each `stone_recover_*` flag is one-time and only set when issuing a replacement stone grant.

## 3) Item Data (`forge-gui/res/adventure/common/world/items.json`)
- [ ] Add `Garruk's Stone` (`questItem: true`).
- [ ] Add `Jace's Stone` (`questItem: true`).
- [ ] Add `Ajani's Stone` (`questItem: true`).
- [ ] Keep `Chandra's Stone` and `Liliana's Stone` as finale-required items **and** make them non-lossable for story-critical flow (mark `questItem: true` or equivalent non-removable handling).
- [ ] Keep `Challenge Coin` variants untouched.

## 4) Enemy/Reward Wiring (`forge-gui/res/adventure/common/world/enemies.json`)
- [ ] Add a dedicated Ajani story boss deck at `forge-gui/res/adventure/common/decks/miniboss/ajani_story.dck`.
- [ ] Add Ajani story boss entry using `decks/miniboss/ajani_story.dck`.
- [ ] Do **not** reuse `decks/standard/whitewizard_easy_ajani.dck` (it is part of randomized minor-enemy pools).
- [ ] Ensure Ajani reward includes `Ajani's Stone`.
- [ ] Green/Blue story encounters must be deterministic story bosses (existing `Garruk` / `Jace` boss entries or explicit story variants), never randomized minor-enemy pools.
- [ ] Keep existing Garruk/Jace signature rewards (`Garruk's Mighty Axe`, `Jace's Signature Hoodie`) intact.
- [ ] Append `Garruk's Stone` to `Garruk` rewards and append `Jace's Stone` to `Jace` rewards.
- [ ] Do not break existing Chandra/Liliana reward drops.

## 5) POI + Map Wiring
- [ ] Reuse existing `Garruk Forest` for green attunement.
- [ ] Reuse existing `Jacehold` for blue attunement.
- [ ] Add new white story POI for Ajani in `forge-gui/res/adventure/common/world/points_of_interest.json`.
- [ ] Add one new white dungeon map under `forge-gui/res/adventure/common/maps/map/main_story/`.
- [ ] Explicitly wire quest `57` stages to Garruk flow (POI/tag + map encounter + deterministic boss + attunement completion step).
- [ ] Explicitly wire quest `58` stages to Jace flow (POI/tag + map encounter + deterministic boss + attunement completion step).
- [ ] Explicitly wire quest `59` stages to Ajani flow (POI/tag + map encounter + deterministic boss + attunement completion step).

## 6) Narrative Consistency Requirements
- [ ] Preserve Donovan/Viv/Acirxes continuity.
- [ ] Do not contradict shard mines/library/staff setup.
- [ ] Keep tone: Donovan morally ambiguous, not random retcon villain.
- [ ] Keep current implication: rescuing major walkers is the core thread.
- [ ] Replace the current Spawn NPC “end of currently available story” developer-note branch with canonical continuation dialogue that leads into the new post-`52` questline.

## 6A) Legacy Save Compatibility Contract (Issue 5)
- [ ] **Must use the same full flag set as Section 2**: `post52_started`, `q54_complete`, `attune_black`, `attune_red`, `attune_green`, `attune_blue`, `attune_white`, `fivefold_ready`, `convergence_complete`, optional `convergence_enhanced`, and `stone_recover_black/red/green/blue/white`.
- [ ] Bootstrap rule: if `mainQuest >= 3`, `post52_started` is unset, and `q54_complete` is unset, Spawn NPC must issue quest `54` once and set `post52_started=1`.
- [ ] Re-entry rule: if `q54_complete=1`, Spawn NPC must never issue quest `54` again.
- [ ] Duplicate active story-quest prevention is tracked as a separate bug/PR and is explicitly out of scope for this checklist.
- [ ] Bootstrap recovery hole fix: if `post52_started=1`, `q54_complete` is unset, and quest `54` is neither active nor completed, Spawn NPC must re-issue quest `54` once (without resetting prior rewards/progression flags).
- [ ] Stone backfill rule: if player is on/after quests `55–59`, lacks the required stone, and already cleared the corresponding source content in older saves, Donovan dialogue provides a one-time replacement grant.
- [ ] Recovery flags: use one-time flags `stone_recover_black`, `stone_recover_red`, `stone_recover_green`, `stone_recover_blue`, `stone_recover_white`.
- [ ] Missing-stone recovery must work for old saves where stones were lost before `questItem` protection existed.
- [ ] Out-of-order rule A: if a player already owns a required stone before its attunement quest becomes active, the quest must validate immediately without requiring a repeat boss fight.
- [ ] Out-of-order rule B: if `attune_<color>=1` is already present before that quest is issued, the quest must fast-forward/auto-complete to the next step.
- [ ] Out-of-order implementation requirement: perform the checks above via explicit dialogue/stage actions at quest entry (and Donovan fallback), not via implicit quest-engine state refresh.
- [ ] Repeat-battle rule: duplicate stone drops are allowed, but progression treats stones as binary possession only and never grants extra attunement flags from duplicates.
- [ ] POI activation rule: keep minimum-threshold activation (`mainQuest` minimum value) for legacy compatibility; tighter activation scoping is a separate bug/PR.

## 7) Optional Relic Branch (non-blocking)
- [ ] Optional checks may reference: `Strange Key`, `First Shard`, `Second Shard`, `Third Shard`, `Fourth Shard`, `Fifth Shard`, `Nahiri's Key`, `Sorin's Key`, `Tibalt's Key`.
- [ ] Optional branch must never block base completion of `60`.

## 8) Validation Checklist
- [ ] Fresh run from quest `52` transitions to `54` once.
- [ ] Blue path explicitly grants/validates `Jace's Stone`.
- [ ] Green path explicitly grants/validates `Garruk's Stone`.
- [ ] White path explicitly grants/validates `Ajani's Stone`.
- [ ] Garruk still grants `Garruk's Mighty Axe` in addition to `Garruk's Stone`.
- [ ] Jace still grants `Jace's Signature Hoodie` in addition to `Jace's Stone`.
- [ ] Finale cannot complete without all five attunements.
- [ ] Legacy matrix L1: save at quest `52` cleanly bootstraps into `54` once.
- [ ] Legacy matrix L2: `mainQuest=3` + no `post52_started` + no `q54_complete` issues `54` on first Spawn talk only.
- [ ] Legacy matrix L3: `q54_complete=1` never re-issues `54` on repeated Spawn interactions.
- [ ] Legacy matrix L4: pre-patch saves with cleared source content but missing stones can recover each missing stone once via Donovan.
- [ ] Legacy matrix L5: player with pre-owned stone completes matching attunement step without forced refight.
- [ ] Legacy matrix L6: repeated Garruk/Jace/other stone-source boss fights do not alter attunement flags after first attunement is set.
- [ ] Legacy matrix L7: post-`52` POIs activate for `mainQuest` values above the minimum threshold.
- [ ] Legacy matrix L8: `post52_started=1` + no active/completed `54` + `q54_complete` unset re-issues `54` correctly and remains idempotent on repeated talks.
- [ ] Legacy saves near `52` do not duplicate rewards or soft-lock.
- [ ] Post-finale state remains stable; side-quest loop still works.

## 9) Done Definition
- [ ] All new quests (`54–60`) load and progress without dead ends.
- [ ] All flag writes/reads match Section 2 exactly.
- [ ] All five stones are represented in gameplay and finale gating.
- [ ] No contradiction with existing implemented dialogue implications.
