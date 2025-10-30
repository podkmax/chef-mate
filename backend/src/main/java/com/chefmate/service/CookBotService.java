package com.chefmate.service;

import com.chefmate.bot.TelegramKeyboards;
import com.chefmate.dto.BaseProductDto;
import com.chefmate.dto.ClientStockDto;
import com.chefmate.dto.IngredientAggregateDto;
import com.chefmate.dto.OrderDto;
import com.chefmate.model.User;
import com.chefmate.repo.UserRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

@Service
public class CookBotService {
    private final OrderService orderService;
    private final ClientStockService clientStockService;
    private final BaseProductService baseProductService;
    private final UserRepository userRepository;
    private final long cookTelegramId;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private final Map<Long, StockConversation> stockStates = new ConcurrentHashMap<>();

    public CookBotService(
            OrderService orderService,
            ClientStockService clientStockService,
            BaseProductService baseProductService,
            UserRepository userRepository,
            @Value("${telegram.bot.cook-id:0}") long cookTelegramId) {
        this.orderService = orderService;
        this.clientStockService = clientStockService;
        this.baseProductService = baseProductService;
        this.userRepository = userRepository;
        this.cookTelegramId = cookTelegramId;
    }

    public boolean isCook(Long telegramId) {
        return telegramId != null && telegramId == cookTelegramId && cookTelegramId > 0;
    }

    public boolean handleMessage(Long telegramId, Long chatId, String text, BotOrderSessionService.SendReply reply) {
        if (!isCook(telegramId) || text == null) {
            return false;
        }
        StockConversation state = stockStates.get(chatId);
        if (state != null && state.awaitingQuantity) {
            handleStockQuantityInput(chatId, text, reply);
            return true;
        }
        String normalized = text.trim();
        if ("/start".equalsIgnoreCase(normalized) || "/help".equalsIgnoreCase(normalized)) {
            reply.send(simpleMessage(chatId, "Команды повара:\n/orders/today — заказы на сегодня\n/stock — склад клиентов"));
            return true;
        }
        if ("/orders/today".equalsIgnoreCase(normalized)) {
            showTodayOrders(chatId, reply);
            return true;
        }
        if ("/stock".equalsIgnoreCase(normalized)) {
            showClientSelection(chatId, reply);
            return true;
        }
        reply.send(simpleMessage(chatId, "Неизвестная команда. Используй /orders/today."));
        return true;
    }

    public boolean handleCallback(Long telegramId, CallbackQuery cq, BotOrderSessionService.SendReply reply) {
        if (!isCook(telegramId) || cq == null || cq.getData() == null) {
            return false;
        }
        String data = cq.getData();
        if (!data.startsWith("cook:")) {
            return false;
        }
        Long chatId = cq.getMessage().getChatId();
        if (data.startsWith("cook:view:")) {
            Long orderId = parseLong(data.substring("cook:view:".length()));
            if (orderId != null) {
                showOrderDetails(chatId, orderId, reply);
            } else {
                reply.send(simpleMessage(chatId, "Не удалось распознать заказ."));
            }
            return true;
        } else if (data.startsWith("cook:cancel:")) {
            Long orderId = parseLong(data.substring("cook:cancel:".length()));
            if (orderId != null) {
                cancelOrder(chatId, orderId, reply);
            } else {
                reply.send(simpleMessage(chatId, "Не удалось распознать заказ."));
            }
            return true;
        } else if (data.startsWith("cook:confirm:")) {
            Long orderId = parseLong(data.substring("cook:confirm:".length()));
            if (orderId != null) {
                confirmOrder(chatId, orderId, reply);
            } else {
                reply.send(simpleMessage(chatId, "Не удалось распознать заказ."));
            }
            return true;
        } else if ("cook:list:today".equals(data)) {
            showTodayOrders(chatId, reply);
            return true;
        } else if (data.startsWith("cook:stock:")) {
            handleStockCallback(chatId, data, reply);
            return true;
        }
        return false;
    }

