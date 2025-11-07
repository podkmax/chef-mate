package com.chefmate.service;

import com.chefmate.bot.TelegramKeyboards;
import com.chefmate.dto.CookOrderDishDto;
import com.chefmate.dto.IngredientAggregateDto;
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

    public void notifyNewOrder(Order order, List<CookOrderDishDto> dishes, List<IngredientAggregateDto> ingredients) {
        if (cookChatId <= 0 || order == null) {
            return;
        }
        TelegramClient client = new OkHttpTelegramClient(botToken);
        String datePart = order.targetDate != null ? DATE_FORMAT.format(order.targetDate) : "не указано";
        String text = buildMessage(order, datePart, dishes, ingredients);
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

    private String buildMessage(Order order, String datePart, List<CookOrderDishDto> dishes, List<IngredientAggregateDto> ingredients) {
        StringBuilder sb = new StringBuilder();
        sb.append("📦 Заказ №")
                .append(order.id != null ? order.id : "?")
                .append(" на ")
                .append(datePart)
                .append("\n");
        sb.append("Статус: ")
                .append(order.status != null ? order.status.name() : "CREATED")
                .append("\n\n");
        sb.append("🍽 Позиции:\n");
        if (dishes == null || dishes.isEmpty()) {
            sb.append("— позиции отсутствуют —\n");
        } else {
            for (CookOrderDishDto dish : dishes) {
                String name = dish.name != null && !dish.name.isBlank()
                        ? dish.name
                        : ("Блюдо #" + (dish.dishId != null ? dish.dishId : ""));
                int portions = dish.portions != null ? dish.portions : 1;
                sb.append("— ")
                        .append(name)
                        .append(" x ")
                        .append(portions)
                        .append("\n");
            }
        }
        sb.append("\n🥕 Ингредиенты:\n");
        if (ingredients == null || ingredients.isEmpty()) {
            sb.append("— нет данных —");
        } else {
            for (IngredientAggregateDto ingr : ingredients) {
                String name = ingr.name != null && !ingr.name.isBlank() ? ingr.name : "Ингредиент";
                String qty = ingr.totalQty != null
                        ? ingr.totalQty.stripTrailingZeros().toPlainString()
                        : "0";
                String unitShort = ingr.unitShortName != null && !ingr.unitShortName.isBlank()
                        ? ingr.unitShortName
                        : (ingr.unit != null && ingr.unit.shortName != null && !ingr.unit.shortName.isBlank()
                        ? ingr.unit.shortName
                        : "");
                String unit = unitShort.isBlank() ? "" : " " + unitShort;
                sb.append("— ")
                        .append(name)
                        .append(" — ")
                        .append(qty)
                        .append(unit)
                        .append("\n");
            }
        }
        return sb.toString().trim();
    }
}
