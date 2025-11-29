package com.chefmate.bot;

import com.chefmate.service.BotOrderSessionService;
import com.chefmate.service.CookBotService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
@Profile("default")
public class ChefMateBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
    private final BotOrderSessionService botOrderSessionService;
    private final CookBotService cookBotService;
    private final TelegramClient telegramClient;
    @Value("${telegram.bot.token}")
    private String token;
    public ChefMateBot(BotOrderSessionService botOrderSessionService, CookBotService cookBotService) {
        this.botOrderSessionService = botOrderSessionService;
        this.cookBotService = cookBotService;
        // telegramClient = create here, but token may be injected after construct, so create in lazy getter
        telegramClient = null;
    }
    @Override
    public String getBotToken() { return token; }
    @Override
    public LongPollingSingleThreadUpdateConsumer getUpdatesConsumer() { return this; }
    @Override
    public void consume(Update update) {
        TelegramClient client = getClient();
        if (update.hasMessage()) {
            Message msg = update.getMessage();
            if (msg.hasText()) {
                String text = msg.getText();
                Long fromId = msg.getFrom().getId();
                if (cookBotService.handleMessage(fromId, msg.getChatId(), text, sm -> safeSend(client, sm))) {
                    return;
                }
                if ("/start".equals(text)) {
                    botOrderSessionService.handleStart(
                            fromId,
                            msg.getChatId(),
                            (SendMessage sm) -> safeSend(client, sm)
                    );
                } else if (!botOrderSessionService.handleText(
                        msg.getChatId(),
                        text,
                        (SendMessage sm) -> safeSend(client, sm))) {
                    safeSend(client, SendMessage.builder()
                            .chatId(msg.getChatId().toString())
                            .text("Я понимаю только /start, кнопки меню или дату вручную.")
                            .build());
                }
            }
        } else if (update.hasCallbackQuery()) {
            var callbackQuery = update.getCallbackQuery();
            if (cookBotService.handleCallback(
                    callbackQuery.getFrom().getId(),
                    callbackQuery,
                    sm -> safeSend(client, sm))) {
                return;
            }
            botOrderSessionService.processCallback(
                callbackQuery,
                (SendMessage sm) -> safeSend(client, sm)
            );
        }
    }
    private TelegramClient getClient() {
        // TelegramClient в 9.x лучше создавать на каждый consume или лениво при первом вызове
        // чтобы токен был подставлен из @Value
        return telegramClient != null ? telegramClient : new OkHttpTelegramClient(getBotToken());
    }
    private void safeSend(TelegramClient client, SendMessage sm) {
        try { client.execute(sm); } catch (Exception ignored) {}
    }
}