    private void handleStockQuantityInput(Long chatId, String text, BotOrderSessionService.SendReply reply) {
        StockConversation state = stockStates.get(chatId);
        if (state == null || !state.awaitingQuantity) {
            return;
        }
        BigDecimal qty;
        try {
            qty = new BigDecimal(text.replace(",", "."));
        } catch (NumberFormatException ex) {
            reply.send(simpleMessage(chatId, "Не удалось разобрать количество. Введите число, например 1.5"));
            return;
        }
        if (qty.compareTo(BigDecimal.ZERO) < 0) {
            reply.send(simpleMessage(chatId, "Количество должно быть неотрицательным."));
            return;
        }
        ClientStockDto request = new ClientStockDto();
        request.baseProductId = state.baseProductId;
        request.qty = qty;
        if (state.action == StockAction.EDIT && state.stockId != null) {
            request.id = state.stockId;
        } else {
            ClientStockDto existing = clientStockService.findStock(state.userId, state.baseProductId);
            if (existing != null) {
                request.id = existing.id;
            }
        }
        ClientStockDto saved = clientStockService.saveStock(state.userId, request);
        state.awaitingQuantity = false;
        state.baseProductId = null;
        state.stockId = saved.id;
        reply.send(simpleMessage(chatId, "Склад обновлён."));
        showStockList(chatId, state.userId, reply);
        showClientActions(chatId, state.userId, reply);
    }

    private void showTodayOrders(Long chatId, BotOrderSessionService.SendReply reply) {
        List<OrderDto> orders = orderService.findByDate(LocalDate.now());
        if (orders.isEmpty()) {
            reply.send(simpleMessage(chatId, "На сегодня заказов нет."));
            return;
        }
        var rows = orders.stream()
                .sorted((a, b) -> Long.compare(a.id != null ? a.id : 0, b.id != null ? b.id : 0))
                .map(o -> List.<InlineKeyboardButton>of(InlineKeyboardButton.builder()
                        .text("Заказ №" + o.id + statusSuffix(o.status))
                        .callbackData("cook:view:" + o.id)
                        .build()))
                .toList();
        InlineKeyboardMarkup markup = TelegramKeyboards.inline(rows);
        reply.send(SendMessage.builder()
                .chatId(chatId.toString())
                .text("Заказы на сегодня:")
                .replyMarkup(markup)
                .build());
    }

    private void showOrderDetails(Long chatId, Long orderId, BotOrderSessionService.SendReply reply) {
        OrderDto dto = orderService.findById(orderId);
        if (dto == null) {
            reply.send(simpleMessage(chatId, "Заказ №" + orderId + " не найден."));
            return;
        }
        List<IngredientAggregateDto> ingredients = orderService.aggregateIngredients(orderId, false);
        StringBuilder sb = new StringBuilder();
        sb.append("📦 Заказ №").append(orderId);
        if (dto.targetDate != null) {
            sb.append(" на ").append(DATE_FORMAT.format(dto.targetDate));
        }
        sb.append("\nСтатус: ").append(dto.status != null ? dto.status : "—");
        if (dto.comment != null && !dto.comment.isBlank()) {
            sb.append("\nКомментарий: ").append(dto.comment);
        }
        sb.append("\n\nИнгредиенты:\n");
        if (ingredients.isEmpty()) {
            sb.append("— нет данных —");
        } else {
            for (IngredientAggregateDto ingr : ingredients) {
                sb.append(formatIngredient(ingr)).append("\n");
            }
        }
        InlineKeyboardMarkup markup = orderActionsKeyboard(dto);
        reply.send(SendMessage.builder()
                .chatId(chatId.toString())
                .text(sb.toString().trim())
                .replyMarkup(markup)
                .build());
    }

