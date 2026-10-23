# NageshSpotify

- **Preferred language: Java.** Write new code, tooling and scripts in Java (Spring Boot for the backend). Don't introduce Python.
- Build and test with the Maven wrapper: `./mvnw test`. Run locally with `./mvnw spring-boot:run` (http://localhost:8080).
- Backend code lives in `src/main/java/com/nagesh/spotify`; the browser UI (HTML/CSS/JS) is served from `src/main/resources/static`.
- The Android app (Java, no AndroidX — framework APIs only, minSdk 24) is in `android/`. Build it with Android Studio / `./gradlew assembleRelease`, or without Gradle via `cd android && java tools/BuildApk.java`. Avoid lambdas and Java 9+ APIs in app code so the `dx` fallback dexer can handle it. After changing the app, rebuild and update `downloads/NageshSpotify-<version>.apk`.
