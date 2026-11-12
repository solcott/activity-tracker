# ui/ios-design-system Specification

## Purpose
What the native iOS app's own controls and its loaded-content layout do, so every native screen looks and behaves alike and follows the app's rules for errors and loading.

## Requirements

### Requirement: Native screens share one set of controls
A native iOS screen SHALL draw its main, secondary and plain actions, its icon buttons, text fields, list rows, cards and progress with the app's native controls, so the same control looks and behaves the same on every native screen, in light and dark.

#### Scenario: The main action in dark
- **WHEN** a screen's main action is shown with the dark appearance
- **THEN** its label is readable against the app colour that fills it

#### Scenario: A disabled action
- **WHEN** a main action can't be taken
- **THEN** it is dimmed and does nothing when tapped

#### Scenario: An icon button
- **WHEN** VoiceOver reaches a button that shows only an icon
- **THEN** it reads the label the screen gave that button

### Requirement: A field says what is wrong with it
A native text field SHALL show its label, the text under it that explains it when there is some, and, when the screen marks it as wrong, that text in the error colour.

#### Scenario: A field in error
- **WHEN** a screen marks a field as wrong with the text "Enter a number"
- **THEN** the field shows "Enter a number" in the error colour

### Requirement: A list row shows its value and what it does
A native list row SHALL show its title, a summary of its value when it has one, and either that it opens something or the control that changes it. A row whose page is the one shown beside its list SHALL be marked as chosen, to the eye and to VoiceOver, and no other row of that list SHALL be.

#### Scenario: A row that opens a page
- **WHEN** a row for "Units" with the summary "Imperial" opens a page
- **THEN** it shows "Units", "Imperial" and the system's disclosure mark, and tapping anywhere on it opens the page

#### Scenario: The row of the page shown
- **WHEN** a list shows beside the page its "Privacy zones" row opened
- **THEN** that row is filled with the system's selection colour and VoiceOver reads it as selected, and the rows around it are not

### Requirement: Errors read as on every platform
A native screen SHALL show an error as what failed and, when the reason is one the athlete can act on or wait out, why, in the error colour, never the failure's own text.

#### Scenario: A failure with Retry
- **WHEN** a native screen shows a failed load with a way to try again
- **THEN** it shows the error's text and a Retry button that runs the retry

### Requirement: Loaded content follows the loading rules natively
A native screen that shows loaded content SHALL follow `ui/content-loading`: progress before the first answer, the failure with Retry when nothing has loaded, and, once something has loaded, the content kept on screen with a failed refresh reported above it.

#### Scenario: Before the first answer
- **WHEN** a native screen's content hasn't answered yet
- **THEN** it shows progress and nothing else

#### Scenario: A first load that fails
- **WHEN** the first load fails because the server can't be reached
- **THEN** the screen shows what failed, "Couldn't reach the server." and Retry

#### Scenario: A refresh that fails
- **WHEN** content has loaded and a later refresh fails
- **THEN** the content stays and the failure shows above it with Retry

### Requirement: Icons and sport images take the text's size and colour
An app icon or a sport's image in a native view SHALL scale with the text beside it and take the colour the view gives it.

#### Scenario: Larger text
- **WHEN** the athlete uses a larger text size
- **THEN** a row's icon grows with its title

### Requirement: A page in a column has a header of its own
A native page drawn in a column beside another screen, under that screen's navigation bar, SHALL show its title in a header centred at the top of its column, in the weight of a bar's title. Content SHALL scroll under it as under the system's bar, in light and dark, on every supported iOS version. A page that can go back from the column SHALL show a back button at the header's leading end, drawn and named as the system's is, with the title centred clear of it.

#### Scenario: A page beside a list
- **WHEN** a native page titled "Segments & groups" is drawn beside a list
- **THEN** "Segments & groups" is centred at the top of the page's column, below the bar over both columns

#### Scenario: Scrolled content
- **WHEN** the page in the column is scrolled
- **THEN** its rows pass under the header, which stays readable over them

#### Scenario: A deeper page
- **WHEN** a native page that can go back is drawn in the column
- **THEN** its header shows the system's back chevron at its leading end, VoiceOver reads it as Back, and tapping it goes back

#### Scenario: A page that can't go back right now
- **WHEN** a deeper page in the column says it can't go back, as a running leave does
- **THEN** its header shows no back button

### Requirement: Pictures from the network show with a placeholder
A native screen SHALL show a photo or an avatar from its URL filling its frame, with a placeholder in its place until it loads, when it fails, and when there is none. A photo this device uploaded and holds for the session (a `heldphoto:` URL) SHALL show from those bytes, as on the other platforms. An avatar SHALL show the athlete's initial until its picture loads.

