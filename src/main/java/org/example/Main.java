package org.example;

import com.formdev.flatlaf.FlatLightLaf;
import org.example.bot.MyTelegramBot;
import org.example.gui.MainFrame;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        // 1. Set FlatLaf Look and Feel
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (UnsupportedLookAndFeelException e) {
            System.err.println("Failed to initialize FlatLaf look and feel.");
        }

        // 2. Initialize and Register Telegram Bot with hardcoded credentials
        String botToken = "8855344925:AAE_hgOcpbpv_Ql9AqMa6HwFqUjXcYF43DE";
        MyTelegramBot telegramBot = new MyTelegramBot(botToken);

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(telegramBot);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to register the Telegram bot: " + e.getMessage(), "Telegram API Error", JOptionPane.ERROR_MESSAGE);
            return; // Exit if bot registration fails
        }

        // 3. Create and show the main application window
        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame(telegramBot);
            mainFrame.setVisible(true);
        });
    }
}
