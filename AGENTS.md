# AGENTS.md — Routine Flow

## Проект

Routine Flow — офлайн Android-приложение для цепочек действий с повторяющимся расписанием. Пользователь создаёт цепочки, добавляет действия, задаёт длительность и повторение, запускает цепочки и отмечает выполнение.

Главные ограничения:

- не терять пользовательские данные;
- сохранять офлайн-работу;
- не ломать обратную совместимость JSON;
- все пользовательские строки локализовать на русский и английский;
- новые изменения покрывать тестами.

## Стек

- Kotlin, Android View, Material Components, без Compose;
- MVVM, `StateFlow`, Coroutines;
- Hilt через KSP;
- `org.json`;
- min API 26, compile/target SDK 35;
- AGP 8.6.1, Gradle Wrapper 8.7;
- `applicationId`: `com.routineflow.app`.

## Структура

```text
app/src/main/kotlin/com/routineflow/app/
├── MainActivity.kt             # host навигации, insets, callbacks UI
├── model/                      # Chain, Action, AppState, RecurrenceRule
├── data/                       # Repository и routine-flow.json
├── domain/                     # Use Cases, повторения, codec, алгоритмы
├── presentation/
│   ├── MainViewModel.kt        # состояние, CRUD, таймер, навигация
│   ├── *Formatter.kt           # форматирование для UI
│   ├── ScreenLayout.kt         # общие layout-параметры экранов
│   ├── components/             # переиспользуемые диалоги и UI-компоненты
│   └── screens/                # Run, Chains, ChainEditor, Execution,
│                               # Running и Stats
├── notifications/              # RoutineNotifier и Android-реализация
└── di/                         # Hilt-модули

app/src/test/                   # unit-тесты domain, data, ViewModel
app/src/androidTest/            # Android/UI-тесты экранов и сценариев
app/src/main/res/               # строки, темы, drawable иконки
```

## Куда добавлять код

### Экран

Новый экран — отдельный builder-файл в `presentation/screens/`. Он получает состояние и callbacks через конструктор и возвращает `View`/`LinearLayout`.

Подключение навигации выполняется в `MainActivity`. Экран не должен сам создавать ViewModel, Repository или сохранять данные. Для экрана с закреплённой нижней панелью использовать `ScreenLayout.fillRemaining()` или layout с `height = 0` и `weight = 1`.

### Компонент

Переиспользуемый UI-блок — отдельный файл в `presentation/components/`. Он не должен зависеть от конкретной Activity и должен получать данные и callbacks через параметры.

Одноразовый простой блок можно оставить приватной функцией screen builder.

### Логика и данные

- расчёт повторений — `domain/`, отдельная Strategy и Factory;
- прикладная операция — Use Case в `domain/`;
- состояние — `MainViewModel` и `StateFlow`;
- новый источник данных — интерфейс Repository в `data/`, реализация и Hilt binding в `di/`;
- уведомления — через `RoutineNotifier`;
- форматирование для пользователя — `presentation`, не domain.

Бизнес-логику не добавлять в `MainActivity`. Repository и Use Case не создавать вручную в UI.

### Строки и темы

Каждую пользовательскую строку добавлять одновременно в:

- `app/src/main/res/values/strings.xml`;
- `app/src/main/res/values-en/strings.xml`.

Проверять светлую и тёмную тему. Не использовать жёсткие белый/чёрный цвета без проверки контраста. Для новых иконок указывать `contentDescription`, если иконка интерактивная.

## Данные и JSON

Файл данных: внутреннее хранилище приложения, `routine-flow.json`. Текущий JSON — `version: 2`; старый корневой массив и `durationMinutes` должны продолжать читаться.

При изменении формата:

1. увеличить `version`;
2. оставить чтение предыдущего формата;
3. добавить тесты на старый и новый формат;
4. не удалять данные автоматически.

Правила повторения типизированы через `RecurrenceRule`. `RecurrenceRuleCodec` сохраняет совместимость со строками `NONE`, `DAILY`, `WEEKLY:Пн,Ср`, `MONTHLY:1,15`, `INTERVAL:3:DAYS`.

## Тесты

Для каждой новой логики или исправления добавлять тест:

