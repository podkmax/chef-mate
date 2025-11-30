package com.chefmate.service;

import com.chefmate.bot.TelegramKeyboards;
import com.chefmate.dto.CookOrderDishDto;
import com.chefmate.dto.IngredientAggregateDto;
import com.chefmate.model.Order;
import com.chefmate.model.OrderStatus;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CookNotificationService {
    private final long cookChatId;
    private final String botToken;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String DEFAULT_DATE = "не указано";
    private static final String DEFAULT_DISH_NAME = "Блюдо";
    private static final String DISHES_HEADER = "🍽 Позиции:";
    private static final String INGREDIENTS_HEADER = "🥕 Ингредиенты:";

    public CookNotificationService(
            @Value("${telegram.bot.cook-id:0}") long cookChatId,
            @Value("${telegram.bot.token}") String botToken) {
        this.cookChatId = cookChatId;
        this.botToken = botToken;
    }

    public void notifyNewOrder(Order order, List<CookOrderDishDto> dishes, List<IngredientAggregateDto> ingredients) {
        if (order == null || cookChatId <= 0 || botToken == null || botToken.isBlank()) {
            return;
        }
        TelegramClient client = new OkHttpTelegramClient(botToken);
        SendMessage message = buildMessage(order, dishes, ingredients);
        try {
            client.execute(message);
        } catch (Exception ex) {
            log.warn("Failed to notify cook about order {}", order.getId(), ex);
        }
    }

    public long getCookChatId() {
        return cookChatId;
    }

    private SendMessage buildMessage(Order order, List<CookOrderDishDto> dishes, List<IngredientAggregateDto> ingredients) {
        String datePart = order.getTargetDate() != null ? DATE_FORMAT.format(order.getTargetDate()) : DEFAULT_DATE;
        String text = buildBody(order, datePart, dishes, ingredients);
        InlineKeyboardButton viewButton = InlineKeyboardButton.builder()
                .text("Посмотреть")
                .callbackData("cook:view:" + order.getId())
                .build();
        InlineKeyboardButton cancelButton = InlineKeyboardButton.builder()
                .text("Отменить")
                .callbackData("cook:cancel:" + order.getId())
                .build();
        return SendMessage.builder()
                .chatId(Long.toString(cookChatId))
                .text(text)
                .replyMarkup(TelegramKeyboards.inline(List.of(List.of(viewButton, cancelButton))))
                .build();
    }

    private String buildBody(Order order, String datePart, List<CookOrderDishDto> dishes, List<IngredientAggregateDto> ingredients) {
        StringBuilder sb = new StringBuilder();
        sb.append("📦 Заказ №")
                .append(order.getId() != null ? order.getId() : "?")
                .append(" на ")
                .append(datePart)
                .append("\n")
                .append("Статус: ")
                .append(order.getStatus() != null ? order.getStatus().name() : OrderStatus.CREATED.name())
                .append("\n\n")
                .append(DISHES_HEADER)
                .append("\n");
        if (dishes == null || dishes.isEmpty()) {
            sb.append("— позиции отсутствуют —\n");
        } else {
            for (CookOrderDishDto dish : dishes) {
                String name = resolveDishName(dish);
                int portions = dish.portions() != null ? dish.portions() : 1;
                sb.append("— ")
                        .append(name)
                        .append(" x ")
                        .append(portions)
                        .append("\n");
            }
        }
        sb.append("\n")
                .append(INGREDIENTS_HEADER)
                .append("\n");
        if (ingredients == null || ingredients.isEmpty()) {
            sb.append("— нет данных —");
        } else {
            for (IngredientAggregateDto ingr : ingredients) {
                String name = resolveIngredientName(ingr);
                String qty = formatQuantity(ingr);
                String unit = resolveUnitLabel(ingr);
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

    private String resolveDishName(CookOrderDishDto dish) {
        if (dish == null) {
            return DEFAULT_DISH_NAME;
        }
        String name = dish.name();
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return DEFAULT_DISH_NAME + " #" + (dish.dishId() != null ? dish.dishId() : "");
    }

    private String resolveIngredientName(IngredientAggregateDto ingredient) {
        if (ingredient == null || ingredient.name() == null || ingredient.name().isBlank()) {
            return "Ингредиент";
        }
        return ingredient.name().trim();
    }

    private String formatQuantity(IngredientAggregateDto ingredient) {
        if (ingredient == null || ingredient.totalQty() == null) {
            return "0";
        }
        return ingredient.totalQty().stripTrailingZeros().toPlainString();
    }

    private String resolveUnitLabel(IngredientAggregateDto ingredient) {
        if (ingredient == null) {
            return "";
        }
        String unitShort = ingredient.unitShortName();
        if (unitShort == null || unitShort.isBlank()) {
            unitShort = ingredient.unit() != null ? ingredient.unit().shortName() : null;
        }
        if (unitShort == null || unitShort.isBlank()) {
            return "";
        }
        return " " + unitShort.trim();
    }
}
