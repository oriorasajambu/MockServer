package com.minion.mockserver.core.designsystem.icon

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.minion.mockserver.core.designsystem.R

/**
 * The app's icon set: Phosphor, vendored as vector drawables.
 *
 * Vendored rather than pulled from the icon font the design prototype used. A font would ship
 * about fifteen hundred glyphs to draw the forty-five this app actually needs, and an icon font
 * renders as text — so it inherits the user's font scale, gets picked up by text-to-speech, and
 * has no sensible fallback if the file fails to load. These are the same source SVGs, transcribed
 * verbatim onto a 256x256 viewport.
 *
 * Named properties rather than raw `R.drawable` ids at the call site: `MockIcons.Play` is
 * checkable by the compiler and greppable, where `R.drawable.ic_ph_play` scattered across four
 * feature modules is neither. Each resolves through `vectorResource`, so a call site keeps using
 * `Icon(imageVector = ...)` exactly as it did with Material's set.
 *
 * `Fill` variants exist only where the design uses one — a selected tab, a succeeded test, an
 * injected fault. Regular is the default everywhere else.
 */
object MockIcons {

    val ArrowLeft: ImageVector @Composable get() = vector(R.drawable.ic_ph_arrow_left)
    val ArrowRight: ImageVector @Composable get() = vector(R.drawable.ic_ph_arrow_right)
    val ArrowSquareOut: ImageVector @Composable get() = vector(R.drawable.ic_ph_arrow_square_out)
    val ArrowsDownUp: ImageVector @Composable get() = vector(R.drawable.ic_ph_arrows_down_up)
    val BatteryHigh: ImageVector @Composable get() = vector(R.drawable.ic_ph_battery_high)
    val CaretDown: ImageVector @Composable get() = vector(R.drawable.ic_ph_caret_down)
    val CaretRight: ImageVector @Composable get() = vector(R.drawable.ic_ph_caret_right)
    val CaretUp: ImageVector @Composable get() = vector(R.drawable.ic_ph_caret_up)
    val Check: ImageVector @Composable get() = vector(R.drawable.ic_ph_check)
    val CheckCircle: ImageVector @Composable get() = vector(R.drawable.ic_ph_check_circle)
    val CheckCircleFill: ImageVector @Composable get() = vector(R.drawable.ic_ph_check_circle_fill)
    val CheckFill: ImageVector @Composable get() = vector(R.drawable.ic_ph_check_fill)
    val Circle: ImageVector @Composable get() = vector(R.drawable.ic_ph_circle)
    val CircleDashed: ImageVector @Composable get() = vector(R.drawable.ic_ph_circle_dashed)
    val Copy: ImageVector @Composable get() = vector(R.drawable.ic_ph_copy)
    val DiceFive: ImageVector @Composable get() = vector(R.drawable.ic_ph_dice_five)
    val DotsThreeVertical: ImageVector @Composable get() = vector(R.drawable.ic_ph_dots_three_vertical)
    val DotsThreeVerticalFill: ImageVector @Composable get() = vector(R.drawable.ic_ph_dots_three_vertical_fill)
    val Export: ImageVector @Composable get() = vector(R.drawable.ic_ph_export)
    val FileArrowDown: ImageVector @Composable get() = vector(R.drawable.ic_ph_file_arrow_down)
    val FloppyDisk: ImageVector @Composable get() = vector(R.drawable.ic_ph_floppy_disk)
    val FolderOpen: ImageVector @Composable get() = vector(R.drawable.ic_ph_folder_open)
    val Gear: ImageVector @Composable get() = vector(R.drawable.ic_ph_gear)
    val GearFill: ImageVector @Composable get() = vector(R.drawable.ic_ph_gear_fill)
    val GitFork: ImageVector @Composable get() = vector(R.drawable.ic_ph_git_fork)
    val HardDrives: ImageVector @Composable get() = vector(R.drawable.ic_ph_hard_drives)
    val Info: ImageVector @Composable get() = vector(R.drawable.ic_ph_info)
    val Lightning: ImageVector @Composable get() = vector(R.drawable.ic_ph_lightning)
    val LightningFill: ImageVector @Composable get() = vector(R.drawable.ic_ph_lightning_fill)
    val LinkSimple: ImageVector @Composable get() = vector(R.drawable.ic_ph_link_simple)
    val ListDashes: ImageVector @Composable get() = vector(R.drawable.ic_ph_list_dashes)
    val ListDashesFill: ImageVector @Composable get() = vector(R.drawable.ic_ph_list_dashes_fill)
    val MagnifyingGlass: ImageVector @Composable get() = vector(R.drawable.ic_ph_magnifying_glass)
    val Pause: ImageVector @Composable get() = vector(R.drawable.ic_ph_pause)
    val PencilSimple: ImageVector @Composable get() = vector(R.drawable.ic_ph_pencil_simple)
    val Play: ImageVector @Composable get() = vector(R.drawable.ic_ph_play)
    val PlayFill: ImageVector @Composable get() = vector(R.drawable.ic_ph_play_fill)
    val Plugs: ImageVector @Composable get() = vector(R.drawable.ic_ph_plugs)
    val Plus: ImageVector @Composable get() = vector(R.drawable.ic_ph_plus)
    val PlusCircle: ImageVector @Composable get() = vector(R.drawable.ic_ph_plus_circle)
    val Pulse: ImageVector @Composable get() = vector(R.drawable.ic_ph_pulse)
    val PulseFill: ImageVector @Composable get() = vector(R.drawable.ic_ph_pulse_fill)
    val QrCode: ImageVector @Composable get() = vector(R.drawable.ic_ph_qr_code)
    val Question: ImageVector @Composable get() = vector(R.drawable.ic_ph_question)
    val QuestionFill: ImageVector @Composable get() = vector(R.drawable.ic_ph_question_fill)
    val Scissors: ImageVector @Composable get() = vector(R.drawable.ic_ph_scissors)
    val ShareNetwork: ImageVector @Composable get() = vector(R.drawable.ic_ph_share_network)
    val SlidersHorizontal: ImageVector @Composable get() = vector(R.drawable.ic_ph_sliders_horizontal)
    val Stop: ImageVector @Composable get() = vector(R.drawable.ic_ph_stop)
    val StopFill: ImageVector @Composable get() = vector(R.drawable.ic_ph_stop_fill)
    val TerminalWindow: ImageVector @Composable get() = vector(R.drawable.ic_ph_terminal_window)
    val ToggleLeft: ImageVector @Composable get() = vector(R.drawable.ic_ph_toggle_left)
    val Trash: ImageVector @Composable get() = vector(R.drawable.ic_ph_trash)
    val WarningCircle: ImageVector @Composable get() = vector(R.drawable.ic_ph_warning_circle)
    val WarningOctagon: ImageVector @Composable get() = vector(R.drawable.ic_ph_warning_octagon)
    val X: ImageVector @Composable get() = vector(R.drawable.ic_ph_x)
}

/**
 * Not `@ReadOnlyComposable`: `vectorResource` reads the local configuration and has to be
 * invalidated when it changes, which that annotation would prevent.
 */
@Composable
private fun vector(@DrawableRes id: Int): ImageVector = ImageVector.vectorResource(id)
