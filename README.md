# NEON WALLET

Native Android prototype built with Kotlin and Jetpack Compose.

- Russian interface with black / purple neon styling
- Dashboard with a sample balance
- Interactive demo top-up and transfer dialogs
- Virtual card mockup
- Transaction history and profile
- GitHub Actions workflow builds a debug APK

**Demo only:** balances and transactions are stored in local app state. This is not a bank account, does not move real money, and the virtual card is not a payment card.

## Build
Open the repository in Android Studio (Hedgehog or newer), sync Gradle, then choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

Or open **Actions → Build Android APK → Run workflow**. The generated APK is attached to the successful workflow run as the `neon-wallet-debug-apk` artifact.
