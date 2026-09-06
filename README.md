# CVT Temperature Monitor for Mitsubishi Lancer X

[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-purple?logo=kotlin)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-8.0+-green?logo=android)](https://www.android.com/)
[![Android CI](https://github.com/shlyahten/CVT/actions/workflows/android.yml/badge.svg)](https://github.com/shlyahten/CVT/actions/workflows/android.yml)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-latest-blue)](https://developer.android.com/jetpack/compose)

Android-приложение на **Kotlin + Jetpack Compose** для мониторинга температуры вариатора (CVT) автомобилей **Mitsubishi Lancer X** через адаптер **ELM327** по Bluetooth Classic (SPP).

Оптимизировано как для смартфонов, так и для **автомагнитол (ГУ Teyes и других Android-устройств)**: включает фоновый сервис с автоподключением, плавающий оверлей поверх навигаторов и автозапуск при старте системы.

---

## 📸 Скриншоты

| Главный экран | Выбор устройства | Ошибка подключения |
|:-------------:|:----------------:|:------------------:|
| ![Main Screen](screenshots/main_screen.png) | ![Device Selection](screenshots/device_selection.png) | ![Connection Error](screenshots/connection_error.png) |

---

## ✨ Функции

- 🌡️ **Чтение температуры CVT** — режимы Temp 1 (PIDs.csv), Temp 2 (кубическая аппроксимация), а также Raw N (сырое значение счетчика) по PID `2103` (header `7E1`)
- 🎨 **Цветовые зоны температуры** — индикация прогрева (<50°C), нормы (50–89°C), повышенной (90–99°C) и перегрева (≥100°C)
- 🪟 **Плавающий виджет (Overlay)** — перетаскиваемый компактный индикатор температуры поверх навигатора (Яндекс.Навигатор, 2ГИС)
- 🚗 **Оптимизация для ГУ Teyes / Android-магнитол**:
  - Фоновый Foreground Service (`CvtOverlayService`) для непрерывного опроса
  - Автоподключение к последнему выбранному OBD-адаптеру
  - Автозапуск службы при включении зажигания / загрузке ГУ (`BOOT_COMPLETED`, `QUICKBOOT_POWERON`, `com.ts.headunit.power.on`)
  - Адаптивный ландшафтный двухколоночный интерфейс
- 🛢️ **Деградация масла** (по запросу) — PID `2110`
- 🔄 **Авто-обновление** данных ~1 раз в секунду с обработкой потери связи
- 📋 **Лог событий** подключения и ошибок с возможностью копирования и очистки
- 📱 **Современный UI** на Jetpack Compose с Material 3 и автомобильной тёмной темой

---

## 📋 Требования

| Компонент | Требование |
|-----------|------------|
| **OS** | Android 8.0 (API 26) и выше |
| **Bluetooth** | Обязателен Bluetooth Classic (SPP) |
| **Адаптер** | ELM327 (v1.5 рекомендуется) или совместимый OBDII-адаптер |
| **Автомобиль** | Mitsubishi Lancer X с вариатором CVT (или соплатформенный Outlander XL) |

> ⚠️ **Android 12+**: требуется системное разрешение `BLUETOOTH_CONNECT`.  
> ⚠️ **Плавающий виджет**: требуется системное разрешение «Поверх других приложений» (`SYSTEM_ALERT_WINDOW`).

---

## 📥 Установка

### Для пользователей

Скачайте готовый APK из артефактов GitHub Actions CI:

1. Перейдите на страницу [GitHub Actions](https://github.com/shlyahten/CVT/actions)
2. Выберите последний успешный запуск рабочего процесса **Android CI**
3. В разделе **Artifacts** скачайте:
   - `release-apk` — оптимизированная релизная сборка
   - `debug-apk` — отладочная сборка
4. Установите APK на устройство (разрешите установку из неизвестных источников)

### Для разработчиков

```bash
# Клонирование репозитория
git clone https://github.com/shlyahten/CVT.git
cd CVT

# Сборка debug-версии
./gradlew assembleDebug

# Сборка release-версии
./gradlew assembleRelease

# Сборка Android App Bundle
./gradlew bundleRelease
```

Собранные файлы:
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`
- Release Bundle: `app/build/outputs/bundle/release/app-release.aab`

---

## 🚀 Использование

1. **Спарьте ELM327** в настройках Bluetooth Android
   - PIN по умолчанию: обычно `1234` или `0000`
2. **Запустите приложение CVT**
3. **Выдайте разрешения**:
   - `BLUETOOTH_CONNECT` (Android 12+)
   - «Поверх других окон» (для плавающего виджета)
   - «Уведомления» (для работы фонового сервиса)
4. **Выберите устройство** из списка спаренных
5. Нажмите **START MONITOR**
6. **Настройки для автомагнитолы (Teyes)**:
   - Включите переключатель **«Плавающий виджет»**, чтобы видеть температуру в навигаторах
   - Включите **«Автозапуск на ГУ»** и **«Автоподключение»**, чтобы мониторинг активировался при включении зажигания / пробуждении ГУ

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
| **Temp 1** (PIDs.csv) | `(0.000000002344*(N^5))+(-0.000001387*(N^4))+(0.0003193*(N^3))+(-0.03501*(N^2))+(2.302*N)+(-36.6)` |
| **Temp 2** (Кубическая) | `(0.0000286*N*N*N)+(-0.00951*N*N)+(1.46*N)+(-30.1)` |
| **Raw N** | Сырое значение счетчика байта `N` без пересчёта |

### PID `2110` — Деградация масла (CVT Oil Degradation)

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
| `N` | Алиас к первому байту данных `AA` (основная переменная для формул CVT) |

---

## ❗ Troubleshooting

| Проблема | Причина / Решение |
|----------|-------------------|
| **Нет адаптера в списке** | Спарьте адаптер в системных настройках Bluetooth Android |
| **Виджет не появляется** | Разрешите приложению «Отображение поверх других окон» (`SYSTEM_ALERT_WINDOW`) в системных настройках |
| **Сервис выгружается в фоне** | Отключите оптимизацию батареи («Не экономить заряд») для CVT в настройках Android |
| **NO DATA / Ошибка опроса** | ЭБУ не отвечает на `2103`: проверьте протокол (ISO 15765-4 CAN 11bit 500k), включено ли зажигание, качество клона ELM327 |
| **Connect error** | Адаптер занят другим приложением (Torque, Car Scanner и т.д.), либо неверный PIN |
| **Адаптер не читает PID** | Используйте качественный ELM327 v1.5 (усечённые v2.1 часто не поддерживают расширенные PID Mitsubishi) |

---

## 👨‍💻 Для разработчиков

### Архитектура

Проект построен по модульному принципу Clean Architecture (Repository / Use Case) с разделением слоев:

```
app/src/main/java/ru/shlyahten/cvt/
├── BootReceiver.kt          # Приём широковещательных интентов старта системы (Teyes / BOOT)
├── CvtApp.kt                # Application-класс, инициализация каналов уведомлений
├── CvtOverlayService.kt     # Foreground Service: непрерывный опрос OBD и плавающий виджет
├── MainActivity.kt          # Compose UI: адаптивный интерфейс (телефон / планшет / ГУ)
├── bluetooth/               # Bluetooth Classic (SPP) сокет-клиент
├── config/                  # Конфигурация PID и автомобилей (VehicleConfigs)
├── data/
│   ├── AppSettings.kt       # Настройки приложения (SharedPreferences)
│   └── repository/          # Репозиторий для работы с OBD
├── domain/                  # Бизнес-логика (UseCases, Models)
├── elm/                     # Инициализация и парсинг ответов ELM327
├── obd/                     # Декодирование OBD-данных, формулы
├── ui/                      # Jetpack Compose UI + ViewModel + Dark Theme
└── model/                   # UI-модели
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

### Сборка и тестирование

```bash
# Проверка линтером
./gradlew lintDebug

# Запуск unit-тестов
./gradlew test

# Запуск instrumented-тестов на подключенном устройстве
./gradlew connectedAndroidTest

# Сборка release-версии
./gradlew assembleRelease
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
