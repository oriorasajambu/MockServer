package com.minion.scaffold.core.network

import javax.inject.Qualifier

/**
 * Qualifies the API base URL, provided by `:app`.
 *
 * The alternative — reading `BuildConfig.BASE_URL` here — would make this module depend on the
 * application it happens to be compiled into, which is the quiet way a "core" module stops being
 * reusable. Taking it as an injected value keeps the direction of knowledge pointing the right
 * way: the app knows about its environments; the network layer does not.
 */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class BaseUrl
