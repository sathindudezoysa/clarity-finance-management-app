# **Software Requirements Specification (SRS)**

| Metadata Attribute | Document Details |
| :---- | :---- |
| **Project Name** | clarity |
| **Module** | Platform Based Development |
| **Document Version** | 1.0 |
| **Date** | September 2026 |

# **1\. Introduction**

## **1.1 Purpose**

This document outlines the formal Software Requirements Specification (SRS) for the personal finance management mobile solution. The system empowers users to systematically track income, monitor expenditures, and accomplish long-term financial objectives. Key capabilities include multi-account balance handling across diverse currencies alongside daily progress tracking toward targeted savings milestones to drive user motivation. Furthermore, this specification details the functional scope, technical stack architecture, non-functional attributes, and operational constraints.

## **1.2 Scope**

This document applies to the native Android mobile application designed to provide intuitive mobile personal finance and budgeting services to end-users. The application leverages modern Android development paradigms, including declarative UI, reactive data flow, and cloud backend integration.

* **In Scope:** Secure user authentication, multi-account and currency support, cash wallet management, recurring income/expense logging, budget tracking with alert thresholds, goal setting with dynamic rate calculations, transaction receipt attachments, cloud data synchronization/export (e.g., Google Drive/Sheets), dynamic dashboard analytics, and offline caching.

**Out of Scope:** Automated direct open-banking API integrations, automated credit card statement parsing, and web platform administration interfaces (deferred to future releases).

## **1.3 Definitions, Acronyms, and Abbreviations**

* SRS: Software Requirements Specification  
* SDK: Software Development Kit  
* API: Application Programming Interface  
* MVVM: Model-View-ViewModel Architecture Pattern  
* FCM: Firebase Cloud Messaging  
* UI/UX: User Interface / User Experience

# **2\. Overall Description**

## **2.1 Product Perspective**

The mobile application functions as an independent native Android client communicating with Firebase backend services for authentication, Firestore NoSQL database storage, Cloud Storage for media attachments, and Firebase Cloud Messaging (FCM) for push notifications. Additionally, it integrates with external exchange-rate APIs for multi-currency conversion and Google Drive API for automated spreadsheet export capabilities.

## **2.2 Operating Environment & Technical Platform**

* **Operating System:** Android OS  
* **Minimum SDK:** API Level 26 (Android 8.0 Oreo) – Ensures compatibility across \~95%+ of active Android devices while supporting modern background execution limits.  
* **Target SDK:** API Level 34+ (Android 14+) – Complies with latest Google Play requirements and platform security standards.  
* **Target Devices:** Smartphones and Tablets.

## **2.3 Technology Stack**

* **Programming Language:** Kotlin (100% Kotlin codebase adhering to modern Android standards).  
* **UI Framework:** Jetpack Compose with Material Design 3 components.  
* **Architecture:** MVVM (Model-View-ViewModel) pattern utilizing Unidirectional Data Flow (UDF).  
* **State Management:** Kotlin StateFlow, SharedFlow, and ViewModel `uiState` patterns.  
* **Backend Services:** Firebase Suite:  
  * **Firebase Authentication:** Secure email/password and social sign-in.  
  * **Cloud Firestore / Realtime Database:** NoSQL database for real-time data synchronization.  
  * **Firebase Storage:** Cloud storage for media and user profile uploads.  
  * **Firebase Cloud Messaging (FCM):** Push notifications.

# **3\. System Architecture & Design Constraints**

## **3.1 Architectural Overview (MVVM)**

* **View (Jetpack Compose Composables):** Responsible solely for displaying the UI and capturing user interactions. Observes StateFlow emitted by ViewModel.  
* **ViewModel:** Holds application state, handles presentation logic, and processes user intent. Decoupled from Android Framework classes for testability.  
* **Repository / Model:** Encapsulates data sources (Firebase Firestore, Local Data Store / Room if caching is required).

## **3.2 Constraints**

* App must maintain responsiveness using Kotlin Coroutines for asynchronous execution.  
* Offline capability for cached reading where applicable.

# **4\. System Requirements**

## **4.1 Functional Requirements**

### **FR-1: User Authentication & Profile Management**

* **FR-1.1:** The application shall enable user registration using Email/Password via Firebase Authentication.  
* **FR-1.2:** The application shall allow registered users to log in securely, recover forgotten passwords, and maintain session persistence.  
* **FR-1.3:** Users shall be able to view and update profile details (e.g., Name, Preferred Currency, Profile Picture stored in Firebase Storage).

### **FR-2: Transaction Management & Cash Wallet**

