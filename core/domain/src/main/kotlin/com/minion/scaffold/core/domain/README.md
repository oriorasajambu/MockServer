# `:core:domain`

Pure Kotlin. Zero Android, zero Compose, zero Retrofit, zero Room — enforced by the build:
this module applies `minion.jvm.library`, not the Android plugin, so `import android.*` is a
compile error rather than a code-review comment.

Holds domain concepts shared by **more than one** feature. A model only one screen uses belongs
in that feature's own `domain/` package.

```
domain/
├── model/        # data classes. No annotations, no framework types.
├── repository/   # interfaces returning AppResult<T> or Flow<T>. Implemented in :core:data.
└── usecase/      # one class per action, `operator fun invoke()`
```

Three rules that carry most of the value:

- **Repository interfaces are declared here and implemented elsewhere.** That inversion is what
  lets the domain be tested with no network, no database and no Android.
- **Use cases return `AppResult<T>`**, never throw. Exceptions stop at the data layer.
- **One use case, one action.** `GetUsersUseCase` with `operator fun invoke()`, called as
  `getUsers()`. A "UserUseCase" with six methods is a service class wearing a use case's name.
