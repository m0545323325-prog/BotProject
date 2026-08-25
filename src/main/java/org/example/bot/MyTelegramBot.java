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

        // Case-insensitive check for start commands, now including "שלום"
        if (messageText.equalsIgnoreCase("/start") || messageText.equalsIgnoreCase("היי") || messageText.equalsIgnoreCase("שלום")) {
            
            CommunityMember newMember = new CommunityMember(from.getId(), from.getFirstName(), from.getUserName());
            
            // Rely on PollManager to handle logic and return whether the user was actually added
            boolean wasAdded = pollManager.addCommunityMember(newMember);

            if (wasAdded) {
                // This block runs only if the member is new
                System.out.println("New member registered: " + newMember.getFirstName());

                // Send a welcome message to the new member
                sendMessage(newMember.getChatId(), "Welcome! You have been registered to the community.");

                // Notify all other community members about the new joiner
                String notificationText = String.format("משתמש חדש הצטרף: %s. סך חברי הקהילה: %d",
                        newMember.getFirstName(), pollManager.getCommunityMembers().size());

                pollManager.getCommunityMembers().stream()
                        .filter(member -> member.getChatId() != newMember.getChatId()) // Don't send to the new member
                        .forEach(member -> sendMessage(member.getChatId(), notificationText));
            } else {
                // This block runs if the member already existed
                sendMessage(chatId, "You are already a member of the community.");
            }
        }
        // Any other message is ignored as per the requirements
    }

    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long userId = update.getCallbackQuery().getFrom().getId();
        String[] parts = callbackData.split(":");

        if (parts.length != 3 || !parts[0].equals("vote")) {
            answerCallbackQuery(update.getCallbackQuery().getId(), "Invalid callback data format.");
            return;
        }

        int questionIndex;
        int optionIndex;
        try {
            questionIndex = Integer.parseInt(parts[1]);
            optionIndex = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            answerCallbackQuery(update.getCallbackQuery().getId(), "Invalid question or option index.");
            return;
        }

        Poll activePoll = pollManager.getActivePoll();
        if (activePoll == null || !activePoll.isActive()) {
            answerCallbackQuery(update.getCallbackQuery().getId(), "No active poll found.");
            return;
        }

        Optional<PollParticipant> participantOpt = activePoll.getParticipants().stream()
                .filter(p -> p.getMember().getChatId() == userId)
                .findFirst();

        if (participantOpt.isEmpty()) {
            answerCallbackQuery(update.getCallbackQuery().getId(), "You are not a participant in this poll.");
            return;
        }

        PollParticipant participant = participantOpt.get();
        if (participant.getAnsweredQuestionIndices().contains(questionIndex)) {
            answerCallbackQuery(update.getCallbackQuery().getId(), "You have already answered this question.");
            return;
        }

        if (questionIndex < 0 || questionIndex >= activePoll.getQuestions().size()) {
            answerCallbackQuery(update.getCallbackQuery().getId(), "Invalid question index.");
            return;
        }
        PollQuestion question = activePoll.getQuestions().get(questionIndex);
        if (optionIndex < 0 || optionIndex >= question.getOptions().size()) {
            answerCallbackQuery(update.getCallbackQuery().getId(), "Invalid option index.");
            return;
        }

        question.getOptions().get(optionIndex).incrementVoteCount();
        participant.addAnsweredQuestion(questionIndex);

        if (participant.getAnsweredQuestionIndices().size() == activePoll.getQuestions().size()) {
            participant.setCompletionStatus("השלים");
        } else {
            participant.setCompletionStatus("בתהליך");
        }

        answerCallbackQuery(update.getCallbackQuery().getId(), "Your vote was recorded!");

        boolean allCompleted = activePoll.getParticipants().stream()
                .allMatch(p -> p.getCompletionStatus().equals("השלים"));
        if (allCompleted) {
            pollManager.endPoll();
            System.out.println("Poll ended early: All participants completed.");
        }
    }

    public void sendPollQuestions(List<PollParticipant> participants) {
        Poll activePoll = pollManager.getActivePoll();
        if (activePoll == null) return;

        for (PollParticipant participant : participants) {
            for (int i = 0; i < activePoll.getQuestions().size(); i++) {
                if (!participant.getAnsweredQuestionIndices().contains(i)) {
                    PollQuestion question = activePoll.getQuestions().get(i);
                    SendMessage message = new SendMessage();
                    message.setChatId(String.valueOf(participant.getMember().getChatId()));
                    message.setText(question.getQuestionText());

                    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                    List<InlineKeyboardButton> row = new ArrayList<>();

                    for (int j = 0; j < question.getOptions().size(); j++) {
                        InlineKeyboardButton button = new InlineKeyboardButton();
                        button.setText(question.getOptions().get(j).getOptionText());
                        button.setCallbackData(String.format("vote:%d:%d", i, j));
                        row.add(button);
                    }
                    keyboard.add(row);
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
                    sendMessage(p.getMember().getChatId(), "Reminder: Please complete the active poll!");
                    p.setReminderSent(true);
                });
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