    private void cancelOrder(Long chatId, Long orderId, BotOrderSessionService.SendReply reply) {
        OrderDto dto;
        try {
            dto = orderService.cancelOrder(orderId);
        } catch (RuntimeException ex) {
            reply.send(simpleMessage(chatId, "Не удалось отменить заказ №" + orderId + "."));
            return;
        }
        String text = "Заказ №" + orderId + " отменён.";
        reply.send(SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .replyMarkup(orderActionsKeyboard(dto))
                .build());
    }

    private void confirmOrder(Long chatId, Long orderId, BotOrderSessionService.SendReply reply) {
        OrderDto dto;
        try {
            dto = orderService.confirmOrder(orderId);
        } catch (RuntimeException ex) {
            reply.send(simpleMessage(chatId, "Не удалось подтвердить заказ №" + orderId + "."));
            return;
        }
        String text = "Заказ №" + orderId + " подтверждён.";
        reply.send(SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .replyMarkup(orderActionsKeyboard(dto))
                .build());
    }

    private InlineKeyboardMarkup orderActionsKeyboard(OrderDto dto) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        boolean canConfirm = dto.status == null || "CREATED".equals(dto.status);
        boolean canCancel = !"CANCELLED".equals(dto.status);
        if (canConfirm) {
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("Подтвердить")
                    .callbackData("cook:confirm:" + dto.id)
                    .build()));
        }
        if (canCancel) {
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("Отменить")
                    .callbackData("cook:cancel:" + dto.id)
                    .build()));
        }
        rows.add(List.of(InlineKeyboardButton.builder()
                .text("К списку")
                .callbackData("cook:list:today")
                .build()));
        return TelegramKeyboards.inline(rows);
    }

    private static String formatIngredient(IngredientAggregateDto ingr) {
        String qty = ingr.totalQty != null ? ingr.totalQty.stripTrailingZeros().toPlainString() : "?";
        String unit = ingr.unit != null ? ingr.unit : "";
        return ingr.name + " — " + qty + (unit.isBlank() ? "" : " " + unit);
    }

    private static String statusSuffix(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "CONFIRMED" -> " ✅";
            case "CANCELLED" -> " ❌";
            default -> "";
        };
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static SendMessage simpleMessage(Long chatId, String text) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build();
    }

    private void showClientSelection(Long chatId, BotOrderSessionService.SendReply reply) {
        List<User> clients = userRepository.findAll().stream()
                .filter(u -> u.role != null && u.role.equalsIgnoreCase("CLIENT"))
                .sorted(Comparator.comparing(u -> u.name != null ? u.name : ""))
                .toList();
        if (clients.isEmpty()) {
            reply.send(simpleMessage(chatId, "Клиенты не найдены."));
            return;
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (User user : clients) {
            String label = (user.name != null ? user.name : "Client") + " #" + user.id;
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text(label)
                    .callbackData("cook:stock:client:" + user.id)
                    .build()));
        }
        reply.send(SendMessage.builder()
                .chatId(chatId.toString())
                .text("Выбери клиента для управления складом:")
                .replyMarkup(TelegramKeyboards.inline(rows))
                .build());
    }

    private void showClientActions(Long chatId, Long userId, BotOrderSessionService.SendReply reply) {
        StockConversation state = stockStates.computeIfAbsent(chatId, k -> new StockConversation());
        state.reset();
        state.userId = userId;
        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(
                        InlineKeyboardButton.builder().text("➕ Добавить").callbackData("cook:stock:add:" + userId).build(),
                        InlineKeyboardButton.builder().text("✏️ Изменить").callbackData("cook:stock:edit:" + userId).build()
                ),
                List.of(
                        InlineKeyboardButton.builder().text("🗑 Удалить").callbackData("cook:stock:remove:" + userId).build(),
                        InlineKeyboardButton.builder().text("📋 Показать").callbackData("cook:stock:show:" + userId).build()
                )
        );
        reply.send(SendMessage.builder()
                .chatId(chatId.toString())
                .text("Выбран клиент #" + userId + ". Что сделать со складом?")
                .replyMarkup(TelegramKeyboards.inline(rows))
                .build());
    }

    private void showStockList(Long chatId, Long userId, BotOrderSessionService.SendReply reply) {
        List<ClientStockDto> stock = clientStockService.getClientStock(userId);
        StringBuilder sb = new StringBuilder();
        sb.append("Склад клиента #").append(userId).append(":\n");
        if (stock.isEmpty()) {
            sb.append("— пусто —");
        } else {
            for (ClientStockDto item : stock) {
                sb.append("— ")
                        .append(item.baseProductName != null ? item.baseProductName : item.baseProductId)
                        .append(" — ")
                        .append(item.qty != null ? item.qty.stripTrailingZeros().toPlainString() : "0")
                        .append(item.unit != null ? " " + item.unit : "")
                        .append("\n");
            }
        }
        reply.send(simpleMessage(chatId, sb.toString().trim()));
    }

    private void handleStockCallback(Long chatId, String data, BotOrderSessionService.SendReply reply) {
        String[] parts = data.split(":");
        if (parts.length < 3) {
            return;
        }
        String action = parts[2];
        if ("client".equals(action) && parts.length >= 4) {
            Long userId = parseLong(parts[3]);
            if (userId != null) {
                showClientActions(chatId, userId, reply);
            }
            return;
        }
        StockConversation state = stockStates.computeIfAbsent(chatId, k -> new StockConversation());
        Long currentUserId = state.userId;
        if (("add".equals(action) || "edit".equals(action) || "remove".equals(action) || "show".equals(action))
                && parts.length >= 4) {
            currentUserId = parseLong(parts[3]);
            if (currentUserId == null) {
                reply.send(simpleMessage(chatId, "Не удалось распознать клиента."));
                return;
            }
            state.userId = currentUserId;
        }
        if ("show".equals(action)) {
            showStockList(chatId, currentUserId, reply);
            return;
        }
        if ("add".equals(action)) {
            promptBaseProductSelection(chatId, currentUserId, StockAction.ADD, reply);
            return;
        }
        if ("edit".equals(action)) {
            promptStockSelection(chatId, currentUserId, StockAction.EDIT, reply);
            return;
        }
        if ("remove".equals(action)) {
            promptRemoveSelection(chatId, currentUserId, reply);
            return;
        }
        if ("addbp".equals(action) && parts.length >= 5) {
            Long userId = parseLong(parts[3]);
            UUID baseProductId = parseUuid(parts[4]);
            if (userId == null || baseProductId == null) {
                reply.send(simpleMessage(chatId, "Не удалось распознать базовый продукт."));
                return;
            }
            StockConversation conv = stockStates.computeIfAbsent(chatId, k -> new StockConversation());
            conv.userId = userId;
            conv.baseProductId = baseProductId;
            conv.action = StockAction.ADD;
            ClientStockDto existing = clientStockService.findStock(userId, baseProductId);
            conv.stockId = existing != null ? existing.id : null;
            conv.awaitingQuantity = true;
            BaseProductDto bp = baseProductService.findById(baseProductId);
            reply.send(simpleMessage(chatId, "Введите количество для \"" + (bp != null ? bp.name : baseProductId)
                    + "\" (в " + (bp != null ? bp.unit : "") + "):"));
            return;
        }
        if ("editbp".equals(action) && parts.length >= 6) {
            Long userId = parseLong(parts[3]);
            UUID stockId = parseUuid(parts[4]);
            UUID baseProductId = parseUuid(parts[5]);
            if (userId == null || stockId == null || baseProductId == null) {
                reply.send(simpleMessage(chatId, "Не удалось распознать запись склада."));
                return;
            }
            StockConversation conv = stockStates.computeIfAbsent(chatId, k -> new StockConversation());
            conv.userId = userId;
            conv.baseProductId = baseProductId;
            conv.stockId = stockId;
            conv.action = StockAction.EDIT;
            conv.awaitingQuantity = true;
            BaseProductDto bp = baseProductService.findById(baseProductId);
            reply.send(simpleMessage(chatId, "Введите новое количество для \"" + (bp != null ? bp.name : baseProductId)
                    + "\" (в " + (bp != null ? bp.unit : "") + "):"));
            return;
        }
        if ("removeconfirm".equals(action) && parts.length >= 5) {
            Long userId = parseLong(parts[3]);
            UUID stockId = parseUuid(parts[4]);
            if (userId == null || stockId == null) {
                reply.send(simpleMessage(chatId, "Не удалось распознать запись склада."));
                return;
            }
            clientStockService.deleteStock(userId, stockId);
            reply.send(simpleMessage(chatId, "Позиция удалена."));
            showStockList(chatId, userId, reply);
            showClientActions(chatId, userId, reply);
        }
    }

    private void promptBaseProductSelection(Long chatId, Long userId, StockAction action, BotOrderSessionService.SendReply reply) {
        List<BaseProductDto> products = baseProductService.findAll();
        if (products.isEmpty()) {
            reply.send(simpleMessage(chatId, "Базовые продукты не найдены."));
            return;
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (BaseProductDto product : products) {
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text(product.name + " (" + product.unit + ")")
                    .callbackData("cook:stock:addbp:" + userId + ":" + product.id)
                    .build()));
        }
        stockStates.computeIfAbsent(chatId, k -> new StockConversation()).action = action;
        reply.send(SendMessage.builder()
                .chatId(chatId.toString())
                .text("Выбери базовый продукт для добавления:")
                .replyMarkup(TelegramKeyboards.inline(rows))
                .build());
    }

    private void promptStockSelection(Long chatId, Long userId, StockAction action, BotOrderSessionService.SendReply reply) {
        List<ClientStockDto> stock = clientStockService.getClientStock(userId);
        if (stock.isEmpty()) {
            reply.send(simpleMessage(chatId, "Склад пуст. Сначала добавь позицию."));
            return;
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (ClientStockDto item : stock) {
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text((item.baseProductName != null ? item.baseProductName : item.baseProductId) + " (" +
                            item.qty.stripTrailingZeros().toPlainString() + " " + item.unit + ")")
                    .callbackData("cook:stock:editbp:" + userId + ":" + item.id + ":" + item.baseProductId)
                    .build()));
        }
        stockStates.computeIfAbsent(chatId, k -> new StockConversation()).action = action;
        reply.send(SendMessage.builder()
                .chatId(chatId.toString())
                .text("Выбери позицию для изменения:")
                .replyMarkup(TelegramKeyboards.inline(rows))
                .build());
    }

    private void promptRemoveSelection(Long chatId, Long userId, BotOrderSessionService.SendReply reply) {
        List<ClientStockDto> stock = clientStockService.getClientStock(userId);
        if (stock.isEmpty()) {
            reply.send(simpleMessage(chatId, "Склад пуст."));
            return;
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (ClientStockDto item : stock) {
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("Удалить " + (item.baseProductName != null ? item.baseProductName : item.baseProductId))
                    .callbackData("cook:stock:removeconfirm:" + userId + ":" + item.id)
                    .build()));
        }
        reply.send(SendMessage.builder()
                .chatId(chatId.toString())
                .text("Выбери позицию для удаления:")
                .replyMarkup(TelegramKeyboards.inline(rows))
                .build());
    }

    private enum StockAction {
        ADD, EDIT
    }

    private static class StockConversation {
        Long userId;
        UUID baseProductId;
        UUID stockId;
        StockAction action;
        boolean awaitingQuantity;

        void reset() {
            baseProductId = null;
            stockId = null;
            action = null;
            awaitingQuantity = false;
        }
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
