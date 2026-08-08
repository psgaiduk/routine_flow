# Routine Flow

Офлайн Android-приложение для создания цепочек действий, настройки повторений и последовательного выполнения задач.

Приложение работает без аккаунта и сервера. Данные хранятся во внутреннем файле приложения `routine-flow.json`.

## Возможности

- создание, переименование и удаление цепочек;
- добавление, редактирование и удаление действий;
- длительность действия в формате `часы : минуты : секунды`;
- длительность нового действия по умолчанию — 1 минута;
- повторения по дням, неделям и месяцам;
- выбор нескольких дней недели для недельного повторения;
- повторение по конкретным числам месяца;
- повторение по позиции дня недели в месяце: первый, второй, третий, четвёртый или последний;
- локализация русского и английского интерфейса;
- светлая и тёмная тема;
- drag-and-drop сортировка цепочек и действий;
- отдельная кнопка быстрого запуска цепочки;
- экран «Сегодня» с активными и завершёнными цепочками;
- последовательный запуск действий с таймером;
- пауза, продолжение, сброс, завершение и пропуск действия;
- отображение прогресса и переработки сверх планового времени;
- уведомления и звуковой сигнал при превышении времени;
- сохранение и восстановление всех цепочек через JSON;
- автоматическая проверка unit-тестов и сборки через GitHub Actions.

## Архитектура

В приложении используется один Activity-host и отдельные builders экранов и компонентов. Activity отвечает за жизненный цикл, системные insets, навигацию и передачу callback’ов. Состояние данных и навигации хранится во ViewModel.

```text
app/src/main/kotlin/com/routineflow/app/
│
├── App.kt
│   └── Application-класс приложения и Hilt-точка входа
│
├── MainActivity.kt
│   └── Activity-host: lifecycle, insets, навигация и callbacks
│
├── model/
│   ├── Models.kt
│   │   ├── Action — действие цепочки
│   │   ├── Chain — цепочка действий
│   │   ├── RunningAction — состояние текущего таймера
│   │   └── AppState — состояние данных и запущенного действия
│   └── RecurrenceRule.kt
│       ├── None
│       ├── Daily
│       ├── Weekly
│       ├── Monthly
│       ├── Interval
│       └── Unknown
│
├── data/
│   ├── ChainRepository.kt
│   │   └── контракт доступа к цепочкам
│   ├── LocalChainRepository.kt
│   │   └── StateFlow и сохранение во внутренний файл приложения
│   └── RoutineFlowJsonCodec.kt
│       └── кодирование JSON v2 и чтение старого формата
│
├── domain/
│   ├── GetTodayActionsUseCase.kt
│   │   └── определяет, должно ли действие выполняться сегодня
│   ├── RoutineDay.kt
│   │   └── бизнес-день и граница дня в 04:00
│   ├── RecurrenceRuleCodec.kt
│   │   └── преобразование строки совместимости в RecurrenceRule
│   ├── RecurrenceRuleParser.kt
│   │   └── разбор правил для отображения и тестов
│   ├── RecurrenceStrategy.kt
│   │   └── расчёт фактического повторения по календарю
│   └── ReorderItems.kt
│       └── общий алгоритм перестановки элементов
│
├── presentation/
│   ├── MainViewModel.kt
│   │   └── данные, таймер, CRUD, сортировка и навигационное состояние
│   ├── NavigationState.kt
│   │   └── выбранная вкладка, цепочка редактора и цепочка выполнения
│   ├── RecurrenceDisplayFormatter.kt
│   │   └── локализованное описание правила повторения
│   ├── TimeFormatter.kt
│   │   └── отображение длительности и таймера
│   ├── ScreenLayout.kt
│   │   └── общие LayoutParams для экранов с закреплённой нижней панелью
│   │
│   ├── components/
│   │   ├── ActionEditorDialog.kt
│   │   │   └── создание и редактирование действия
│   │   ├── ChainDialogFactory.kt
│   │   │   └── диалоги создания и переименования цепочки
│   │   ├── CompactPicker.kt
│   │   │   └── колесо выбора часов, минут и секунд
│   │   ├── RecurrenceEditor.kt
│   │   │   └── редактор дней, недель и месяцев
│   │   └── ReorderDragController.kt
│   │       └── общий drag-and-drop для изменения порядка
│   │
│   └── screens/
│       ├── RunScreen.kt
│       │   └── список активных и завершённых цепочек на сегодня
│       ├── ChainsScreen.kt
│       │   └── список всех цепочек и сортировка цепочек
│       ├── ChainEditorScreen.kt
│       │   └── редактирование названия, действий и их порядка
│       ├── ChainExecutionScreen.kt
│       │   └── просмотр действий перед запуском цепочки
│       ├── RunningScreen.kt
│       │   └── активный таймер, прогресс и управление выполнением
│       └── StatsScreen.kt
│           └── экран статистики и его placeholder
│
├── notifications/
│   ├── RoutineNotifier.kt
│   │   └── интерфейс уведомлений для ViewModel и тестов
│   └── OvertimeNotifier.kt
│       └── Android notification channel, звук, вибрация и progress notification
│
└── di/
    └── AppModule.kt
        └── Hilt-привязки Repository и RoutineNotifier
```