#### Scenario: An item with no photo
- **WHEN** a gear item has no photo
- **THEN** its row shows the placeholder in the photo's place

#### Scenario: A non-public photo
- **WHEN** alice's gear item visible only to her has a photo the session holds
- **THEN** its row shows that photo

#### Scenario: An athlete without a picture
- **WHEN** an account named Bob has no avatar
- **THEN** its avatar shows "B"

### Requirement: A paged list follows the loading rules natively
A native screen that shows a paged list SHALL follow `ui/content-loading` as the shared UI's paged list does: progress or the first page's failure with Retry until the first answer; once answered, a refresh's progress or failure above the items, the empty message when the list holds nothing, and the next page's progress or failure with Retry below the items. It SHALL ask for the next page once the end of the items is on screen and more can be loaded, and not before.

#### Scenario: A failed next page
- **WHEN** a list's next page fails because the server can't be reached
- **THEN** the loaded items stay, and below them it reads that it couldn't reach the server, with Retry

#### Scenario: The end not yet on screen
- **WHEN** a list has loaded more items than fit on screen, and more remain
- **THEN** no further page is asked for until the last loaded item is scrolled onto the screen

### Requirement: A page's actions sit beside its title
A native page's actions (such as Add) SHALL show at the trailing end of the stack's bar when the page is alone, and at the trailing end of its column header when it is drawn beside another screen.

#### Scenario: Gear beside the list
- **WHEN** Gear is drawn beside Settings' list
- **THEN** its header reads "Gear" with Add and More at its trailing end

### Requirement: A page's title can say whose page it is
A native page's title SHALL be able to carry a second, smaller line under it, such as whose list the page is. In the stack's bar it SHALL show as the system shows a navigation subtitle, on every supported iOS version, and in a column header as a second line under the title. VoiceOver SHALL read both.

#### Scenario: A list with a subtitle
- **WHEN** alice's Followers list is shown in its stack
- **THEN** the bar reads "Followers" with "@alice.test" under it, smaller and secondary

#### Scenario: No subtitle yet
- **WHEN** the page's subtitle isn't known yet
- **THEN** the bar shows the title alone, at its usual place

### Requirement: A tab's first page has a large title
A native page that is the first page of a tab's stack SHALL show its title large in the stack's bar, as iOS titles a tab's root and the shared UI titles its tab roots, on an iPhone and on an iPad with its tabs at the top. A page pushed over it, and a page drawn in a column beside another, SHALL keep the inline title or the column header.

#### Scenario: The You tab on an iPad
- **WHEN** alice opens the You tab on an iPad
- **THEN** her profile's title, her name, shows large under the tabs

#### Scenario: A profile pushed over it
- **WHEN** alice opens Following from her profile and then bob's profile
- **THEN** Following and bob's profile each show an inline title

### Requirement: Native form text fields sit in the form's rows
A native form's text field SHALL be able to show as a row of the system's grouped form, its label leading and its text trailing on one line, or as its label above several lines of text. The text that explains it, or its error, SHALL show under the row, and VoiceOver SHALL read the label with the text.

#### Scenario: A field marked wrong
- **WHEN** a form marks its name field as wrong with "Give your gear a name"
- **THEN** that text shows under the row in the error colour

### Requirement: Native form menu choices keep their choice trailing
A native form's menu choice SHALL show its label leading, wrapping when long, and the choice with the system's menu mark at the trailing end on one line, and SHALL open the choices with the current one checked. VoiceOver SHALL read its name with the choice.

#### Scenario: A long label beside a choice
- **WHEN** a choice's label is too long for its row's width
- **THEN** the label wraps and the choice stays on one line at the trailing edge

### Requirement: A filter shows its choice and opens a menu
A native filter over a list SHALL show the option chosen and, when tapped, a menu of every option with the chosen one checked. It SHALL look filled while its choice narrows the list and plain while it is the choice that shows everything, in light and dark. VoiceOver SHALL read its choice and that it opens a menu.

#### Scenario: A filter narrowing the list
- **WHEN** a board's age filter has "45–54" chosen
- **THEN** it reads "45–54", filled, and its menu has "45–54" checked

#### Scenario: A filter showing everything
- **WHEN** a board's gender filter has "All genders" chosen
- **THEN** it reads "All genders" and is not filled

### Requirement: Rows can follow a paged list's items
A native paged list SHALL be able to show rows of the screen's own after its items, such as the viewer's own place, above the empty message and the next page's progress or failure, as the shared board orders them.

#### Scenario: The viewer's place and the next page
- **WHEN** a board's first page is loaded, more can load, and the viewer isn't on that page
- **THEN** "Your place" with the viewer's entry shows under the entries, and the next page's progress below it

