# Design

## Context

Two boards share `CountBoard` (`server/appview/.../api/CountBoards.kt`): segment Mayors and dive site Regulars.
- It orders by count, then by the reached date (`MAX(started_on)`, a local `YYYY-MM-DD`), then by athlete.
- It pages with a `"<count>::<date>::<did>"` keyset cursor.
- It gives competition ranks through `ranksOf`, where an entry ties with the one before it on equal (count, date).

`segment_efforts` stores only the activity's local date (`started_on`) and the effort's offset from the activity's start (`start_offset_s`). The activity's UTC start is `activities.started_at` (epoch ms), which the matcher already reads when it writes efforts (`SegmentMatcher.insertEfforts`).

On the clients, a Mayors row is `RankRow` (`:app-common-ui`, and `RankRow` in `RankBoards.swift`) with these inputs:
- a rank string from `RankBoardTexts.rank`;
- `accented`, from `SegmentMayorsState.accents`;
- the detail line from `SegmentBoardTexts.reached`.

The unarchived `segment-board-restyle` change already shapes these rows and the pinned place. This change builds on what it implemented and touches none of its requirements.

## Goals / Non-Goals

**Goals:**
- Give a Mayors board a total order, decided by when each count was reached, with one athlete at each rank.
- Crown the Mayor's row using one rule in Kotlin, drawn by both UIs.

**Non-Goals:**
- Changing Regulars' order or shared ranks.
- Changing the segment page's Mayor card, which already shows the board's first entry.
- Changing the lexicon. `rank` is already an integer, and the reached date is unchanged.
- Counting activities instead of efforts. Laps still count once each, as the spec says.

## Decisions

1. **Store each effort's start instant on the row.**
   - **Change:** add `segment_efforts.started_at BIGINT NOT NULL`, in epoch ms: the activity's `started_at` plus `start_offset_s * 1000`.
   - **Written by:** `insertEfforts`, from the activity it already holds.
   - **Existing rows:** a migration adds the column as nullable and fills it from `activities` with a correlated `UPDATE`, which works in both H2 and Postgres. The column is `NOT NULL DEFAULT 0`, so a row whose activity row is gone keeps only its offset; such rows can't be counted anyway, since boards join their activity's tier.
   - *Rejected: joining `activities` in `rankedEfforts`.* Every board query would carry a join only Mayors needs, and the `HAVING` keyset would aggregate over it.
   - *Rejected: the effort's end.* "Reached" means the effort that made the count, and its start is what athletes see as when they rode.

2. **`CountBoard` takes an optional tie-break, and unshared ranks follow from it.**
   - **New parameter:** `CountBoard` gets an optional `order` of extra aggregate keys after (count, reached), each with its direction. For Mayors these are `MAX(started_at)` ascending, then `MIN(started_at)` ascending.
   - **Mayors (with the keys):**
     - the keyset compares (count, reached, …keys, athlete), and the cursor carries every key: `"<count>::<date>::<k1>::<k2>::<did>"`;
     - `tied` is always false, so each rank is the entry's position, counted from the athletes listed before it.
   - **Regulars (without the keys):** unchanged, with the same three-part cursor and shared ranks.
   - The date stays the second key even though `MAX(started_at)` nearly implies it. The instant is UTC, and the date is local to each activity, so ordering by the instant alone could disagree with the dates the board shows (two athletes in different time zones). Day first, then instant within the day, keeps the order consistent with what's displayed.
   - *Rejected: a Mayors-only board class.* The paging and keyset logic would be copied, and they belong together.

3. **Ranks are positions on the Mayors board.** `ahead` counts the athletes strictly before the entry under the full order, so `rank = ahead + 1`. `entryOf` (the viewer's own place) ranks the same way.

4. **The crown rule lives in the contracts.**
   - **Which row:** `SegmentMayorsState.crowned(entry) = entry.rank == 1`.
   - **Its line:** `SegmentBoardTexts.reached(entry)` gives `mayors_reached_mayor` ("Mayor · Reached %1$s") for a crowned entry, else `mayors_reached`.
   - **Its spoken name:** a new string, `mayors_mayor` ("Mayor").
   - `accents` stays as it is: on Mayors only rank 1, which is now the crown's colour.

5. **`RankRow` takes `crowned`, on both platforms.**
   - **When `crowned` is true:** the rank cell, keeping its fixed width, draws a 28 dp/pt circle filled with the accent's container colour, holding `AppIcon.Crown` tinted to the accent. It carries `mayors_mayor` as its content description and accessibility label, and the rank text isn't drawn.
   - **Compose:**
     - Fill: `tertiaryContainer`. Icon: `onTertiaryContainer`.
     - `AppIcon.Crown` is a simple filled crown drawn on Material's 24-unit grid in `MaterialIcons`, since the legacy Material set the app copies from has none.
   - **iOS:**
     - Fill: `LikeColor` at low opacity. Icon: `LikeColor`. These are the amber the accented ranks already use.
     - The symbol is `crown.fill`, which `generateIosResources` writes into the generated `AppIcon`.
   - **Regulars and the leaderboard** never pass `crowned`.

## Risks / Trade-offs

- **[The order reveals who reached a count first on a given day.]** It doesn't reveal the time of day. The start-time category can be hidden from viewers, and this leaks only the relative order of two efforts on one date, both of which the viewer may already see. → The spec forbids serving any time. The order is the only use of `started_at`, and it is never in an entry.
- **[A Mayors cursor issued before the deploy has three parts and no longer parses.]** → A malformed cursor already reads the board from the start, so a client mid-scroll at deploy time reloads page one. That's acceptable for a board.
- **[The backfill `UPDATE` runs over every effort.]** → It's one correlated update on the primary-key join, run once in the migration. Efforts number in the thousands locally and far fewer than activities' streams in production.
- **[Two efforts can start at the same millisecond]** (a group ride's synced files). → The first counted effort decides, then the DID. That's arbitrary but stable, and it's practically never reached.

## Migration Plan

1. Deploy the AppView with the migration. The matcher writes `started_at` from the moment it runs, and the backfill covers older rows.
2. The clients' crown needs no server support beyond ranks. An older AppView with shared ranks would crown each athlete at rank 1, which is why the AppView ships first.

**Rollback:** an older AppView ignores the column.