* **FR-2.1:** The application shall support core CRUD operations (Create, Read, Update, Delete) for income and expense transactions with minimal user steps.  
* **FR-2.2:** The application shall allow users to categorize transactions using pre-defined or custom tags (e.g., Food, Travel, Groceries, Savings) and optional descriptions.  
* **FR-2.3:** The application shall maintain a quick-access Cash Wallet for frequent cash transactions, with automated top-up options linked to bank account withdrawals.  
* **FR-2.4:** The application shall support attaching transaction proofs (photos/PDFs of receipts or bank exports) and display a verification icon for verified receipt amounts.

### **FR-3: Multi-Account & Multi-Currency Handling**

* **FR-3.1:** The application shall enable users to configure multiple bank accounts, designate a default daily transaction account, and select specific accounts per transaction.  
* **FR-3.2:** Users shall be able to easily identify accounts using custom profile colors, nicknames, and bank logos.  
* **FR-3.3:** The application shall support logging transactions in foreign currencies and automatically convert amounts to the user's preferred primary currency using real-time exchange rates.

### **FR-4: Income Sources & Budget Management**

* **FR-4.1:** The application shall support tracking multiple income sources, including recurring income (e.g., salary) with monthly confirmation prompts.  
* **FR-4.2:** Users shall be able to establish monthly budgets per category (e.g., Food, Travel, Groceries).  
* **FR-4.3:** The application shall issue threshold warning alerts and motivational feedback when spending nears or exceeds allocated budget limits.

### **FR-5: Financial Goals & Progress Tracking**

* **FR-5.1:** The application shall feature a dedicated goals page enabling users to create and track multiple long-term financial targets.  
* **FR-5.2:** The application shall calculate required monthly savings rates for individual goals as well as an aggregate total across all active goals.  
* **FR-5.3:** The application shall present visual progress indicators and motivational milestones to encourage goal completion.

### **FR-6: Dashboard Analytics, Cloud Sync & Export**

* **FR-6.1:** The application dashboard shall present reactive financial summaries constructed using Jetpack Compose LazyColumn/LazyRow elements.  
* **FR-6.2:** The application shall sync transaction data in real time with Cloud Firestore and support exporting monthly budgeting sheets to Google Drive (Excel/CSV format).  
* **FR-6.3:** Users shall be able to download and export complete monthly transaction and expense records for offline auditing.

### **FR-7: Real-Time Synchronization & Push Notifications**

- **FR-7.1:** The application shall receive and display contextual push notifications via Firebase Cloud Messaging (FCM) for budget warnings, recurring bill reminders, and goal updates.

# **5\. Non-Functional Requirements**

## **5.1 Security Requirements**

* **NFR-1.1 Data Encryption:** All network traffic must enforce HTTPS/TLS 1.3 encryption, and sensitive local user data must be encrypted at rest.  
* **NFR-1.2 Credential & Key Management:** API keys and Firebase credentials must be managed securely via `local.properties` or environment variables, avoiding hardcoded secrets in the codebase.  
* **NFR-1.3 Access Control:** Firestore security rules must enforce strict user-level Role-Based Access Control (RBAC) ensuring users only access their own financial records.

## **5.2 Performance & Reliability Requirements**

* **NFR-2.1 Response Time & Frame Rate:** Screen rendering and UI state transitions shall maintain a 60 FPS UI thread execution (\<16ms frame time) using asynchronous execution via Kotlin Coroutines.  
* **NFR-2.2 Offline Availability & Data Sync:** The application must support offline caching for transaction entries and balance reads, seamlessly synchronizing data once connectivity is restored.  
* **NFR-2.3 Network Efficiency:** Data transfers shall utilize efficient JSON serialization with minimal payload overhead to operate effectively over limited mobile networks.

## **5.3 Usability & Minimalistic Design Requirements**

* **NFR-3.1 Material Design Standards:** The application shall follow Material Design 3 guidelines using Jetpack Compose `MaterialTheme` to support dynamic dark/light modes smoothly.  
- **NFR-3.2 Frictionless Transaction Input:** UI flows for adding daily transactions must require minimum steps to reduce user friction and foster consistent long-term adoption.  
* **NFR-3.3 Efficiency & Click Limits:** Every user interaction flow must be completed within a maximum of 5 clicks to ensure optimal efficiency and streamlined navigation.  
* **NFR-3.4 User Engagement & Retention:** The system shall incorporate engagement features (such as daily streak tracking, motivational milestones, and gamified progress rewards) designed to boost user retention and increase time spent within the application.

# **6\. Revision History**

| Version | Date | Description | Author |
| :---- | :---- | :---- | :---- |
| **1.0** | Sep 27, 2026 | Initial draft created for Platform Based Development module assignment. | [Sathindu Dhanushka De Zoysa](mailto:sathindu.d.zoysa@gmail.com) |
