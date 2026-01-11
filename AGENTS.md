# AGENTS.md - LanguageTool Droid

## 🎯 Project Vision
**"Functional and Beautiful"**
LanguageTool Droid brings powerful, privacy-focused grammar checking to Android with a premium user experience. It shouldn't just work; it should feel native, modern, and delightful.

## 🏗 System Architecture
- **Core Component**: `SpellCheckerService` (Android API).
- **Network Layer**: `Retrofit` + `OkHttp` (Talking to LanguageTool API).
- **UI Architecture**: Single Activity (`MainActivity`) with Material 3 Design.

## 🚀 Build & Deployment (CI/CD)
To ensure the app **always compiles** and remains functional, we follow this rigorous process:

### 1. Continuous Integration (CI)
- **Workflow**: `.github/workflows/build.yml`
- **Trigger**: Every `push` to `main` and every `pull_request`.
- **Action**: Compiles the `Debug` variant. If this fails, **do not merge**.

### 2. Continuous Delivery (CD)
- **Workflow**: `.github/workflows/release.yml`
- **Trigger**: Pushing a tag starting with `v` (e.g., `v1.0.0`).
- **Action**: 
    1. Compiles the `Release` variant.
    2. Signs the APK (currently using debug key for testing).
    3. Creates a GitHub Release.
    4. Uploads the APK as an asset.

## 🎨 Design Guidelines (Material 3)
- **Theme**: Automatic Day/Night mode support.
- **Colors**: Semantic tokens (e.g., `@color/card_bg`) defined in `values/colors.xml` and `values-night/colors.xml`.
- **Components**: Use `MaterialCardView`, `UncontainedButton`, `OutlinedTextField`.
- **Typography**: Clean, legible, using system defaults with hierarchy (Bold Titles, generic body).

## 👩‍💻 Dev Cheatsheet
- **Local Build**: `./gradlew assembleDebug`
- **Release Build**: `./gradlew assembleRelease`
- **Fix "SDK location not found"**: Ensure `local.properties` has `sdk.dir=/path/to/android/sdk`.

## 🚨 Troubleshooting
- **Missing Resources**: Always check `strings.xml` and `values-es/strings.xml` for duplicates.
- **Service Not Visible**: Ensure `spell_checker.xml` has a valid `<subtype>`.
- **API Errors**: Check logic in `MainActivity.kt` and `LanguageToolClient.kt` handling default values ("auto").
