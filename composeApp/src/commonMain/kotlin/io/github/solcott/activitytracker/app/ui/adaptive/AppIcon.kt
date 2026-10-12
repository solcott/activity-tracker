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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.mohamedrejeb.calf.cupertino.icons.CupertinoIcons

/**
 * The app's chrome icons, each as a Material icon, a Cupertino glyph (for Compose-drawn iOS UI) and an SF Symbol name (for UIKit menus and
 * the tab bar). The `filled*` variants are the selected or active form; they default to the plain one.
 */
enum class AppIcon(
    internal val material: ImageVector,
    internal val cupertino: Int,
    internal val sfSymbol: String,
    internal val filledMaterial: ImageVector = material,
    internal val filledCupertino: Int = cupertino,
    internal val filledSfSymbol: String = sfSymbol,
) {
    Back(Icons.AutoMirrored.Filled.ArrowBack, CupertinoIcons.chevronBack, "chevron.backward"),
    Search(Icons.Filled.Search, CupertinoIcons.search, "magnifyingglass"),
    Add(Icons.Filled.Add, CupertinoIcons.plus, "plus"),
    Record(Icons.Filled.PlayArrow, CupertinoIcons.largecircleFillCircle, "record.circle"),
    ManualEntry(Icons.Filled.Edit, CupertinoIcons.squarePencil, "square.and.pencil"),
    Import(Icons.Filled.AddCircle, CupertinoIcons.trayArrowDown, "tray.and.arrow.down"),
    Settings(Icons.Filled.Settings, CupertinoIcons.gear, "gearshape"),
    Refresh(Icons.Filled.Refresh, CupertinoIcons.arrowClockwise, "arrow.clockwise"),
    Send(Icons.AutoMirrored.Filled.Send, CupertinoIcons.arrowUpCircleFill, "arrow.up.circle.fill"),
    Clear(Icons.Filled.Clear, CupertinoIcons.xmarkCircleFill, "xmark.circle.fill"),
    Calendar(Icons.Filled.DateRange, CupertinoIcons.calendar, "calendar"),
    Check(Icons.Filled.Check, CupertinoIcons.checkmark, "checkmark"),
    Chevron(Icons.AutoMirrored.Filled.KeyboardArrowRight, CupertinoIcons.chevronForward, "chevron.forward"),
    ChevronBack(Icons.AutoMirrored.Filled.KeyboardArrowLeft, CupertinoIcons.chevronBack, "chevron.backward"),
    ChevronDown(Icons.Filled.KeyboardArrowDown, CupertinoIcons.chevronDown, "chevron.down"),
    Delete(Icons.Filled.Delete, CupertinoIcons.trash, "trash"),
    More(Icons.Filled.MoreVert, CupertinoIcons.ellipsisCircle, "ellipsis.circle"),
    Ungroup(MaterialIcons.CallSplit, CupertinoIcons.arrowBranch, "arrow.branch"),
    Close(Icons.Filled.Close, CupertinoIcons.xmark, "xmark"),
    Location(Icons.Filled.Place, CupertinoIcons.location, "location"),
    AddPhoto(Icons.Filled.Add, CupertinoIcons.photoOnRectangle, "photo.badge.plus"),
    DropDown(Icons.Filled.ArrowDropDown, CupertinoIcons.chevronUpChevronDown, "chevron.up.chevron.down"),
    ExpandAll(MaterialIcons.UnfoldMore, CupertinoIcons.rectangleExpandVertical, "rectangle.expand.vertical"),
    CollapseAll(MaterialIcons.UnfoldLess, CupertinoIcons.rectangleCompressVertical, "rectangle.compress.vertical"),
    Kudos(
        Icons.Outlined.ThumbUp,
        CupertinoIcons.handThumbsup,
        "hand.thumbsup",
        Icons.Filled.ThumbUp,
        CupertinoIcons.handThumbsupFill,
        "hand.thumbsup.fill",
    ),
    Feed(Icons.Outlined.Home, CupertinoIcons.house, "house", Icons.Filled.Home, CupertinoIcons.houseFill, "house.fill"),
    You(Icons.Outlined.Person, CupertinoIcons.person, "person", Icons.Filled.Person, CupertinoIcons.personFill, "person.fill"),
}
