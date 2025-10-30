package com.chefmate.service;

import com.chefmate.bot.TelegramKeyboards;
import com.chefmate.dto.DishDto;
import com.chefmate.dto.OrderDto;
import com.chefmate.dto.OrderItemDto;
import com.chefmate.model.User;
import com.chefmate.repo.UserRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

@Service
public class BotOrderSessionService {
    public interface SendReply {
        void send(SendMessage sm);
    }

    private enum Stage {
        MENU,
        PORTION,
        DATE,
        CONFIRM
    }

    private final DishService dishService;
    private final OrderService orderService;
    private final UserRepository userRepo;
    private final Map<Long, SessionState> sessions = new ConcurrentHashMap<>();
    private static final DateTimeFormatter DATE_REPLY_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public BotOrderSessionService(DishService dishService, OrderService orderService, UserRepository userRepo) {
        this.dishService = dishService;
        this.orderService = orderService;
        this.userRepo = userRepo;
    }

    public void handleStart(Long telegramId, Long chatId, SendReply sendReply) {
        User user = userRepo.findByTelegramId(telegramId).orElseGet(() -> {
            User u = new User();
            u.telegramId = telegramId;
            u.name = "tg" + telegramId;
            u.role = "CLIENT";
            userRepo.save(u);
            return u;
        });
        sessions.put(chatId, new SessionState());
        showMenu(chatId, sendReply);
    }

