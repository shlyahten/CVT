# CVT Temperature Monitor for Mitsubishi Lancer X

[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-purple?logo=kotlin)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-8.0+-green?logo=android)](https://www.android.com/)
[![Build Status](https://github.com/shlyahten/CVT/actions/workflows/android.yml/badge.svg)](https://github.com/shlyahten/CVT/actions)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-latest-blue)](https://developer.android.com/jetpack/compose)

Android-приложение на **Kotlin + Jetpack Compose** для чтения температуры вариатора (CVT) автомобилей **Mitsubishi Lancer X** через адаптер **ELM327** по Bluetooth Classic (SPP).

---

## 📸 Скриншоты

| Главный экран | Выбор устройства | Ошибка подключения |
|:-------------:|:----------------:|:------------------:|
| ![Main Screen](screenshots/main_screen.png) | ![Device Selection](screenshots/device_selection.png) | ![Connection Error](screenshots/connection_error.png) |

---

## ✨ Функции

- 🌡️ **Чтение температуры CVT** — два режима (Temp 1 / Temp 2) по PID `2103`
- 🛢️ **Деградация масла** (опционально) — PID `2110`
- 🔄 **Авто-обновление** данных ~1 раз в секунду
- 📋 **Лог событий** подключения и ошибок
- 📱 **Современный UI** на Jetpack Compose с Material 3

---

## 📋 Требования

| Компонент | Требование |
|-----------|------------|
| **OS** | Android 8.0 (API 24) и выше |
| **Bluetooth** | Обязателен Bluetooth Classic (SPP) |
| **Адаптер** | ELM327 или совместимый OBDII-адаптер |
| **Автомобиль** | Mitsubishi Lancer X с вариатором CVT |

> ⚠️ **Android 12+**: требуется разрешение `BLUETOOTH_CONNECT` (выдаётся при первом запуске).

---

## 📥 Установка

### Для пользователей

Скачайте готовый APK из артефактов CI:

1. Перейдите на страницу [GitHub Actions](https://github.com/shlyahten/CVT/actions)
2. Выберите последний успешный запуск сборки
3. В разделе **Artifacts** скачайте `debug-apk` или `release-apk`
4. Установите APK на устройство (разрешите установку из неизвестных источников)

### Для разработчиков

```bash
# Клонирование репозитория
git clone https://github.com/shlyahten/CVT.git
cd CVT

# Сборка debug-версии
./gradlew assembleDebug

# APK будет расположен в app/build/outputs/apk/debug/app-debug.apk
```

---

## 🚀 Использование

1. **Спарьте ELM327** в настройках Bluetooth Android
   - PIN по умолчанию: `1234` или `0000`
2. **Запустите приложение**
3. **Выдайте разрешение** `BLUETOOTH_CONNECT` (Android 12+)
4. **Выберите устройство** из списка спаренных
5. Нажмите **Connect**
6. Температура обновляется автоматически (~1 сек)

---

## 🔧 PIDs и формулы

### PID `2103` — Температура CVT

```
Header: 7E1
ModeAndPID: 2103
```

**Формулы:**

| Название | Формула |
|----------|---------|
| **Temp 1** | `(0.000000002344*(N^5))+(-0.000001387*(N^4))+(0.0003193*(N^3))+(-0.03501*(N^2))+(2.302*N)+(-36.6)` |
| **Temp 2** | `(0.0000286*N*N*N)+(-0.00951*N*N)+(1.46*N)+(-30.1)` |

### PID `2110` — Деградация масла (опционально)

```
Header: 7E1
ModeAndPID: 2110
Formula: AC*256+AD
```

### Переменные

| Переменная | Описание |
|------------|----------|
| `AA`, `AB`, `AC`, `AD`… | Байты данных после ответа `61 xx` |
| `A`, `B`, `C`, `D`… | Алиасы к `AA`, `AB`, `AC`, `AD` |
| `N` | Алиас к `AA` (основная переменная для температурных формул) |

---

## ❗ Troubleshooting

| Проблема | Решение |
|----------|---------|
| **Нет устройства в списке** | Устройство не спарено в системных настройках Bluetooth |
| **NO DATA** | ECU не отвечает на `2103`/`2110`: проверьте протокол, качество связи, поддержку адаптером |
| **Connect error** | Неудачное спаривание, неправильный PIN, адаптер занят другим приложением |
| **Нет разрешения на Android 12+** | Выдайте `BLUETOOTH_CONNECT` в настройках приложения или переустановите |
| **Адаптер не определяется** | Попробуйте другой ELM327 (дешёвые клоны могут не поддерживать расширенные PID) |

---

## 👨‍💻 Для разработчиков

### Архитектура

Проект использует подход **Repository / Use Case**:

```
app/src/main/java/ru/shlyahten/cvt/
├── bluetooth/          # Bluetooth SPP клиент
├── config/             # Конфигурация PID и автомобилей
├── data/repository/    # Репозиторий для работы с OBD
├── domain/             # Бизнес-логика (UseCases, Models)
├── elm/                # Парсинг ответов ELM327
├── obd/                # Декодирование OBD-данных, формулы
├── ui/                 # Jetpack Compose UI + ViewModel
└── model/              # UI-модели
```

### Как добавить новый PID / формулу

1. Откройте [`VehicleConfigs.kt`](app/src/main/java/ru/shlyahten/cvt/config/VehicleConfigs.kt)
2. Добавьте новый `PidConfig` или расширьте существующий:

```kotlin
val NewPid = PidConfig(
    modeAndPid = "21XX",
    headerHex = "7E1",
    formulas = mapOf(
        "MyFormula" to "A*256+B-40"  // Пример формулы
    )
)
```

3. Обновите `VehicleConfig` и `allConfigs`
4. Формулы поддерживают переменные `A`–`H`, `N`, арифметические операции и функции

### Сборка релиза

```bash
# Сборка release-версии
./gradlew assembleRelease

# APK будет в app/build/outputs/apk/release/app-release.apk
```

Для подписанной версии настройте `signingConfigs` в `app/build.gradle.kts`.

### Тесты

```bash
# Запуск unit-тестов
./gradlew test

# Запуск instrumented-тестов
./gradlew connectedAndroidTest
```

---

## 🤝 Contributing

Вклад в проект приветствуется!

1. Создайте fork репозитория
2. Создайте ветку `feature/your-feature-name`
3. Внесите изменения и убедитесь, что тесты проходят
4. Проверьте код линтером: `./gradlew lint`
5. Отправьте Pull Request

**Перед PR убедитесь:**
- ✅ Код компилируется без ошибок
- ✅ Unit-тесты проходят
- ✅ Линтинг не выдаёт критичных предупреждений

---

## 📄 Лицензия

MIT License — см. файл [LICENSE](LICENSE), если присутствует.

---

## 📬 Контакты

Автор: [shlyahten](https://github.com/shlyahten)  
Репозиторий: [github.com/shlyahten/CVT](https://github.com/shlyahten/CVT)
