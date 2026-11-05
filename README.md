<div align="center">

# Clarity

### Personal finance, made easier to understand.

Track your money across accounts and currencies, stay on top of budgets, and make steady progress toward savings goals — in one native Android app.

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)
![Minimum Android](https://img.shields.io/badge/Min%20SDK-26-555555)

</div>

---

## About the project

**Clarity** is a personal finance management application for Android, developed for the **Platform Based Development** module. It is designed to help users record income and expenses, organize multiple financial accounts, monitor spending limits, and track long-term savings goals.

The project uses **Kotlin and Jetpack Compose** for its interface, **Firebase** for authentication and cloud-backed data, and **Room** for on-device storage. Its code follows an **MVVM-oriented architecture** with repositories, use cases, reactive state, and dependency injection.

> **Documentation note:** This README describes features and integrations evidenced in the repository's `main` branch. The project's Software Requirements Specification (SRS v1.0, September 2026) also contains requirements that are not fully implemented or have not been independently verified. See [Implementation scope](#implementation-scope) for those distinctions.

## Features

### Authentication and settings

- Register and sign in with an email address and password using Firebase Authentication.
- Sign in with a Google account, subject to correct Firebase/Google OAuth configuration.
- Request a password-reset email and maintain an authenticated session.
- Update the profile display name and preferred currency.
- Enable an optional **biometric app lock** on supported devices. This protects app access after sign-in; it does not replace Firebase authentication.

### Transactions and accounts

- Add, view, edit, and delete **income and expense transactions**.
- Categorize transactions with predefined categories or user-created tags.
- Associate transactions with accounts and add optional notes.
- Create and edit multiple accounts, including an account name, bank name, optional account type, and currency.
- Choose a default account for everyday transactions.
- View transaction history with filters and receipt-related indicators.
- Attach supported image/PDF proof files to transactions; attachments have an owner-scoped Firebase Storage path and a local upload/synchronization workflow.
- Review and mark receipt amounts within the app. A verification marker represents an **owner review**, not automatic OCR or independent bank verification.

### Cash wallet and income sources

- View a dedicated **Cash Wallet** balance.
- Record wallet expenses and top up cash from a selected account using corresponding transaction entries.
- Manage recurring income sources and review monthly income confirmation prompts.
- Record recurring income only when it has been confirmed as received.

### Multi-currency support

- Record accounts and transactions in supported currencies.
- Choose a preferred currency for financial summaries.
- Fetch exchange rates from the **ExchangeRate-API** endpoint and reuse locally cached rates when appropriate.
- Preserve conversion information for transactions, while accounting for rates that may be unavailable or out of date.

**Currency codes represented in the implementation:** `LKR`, `USD`, `EUR`, `GBP`, `INR`, `AUD`, `CAD`, `SGD`, `JPY`, and `KWD`.

### Budgets and financial goals

- Set **category-based monthly or yearly budgets**.
- View spending progress and budget status: normal, nearing, reached, or exceeded.
- Evaluate the *nearing* threshold at **80%** of a budget and distinguish reaching a limit from exceeding it.
- Receive device-side budget notifications where notification permissions and platform conditions allow.
- Create and edit savings goals with target amounts and dates.
- Add goal contributions, view progress, calculate required monthly savings, and see aggregate goal summaries.
- View goal-related streak information derived from contribution history.

### Dashboard and exports

- See financial summaries and recent transactions on the home dashboard.
- Navigate between key areas such as accounts, transactions, budgets, goals, wallet, and settings.
- Export records for a selected month as **CSV** or **PDF** through Android's document-creation picker.
- Use local Room data and Firebase synchronization components to support cached data access and pending updates, subject to connectivity and each feature's sync behavior.

## Technology stack

| Area | Technology |
| --- | --- |
| Platform | Native Android |
| Language | Kotlin 1.9.0 |
| UI | Jetpack Compose, Material Design 3 |
| Architecture | MVVM-oriented presentation, repositories, use cases, StateFlow |
| Dependency injection | Hilt (Dagger) |
| Authentication | Firebase Authentication, Google Sign-In |
| Cloud data | Cloud Firestore |
| Attachment storage | Firebase Storage |
| Local persistence | Room (SQLite) |
| Background work | WorkManager, Kotlin Coroutines |
| Navigation | Navigation Compose |
| Currency rates | ExchangeRate-API over HTTPS |
| Exports | CSV and PDF writers with Android Storage Access Framework |
| Build system | Gradle Kotlin DSL |

**Android configuration:** `minSdk 26`, `compileSdk 34`, `targetSdk 34`, Java/JVM target `17`.

## Architecture

Clarity separates UI logic from business rules and data access:

```text
Jetpack Compose screens
        |
        v
ViewModels + StateFlow
        |
        v
Domain use cases / repository interfaces
        |
        v
Repository implementations
        |
        +-- Room database and local caches
        +-- Firebase Auth / Firestore / Storage
        +-- Exchange-rate service
        +-- WorkManager synchronization tasks
```

The application defines screens and routes in `presentation/navigation`, uses Hilt for dependency injection, and manages several authentication-scoped sync processes from `ClarityApp`.

### Project layout

```text
app/src/main/java/com/example/clarity/
├── ClarityApp.kt
├── MainActivity.kt
├── data/
│   ├── export/
│   ├── local/
│   ├── notifications/
│   ├── remote/
│   ├── repository/
│   └── sync/
├── di/
├── domain/
│   ├── model/
│   ├── repository/
│   ├── usecase/
│   └── util/
└── presentation/
    ├── accounts/
    ├── auth/
    ├── budgets/
    ├── goals/
    ├── income/
    ├── main/
    ├── navigation/
    ├── settings/
    ├── theme/
    ├── transactions/
    └── wallet/
```

## Getting started

### Prerequisites

- **Android Studio** with Android SDK Platform 34 installed.
- **JDK 17**, matching the project's Java and Kotlin compilation targets.
- An Android device or emulator running **Android 8.0 (API 26)** or newer.
- A Firebase project configured for this app's authentication and data services.
- An internet connection for initial Firebase authentication, synchronization, and fresh currency-rate requests.

### 1. Clone the repository

```bash
git clone https://github.com/sathindudezoysa/clarity-finance-management-app.git
cd clarity-finance-management-app
```

### 2. Configure Firebase

1. Create or choose a Firebase project in the [Firebase Console](https://console.firebase.google.com/).
2. Register an Android application with the package/application ID **`com.example.clarity`**.
3. Download its `google-services.json` and place it at **`app/google-services.json`**.
4. Enable **Email/Password** authentication. For Google Sign-In, also enable the Google provider and configure the appropriate Android app signing certificate fingerprint(s) (including SHA-1 as needed).
5. Configure **Cloud Firestore** and **Firebase Storage** for that project.
6. Review the repository's [`firestore.rules`](firestore.rules) and [`storage.rules`](storage.rules), then deploy the appropriate rules to your Firebase project before using real financial data.

`app/google-services.json` and `local.properties` are excluded by the project's `.gitignore`. Do not commit private credentials, service-account keys, or other secrets.

### 3. Build and run

1. Open the cloned project in Android Studio.
2. Let Gradle synchronize and resolve dependencies.
3. Select an emulator or connected Android device.
4. Run the **`app`** configuration.
5. Register an account or sign in using a Firebase Authentication provider configured in your project.

Alternatively, build a debug APK from the repository root:

**macOS / Linux**

```bash
./gradlew assembleDebug
```

**Windows**

```powershell
.\gradlew.bat assembleDebug
```

The Gradle wrapper is included in the repository. A successful local build depends on correct Android SDK and Firebase configuration; build success has **not** been independently verified for this README.

## Testing

The Android module declares JUnit, AndroidX Test/Espresso, Compose UI testing, and WorkManager testing dependencies. To run the standard Gradle tasks:

```bash
# Local unit tests
./gradlew testDebugUnitTest

# Instrumented tests (requires an emulator or connected device)
./gradlew connectedDebugAndroidTest
```

On Windows, use `gradlew.bat` instead of `./gradlew`. The repository also contains Firebase rule files and separate Firebase test configuration files. **No passing test count, code-coverage percentage, performance benchmark, or CI status is claimed here.**

## Implementation scope

The SRS describes the intended product, while the current repository shows its actual development scope:

| Capability | What is supported by the inspected code |
| --- | --- |
| Email/password, Google sign-in, password reset | Firebase-based flows are present. |
| Income/expense transactions, categories, accounts | CRUD-oriented screens and domain/data components are present. |
| Cash wallet and recurring income | Wallet top-up/expense flows and monthly income confirmation exist. |
| Currency conversion | Exchange-rate API integration and local rate caching exist; rates are not guaranteed to be live when offline. |
| Budgets and goals | Category budgets, threshold evaluation, savings goals, contributions, and progress calculations exist. |
| Receipt attachments | Image/PDF proof handling exists; receipt amount verification is user-reviewed, not automated verification. |
| Export | **Manual CSV/PDF file export** exists. Automatic Google Drive API export described in the SRS is **not evidenced** by the inspected implementation. |
| Notifications | Budget and wallet reminder mechanisms use Android-side notification/background-work components. **Firebase Cloud Messaging push delivery is not evidenced** by the declared app dependencies. |
| Profile and account personalization | Profile-name/currency settings and named accounts exist. Profile-photo upload, bank logos, and custom account colors are **not confirmed** as implemented. |
| Non-functional targets | The SRS's precise frame-rate, click-limit, TLS-version, and at-rest encryption targets have **not been independently tested or verified** here. |

The SRS explicitly keeps direct open-banking connections, automatic credit-card statement parsing, and a web administration interface **out of scope** for this release.

## Security and data handling

- Authentication is handled through Firebase Authentication.
- The repository contains **owner-scoped Firestore and Firebase Storage rules**; effective protection requires these rules to be reviewed and deployed in the actual Firebase project.
- Firebase Storage rules limit supported receipt content types and upload size.
- Local persistence and background synchronization are used for multiple financial data types.
- The app uses HTTPS for its exchange-rate request; this README does not claim that a particular TLS protocol version or encryption-at-rest policy has been audited.

> **Important:** Clarity is a student project and should not be treated as a certified banking, accounting, or financial-advice product. Do not store sensitive production financial records until configuration, security rules, and data-handling behavior have been reviewed and tested for the intended environment.

## Project context

Clarity was specified as part of the **Platform Based Development** module. Its SRS is titled *Software Requirements Specification (SRS)*, version **1.0**, dated **September 2026**. The specification describes the intended functional requirements, non-functional requirements, Android architecture, and integration boundaries.

**Repository:** [sathindudezoysa/clarity-finance-management-app](https://github.com/sathindudezoysa/clarity-finance-management-app)

## License

No `LICENSE` file was visible in the inspected repository root. Do not assume that the project is distributed under an open-source license until the maintainers add one.
