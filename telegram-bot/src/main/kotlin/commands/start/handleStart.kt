package commands.start

import BirthdayChats.telegramChatId
import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.CommandHandler
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.chat.Chat
import eu.vendeli.tgbot.utils.common.setChain
import io.github.kroune.logger
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

@CommandHandler(["/start"])
suspend fun handleStart(user: User, bot: TelegramBot, chat: Chat) {
    val chatId = chat.id

    logger.info { "User ${user.id} in ${chatId} started the bot" }

    // Check if chat already exists and its status
    val chatInfo = transaction {
        BirthdayChats.selectAll()
            .where { BirthdayChats.telegramChatId eq chatId }
            .map {
                Pair(it[BirthdayChats.id], it[BirthdayChats.isActive])
            }
            .singleOrNull()
    }

    if (chatInfo != null) {
        val (chatDbId, isActive) = chatInfo

        // If chat was inactive, reactivate it
        if (isActive) {
            transaction {
                BirthdayChats.update({ BirthdayChats.id eq chatDbId }) {
                    it[BirthdayChats.isActive] = true
                }
            }
            logger.info { "Chat $chatId was inactive, now reactivated" }
            message {
                """
                ✅ Добро пожаловать обратно! Ваш чат был повторно активирован.
                
                /help - Показать справочное сообщение
                """.trimIndent()
            }.send(chat, bot)
        } else {
            logger.info { "User $chatId already registered" }
            message {
                """
                ✅ Вы уже зарегистрированы!
                
                /help - Показать справочное сообщение
                """.trimIndent()
            }.send(chat, bot)
        }
        return
    }

    // Add chat to database
    val chatDbId = transaction {
        BirthdayChats.insert {
            it[telegramChatId] = chatId
        }[BirthdayChats.id].value
    }

    logger.info { "Chat $chatId registered with DB ID $chatDbId" }

    message {
        """
        Добро пожаловать в бот для уведомлений о днях рождения! 🎂
        
        Я помогу вам отслеживать дни рождения и отправлять уведомления.
        
        Пожалуйста, введите название вашей группы (например, БДРИП251 или БПМИ25 для всех групп ПМИ 25 года) или введите "пропустить", чтобы пропустить этот шаг.
        """.trimIndent()
    }.send(chatId, bot)

    // Start the input chain for group registration
    bot.inputListener.setChain(user, GroupRegistrationChain.GroupName)
}