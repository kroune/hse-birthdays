# План рефакторинга hse-birthdays

## 📅 Дата составления: 29 января 2026

---

## 🔍 Анализ текущего состояния

### Выявленные проблемы:

1. **Нет Dependency Injection (Koin)** — зависимости создаются напрямую, отсутствует DI контейнер
2. **Захардкоженные креденшалы БД** — пароль и URL БД находятся прямо в коде (`Database.kt`)
3. **Глобальный логгер** — используется один `logger` на весь проект, вместо отдельных логгеров по модулям
4. **Дублирование кода** — одинаковая логика инициализации БД в двух модулях (`users-database` и `telegram-bot`)
5. **Огромные файлы**:
    - `BirthdayScheduler.kt` — 498 строк
    - `handleSearch.kt` — 656 строк
    - `hse-scraping/Main.kt` — 224 строки с глубокой вложенностью
6. **Отсутствие пакетной структуры** — файлы в корневых пакетах без организации
7. **Смешение ответственности** — логика БД, бизнес-логика и презентация в одних файлах
8. **Отсутствие репозиториев** — прямые вызовы `transaction {}` везде по коду
9. **Нет абстракции над Telegram Bot API** — бизнес-логика тесно связана с telegram-bot библиотекой

---

## ✅ Принятые решения

