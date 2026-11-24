## 1. Lexicon and AppView

- [x] 1.1 `feed/like.json` names comments as subjects; `feed/defs.json` adds `commentView.likeCount` and `#commentViewerState { like }`; mirror both in `core/lexicon`
- [x] 1.2 `commentViews` takes the subject and viewer, counts each comment's likes by who may see it and fills `viewer.like`; update its callers
- [x] 1.3 AppView tests: count, viewer's like, a giver who can't see the comment, a blocked giver, duplicate likes, a like on a reply capped by its root's audience

## 2. Client data

- [x] 2.1 Like and unlike a comment, placed by its effective tier; skip cached feed counts for comment subjects; rejected-change handling for comment likes
- [x] 2.2 Lay local like overrides over listed comments in `rememberComments`
- [x] 2.3 Data and presenter tests

## 3. UI

- [x] 3.1 `CommentEvent.Like`, `CommentAction.Like`, row texts and model fields (`CommentsTexts.rows`) for both UIs
- [x] 3.2 Compose comment row: like button and count after Reply; own comment shows the count only
- [x] 3.3 iOS `CommentRowView` the same
- [x] 3.4 Fixtures, UI tests, Android screenshots, iOS snapshots, `./gradlew check`
