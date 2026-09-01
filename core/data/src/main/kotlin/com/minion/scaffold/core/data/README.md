# `:core:data`

Data that is genuinely shared **between** features — a session/token store, a user-preferences
store, a repository two or more features both read.

A feature's own data layer does **not** live here. It lives in the feature module, as
`data/` alongside `domain/` and `presentation/`, with the `*Api`, DTOs, mapper and repository
implementation all `internal` and bound by an `internal` Hilt module in the same module. That is
why `minion.android.feature` grants `:core:network` but not `:core:data`.

The rule for promoting something into this module: **two features already need it.** One feature
needing it is not a shared concern, it is that feature's concern.

Packages to use when something does move here:

```
data/
├── repository/   # *RepositoryImpl.kt implementing a :core:domain interface
├── remote/       # *Api.kt, *Dto.kt for shared endpoints
├── local/        # DataStore / Room access
├── mapper/       # Dto.toDomain(), Entity.toDomain() extension functions
└── di/           # @Binds modules, next to the implementations they bind
```

The error boundary — `safeCall` and `Throwable.toDomainError()` — is in `:core:network`, not
here, because features need it too and it translates exceptions that `:core:network` owns.
