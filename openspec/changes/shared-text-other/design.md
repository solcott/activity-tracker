# Design

## Context

`AppText.joined` already joins a list of names through a shared string (`text_list_pair`). The public notes and the end dialog are
lists of `AppText` that each UI resolves and joins itself. Bullets are drawn three ways: Compose writes "• " before each line, Swift's
Account does the same, and Swift's Leave draws the mark in an `HStack` beside the line (a hanging indent). Leave's deleted and stays
lines are listed in each UI from the strings.

## Goals / Non-Goals

**Goals:** one join per kind of piece, one bullet mark and one bullet layout per platform, every list's lines in Kotlin.

**Non-Goals:** wording changes; other lists drawn without a mark (Leave's places, the sign-in lines).

## Decisions

- **Join through strings, not in Kotlin code.** `AppText.sentences`/`paragraphs` fold the list with `text_sentence_pair`/
  `text_paragraph_pair`, like `joined`, so a language that joins sentences without a space can say so in its strings. A plain
  `joinToString` on resolved text would need each UI to resolve first, which is what we are removing. Both reduce to null for none; the
  callers here always have at least one line (`publicNote` always has its first sentence; the end dialog shows only with work pending,
  and `endMessage` reads as empty text otherwise).
- **`\n` in strings.xml.** Compose resources and `generateIosResources` (`androidText`) both undo Android's `\n` escape, so
  `text_paragraph_pair` is `%1$s\n\n%2$s`. A Compose UI test checks the resolved string has the blank line.
- **Change the functions' return type.** `publicNote` returns `AppText` rather than adding `publicNoteText` beside the list: no other
  caller needs the list, and the tests can compare the joined structure.
- **Bullets with a hanging indent everywhere.** Swift's Leave layout (mark, then the line, wrapping under the line) reads better than a
  "• " prefix, and SwiftUI and Compose can both draw it. The mark is a string (`text_bullet`, "•") through `LabelText.bullet`, so a
  locale can use its own. The layout stays per platform: one `Bullets` composable (internal to `:feature-settings-ui`, the only feature
  that draws bullets) and one Swift `BulletList`.
- **Country lookup.** `CountryNames.name(of:locale:)` becomes static, so the profile need not build every country's name; the instance
  method calls it with its locale.
- **FIT failures.** No change: `UiError`'s text never includes a failure's message (ui/error-messages), and batch scans turn failures
  into `SkipReason`s. The Swift messages stay as developer text in the exception.

## Risks / Trade-offs

- The hanging indent changes how a wrapped bullet line looks on Android and in iOS Account: Android references and iOS snapshots that
  wrap a bullet line change and are looked at.
