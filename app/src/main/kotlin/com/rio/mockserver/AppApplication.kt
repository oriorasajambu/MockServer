package com.rio.mockserver

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * The Hilt entry point. Every `@Inject` in the app resolves through the component this generates,
 * which is why it must be named in `AndroidManifest.xml`'s `android:name`.
 *
 * Keep it empty. Work in `onCreate` runs before the first frame on every cold start, so anything
 * put here is paid for by every launch — including launches that never use it. Initialisation
 * that can be lazy belongs behind an injected type; initialisation that genuinely cannot belongs
 * in `androidx.startup`.
 */
@HiltAndroidApp
class AppApplication : Application()
