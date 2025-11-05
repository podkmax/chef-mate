package com.chefmate.service;

import com.chefmate.dto.IngredientAggregateDto;
import com.chefmate.model.Order;
import com.chefmate.model.User;
import com.chefmate.repo.UserRepository;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Service
public class ClientNotificationService {
    private final UserRepository userRepository;
    private final String botToken;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public ClientNotificationService(UserRepository userRepository, @Value("${telegram.bot.token}") String botToken) {
        this.userRepository = userRepository;
        this.botToken = botToken;
    }

    public void notifyOrderConfirmed(Order order, List<IngredientAggregateDto> ingredients) {
        if (order == null || order.userId == null) {
            return;
        }
        Optional<User> userOpt = userRepository.findById(order.userId);
        if (userOpt.isEmpty()) {
            return;
        }
        User user = userOpt.get();
        if (user.telegramId == null || user.telegramId <= 0) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        String datePart = order.targetDate != null ? DATE_FORMAT.format(order.targetDate) : "не указана";
        sb.append("🛒 Список продуктов для заказа №")
                .append(order.id != null ? order.id : "?")
                .append(" на ")
                .append(datePart)
                .append("\n\n");
        if (ingredients == null || ingredients.stream().allMatch(it -> isZeroOrNull(requiredQty(it)))) {
            sb.append("— список пуст —\n");
        } else {
            for (IngredientAggregateDto ingr : ingredients) {
                BigDecimal required = requiredQty(ingr);
                if (isZeroOrNull(required)) {
                    continue;
                }
                String name = ingr.name != null ? ingr.name : "Ингредиент";
                String qty = required.stripTrailingZeros().toPlainString();
                String unit = ingr.unit != null && ingr.unit.shortName != null && !ingr.unit.shortName.isBlank()
                        ? " " + ingr.unit.shortName
                        : "";
                sb.append("— ").append(name).append(" — ").append(qty).append(unit).append("\n");
            }
        }
        sb.append("\n✅ Хорошего дня!");
        SendMessage message = SendMessage.builder()
                .chatId(user.telegramId.toString())
                .text(sb.toString().trim())
                .build();
        try {
            TelegramClient client = new OkHttpTelegramClient(botToken);
            client.execute(message);
        } catch (Exception ignored) {
        }
    }

    private boolean isZeroOrNull(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) <= 0;
    }

    private BigDecimal requiredQty(IngredientAggregateDto dto) {
        if (dto.requiredQty != null) {
            return dto.requiredQty;
        }
        return dto.totalQty;
    }
}
