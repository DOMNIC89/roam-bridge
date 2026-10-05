# RoamBridge (SIMRelay) 📱🌉

> **High-reliability, open-source Android SMS & OTP Gateway for expatriates, remote workers, and cross-border users.**

RoamBridge turns any secondary or spare Android phone left in your home country into an intelligent, zero-cost, two-way SMS relay bridge.

---

## 🌟 Key Features

### 1. 📥 Inbound Smart Forwarding
* **Intelligent On-Device Topic Classifier:** Classifies SMS in real-time into:
  * 🚨 **Critical OTP / 2FA** (High-priority token extraction)
  * 💳 **Banking & Transactions** (Amount & debit/credit detection)
  * 🏛️ **Govt & Utilities** (Bill due alerts, tax notices)
  * 💬 **Personal & Direct** (Normal text messages)
  * 📢 **Spam Filter** (Suppresses promotional spam)
* **Instant Telegram Alerts:** Delivers formatted alerts to your personal Telegram Bot in `<2 seconds`.
* **Direct SMTP Emailing:** Dispatches structured HTML emails via your Gmail (App Password), Fastmail, or custom SMTP server.

### 2. 📤 Reverse SMS (Remote Outbox)
Send SMS from your home SIM card while sitting anywhere in the world!
* **Telegram Trigger:** Simply send `/sms <phone_number> <message>` to your Telegram Bot. The phone dispatches the SMS and replies with a delivery confirmation.
* **Email (IMAP) Trigger:** Send an email with subject `[SEND-SMS: <PIN>]` containing `TO: <number>` and `MSG: <content>`.

### 3. 🛡️ Unattended 24/7 Resilience
* **Persistent Foreground Daemon:** Resists Android OS background kills.
* **Auto-Start on Boot:** Restarts automatically if the home phone reboots.
* **Daily Heartbeat Monitor:** Sends battery %, charging status, and signal strength updates.
* **Zero-Knowledge Privacy:** All routing is peer-to-peer directly between your phone and your Telegram/SMTP accounts.

---

## 🚀 Setup Guide

### A. Telegram Bot Setup (2 Minutes)
1. Message `@BotFather` on Telegram and create a new bot (run `/newbot`).
2. Copy your **Bot Token** (e.g. `123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ`).
3. Message `@userinfobot` on Telegram to get your numerical **Chat ID** (e.g. `987654321`).
4. Enter both in the RoamBridge **Settings** screen and tap **Send Test Message**.

### B. Direct SMTP Setup (e.g. Gmail)
1. Go to your Google Account $\rightarrow$ **Security** $\rightarrow$ **2-Step Verification** $\rightarrow$ **App Passwords**.
2. Generate an App Password named `RoamBridge`.
3. In RoamBridge Settings:
   * **Host:** `smtp.gmail.com` | **Port:** `465` (SSL)
   * **Username:** Your full Gmail address
   * **Password:** The 16-character App Password
   * **Destination Email:** Your personal email abroad.
4. Tap **Send Test Email via SMTP**.

### C. Reverse SMS Commands
To send an SMS from home:
* **Via Telegram:**
  ```text
  /sms +14155552671 ACTIVATE CARD 1234
  ```
* **Via Email:**
  ```text
  Subject: [SEND-SMS: 1234]
  Body:
  TO: +14155552671
  MSG: ACTIVATE CARD 1234
  ```

---

## 🏗️ Project Architecture

```text
app/src/main/java/com/roambridge/app/
├── data/
│   ├── classifier/
│   │   ├── SmsClassifier.kt          # Regex & heuristic topic / OTP parser
│   │   └── SmsTopic.kt               # Topic taxonomy
│   ├── db/
│   │   ├── AppDatabase.kt            # Room database
│   │   ├── SmsLogDao.kt              # Log queries & metrics
│   │   └── SmsLogEntity.kt           # Room entity
│   ├── model/
│   │   └── AppSettings.kt            # Configuration data model
│   ├── network/
│   │   ├── TelegramService.kt        # Telegram Bot API client & command poller
│   │   ├── SmtpSender.kt             # Direct SMTP emailer (Jakarta Mail)
│   │   └── ImapReceiver.kt           # IMAP email reader for reverse SMS
│   └── repository/
│       ├── SettingsRepository.kt     # Encrypted shared preferences
│       └── SmsRepository.kt          # Relay coordinator
├── receiver/
│   ├── SmsBroadcastReceiver.kt       # Inbound SMS interceptor
│   └── BootCompletedReceiver.kt      # Boot receiver daemon starter
├── service/
│   ├── BridgeForegroundService.kt    # Foreground service & keep-alive
│   ├── SmsOutboxManager.kt           # SmsManager cellular dispatcher
│   └── HeartbeatWorker.kt            # Daily health & battery reporter
├── ui/
│   ├── components/                   # Bottom nav, Topic & Status badges
│   ├── screens/
│   │   ├── DashboardScreen.kt        # Live relay activity feed & stats
│   │   ├── SettingsScreen.kt         # Telegram, SMTP & PIN credentials
│   │   ├── RulesScreen.kt            # Topic routing preferences
│   │   └── OutboxScreen.kt           # Reverse SMS testing & history
│   └── MainActivity.kt               # Jetpack Compose UI entry point
└── util/
    └── PermissionHelper.kt           # Runtime SMS & battery permissions
```

---

## 🛠️ Building & Installing

### Prerequisites
* Android Studio Ladybug / Meerkat or newer
* JDK 17+
* Android SDK 35 (minSdk 26)

### Run & Build
1. Open the repository root in Android Studio.
2. Let Gradle sync dependencies.
3. Build & run on your Android device:
   ```bash
   ./gradlew assembleDebug
   ```
4. Grant SMS, Notification, and Battery Optimization permissions on first launch.
