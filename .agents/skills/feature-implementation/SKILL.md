---
name: Feature Implementation
description: Guidelines and rules for implementing new features in the Clarity Android app following the strict layer-based Clean Architecture and MVVM pattern.
---

# Feature Implementation Skill (Clarity Android App)

This skill provides context and strict rules for agents executing feature implementation tasks in this repository. Ensure you adhere to these guidelines to maintain architectural consistency.

## 1. Do Not Modify Existing Project Structure
The project uses a **Layer-by-Layer** (Layer-First) package structure. You MUST NOT refactor the structure to be "Feature-First". 
All code must be placed within the existing top-level packages:
- `app/src/main/java/com/example/clarity/data/`
- `app/src/main/java/com/example/clarity/domain/`
- `app/src/main/java/com/example/clarity/presentation/`
- `app/src/main/java/com/example/clarity/di/`

**Rule:** When adding a new feature, map its files into the corresponding layer directories above. Do not create new generic module folders at the root package level.

## 2. Clean Architecture Layers

### Domain Layer (`domain/`)
- **Responsibility:** Contains enterprise logic and use cases. It must be Android-independent.
- **Components:**
  - `model/`: Data classes representing business models.
  - `repository/`: Interfaces defining data operations (e.g. `UserRepository`).
  - `usecase/`: Classes containing a single action for the presentation layer to call (e.g. `GetUserUseCase`).
- **Rule:** This layer must NOT import from `data` or `presentation` layers.

### Data Layer (`data/`)
- **Responsibility:** Implements repositories and handles external data sources (API, Database, Firebase).
- **Components:**
  - `local/`: Room Database classes, DAOs, and Entity mapping (`Entity`).
  - `remote/`: Firebase Auth, Firestore Data Sources, API calls.
  - `repository/`: Concrete implementations of the interfaces defined in the Domain layer (`AuthRepositoryImpl`).
  - `mapper/`: Extension functions mapping `Entity` or `DTO` to Domain `Model`.

### Presentation Layer (`presentation/`)
- **Responsibility:** Managing UI state and handling user interactions using MVVM + Jetpack Compose.
- **Components:**
  - `[feature_name]/`: Create a folder for your feature inside presentation (e.g. `presentation/auth/`).
  - `[Feature]Screen.kt`: Jetpack Compose UI component.
  - `[Feature]ViewModel.kt`: `HiltViewModel` managing UI state and interacting with UseCases.
  - `[Feature]State.kt`: Data class containing values observed by the Compose UI.
- **Rule:** Views must observe state; they should not mutate state directly.

### Dependency Injection (`di/`)
- **Responsibility:** Providing dependencies for Hilt.
- **Components:**
  - Use `AppModule`, `DatabaseModule`, `FirebaseModule`, `RepositoryModule` for providing corresponding layers.

## 3. Technology Stack & Dependencies Rules
- **UI:** Pure Jetpack Compose. Avoid using XML layouts or Fragments.
- **Navigation:** Jetpack Compose Navigation (`NavGraph`, `NavHost`).
- **Dependency Injection:** Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`).
- **Local Database:** Room Database.
- **Remote Data:** Firebase Auth, Firebase Firestore.
- **Concurrency:** Kotlin Coroutines and Flows (`StateFlow`, `SharedFlow`).

**Dependency Rules:**
Only add new dependencies to `build.gradle.kts` if absolutely required to fulfill a requirement. Do not bump versions or overhaul `build.gradle.kts` indiscriminately. Do not introduce Gradle Version Catalogs unless explicitly told to.
