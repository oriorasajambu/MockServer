package com.minion.scaffold.showkase

import com.airbnb.android.showkase.annotation.ShowkaseRoot
import com.airbnb.android.showkase.annotation.ShowkaseRootModule

/**
 * The aggregation point for the Showkase component browser.
 *
 * Exactly one of these may exist, and it must be in the module whose KSP run sees every other
 * module's generated metadata — that is `:app`. It gathers the `@Preview` functions from
 * `:core:designsystem`, `:core:ui` and every feature into one browsable catalog.
 *
 * **Must live in `src/main`, not `src/debug`.** KSP does not scan the debug source set for
 * annotations, so a root placed there compiles but generates nothing, and the
 * `Showkase.getBrowserIntent` extension below never appears. Only the processor is debug-scoped
 * (`kspDebug`), so nothing is generated into a release build regardless.
 *
 * The generated extension lands in *this* package, which is why [ShowkaseLauncherActivity] shares
 * it and needs no import for it.
 */
@ShowkaseRoot
class ShowkaseRootModule : ShowkaseRootModule
