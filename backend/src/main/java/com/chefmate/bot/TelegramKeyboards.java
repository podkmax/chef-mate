package com.chefmate.bot;

import java.util.List;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

public final class TelegramKeyboards {
    private TelegramKeyboards() {
    }

    public static InlineKeyboardMarkup inline(List<? extends List<? extends InlineKeyboardButton>> rows) {
        List<InlineKeyboardRow> mapped = rows.stream()
                .map(row -> new InlineKeyboardRow(List.copyOf(row)))
                .toList();
        return InlineKeyboardMarkup.builder()
                .keyboard(mapped)
                .build();
    }
}
