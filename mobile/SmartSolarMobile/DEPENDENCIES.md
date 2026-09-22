# Android Phase 0 dependencies

The generated AGP 8.8.2 / Gradle 8.10.2 / SDK 35 project is retained. Application code is Java 11 with native XML Views (minimum API 26). Gradle files use Kotlin DSL.

| Dependency | Version | Purpose |
|---|---|---|
| AndroidX AppCompat | 1.7.0 (existing catalog) | AppCompatActivity |
| Material Components | 1.12.0 (existing catalog) | Generated native Views theme |
| AndroidX Activity | 1.10.1 (existing catalog) | Edge-to-edge/window handling |
| Retrofit | 2.11.0 | Typed REST calls |
| Retrofit converter-gson | 2.11.0 | API JSON serialization |
| OkHttp | 4.12.0 | HTTP transport/interceptors |
| OkHttp logging-interceptor | 4.12.0 | Debug BASIC request metadata; no headers/bodies |
| JUnit | 4.13.2 (existing catalog) | Local unit tests |
| OkHttp MockWebServer | 4.12.0 (test only) | Host-side HTTP/401 tests |
| AndroidX test JUnit / Espresso | 1.2.1 / 3.6.1 (existing catalog) | Retained instrumentation-test support |

Retrofit and OkHttp versions match the prepared foundation. Gson is supplied transitively by converter-gson. SQLiteOpenHelper is part of Android; no SQLite library is needed. Removed the unused ConstraintLayout implementation dependency with the generated Hello World layout. Its unused catalog entry was also removed. All added versions are centralized in gradle/libs.versions.toml.

There are no Maps/QR/location/camera/database-server/client business-rule dependencies. No Kotlin application plugin or Kotlin source is added; third-party libraries may have Kotlin runtime transitive dependencies.

The executable configuration is `app/build.gradle.kts`. The release URL is a public Gradle property, not a credential; see README. Never put passwords, signing keys, or tokens in dependency/build files.