### Поток данных

```text
UI screen/component
        │ callback
        ▼
MainActivity ──► MainViewModel ──► Use Case / Strategy
                       │
                       ▼
                ChainRepository
                       │
                       ▼
              routine-flow.json
```

Новые изменения данных должны проходить через `MainViewModel` и `ChainRepository`. UI-компоненты не создают Repository и не изменяют файл напрямую.

## Навигация

`NavigationState` хранит:

- `AppTab.RUN` — экран сегодняшних задач;
- `AppTab.CHAINS` — список цепочек или редактор выбранной цепочки;
- `AppTab.STATS` — статистика;
- `chainId` — открытая цепочка для редактирования;
- `executionChainId` — открытая цепочка для выполнения.

`MainActivity` наблюдает `MainViewModel.state` и `MainViewModel.navigation`, после чего устанавливает нужный screen builder в качестве текущего content view.

## Правила повторений

Внутри приложения используется типизированная модель `RecurrenceRule`:

```text
None
Daily
Weekly(days)
Monthly(dates)
Interval(count, unit, weekdays, monthDates, weekdayPosition, weekday)
Unknown(raw)
```

Для обратной совместимости `RecurrenceRuleCodec` понимает старые строки:

```text
NONE
DAILY
WEEKLY:Пн,Ср
MONTHLY:1,15
INTERVAL:3:DAYS
INTERVAL:1:WEEKS:WEEKDAYS:Пн,Ср
INTERVAL:1:MONTHS:DATE:10,25
INTERVAL:1:MONTHS:WEEKDAY:FIRST:Пн
```

## Формат JSON

Новый экспорт использует версию 2:

```json
{
  "version": 2,
  "chains": [
    {
      "id": 1,
      "name": "Morning routine",
      "actions": [
        {
          "id": 2,
          "title": "Water",
          "recurrence": "DAILY",
          "durationSeconds": 60,
          "doneOn": null,
          "executionStatus": null
        }
      ]
    }
  ]
}
```

Импорт поддерживает:

- новый объект с `version` и `chains`;
- старый JSON-массив цепочек;
- старое поле `durationMinutes`, которое преобразуется в секунды;
- старые строковые значения `recurrence`.

Формат нельзя менять разрушительно. При следующем несовместимом изменении нужно увеличить `version` и сохранить миграцию старых данных.

## Тесты

### Unit-тесты

Unit-тесты находятся в `app/src/test`:

- `RecurrenceStrategyTest` — расчёт ежедневных, недельных и месячных повторений;
- `RecurrenceRuleParserTest` — разбор типовых и неизвестных правил;
- `RoutineFlowJsonCodecTest` — JSON v2, legacy JSON и `durationMinutes`;
- `ReorderItemsTest` — перестановка вверх, вниз и обработка неверных индексов;
- `TimeFormatterTest` — форматирование времени;
- `MainViewModelTest` — CRUD, сортировка, импорт и навигация.

Запуск:

```bash
./gradlew testDebugUnitTest
```

### Android instrumentation-тесты

Находятся в `app/src/androidTest` и требуют подключённого эмулятора или телефона:

```bash
./gradlew connectedDebugAndroidTest
```

Smoke-тесты проверяют запуск приложения и переход во вкладку «Цепочки». `ScreenLayoutTest` проверяет layout-контракт экранов с закреплённой нижней панелью: экран получает оставшуюся высоту через `height = 0` и `weight = 1`, поэтому меню не прилипает к последней карточке. `MainUserFlowsTest` проверяет создание цепочки, добавление и редактирование действия, а также запуск цепочки.

## Сборка и запуск

Открыть в Android Studio папку проекта, содержащую `settings.gradle.kts`, а не только папку `app`.

В `Settings → Build, Execution, Deployment → Build Tools → Gradle` выбрать `Gradle JDK`:

- `Embedded JDK 17`, или
- встроенный `JBR 21` Android Studio.

Основные команды:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew installDebug
```

APK после сборки:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Если кнопка запуска пропала в Android Studio:

1. выполнить `File → Sync Project with Gradle Files`;
2. открыть `Run → Edit Configurations`;
3. добавить `Android App` с module `app`;
4. выбрать эмулятор или подключённый телефон;
5. проверить `View → Appearance → Main Toolbar`.

## CI

Workflow находится в `.github/workflows/android.yml` и запускается на push и pull request. Он выполняет:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Instrumentation-тесты в CI пока не запускаются, потому что для них требуется Android Emulator.

## История изменений

История версий находится в [CHANGELOG.md](CHANGELOG.md). Правила структуры проекта, размещения нового кода, тестирования и версионирования находятся в [AGENTS.md](AGENTS.md).

## Ограничения и следующий этап

Пока не реализованы:

- расширенное UI-покрытие сложных сценариев и разных конфигураций устройства;
- дата окончания расписания или количество повторений;
- синхронизация Google Drive;
- разрешение конфликтов синхронизации;
- полноценная статистика.

Google Drive нужно подключать только после стабилизации JSON v2 и модели повторений. Приложение должно продолжать работать офлайн.
