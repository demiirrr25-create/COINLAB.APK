# CoinLab v8.1 🪙

**Türkiye'nin en kapsamlı kripto para takip uygulaması**

[![Android CI](https://github.com/coinlab/coinlab-android/actions/workflows/android.yml/badge.svg)](https://github.com/coinlab/coinlab-android/actions)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-purple.svg)](https://developer.android.com/jetpack/compose)

---

## 🚀 v7.7 — Devrim Güncellemesi

### Performans & Hız
- **R8 Full Mode** ile APK boyutu %30+ küçülme
- **Coil görsel cache**: 100MB disk + 50MB bellek cache (coin logoları anında yüklenir)
- **WebSocket buffer** 128→256 (250+ coin akışı için optimize)
- **Connection Pool** 15 bağlantı / 120s keep-alive (daha hızlı API yanıtları)
- **Metadata cache** 24h→12h (daha taze coin verileri)

### 250+ Coin Desteği
- **BinanceCoinMapper**: 50→250+ coin (TON, KAS, TAO, PEPE, WIF, BONK, JUP, EIGEN, HMSTR ve çok daha fazlası)
- **HardcodedCoinFallback**: Temizlendi, 250 gerçek benzersiz Binance USDT çifti
- **StaticFallbackData**: 20→50 coin (tam offline ilk açılış desteği)
- **DynamicCoinRegistry**: Runtime'da 1000+ coin keşfi

### Yeni Özellikler
- Airdrop Takip Ekranı
- Staking Yönetimi
- Web3 Cüzdan Entegrasyonu
- Coin Karşılaştırma
- Teknik Analiz Ekranı
- Topluluk Ekranı

---

## 📱 Özellikler

### Piyasa Takibi
- 250+ kripto para canlı fiyat takibi
- Gerçek zamanlı Binance WebSocket bağlantısı
- Detaylı coin bilgileri ve istatistikleri
- İnteraktif fiyat grafikleri (1G, 7G, 1A, 3A, 1Y)
- Gelişmiş arama ve filtreleme
- Trend, kazananlar ve kaybedenler listeleri

### Portföy Yönetimi
- Al/Sat işlem takibi
- Toplam bakiye ve kâr/zarar hesaplama
- Ortalama alış fiyatı ve PnL analizi
- Birden fazla kripto para desteği

### Takip Listesi
- Favori kripto paraları takibe alma
- Hızlı erişim ve fiyat takibi

### Fiyat Uyarıları
- Hedef fiyat belirleme (üstüne çıkma / altına düşme)
- Arka plan fiyat kontrolü (WorkManager)
- Anlık bildirim desteği

### Kripto Haberler
- CryptoCompare entegrasyonu
- Kategori bazlı filtreleme (BTC, ETH, Trading, Mining, Regülasyon, Teknoloji)
- Kaynak ve tarih bilgisi

### Widget
- Ana ekran widget'ı ile Top 5 coin takibi
- Canlı fiyat ve değişim oranları
- Glance API ile modern tasarım

### Ayarlar
- Tema: Koyu / Açık / Sistem
- Para birimi: TRY, USD, EUR, GBP
- Dil: Türkçe / English
- Biyometrik kilit (parmak izi / yüz tanıma)
- Bildirim tercihleri

---

## 🏗️ Mimari

```
MVVM + Clean Architecture
├── data/          (API, Database, Repository Implementation)
├── domain/        (Models, Repository Interfaces)
├── di/            (Hilt Dependency Injection)
├── ui/            (Jetpack Compose Screens)
│   ├── market/
│   ├── detail/
│   ├── portfolio/
│   ├── news/
│   ├── settings/
│   ├── components/
│   ├── navigation/
│   └── theme/
├── widget/        (Glance App Widget)
├── notification/  (NotificationHelper)
└── worker/        (WorkManager - PriceAlertWorker)
```

---

## 🛠️ Teknoloji Stack

| Kategori | Teknoloji |
|----------|-----------|
| Dil | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt 2.53.1 |
| Networking | Retrofit 2.11.0 + OkHttp 4.12.0 |
| WebSocket | OkHttp (Binance) |
| Database | Room 2.6.1 |
| Preferences | DataStore 1.1.1 |
| Charts | Vico 2.0.0-beta.2 |
| Image | Coil 2.7.0 |
| Widget | Glance 1.1.1 |
| Background | WorkManager 2.10.0 |
| Navigation | Navigation Compose 2.8.5 |
| Build | Gradle 8.5.2 + AGP 8.5.2 + KSP |

---

## 🌐 API Kaynakları

- **CoinGecko API** — Kripto para verileri, fiyatlar, grafikler
- **CryptoCompare API** — Kripto haberler
- **Binance WebSocket** — Gerçek zamanlı fiyat akışı

---

## 🚀 Kurulum

### Gereksinimler
- Android Studio Hedgehog (2023.1.1) veya üzeri
- JDK 17
- Android SDK 35
- Min SDK 26 (Android 8.0)

### Build
```bash
git clone https://github.com/coinlab/coinlab-android.git
cd coinlab-android
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew bundleRelease
```

---

## 📋 Proje Yapısı

```
app/
├── src/main/
│   ├── java/com/coinlab/app/
│   │   ├── CoinLabApp.kt              # Application class
│   │   ├── MainActivity.kt            # Single Activity
│   │   ├── data/
│   │   │   ├── local/                  # Room DB, Entities, DAOs
│   │   │   ├── remote/                 # Retrofit APIs, DTOs, WebSocket
│   │   │   └── repository/            # Repository implementations
│   │   ├── di/                         # Hilt modules
│   │   ├── domain/
│   │   │   ├── model/                  # Domain models
│   │   │   └── repository/            # Repository interfaces
│   │   ├── notification/               # Push notifications
│   │   ├── ui/
│   │   │   ├── components/            # Reusable composables
│   │   │   ├── detail/                # Coin detail screen
│   │   │   ├── market/                # Market list screen
│   │   │   ├── navigation/            # Nav graphs
│   │   │   ├── news/                  # News feed screen
│   │   │   ├── portfolio/             # Portfolio + transactions
│   │   │   ├── settings/              # Settings screen
│   │   │   └── theme/                 # Material 3 theme
│   │   ├── widget/                    # Glance widget
│   │   └── worker/                    # WorkManager workers
│   └── res/
│       ├── drawable/                   # Icons, drawables
│       ├── layout/                     # Widget layouts
│       ├── mipmap-anydpi-v26/         # Adaptive icons
│       ├── values/                     # strings (TR), colors, themes
│       ├── values-en/                  # English strings
│       └── xml/                        # Network config, widget info
├── build.gradle.kts
└── proguard-rules.pro
```

---

## 📄 Lisans

Copyright © 2024 CoinLab. Tüm hakları saklıdır.

---

## 🔗 Bağlantılar

- **Web**: [coinlabtr.com](https://coinlabtr.com)
- **Destek**: info@coinlabtr.com