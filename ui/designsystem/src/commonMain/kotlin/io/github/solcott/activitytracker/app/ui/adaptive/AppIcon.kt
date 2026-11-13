package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The app's chrome icons, each as a Material icon and an SF Symbol name (the iOS app's `AppIcon`, which `generateIosResources` writes from
 * these names). The `filled*` variants are the selected or active form; they default to the plain one.
 */
enum class AppIcon(
    val material: ImageVector,
    val sfSymbol: String,
    internal val filledMaterial: ImageVector = material,
    val filledSfSymbol: String = sfSymbol,
) {
    Back(Icons.AutoMirrored.Filled.ArrowBack, "chevron.backward"),
    Search(Icons.Filled.Search, "magnifyingglass"),
    Add(Icons.Filled.Add, "plus"),
    Record(Icons.Filled.PlayArrow, "record.circle"),
    ManualEntry(Icons.Filled.Edit, "square.and.pencil"),
    Import(Icons.Filled.AddCircle, "tray.and.arrow.down"),
    Settings(Icons.Filled.Settings, "gearshape"),
    Refresh(Icons.Filled.Refresh, "arrow.clockwise"),
    Send(Icons.AutoMirrored.Filled.Send, "arrow.up.circle.fill"),
    Clear(Icons.Filled.Clear, "xmark.circle.fill"),
    Calendar(Icons.Filled.DateRange, "calendar"),
    Check(Icons.Filled.Check, "checkmark"),
    Chevron(Icons.AutoMirrored.Filled.KeyboardArrowRight, "chevron.forward"),
    ChevronBack(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "chevron.backward"),
    ChevronDown(Icons.Filled.KeyboardArrowDown, "chevron.down"),
    ChevronUp(Icons.Filled.KeyboardArrowUp, "chevron.up"),
    Delete(Icons.Filled.Delete, "trash"),
    More(Icons.Filled.MoreVert, "ellipsis.circle"),
    Hide(MaterialIcons.VisibilityOff, "eye.slash"),
    Unhide(MaterialIcons.Visibility, "eye"),
    Ungroup(MaterialIcons.CallSplit, "arrow.branch"),
    Export(Icons.Filled.Share, "square.and.arrow.up"),
    Close(Icons.Filled.Close, "xmark"),
    Location(Icons.Filled.Place, "location"),
    Lock(Icons.Filled.Lock, "lock.fill"),
    AddPhoto(Icons.Filled.Add, "photo.badge.plus"),
    DropDown(Icons.Filled.ArrowDropDown, "chevron.up.chevron.down"),
    ExpandAll(MaterialIcons.UnfoldMore, "rectangle.expand.vertical"),
    CollapseAll(MaterialIcons.UnfoldLess, "rectangle.compress.vertical"),
    Expand(MaterialIcons.Fullscreen, "arrow.up.left.and.arrow.down.right"),
    Recenter(MaterialIcons.CenterFocusStrong, "scope"),
    Like(
        Icons.Outlined.ThumbUp,
        "hand.thumbsup",
        Icons.Filled.ThumbUp,
        "hand.thumbsup.fill",
    ),
    Repost(MaterialIcons.Repeat, "arrow.2.squarepath"),
    Quote(MaterialIcons.FormatQuote, "quote.bubble"),
    Star(MaterialIcons.StarBorder, "star", Icons.Filled.Star, "star.fill"),
    Feed(Icons.Outlined.Home, "house", Icons.Filled.Home, "house.fill"),
    You(Icons.Outlined.Person, "person", Icons.Filled.Person, "person.fill"),
}
