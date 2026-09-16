package org.example.bot;

import org.example.models.CommunityMember;
import org.example.models.Poll;
import org.example.models.PollParticipant;
import org.example.models.PollQuestion;
import org.example.service.PollManager;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MyTelegramBot extends TelegramLongPollingBot {

    private final String botToken;
    private final PollManager pollManager = PollManager.getInstance(); // Ensure shared instance

    public MyTelegramBot(String botToken) {
        this.botToken = botToken;
    }

    @Override
    public String getBotUsername() {
        return "ProjectBot";
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            User from = update.getMessage().getFrom();
            // Debug log for incoming messages
            System.out.println("Received message: \"" + messageText + "\" from: " + from.getFirstName() + " (ID: " + from.getId() + ")");
            handleTextMessage(update);
        } else if (update.hasCallbackQuery()) {
            // Debug log for callback queries
            System.out.println("Received callback query: \"" + update.getCallbackQuery().getData() + "\" from: " + update.getCallbackQuery().getFrom().getFirstName());
            handleCallbackQuery(update);
        }
    }

    private void handleTextMessage(Update update) {
        String messageText = update.getMessage().getText().trim();
        User from = update.getMessage().getFrom();
        long chatId = update.getMessage().getChatId();

        // Per requirements: Start, היי, Hi are the join commands
        if (messageText.equalsIgnoreCase("/start") || messageText.equalsIgnoreCase("היי") || messageText.equalsIgnoreCase("Hi")) {
            
            CommunityMember newMember = new CommunityMember(from.getId(), from.getFirstName(), from.getUserName());
            
            // Rely on PollManager to handle logic and return whether the user was actually added
            boolean wasAdded = pollManager.addCommunityMember(newMember);

            if (wasAdded) {
                // This block runs only if the member is new
                System.out.println("New member registered: " + newMember.getFirstName());

                // Send a rich welcome message to the new member
                String welcomeMsg = String.format(
                    "🎉 *ברוכים הבאים לקהילה!*\n\n" +
                    "שלום %s, הצטרפת בהצלחה! 🙌\n" +
                    "כשסקר חדש יתחיל, תקבל/י הודעה עם השאלות.\n\n" +
                    "📊 מספר חברי הקהילה: *%d*",
                    newMember.getFirstName(), pollManager.getCommunityMembers().size()
                );
                sendMarkdownMessage(newMember.getChatId(), welcomeMsg);

                // Notify all other community members about the new joiner
                String notificationText = String.format(
                    "👤 *חבר/ה חדש/ה הצטרף/ה לקהילה!*\n\n" +
                    "שם: %s\n" +
                    "📊 סך חברי הקהילה: *%d*",
                    newMember.getFirstName(), pollManager.getCommunityMembers().size()
                );

                pollManager.getCommunityMembers().stream()
                        .filter(member -> member.getChatId() != newMember.getChatId()) // Don't send to the new member
                        .forEach(member -> sendMarkdownMessage(member.getChatId(), notificationText));
            } else {
                // This block runs if the member already existed
                sendMessage(chatId, "👋 אתה כבר חבר/ה בקהילה! אין צורך להצטרף שוב.");
            }
        }
        // Any other message is ignored as per the requirements
    }

    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long userId = update.getCallbackQuery().getFrom().getId();
        String[] parts = callbackData.split(":");

        if (parts.length != 3 || !parts[0].equals("vote")) {
            answerCallbackQuery(update.getCallbackQuery().getId(), "❌ נתוני callback לא תקינים.");
            return;
        }

        int questionIndex;
        int optionIndex;
        try {
            questionIndex = Integer.parseInt(parts[1]);
            optionIndex = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            answerCallbackQuery(update.getCallbackQuery().getId(), "❌ אינדקס שאלה או תשובה לא תקין.");
            return;
        }

        Poll activePoll = pollManager.getActivePoll();
        if (activePoll == null || !activePoll.isActive()) {
            answerCallbackQuery(update.getCallbackQuery().getId(), "⏰ הסקר כבר נסגר. לא ניתן להצביע.");
            return;
        }

        Optional<PollParticipant> participantOpt = activePoll.getParticipants().stream()
                .filter(p -> p.getMember().getChatId() == userId)
                .findFirst();

        if (participantOpt.isEmpty()) {
            answerCallbackQuery(update.getCallbackQuery().getId(), "🚫 אינך משתתף/ת בסקר זה.");
            return;
        }

        PollParticipant participant = participantOpt.get();
        if (participant.getAnsweredQuestionIndices().contains(questionIndex)) {
            answerCallbackQuery(update.getCallbackQuery().getId(), "⚠️ כבר ענית על שאלה זו!");
            return;
        }

        if (questionIndex < 0 || questionIndex >= activePoll.getQuestions().size()) {
            answerCallbackQuery(update.getCallbackQuery().getId(), "❌ אינדקס שאלה לא תקין.");
            return;
        }
        PollQuestion question = activePoll.getQuestions().get(questionIndex);
        if (optionIndex < 0 || optionIndex >= question.getOptions().size()) {
            answerCallbackQuery(update.getCallbackQuery().getId(), "❌ אינדקס תשובה לא תקין.");
            return;
        }

        // Record the vote
        question.getOptions().get(optionIndex).incrementVoteCount();
        participant.addAnsweredQuestion(questionIndex);

        int answeredCount = participant.getAnsweredQuestionIndices().size();
        int totalQuestions = activePoll.getQuestions().size();

        if (answeredCount == totalQuestions) {
            participant.setCompletionStatus("השלים");
            // Send completion confirmation via chat message
            sendMessage(participant.getMember().getChatId(),
                    "🎉 כל הכבוד! סיימת לענות על כל " + totalQuestions + " השאלות בסקר. תודה רבה על ההשתתפות!");
        } else {
            participant.setCompletionStatus("בתהליך");
        }

        // Answer the callback with progress info
        String callbackAnswer = String.format("✅ תשובתך נרשמה! ענית על %d מתוך %d שאלות.", answeredCount, totalQuestions);
        answerCallbackQuery(update.getCallbackQuery().getId(), callbackAnswer);

        // Check if all participants completed — end poll early if so
        boolean allCompleted = activePoll.getParticipants().stream()
                .allMatch(p -> p.getCompletionStatus().equals("השלים"));
        if (allCompleted) {
            pollManager.endPoll();
            sendPollClosedMessages(activePoll.getParticipants(), true);
            System.out.println("Poll ended early: All participants completed.");
        }
    }

    public void sendPollQuestions(List<PollParticipant> participants) {
        Poll activePoll = pollManager.getActivePoll();
        if (activePoll == null) return;

        int totalQuestions = activePoll.getQuestions().size();

        for (PollParticipant participant : participants) {
            // Send an intro message first
            sendMarkdownMessage(participant.getMember().getChatId(),
                    "📋 *סקר חדש התחיל!*\n\n" +
                    "מספר שאלות: *" + totalQuestions + "*\n" +
                    "זמן מרבי למענה: *5 דקות*\n\n" +
                    "אנא ענה/י על השאלות הבאות:");

            for (int i = 0; i < totalQuestions; i++) {
                if (!participant.getAnsweredQuestionIndices().contains(i)) {
                    PollQuestion question = activePoll.getQuestions().get(i);
                    SendMessage message = new SendMessage();
                    message.setChatId(String.valueOf(participant.getMember().getChatId()));
                    // Add question numbering
                    message.setText(String.format("❓ שאלה %d מתוך %d:\n\n%s", i + 1, totalQuestions, question.getQuestionText()));

                    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

                    // Each option gets its own row for better readability
                    for (int j = 0; j < question.getOptions().size(); j++) {
                        List<InlineKeyboardButton> row = new ArrayList<>();
                        InlineKeyboardButton button = new InlineKeyboardButton();
                        button.setText(question.getOptions().get(j).getOptionText());
                        button.setCallbackData(String.format("vote:%d:%d", i, j));
                        row.add(button);
                        keyboard.add(row);
                    }
                    markup.setKeyboard(keyboard);
                    message.setReplyMarkup(markup);

                    try {
                        execute(message);
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void sendReminderMessages(List<PollParticipant> participants) {
        participants.stream()
                .filter(p -> !p.getCompletionStatus().equals("השלים") && !p.isReminderSent())
                .forEach(p -> {
                    Poll activePoll = pollManager.getActivePoll();
                    int answered = p.getAnsweredQuestionIndices().size();
                    int total = activePoll != null ? activePoll.getQuestions().size() : 0;

                    String reminderMsg = String.format(
                        "⏰ *תזכורת!*\n\n" +
                        "יש סקר פעיל שטרם סיימת.\n" +
                        "ענית על *%d* מתוך *%d* שאלות.\n" +
                        "נותרו כ-2 דקות לסיום הסקר!\n\n" +
                        "אנא השלם/י את המענה. 📝",
                        answered, total
                    );
                    sendMarkdownMessage(p.getMember().getChatId(), reminderMsg);
                    p.setReminderSent(true);
                });
    }

    /**
     * Sends a message to all participants when the poll closes.
     * @param participants the poll participants
     * @param allCompleted true if poll ended because everyone completed, false if time ran out
     */
    public void sendPollClosedMessages(List<PollParticipant> participants, boolean allCompleted) {
        String closedMessage;
        if (allCompleted) {
            closedMessage = "✅ *הסקר הסתיים!*\n\nכל המשתתפים השלימו את המענה. תודה לכולם! 🎉";
        } else {
            closedMessage = "⏰ *הסקר נסגר!*\n\nהזמן המוקצב (5 דקות) הסתיים.\nתודה לכל מי שהשתתף! 📊";
        }
        for (PollParticipant p : participants) {
            sendMarkdownMessage(p.getMember().getChatId(), closedMessage);
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMarkdownMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setParseMode("Markdown");
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void answerCallbackQuery(String callbackQueryId, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQueryId);
        answer.setText(text);
        answer.setShowAlert(false);
        try {
            execute(answer);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
