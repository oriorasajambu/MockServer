/**
 * UI tier — tokens and atoms. Knows pixels only.
 *
 * `AppTheme`, colour, type, shape and the dumb widgets built on them. Zero domain imports, so it
 * is reusable in any app. Anything that renders a domain model belongs in `:core:ui`.
 *
 * This is the only module where a raw colour, dimension or font may appear. A hex literal
 * anywhere else is a bug the design system was created to prevent.
 *
 * Showkase comes from the `mockserver.android.library.compose` convention, along with Compose itself.
 */
plugins {
    id("mockserver.android.library.compose")
}

android {
    namespace = "com.rio.mockserver.core.designsystem"
}
