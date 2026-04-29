# Contributing Guidelines

Спасибо за интерес к проекту **CVT Temperature Monitor**! Этот документ описывает, как можно внести свой вклад в развитие приложения.

## 📋 Как предложить изменения

### 1. Создайте fork репозитория

Нажмите кнопку **Fork** на GitHub, чтобы создать копию репозитория в вашем аккаунте.

### 2. Клонируйте fork локально

```bash
git clone https://github.com/YOUR_USERNAME/CVT.git
cd CVT
```

### 3. Создайте ветку для изменений

```bash
git checkout -b feature/your-feature-name
# или для исправления багов:
git checkout -b fix/bug-description
```

### 4. Внесите изменения

- Следуйте существующему стилю кода (Kotlin Conventions)
- Добавляйте комментарии для сложной логики
- Обновляйте документацию при изменении функциональности

### 5. Проверьте код

```bash
# Запустите тесты
./gradlew test

# Проверьте линтером
./gradlew lint

# Убедитесь, что сборка проходит
./gradlew assembleDebug
```

### 6. Закоммитьте изменения

Используйте понятные сообщения коммитов:

```bash
git add .
git commit -m "feat: добавить поддержку нового PID"
# или
git commit -m "fix: исправить ошибку подключения на Android 12+"
```

**Формат сообщений:**
- `feat:` — новая функциональность
- `fix:` — исправление бага
- `docs:` — обновление документации
- `style:` — форматирование, стиль кода
- `refactor:` — рефакторинг без изменения функциональности
- `test:` — добавление/обновление тестов
- `chore:` — служебные изменения

### 7. Отправьте Pull Request

1. Запушьте ветку в ваш fork:
   ```bash
   git push origin feature/your-feature-name
   ```
2. Перейдите на страницу вашего fork на GitHub
3. Нажмите **Compare & pull request**
4. Опишите изменения в PR

---

## 🧪 Требования к коду

### Стиль кода

- Следуйте [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Используйте 4 пробела для отступов
- Максимальная длина строки — 120 символов

### Тесты

- Новые функции должны покрываться unit-тестами
- Существующие тесты не должны ломаться

### Документация

- Обновляйте README.md при изменении API или функциональности
- Добавляйте KDoc для публичных методов и классов

---

## 🏗️ Архитектура проекта

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

Проект использует архитектуру с разделением на слои:
- **UI** (Compose) → **ViewModel** → **UseCase** → **Repository** → **Data Source**

---

## 🔧 Полезные команды

```bash
# Сборка debug-версии
./gradlew assembleDebug

# Сборка release-версии
./gradlew assembleRelease

# Запуск всех тестов
./gradlew test

# Запуск линтера
./gradlew lint

# Форматирование кода (если настроено)
./gradlew ktlintFormat
```

---

## ❓ Вопросы?

- Откройте [Issue](https://github.com/shlyahten/CVT/issues) для обсуждения изменений
- Напишите автору через GitHub

---

Спасибо за ваш вклад! 🎉
