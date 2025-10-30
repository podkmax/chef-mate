package com.chefmate.service;

import com.chefmate.bot.TelegramKeyboards;
import com.chefmate.model.Order;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Service
public class CookNotificationService {
    private final long cookChatId;
    private final String botToken;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public CookNotificationService(
            @Value("${telegram.bot.cook-id:0}") long cookChatId,
            @Value("${telegram.bot.token}") String botToken) {
        this.cookChatId = cookChatId;
        this.botToken = botToken;
    }

    public void notifyNewOrder(Order order) {
        if (cookChatId <= 0 || order == null) {
            return;
        }
        TelegramClient client = new OkHttpTelegramClient(botToken);
        String datePart = order.targetDate != null ? DATE_FORMAT.format(order.targetDate) : "не указано";
        String text = "📌 Новый заказ №" + order.id + " на " + datePart;
        InlineKeyboardButton view = InlineKeyboardButton.builder()
                .text("Посмотреть")
                .callbackData("cook:view:" + order.id)
                .build();
        InlineKeyboardButton cancel = InlineKeyboardButton.builder()
                .text("Отменить")
                .callbackData("cook:cancel:" + order.id)
                .build();
        SendMessage msg = SendMessage.builder()
                .chatId(Long.toString(cookChatId))
                .text(text)
                .replyMarkup(TelegramKeyboards.inline(List.of(List.of(view, cancel))))
                .build();
        try {
            client.execute(msg);
        } catch (Exception ignored) {
        }
    }

    public long getCookChatId() {
        return cookChatId;
    }
}
