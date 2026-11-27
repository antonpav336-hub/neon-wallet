# NEON WALLET

Native Android wallet prototype built with Kotlin and Jetpack Compose.

## Included experience

- Russian-language interface with a graphite and lavender visual system
- Local registration and sign-in with PBKDF2 password hashing
- PIN setup and PIN gate when returning to the app
- Dashboard, masked balance, sample top-up, transfer and withdrawal flows
- Payment hub with QR scanning through CameraX and ML Kit
- Contact permission and recipient selection from the phone book
- Bank selection for NEON, ВТБ, Сбер, Т-Банк and Альфа-Банк
- Recipient favourites, amount presets, operation processing and shareable receipts
- Virtual card screen with show/hide details, freeze/unfreeze and spending limit controls
- Searchable and filterable operation history
- High-resolution bundled graphite/lavender artwork and bank mark assets

## Demo and security boundaries

**This is still a local prototype, not a real bank or custodial wallet.** Balances, top-ups, transfers, withdrawals and card payments are simulated and stored locally on the device. The QR scanner reads QR content but does not itself execute a real payment. No real bank account or payment card is issued, and no money is moved.

Passwords and PINs are hashed locally. Account data is not synchronized between devices. A production wallet would still need a secure backend, verified payment-provider webhooks, a regulated banking/payment partner, KYC/AML controls and security review.

## Build

Open the repository in Android Studio (Hedgehog or newer), sync Gradle, then choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

Or open **Actions → Build Android APK → Run workflow**. The workflow runs unit tests, generates the bundled visual artwork, builds the debug APK and attaches it as the `neon-wallet-debug-apk` artifact.

## Bank mark attribution

The Sber, VTB, T-Bank and Alfa-Bank marks are bundled solely to identify the selected bank. The vector artwork was adapted from the Russian bank icon set in [sqkrv/sharespences](https://github.com/sqkrv/sharespences/tree/main/web/src/assets/banks). Bank names and logos remain trademarks of their respective owners; NEON WALLET is not affiliated with or endorsed by those banks.