    public boolean handleText(Long chatId, String text, SendReply sendReply) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        SessionState ss = sessions.computeIfAbsent(chatId, k -> new SessionState());
        if (isCancelCommand(trimmed)) {
            resetSession(chatId, sendReply);
            return true;
        }
        if (isBackCommand(trimmed) && handleBack(chatId, ss, sendReply)) {
            return true;
        }
        if (ss.awaitingCustomDate) {
            if (isBackCommand(trimmed)) {
                ss.awaitingCustomDate = false;
                askDate(chatId, sendReply);
                return true;
            }
            LocalDate parsed = parseDate(trimmed);
            if (parsed == null) {
                sendReply.send(SendMessage.builder()
                        .chatId(chatId.toString())
                        .text("Не смог разобрать дату. Введи в формате YYYY-MM-DD или ДД.ММ.ГГГГ.\nМожно также отправить 'назад' или 'отмена'.")
                        .replyMarkup(backCancelMarkup("date"))
                        .build());
                return true;
            }
            ss.selectedDate = parsed;
            ss.awaitingCustomDate = false;
            showDraft(chatId, sendReply);
            return true;
        }
        return false;
    }

    public void showMenu(Long chatId, SendReply sendReply) {
        SessionState ss = sessions.computeIfAbsent(chatId, k -> new SessionState());
        ss.stage = Stage.MENU;
        List<DishDto> menu = dishService.getActiveDishes();
        if (menu.isEmpty()) {
            sendReply.send(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Меню пока пусто. Напиши нам позже или свяжись с администратором.")
                    .build());
            return;
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (DishDto d : menu) {
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text(d.title)
                    .callbackData("dish:" + d.id)
                    .build()));
        }
        sendReply.send(SendMessage.builder()
                .chatId(chatId.toString())
                .text("Выбери блюдо, и мы соберём заказ. В любой момент используй кнопку Отмена.")
                .replyMarkup(TelegramKeyboards.inline(rows))
                .build());
    }

    public void processCallback(CallbackQuery cq, SendReply sendReply) {
        Long chatId = cq.getMessage().getChatId();
        SessionState ss = sessions.computeIfAbsent(chatId, k -> new SessionState());
        String data = cq.getData();
        if (data.startsWith("nav:")) {
            handleNavigation(chatId, ss, data, sendReply);
            return;
        }
        if (data.startsWith("dish:")) {
            Long dishId = Long.valueOf(data.substring(5));
            ss.selectedDish = dishId;
            askPortions(chatId, dishId, sendReply);
        } else if (data.startsWith("portion:")) {
            int portions = Integer.parseInt(data.substring(8));
            ss.selectedPortions = portions;
            askDate(chatId, sendReply);
        } else if (data.startsWith("date:")) {
            handleDateSelection(chatId, ss, data.substring(5), sendReply);
        } else if (data.equals("confirm")) {
            saveOrder(chatId, sendReply);
        }
    }

    private void handleDateSelection(Long chatId, SessionState ss, String token, SendReply sendReply) {
        if ("custom".equals(token)) {
            ss.awaitingCustomDate = true;
            sendReply.send(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Введи дату вручную (YYYY-MM-DD или ДД.ММ.ГГГГ). Можно отправить 'назад' или нажать Отмена.")
                    .replyMarkup(backCancelMarkup("date"))
                    .build());
            return;
        }
        LocalDate parsed = resolveDateToken(token);
        if (parsed == null) {
            sendReply.send(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Не удалось определить дату, попробуй ещё раз.")
                    .build());
            askDate(chatId, sendReply);
        } else {
            ss.selectedDate = parsed;
            ss.awaitingCustomDate = false;
            showDraft(chatId, sendReply);
        }
    }

    private void askPortions(Long chatId, Long dishId, SendReply sendReply) {
        SessionState ss = sessions.computeIfAbsent(chatId, k -> new SessionState());
        ss.stage = Stage.PORTION;
        String text = "Сколько порций добавить?";
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> portionsRow = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            portionsRow.add(InlineKeyboardButton.builder()
                    .text(String.valueOf(i))
                    .callbackData("portion:" + i)
                    .build());
        }
        rows.add(portionsRow);
        rows.add(List.of(backButton("menu"), cancelButton()));
        sendReply.send(SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .replyMarkup(TelegramKeyboards.inline(rows))
                .build());
    }

    private void askDate(Long chatId, SendReply sendReply) {
        SessionState ss = sessions.computeIfAbsent(chatId, k -> new SessionState());
        ss.stage = Stage.DATE;
        String text = "На когда оформить заказ? Выбери кнопку или введи дату вручную.";
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                InlineKeyboardButton.builder().text("Сегодня").callbackData("date:today").build(),
                InlineKeyboardButton.builder().text("Завтра").callbackData("date:tomorrow").build()));
        rows.add(List.of(InlineKeyboardButton.builder().text("Другой день").callbackData("date:custom").build()));
        rows.add(List.of(backButton("portion"), cancelButton()));
        sendReply.send(SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .replyMarkup(TelegramKeyboards.inline(rows))
                .build());
    }

    private void showDraft(Long chatId, SendReply sendReply) {
        SessionState ss = sessions.get(chatId);
        if (ss == null || ss.selectedDish == null || ss.selectedPortions == null || ss.selectedDate == null) {
            sendReply.send(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Не все параметры выбраны. Начнём заново.")
                    .build());
            showMenu(chatId, sendReply);
            return;
        }
        ss.stage = Stage.CONFIRM;
        String text = "Проверим заказ: блюдо id " + ss.selectedDish
                + ", порций — " + ss.selectedPortions
                + ", дата — " + ss.selectedDate.format(DATE_REPLY_FORMAT)
                + ".\nЕсли всё верно, нажми Подтвердить.";
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(InlineKeyboardButton.builder().text("✅ Подтвердить").callbackData("confirm").build()));
        rows.add(List.of(backButton("date"), cancelButton()));
        sendReply.send(SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .replyMarkup(TelegramKeyboards.inline(rows))
                .build());
    }

    private void saveOrder(Long chatId, SendReply sendReply) {
        SessionState ss = sessions.get(chatId);
        if (ss == null) {
            sendReply.send(SendMessage.builder().chatId(chatId.toString()).text("Начни заново командой /start.").build());
            return;
        }
        if (!ss.saved) {
            Long telegramId = chatId;
            User user = userRepo.findByTelegramId(telegramId)
                    .orElseGet(() -> {
                        User u = new User();
                        u.telegramId = telegramId;
                        u.name = "tgclient" + telegramId;
                        u.role = "CLIENT";
                        userRepo.save(u);
                        return u;
                    });
            OrderDto order = new OrderDto();
            order.userId = user.id;
            order.targetDate = ss.selectedDate != null ? ss.selectedDate : LocalDate.now();
            OrderItemDto item = new OrderItemDto();
            item.dishId = ss.selectedDish;
            item.portions = ss.selectedPortions;
            order.items = List.of(item);
            orderService.createOrder(order);
            ss.saved = true;
        }
        sendReply.send(SendMessage.builder()
                .chatId(chatId.toString())
                .text("Готово! Мы приняли заказ и напомним ближе к дате. Если хочешь оформить ещё один, отправь /start.")
                .build());
        sessions.put(chatId, new SessionState());
    }

    private void handleNavigation(Long chatId, SessionState ss, String data, SendReply sendReply) {
        if ("nav:cancel".equals(data)) {
            resetSession(chatId, sendReply);
            return;
        }
        if (!data.startsWith("nav:back:")) {
            return;
        }
        String target = data.substring("nav:back:".length());
        if ("menu".equals(target)) {
            showMenu(chatId, sendReply);
            return;
        }
        if ("portion".equals(target)) {
            if (ss.selectedDish != null) {
                askPortions(chatId, ss.selectedDish, sendReply);
            } else {
                showMenu(chatId, sendReply);
            }
            return;
        }
        if ("date".equals(target)) {
            if (ss.selectedPortions != null) {
                askDate(chatId, sendReply);
            } else if (ss.selectedDish != null) {
                askPortions(chatId, ss.selectedDish, sendReply);
            } else {
                showMenu(chatId, sendReply);
            }
        }
    }

    private boolean handleBack(Long chatId, SessionState ss, SendReply sendReply) {
        if (ss.stage == Stage.CONFIRM) {
            handleNavigation(chatId, ss, "nav:back:date", sendReply);
            return true;
        }
        if (ss.stage == Stage.DATE) {
            handleNavigation(chatId, ss, "nav:back:portion", sendReply);
            return true;
        }
        if (ss.stage == Stage.PORTION) {
            handleNavigation(chatId, ss, "nav:back:menu", sendReply);
            return true;
        }
        return false;
    }

    private InlineKeyboardButton backButton(String target) {
        return InlineKeyboardButton.builder()
                .text("⬅ Назад")
                .callbackData("nav:back:" + target)
                .build();
    }

    private InlineKeyboardButton cancelButton() {
        return InlineKeyboardButton.builder()
                .text("✖ Отмена")
                .callbackData("nav:cancel")
                .build();
    }

    private InlineKeyboardMarkup backCancelMarkup(String target) {
        return TelegramKeyboards.inline(List.of(List.of(backButton(target), cancelButton())));
    }

    private void resetSession(Long chatId, SendReply sendReply) {
        sessions.put(chatId, new SessionState());
        sendReply.send(SendMessage.builder()
                .chatId(chatId.toString())
                .text("Диалог отменён. Набери /start, когда будешь готов оформить заказ.")
                .build());
    }

    private boolean isCancelCommand(String text) {
        String lower = text.toLowerCase();
        return "/cancel".equalsIgnoreCase(text) || "cancel".equalsIgnoreCase(text) || lower.equals("отмена");
    }

    private boolean isBackCommand(String text) {
        String lower = text.toLowerCase();
        return "/back".equalsIgnoreCase(text) || "back".equalsIgnoreCase(text) || lower.equals("назад");
    }

    private LocalDate resolveDateToken(String token) {
        return switch (token) {
            case "today" -> LocalDate.now();
            case "tomorrow" -> LocalDate.now().plusDays(1);
            default -> parseDate(token);
        };
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException ignored) { }
        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } catch (DateTimeParseException ignored) { }
        return null;
    }

    private static class SessionState {
        Long selectedDish;
        Integer selectedPortions;
        LocalDate selectedDate;
        boolean awaitingCustomDate;
        boolean saved;
        Stage stage = Stage.MENU;
    }
}