### Requirement: A digits-only field keeps the digits of an edit
A native text field that takes digits only SHALL keep the digits of an edit and drop everything else, so a digit typed right after a refused character is kept, however fast it is typed or pasted. An edit that would make the text longer than the field's maximum length SHALL leave the text as it was.

#### Scenario: A letter between digits
- **WHEN** "9a5" is typed quickly into an empty digits-only field
- **THEN** the field reads "95"

#### Scenario: Pasted with a unit
- **WHEN** "170 bpm" is pasted into an empty digits-only field of at most 3 characters
- **THEN** the field reads "170"

#### Scenario: Too long
- **WHEN** "1700" is pasted into an empty digits-only field of at most 3 characters
- **THEN** the field stays empty

### Requirement: A blank form row shows its hint
A native form's text field row SHALL be able to show a hint at its trailing end while it is blank, in the placeholder's colour, such as the default a blank value falls back to, and VoiceOver SHALL read the hint with the label.

#### Scenario: A default behind a blank field
- **WHEN** a sport's max heart rate is blank and the default is 190
- **THEN** the row shows "Default: 190" where the value would be, and typing replaces it

### Requirement: An activity card is native on iOS
A native page that lists activities SHALL draw each as the native activity card, which SHALL show what the shared card shows, in the same order and words: the author line, the moderation notices, the icon and title, a dive's site, the description, the stats, the owner's locks, the route and photos, and Like with the counts.

#### Scenario: A follower's ride
- **WHEN** a native page lists Bob's titled public ride with a route, 3 likes and 1 comment
- **THEN** its card shows Bob, its start, the ride's icon and title, its distance, time and speed, the route, and "3 likes · 1 comment" beside Like

#### Scenario: An untitled run
- **WHEN** a card shows a run with no title
- **THEN** its title reads the activity's type, and VoiceOver reads no separate label for its icon

#### Scenario: A dive
- **WHEN** a card shows a dive at "Blue Hole"
- **THEN** it shows the site under the title and the dive's depth, dive time and water temperature as its stats