- чистая логика, codec, повторения и ViewModel — `app/src/test`;
- открытие экрана и пользовательский сценарий — `app/src/androidTest`;
- для бага сначала тест, воспроизводящий ошибку;
- UI-тесты искать элементы по ресурсам, `contentDescription` или стабильному test tag, а не по координатам;
- тесты не должны зависеть от порядка запуска и данных другого теста.

Перед завершением изменения запускать:

```bash
./gradlew testDebugUnitTest
./gradlew compileDebugAndroidTestKotlin
./gradlew assembleDebug
git diff --check
```

Полный запуск UI-тестов требует подключённого телефона или эмулятора:

```bash
./gradlew connectedDebugAndroidTest
```

## Gradle JDK и JBR Android Studio

Системная Java может отсутствовать — это нормально. Используем JBR, который уже установлен вместе с Android Studio.

### Настройка в Android Studio

1. Открыть `Settings/Preferences`.
2. Перейти в `Build, Execution, Deployment → Build Tools → Gradle`.
3. В поле `Gradle JDK` выбрать `Embedded JDK` или JBR Android Studio.
4. Нажать `Apply`, затем выполнить Gradle Sync.

На macOS путь обычно такой:

```text
/Applications/Android Studio.app/Contents/jbr/Contents/Home
```

Если Android Studio установлена в другом месте, взять путь из поля `Gradle JDK` или найти каталог `Contents/jbr/Contents/Home` внутри приложения.

### Запуск из терминала

Не нужно устанавливать отдельный JDK. Перед командами Gradle указать JBR Android Studio:

```bash
GRADLE_JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
PATH="$GRADLE_JAVA_HOME/bin:$PATH" "$GRADLE_JAVA_HOME/bin/java" -version
PATH="$GRADLE_JAVA_HOME/bin:$PATH" ./gradlew testDebugUnitTest
PATH="$GRADLE_JAVA_HOME/bin:$PATH" ./gradlew assembleDebug
```

В выводе первой команды должна быть Java 21 от JetBrains. Для одной команды можно указать полный путь к `bin` JBR:

```bash
PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH" \
./gradlew testDebugUnitTest assembleDebug
```

Не объединять объявление `GRADLE_JAVA_HOME` и его использование в `PATH` на одной строке: shell может не подставить переменную до запуска команды. Надёжные варианты — отдельные строки выше или полный путь, как в примере.

Если путь не существует, Android Studio закрыта/установлена в другом месте или JBR обновился, проверить реальный путь в настройках Android Studio. После изменения JDK повторить проверку `java -version` и Gradle Sync.

## README и CHANGELOG

Обновлять `README.md`, если изменились структура проекта, команды запуска, возможности, формат данных или тестовый процесс.

В `CHANGELOG.md` новые записи добавлять сверху. Каждый набор изменений, который будет закоммичен, сразу получает новую версию — отдельный `Unreleased` не используется. Заголовок всегда содержит версию и дату в формате `## 2.9.0 — 2026-08-08`.

Правило итераций:

1. Перед началом изменения определить следующую версию.
2. Сразу создать верхний раздел этой версии в `CHANGELOG.md`.
3. После каждой итерации правки кода дополнять тот же раздел описанием изменений.
4. Не создавать новую версию для промежуточных итераций, пока текущий набор не закоммичен.
5. После создания коммита текущая версия считается закрытой; следующий набор изменений получает новую версию и новый верхний раздел.

Любое изменение кода обязательно отражать в `CHANGELOG.md` в рамках текущей версии до создания коммита.

Версия имеет формат `MAJOR.MINOR.PATCH`:

- `MAJOR` — большой эпик; например `0.5.3 → 1.0.0`;
- `MINOR` — новая фича; например `0.1.22 → 0.2.0`;
- `PATCH` — баг, небольшая правка или документация; например `0.2.0 → 0.2.1`.

## Рабочий процесс

1. Определить слой изменения.
2. Найти существующий переиспользуемый код.
3. Реализовать изменение в правильном каталоге.
4. Добавить тесты.
5. Обновить README/CHANGELOG при необходимости.
6. Проверить обе темы и i18n для UI.
7. Запустить Gradle-проверки и `git diff --check`.
8. В результате указать изменённые файлы, проверки и ограничения.
