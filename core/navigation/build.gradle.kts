/**
 * Route contracts, and nothing else. Pure Kotlin — zero Android, zero Compose.
 *
 * This module exists so that a feature can navigate to another feature's screen without
 * depending on it. Both sides know only the `@Serializable` route class declared here; the
 * feature that owns the screen registers it, the feature that wants it constructs it.
 *
 * Nothing that renders, holds state, or knows a `NavController` belongs here. If a route needed
 * a domain model as an argument, that would be a signal the two screens should be exchanging an
 * id instead.
 */
plugins {
    id("mockserver.jvm.library")
    // Applied here rather than in the convention: this is the only pure-Kotlin module that
    // declares @Serializable types, and putting the plugin in mockserver.jvm.library would force it
    // on :core:common and :core:domain, which do not need it.
    alias(libs.plugins.kotlin.serializable)
}

dependencies {
    // api, not implementation: the route classes are this module's entire public surface and they
    // are annotated @Serializable, so every consumer needs the serialization runtime to resolve
    // their generated serializers.
    api(libs.kotlin.serializable)
}