### Requirement: The native card's taps do what the shared card's do
On iOS, tapping the card SHALL open the activity, its author line the athlete (unless the page is the athlete's own), a photo the gallery at that photo, and Like SHALL like or unlike it. Like SHALL be offered only on others' activities and SHALL show whether the viewer gave one.

#### Scenario: Liking
- **WHEN** the viewer taps Like on Bob's activity they haven't liked
- **THEN** the like is sent, as from the shared card, and Like shows as given once the listed activity has it

#### Scenario: Own activity
- **WHEN** a card shows the viewer's own activity
- **THEN** it offers no Like and shows only the counts

### Requirement: The native card's photos follow their labels
On iOS a card's photos SHALL be blurred under a media label with the label's name and Show until the viewer reveals them on that page, a tap on a covered photo revealing rather than opening it; the owner's taken-down activity SHALL show placeholders. The athlete's avatar SHALL be blurred under a media label on their account.

#### Scenario: Revealing
- **WHEN** the viewer taps a card's photo blurred as Graphic media
- **THEN** that card's photos show, and a second tap opens the gallery

#### Scenario: Taken down
- **WHEN** the owner sees their own taken-down activity
- **THEN** its card reads that it was taken down and its photos are placeholders

### Requirement: The native card's route is drawn on the device
On iOS a card with a route the viewer was served SHALL draw it as a line fitted to its tile with its breaks, dashed connectors and markers, as the shared line preview draws it, in the app's accent colour, and SHALL send the route nowhere. With photos the route SHALL be the first tile of a row the viewer scrolls sideways.

#### Scenario: Ride with a break
- **WHEN** a card shows a ride whose line breaks once
- **THEN** its route tile draws two lines joined by a dashed connector, with start, stop, resume and end markers

#### Scenario: No route served
- **WHEN** a card shows a run whose Map category the viewer wasn't served
- **THEN** it has no route tile

### Requirement: A paged list can group its items under headings
A native paged list SHALL be able to lay its loaded items out in sections that shared code chooses, each with an optional heading, in the order given, and SHALL keep asking for the next page once the last item is on screen.

#### Scenario: Months
- **WHEN** a paged list's items fall in two months
- **THEN** it shows each month's heading over its items, the newer first

#### Scenario: The last item of the last section
- **WHEN** the last loaded item is scrolled on screen and the list can load more
- **THEN** the next page is asked for

### Requirement: A post card is native on iOS
A native page that lists posts SHALL draw each as the native post card, which SHALL show what the shared card shows, in the same order and words: who reposted it, the author with the post's audience, the text, the embed, Like and Repost on someone else's post, Quote on a listed post, and the counts.

#### Scenario: A shared proposal
- **WHEN** a native page lists bob's Followers post proposing "Wiggly Woods", with 4 up and 1 down votes, which alice may vote on
- **THEN** its card shows bob and Followers, "Name for segment …", "Proposes “Wiggly Woods”", "Open for votes · 4 up · 1 down", and the up and down votes

#### Scenario: A quote of a post the viewer can't see
- **WHEN** a native card shows carol's quote of a post bob deleted
- **THEN** the quoted post's place says "Post unavailable" and opens nothing

### Requirement: The native post card shows each embed
The native post card SHALL show a proposal with its place, name, status, totals and votes; a quoted post one level deep with its proposal named or a link to the post it quotes; "Post unavailable" for a quoted post the viewer can't see; and the shared line for an embed this version can't show.

#### Scenario: A quote of a quote
- **WHEN** a native card shows dave's quote of carol's quote of bob's post
- **THEN** the quoted post shows carol's words and "Quoting another post", which opens bob's post

### Requirement: The native post card's taps do what the shared card's do
On iOS, tapping the post card SHALL open the post page, its author line the author, "Reposted by" the reposter, the place line the segment or dive site, the quoted post its page, and a vote, Like, Repost or Quote SHALL do what the shared card's do. A vote SHALL be taken back by tapping the viewer's own vote again.

#### Scenario: Taking a vote back
- **WHEN** alice, who voted up on bob's proposal, taps the up vote on its native card
- **THEN** her vote is taken back, as from the shared card

### Requirement: Comments are native on iOS
A native page with comments SHALL list them as the shared list does: the count, each thread's comment or "Comment unavailable", its replies indented under it, the viewer's just-sent replies and comments, "View n more replies", hidden comments dimmed with their note, Reply, and a menu with the shared actions in the shared order, Delete and Block author asking first. The next page SHALL be asked for once one of the last five threads shows, and the field SHALL say which comment it answers.

#### Scenario: A hidden comment on the viewer's own post
- **WHEN** alice sees a comment she hid on her own post, on iOS
- **THEN** it is dimmed, says it's hidden, offers no Reply, and its menu offers Unhide

### Requirement: A native page pulls to refresh
A native page that refreshes SHALL use the system's pull to refresh, whose spinner shows until the page's state says the refresh has ended, and SHALL show a failed refresh in the page's own refresh row.

#### Scenario: A slow refresh
- **WHEN** the athlete pulls the native feed down and the AppView takes two seconds to answer
- **THEN** the system's spinner shows for those two seconds and then closes

### Requirement: A native route map draws the shared route
On iOS a native page SHALL show a route on the system's map, drawn from the polyline and breaks the viewer was served: each piece smoothed as the shared map smooths it, in the accent colour on a white casing, a dashed connector across each break with its stop and resume markers, and the start and end markers where the viewer's map shows them.

#### Scenario: Ride with a break
- **WHEN** a native page shows a ride whose line breaks once
- **THEN** the map draws two lines joined by a dashed connector, with start, stop, resume and end markers

#### Scenario: Hidden ends
- **WHEN** the map shows a dive site's outline, whose ends are hidden
- **THEN** it draws the closed line with no start or end marker

### Requirement: A native route map frames the whole route
A native route map SHALL first frame the whole route north-up with the shared map's margin, never closer than the shared map's closest framing, and SHALL follow the light and dark appearance.

#### Scenario: A few metres of route
- **WHEN** the route spans ten metres
- **THEN** the map frames it no closer than the shared map's closest framing

### Requirement: A native route map sends the route nowhere
The route SHALL NOT be sent to any service; only the map's tiles are fetched, for the region shown, and the map SHALL show its provider's credit.

#### Scenario: Drawing a route
- **WHEN** a native page draws a route on its map
- **THEN** the line and markers are drawn on the device over the tiles, and the provider's credit shows on the map

### Requirement: A native route map is static unless asked otherwise
A native route map SHALL be static by default: a drag over it scrolls the page and never pans the map. Given an enlarged map to open, a tap on the map or its expand button SHALL open it. An interactive map SHALL pan and zoom, stay north-up, and offer Recenter, which frames the whole route again.

#### Scenario: Static on a page
- **WHEN** a viewer drags over the dive site page's map
- **THEN** the page scrolls and the map stays where it was

#### Scenario: Recenter
- **WHEN** a viewer pans an interactive map away and taps Recenter
- **THEN** the whole route is framed again

### Requirement: Previews and snapshots draw a route map without tiles
Xcode previews and snapshot tests SHALL draw a native route map as the line preview the native card draws, at the map's size, with the markers the map would show, and SHALL load no map tiles.

#### Scenario: A snapshot of a page with a map
- **WHEN** a snapshot suite renders the dive site page
- **THEN** the map's place shows the site's outline as a line preview, and the snapshot matches its reference on every run
