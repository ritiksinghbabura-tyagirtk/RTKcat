package com.rtk.telegrammanager.telegram

data class TelegramBotInfo(
    val username: String,
    val firstName: String
)

data class TelegramMessage(
    val chatId: String,
    val text: String
)

data class TelegramUpdate(
    val updateId: Long,
    val message: TelegramMessage?
)