| Вопрос               | Решение                                                                                     |
|----------------------|---------------------------------------------------------------------------------------------|
| Система конфигурации | Текущий `Env.kt` с улучшениями (data class'ы для конфигураций)                              |
| Разделение модулей   | Текущая структура (common, users-database, hse-scraping, telegram-bot) — рефакторинг внутри |
| Тестирование         | Юнит-тесты для критичных частей (парсинг дат, поиск, расчёт возраста)                       |
| Библиотека дат       | kotlinx.datetime вместо java.time                                                           |

---

## 📐 Предлагаемая архитектура

### Структура пакетов (после рефакторинга):

```
common/
  └── src/main/kotlin/io/github/kroune/common/
      ├── config/
      │   ├── Config.kt          # Типизированные конфиги
      │   └── Env.kt             # Чтение .env
      ├── logging/
      │   └── Loggers.kt         # Фабрика логгеров
      └── util/
          └── DateUtils.kt       # Работа с датами (kotlinx.datetime)

users-database/
  └── src/main/kotlin/io/github/kroune/database/
      ├── config/
      │   └── DatabaseConfig.kt
      ├── tables/
      │   ├── Users.kt
      │   ├── Educations.kt
      │   ├── StaffPositions.kt
      │   ├── StaffAddresses.kt
      │   ├── WebRequests.kt
      │   ├── MobileSearches.kt
      │   └── ErrorLogs.kt
      ├── repository/
      │   ├── UserRepository.kt
      │   ├── EducationRepository.kt
      │   └── ErrorLogRepository.kt
      └── DatabaseModule.kt      # Koin модуль

hse-scraping/
  └── src/main/kotlin/io/github/kroune/scraping/
      ├── api/
      │   ├── HseMobileApi.kt
      │   └── HseWebApi.kt
      ├── model/
      │   ├── User.kt
      │   ├── Staff.kt
      │   ├── Education.kt
      │   └── Token.kt
      ├── service/
      │   ├── ScrapingService.kt
      │   └── UserProcessor.kt
      └── ScrapingModule.kt      # Koin модуль

telegram-bot/
  └── src/main/kotlin/io/github/kroune/bot/
      ├── config/
      │   └── BotConfig.kt
      ├── table/
      │   ├── BirthdayChats.kt
      │   ├── BirthdayChatTargetGroups.kt
      │   ├── BirthdayChatAdditionalUsers.kt
      │   └── BirthdayCheckLog.kt
      ├── repository/
      │   ├── ChatRepository.kt
      │   ├── TargetGroupRepository.kt
      │   └── AdditionalUserRepository.kt
      ├── service/
      │   ├── BirthdayService.kt      # Бизнес-логика ДР
      │   ├── SearchService.kt        # Поиск пользователей
      │   └── NotificationService.kt  # Отправка уведомлений
      ├── scheduler/
      │   └── BirthdayScheduler.kt
      ├── command/
      │   ├── StartCommand.kt
      │   ├── HelpCommand.kt
      │   ├── group/
      │   │   ├── AddGroupCommand.kt
      │   │   └── DeleteGroupCommand.kt
      │   ├── user/
      │   │   ├── AddUserCommand.kt
      │   │   ├── DeleteUserCommand.kt
      │   │   └── ListUsersCommand.kt
      │   └── birthday/
      │       ├── UpcomingCommand.kt
      │       └── CheckBirthdaysCommand.kt
      ├── chain/                 # Input chains
      │   ├── GroupRegistrationChain.kt
      │   ├── AddGroupChain.kt
      │   ├── DeleteGroupChain.kt
      │   ├── DeleteUserChain.kt
      │   └── UserSearchChain.kt
      ├── guard/
      │   └── BotStartedGuard.kt
      ├── model/
      │   ├── UserInfo.kt
      │   └── UserWrapper.kt
      ├── cache/
      │   └── TTLCache.kt
      └── BotModule.kt           # Koin модуль
```

---

## 📝 Этапы рефакторинга

### Этап 1: Инфраструктура (приоритет высокий)

**Цель:** Подготовить базу для дальнейшего рефакторинга

1. **Добавить Koin для DI**
    - Добавить зависимость `io.insert-koin:koin-core` в `build.gradle.kts`
    - Создать модули Koin для каждого Gradle-модуля

2. **Вынести конфигурацию БД из кода**
    - Создать `DatabaseConfig` data class
    - Читать параметры подключения из `Env`
    - Убрать хардкод паролей

3. **Создать отдельные логгеры**
    - Заменить глобальный `logger` на логгеры по классам
    - Использовать `KotlinLogging.logger {}` в каждом классе

4. **Организовать пакетную структуру**
    - Перенести файлы в соответствующие пакеты
    - Добавить `package` declarations

5. **Миграция на kotlinx.datetime**
    - Добавить зависимость `org.jetbrains.kotlinx:kotlinx-datetime`
    - Заменить `java.time.*` на `kotlinx.datetime.*`
    - Обновить функции парсинга дат

---

### Этап 2: Database Layer

**Цель:** Изолировать работу с БД в репозитории

1. **Разделить таблицы по файлам**
    - `Users.kt` — таблица пользователей
    - `Educations.kt` — таблица образования
    - `StaffPositions.kt` — таблица должностей
    - `StaffAddresses.kt` — таблица адресов
    - И т.д.

2. **Создать Repository классы**
   ```kotlin
   interface UserRepository {
       fun findById(id: Int): User?
       fun findByMoodleId(moodleId: Int): User?
       fun findByEmail(email: String): User?
       fun findByDescriptionLike(pattern: String): List<User>
       fun save(user: User): Int
       fun existsByMoodleId(moodleId: Int): Boolean
   }
   ```

3. **Вынести все `transaction {}` в репозитории**
    - Команды бота не должны знать о транзакциях
    - Репозитории инкапсулируют работу с Exposed

4. **Объединить инициализацию БД**
    - Один метод `initDatabase()` вместо двух
    - Конфигурация через DI

---

### Этап 3: Telegram Bot — Разбиение больших файлов

**Цель:** Улучшить читаемость и поддерживаемость

#### 3.1 Разбить `BirthdayScheduler.kt` (498 строк)

**Было:** Один файл с планировщиком, логикой проверки ДР и отправкой уведомлений

**Станет:**

| Файл                     | Ответственность                                | ~Строк |
|--------------------------|------------------------------------------------|--------|
| `BirthdayScheduler.kt`   | Планирование задач (cron-like)                 | ~80    |
| `BirthdayService.kt`     | Бизнес-логика: поиск именинников, проверка дат | ~150   |
| `NotificationService.kt` | Формирование и отправка сообщений              | ~100   |
| `BirthdayCheckLog.kt`    | Логирование проверок                           | ~50    |

**Ключевые классы:**

```kotlin
class BirthdayScheduler(
    private val birthdayService: BirthdayService,
    private val scope: CoroutineScope
) {
    fun start()
    fun stop()
}

class BirthdayService(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService
) {
    suspend fun checkAndNotifyBirthdays(specificChatId: Long? = null)
    fun findBirthdayUsers(chatId: Long, date: LocalDate): List<BirthdayPerson>
}

class NotificationService(
    private val bot: TelegramBot
) {
    suspend fun sendBirthdayNotification(chatId: Long, users: List<BirthdayPerson>)
    fun buildBirthdayMessage(users: List<BirthdayPerson>): String
}
```

#### 3.2 Разбить `handleSearch.kt` (656 строк)

**Было:** Один файл с командой, chain'ами, поиском и пагинацией

**Станет:**

| Файл                 | Ответственность             | ~Строк |
|----------------------|-----------------------------|--------|
| `AddUserCommand.kt`  | Команда `/adduser`          | ~30    |
| `UserSearchChain.kt` | Input chain для поиска      | ~200   |
| `SearchService.kt`   | Логика поиска в БД          | ~100   |
| `SearchSession.kt`   | Модели сессии поиска        | ~30    |
| `SearchCallbacks.kt` | Обработчики callback кнопок | ~150   |

**Ключевые классы:**

```kotlin
class SearchService(
    private val userRepository: UserRepository
) {
    fun search(criteria: SearchCriteria): List<SearchResult>
    fun buildSearchQuery(criteria: SearchCriteria): Op<Boolean>
}

data class SearchCriteria(
    val firstName: String? = null,
    val lastName: String? = null,
    val patronymic: String? = null,
    val email: String? = null,
    val groupName: String? = null,
    val userType: String? = null
)
```

---

### Этап 4: HSE Scraping

**Цель:** Улучшить структуру и обработку ошибок

1. **Разбить `Main.kt` на:**
    - `ScrapingService.kt` — основная логика скрапинга
    - `UserProcessor.kt` — обработка и сохранение пользователей

2. **Вынести HttpClient конфигурацию**
    - Создать `HttpClientFactory`
    - Настройки retry в конфиге

3. **Улучшить обработку ошибок**
    - Создать sealed class для результатов
    - Добавить structured logging

**Ключевые классы:**

```kotlin
class ScrapingService(
    private val hseMobile: HseMobileApi,
    private val hseWeb: HseWebApi,
    private val userProcessor: UserProcessor
) {
    suspend fun scrapeUsers(startId: Int, endId: Int, concurrency: Int)
}

class UserProcessor(
    private val userRepository: UserRepository,
    private val educationRepository: EducationRepository,
    private val errorLogRepository: ErrorLogRepository
) {
    suspend fun processUser(moodleId: Int, userDetail: UserDetail)
}
```

---

### Этап 5: Тестирование

**Цель:** Покрыть критичную логику тестами

1. **Юнит-тесты для:**
    - `DateUtils` — парсинг и форматирование дат
    - `BirthdayService.checkBirthdayMatch()` — проверка совпадения дня рождения
    - `BirthdayService.calculateAge()` — расчёт возраста
    - `SearchService.buildSearchQuery()` — построение запросов

2. **Создать test fixtures:**
    - Тестовые данные пользователей
    - Mock репозитории

---

### Этап 6: Финализация

**Цель:** Код ревью и документация

1. **Проверка на соответствие стилю**
    - Запустить detekt
    - Исправить warnings

2. **Обновить документацию**
    - README.md с описанием архитектуры
    - KDoc для публичных API

3. **Очистка**
    - Удалить неиспользуемый код
    - Проверить импорты

---

## 🔧 Технические детали

### Зависимости для добавления

```kotlin
// build.gradle.kts (root)
val koinVersion = "3.5.0"
val kotlinxDatetimeVersion = "0.5.0"

// common/build.gradle.kts
dependencies {
    api("io.insert-koin:koin-core:$koinVersion")
    api("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")
}
```

### Пример Koin модуля

```kotlin
// DatabaseModule.kt
val databaseModule = module {
    single { DatabaseConfig.fromEnv() }
    single { initDatabase(get()) }
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<EducationRepository> { EducationRepositoryImpl(get()) }
}

// BotModule.kt  
val botModule = module {
    single { BotConfig.fromEnv() }
    single { TelegramBot(get<BotConfig>().token) { ... } }
    single { ChatRepository() }
    single { BirthdayService(get(), get(), get()) }
    single { NotificationService(get()) }
    single { BirthdayScheduler(get(), get()) }
}
```

### Пример миграции на kotlinx.datetime

```kotlin
// До (java.time)
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun parseBirthDate(birthDate: String): LocalDate? {
    return LocalDate.parse(birthDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
}

// После (kotlinx.datetime)
import kotlinx . datetime . LocalDate

fun parseBirthDate(birthDate: String): LocalDate? {
    return runCatching { LocalDate.parse(birthDate) }.getOrNull()
}
```

---

## 📊 Метрики успеха

| Метрика                                      | До        | После       |
|----------------------------------------------|-----------|-------------|
| Максимальный размер файла                    | 656 строк | < 200 строк |
| Количество `transaction {}` вне репозиториев | ~30       | 0           |
| Хардкод креденшалов                          | 2 места   | 0           |
| Покрытие тестами критичной логики            | 0%        | > 80%       |

---

## 🚀 Порядок выполнения

1. ✅ Этап 1: Инфраструктура
2. ⬜ Этап 2: Database Layer
3. ⬜ Этап 3: Telegram Bot
4. ⬜ Этап 4: HSE Scraping
5. ⬜ Этап 5: Тестирование
6. ⬜ Этап 6: Финализация

---

## 📌 Примечания

- Каждый этап должен сохранять работоспособность проекта
- После каждого этапа — проверка что бот работает
- Коммиты делать атомарными (один этап = один PR)
