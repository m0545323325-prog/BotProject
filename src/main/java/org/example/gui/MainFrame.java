package org.example.gui;

import com.formdev.flatlaf.FlatClientProperties;
import org.example.bot.MyTelegramBot;
import org.example.gpt.ChatGPTService;
import org.example.models.*;
import org.example.service.PollManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MainFrame extends JFrame implements PollManager.CommunityListener {

    private final MyTelegramBot telegramBot;
    private final PollManager pollManager;
    private final ChatGPTService chatGPTService;

    // UI Components
    private JTabbedPane tabbedPane;

    // Community Panel
    private DefaultTableModel communityTableModel;
    private JLabel totalCommunityMembersLabel;

    // Poll Creation Panel
    private JRadioButton manualCreationRadio;
    private JRadioButton aiCreationRadio;
    private JPanel manualPollInputPanel;
    private JPanel aiPollInputPanel;
    private JTextField aiTopicField;
    private JTextArea aiPreviewArea;
    private JPanel aiQuestionsContainerPanel;
    private List<QuestionInputPanel> questionInputPanels;
    private List<QuestionInputPanel> aiQuestionInputPanels;
    private List<PollQuestion> lastGeneratedQuestions;

    private JRadioButton immediateSendRadio;
    private JRadioButton delayedSendRadio;
    private JTextField delayMinutesField;
    private JLabel delayCountdownLabel;
    private Timer delayedSendTimer;

    // Active Poll Monitoring Panel
    private JLabel totalParticipantsLabel;
    private JLabel completedCountLabel;
    private JLabel pendingCountLabel;
    private JLabel timeRemainingLabel;
    private DefaultTableModel participantsTableModel;
    private Timer activePollTimer;
    private Instant pollStartTime;
    private static final long MAX_POLL_DURATION_SECONDS = 5 * 60; // 5 minutes
    private static final long REMINDER_TRIGGER_SECONDS = 3 * 60; // 3 minutes remaining

    public MainFrame(MyTelegramBot telegramBot) {
        this.telegramBot = telegramBot;
        this.pollManager = PollManager.getInstance();
        
        String chatGPTApiKey = "Oi8ugP8d8aqOyc9jRdz0GW1yaI9dX5PlVIKtzKKS8an5VQyzPsoATMBOxgOs2YGU";
        this.chatGPTService = new ChatGPTService(chatGPTApiKey);

        setTitle("Telegram Poll Management System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        UIManager.put("Button.arc", 10);
        UIManager.put("Component.arc", 10);
        UIManager.put("ProgressBar.arc", 10);
        UIManager.put("TextComponent.arc", 10);

        initComponents();
        setupListeners();
        updateCommunityPanel(); 
    }

    private void initComponents() {
        tabbedPane = new JTabbedPane();
        tabbedPane.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSABLE, false);

        tabbedPane.addTab("Community", createCommunityPanel());
        tabbedPane.addTab("Create Poll", createPollCreationPanel());
        tabbedPane.addTab("Active Poll", createActivePollMonitoringPanel());

        add(tabbedPane, BorderLayout.CENTER);
    }

    private void setupListeners() {
        pollManager.addPollStateListener(this::handlePollStateChange);
        pollManager.addCommunityListener(this);
    }

    @Override
    public void onMemberAdded(CommunityMember member, int totalCount) {
        communityTableModel.addRow(new Object[]{
                member.getFirstName(),
                member.getUsername(),
                member.getJoinedTime()
        });
        totalCommunityMembersLabel.setText("Total Community Members: " + totalCount);
    }

    @Override
    public void onCommunityLoaded(List<CommunityMember> members) {
        updateCommunityPanel();
    }

    private void handlePollStateChange(Poll poll) {
        SwingUtilities.invokeLater(() -> {
            if (pollManager.isPollActive() && poll != null) {
                pollStartTime = poll.getStartTimestamp();
                startActivePollTimer();
                tabbedPane.setSelectedIndex(2);
            } else {
                stopActivePollTimer();
                if (poll != null && !poll.isActive()) {
                    showPollResultsDialog(poll);
                }
            }
            updateCommunityPanel();
            updateActivePollMonitoringPanel();
        });
    }

    private JPanel createCommunityPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        totalCommunityMembersLabel = new JLabel("Total Community Members: 0");
        totalCommunityMembersLabel.setFont(totalCommunityMembersLabel.getFont().deriveFont(Font.BOLD, 16));
        panel.add(totalCommunityMembersLabel, BorderLayout.NORTH);

        String[] columnNames = {"Name", "Telegram Username", "Joined Time"};
        communityTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable communityTable = new JTable(communityTableModel);
        communityTable.setFillsViewportHeight(true);
        JScrollPane scrollPane = new JScrollPane(communityTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void updateCommunityPanel() {
        SwingUtilities.invokeLater(() -> {
            communityTableModel.setRowCount(0);
            List<CommunityMember> members = pollManager.getCommunityMembers();
            for (CommunityMember member : members) {
                communityTableModel.addRow(new Object[]{
                        member.getFirstName(),
                        member.getUsername(),
                        member.getJoinedTime()
                });
            }
            totalCommunityMembersLabel.setText("Total Community Members: " + members.size());
        });
    }

    private JPanel createPollCreationPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel creationMethodPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        creationMethodPanel.setBorder(BorderFactory.createTitledBorder("Poll Creation Method"));
        ButtonGroup creationMethodGroup = new ButtonGroup();
        manualCreationRadio = new JRadioButton("Manual Input");
        aiCreationRadio = new JRadioButton("AI Generated (ChatGPT)");
        creationMethodGroup.add(manualCreationRadio);
        creationMethodGroup.add(aiCreationRadio);
        creationMethodPanel.add(manualCreationRadio);
        creationMethodPanel.add(aiCreationRadio);
        panel.add(creationMethodPanel, BorderLayout.NORTH);

        manualPollInputPanel = createManualPollInputPanel();
        aiPollInputPanel = createAIPollInputPanel();
        
        addQuestionInputPanel();

        JPanel contentPanel = new JPanel(new CardLayout());
        contentPanel.add(manualPollInputPanel, "Manual");
        contentPanel.add(aiPollInputPanel, "AI");
        panel.add(contentPanel, BorderLayout.CENTER);

        manualCreationRadio.setSelected(true);
        ((CardLayout) contentPanel.getLayout()).show(contentPanel, "Manual");

        manualCreationRadio.addActionListener(e -> ((CardLayout) contentPanel.getLayout()).show(contentPanel, "Manual"));
        aiCreationRadio.addActionListener(e -> ((CardLayout) contentPanel.getLayout()).show(contentPanel, "AI"));

        JPanel bottomPanel = createLaunchControlsPanel();
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createManualPollInputPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        questionInputPanels = new ArrayList<>();

        JButton addQuestionButton = new JButton("Add Question");
        addQuestionButton.addActionListener(e -> addQuestionInputPanel());
        panel.add(addQuestionButton);

        return panel;
    }

    private void addQuestionInputPanel() {
        if (questionInputPanels.size() >= 3) {
            JOptionPane.showMessageDialog(this, "Maximum 3 questions allowed per poll.", "Limit Reached", JOptionPane.WARNING_MESSAGE);
            return;
        }
        QuestionInputPanel newQuestionPanel = new QuestionInputPanel(this);
        questionInputPanels.add(newQuestionPanel);
        manualPollInputPanel.add(newQuestionPanel);
        manualPollInputPanel.revalidate();
        manualPollInputPanel.repaint();
    }

    public void removeQuestionInputPanel(QuestionInputPanel panel) {
        if (questionInputPanels != null && questionInputPanels.contains(panel)) {
            if (questionInputPanels.size() <= 1) {
                JOptionPane.showMessageDialog(this, "A poll must have at least one question.", "Limit Reached", JOptionPane.WARNING_MESSAGE);
                return;
            }
            questionInputPanels.remove(panel);
            manualPollInputPanel.remove(panel);
            manualPollInputPanel.revalidate();
            manualPollInputPanel.repaint();
        } else if (aiQuestionInputPanels != null && aiQuestionInputPanels.contains(panel)) {
            if (aiQuestionInputPanels.size() <= 1) {
                JOptionPane.showMessageDialog(this, "A poll must have at least one question.", "Limit Reached", JOptionPane.WARNING_MESSAGE);
                return;
            }
            aiQuestionInputPanels.remove(panel);
            aiQuestionsContainerPanel.remove(panel);
            aiQuestionsContainerPanel.revalidate();
            aiQuestionsContainerPanel.repaint();
        }
    }

    private JPanel createAIPollInputPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        inputPanel.add(new JLabel("Topic / Prompt:"));
        aiTopicField = new JTextField(25);
        inputPanel.add(aiTopicField);
        JButton generateButton = new JButton("Generate Questions (צור שאלות)");
        generateButton.addActionListener(this::generateAIPollQuestions);
        inputPanel.add(generateButton);
        panel.add(inputPanel, BorderLayout.NORTH);

        aiQuestionsContainerPanel = new JPanel();
        aiQuestionsContainerPanel.setLayout(new BoxLayout(aiQuestionsContainerPanel, BoxLayout.Y_AXIS));
        aiQuestionInputPanels = new ArrayList<>();

        JScrollPane scrollPane = new JScrollPane(aiQuestionsContainerPanel);
        scrollPane.setBorder(BorderFactory.createTitledBorder("שאלות ותשובות שנוצרו ע\"י AI (ניתן לעריכה בשדות הקלט)"));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void generateAIPollQuestions(ActionEvent e) {
        String topic = aiTopicField.getText().trim();
        if (topic.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a topic for AI generation.", "Input Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        aiQuestionsContainerPanel.removeAll();
        aiQuestionInputPanels.clear();
        JLabel loadingLabel = new JLabel("יוצר שאלות ותשובות עבור: '" + topic + "'... נא להמתין");
        loadingLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        aiQuestionsContainerPanel.add(loadingLabel);
        aiQuestionsContainerPanel.revalidate();
        aiQuestionsContainerPanel.repaint();

        new SwingWorker<List<PollQuestion>, Void>() {
            @Override
            protected List<PollQuestion> doInBackground() throws IOException, InterruptedException {
                return chatGPTService.generatePollQuestions(topic);
            }

            @Override
            protected void done() {
                try {
                    lastGeneratedQuestions = get();
                    SwingUtilities.invokeLater(() -> {
                        aiQuestionsContainerPanel.removeAll();
                        aiQuestionInputPanels.clear();

                        questionInputPanels.clear();
                        manualPollInputPanel.removeAll();
                        JButton addQuestionButton = new JButton("Add Question");
                        addQuestionButton.addActionListener(ev -> addQuestionInputPanel());
                        manualPollInputPanel.add(addQuestionButton);

                        if (lastGeneratedQuestions != null && !lastGeneratedQuestions.isEmpty()) {
                            for (PollQuestion q : lastGeneratedQuestions) {
                                // Add to AI container input fields
                                QuestionInputPanel aiQPanel = new QuestionInputPanel(MainFrame.this);
                                aiQPanel.setQuestionText(q.getQuestionText());
                                aiQPanel.setOptions(q.getOptions());
                                aiQuestionInputPanels.add(aiQPanel);
                                aiQuestionsContainerPanel.add(aiQPanel);

                                // Add to Manual container input fields
                                QuestionInputPanel manualQPanel = new QuestionInputPanel(MainFrame.this);
                                manualQPanel.setQuestionText(q.getQuestionText());
                                manualQPanel.setOptions(q.getOptions());
                                questionInputPanels.add(manualQPanel);
                                manualPollInputPanel.add(manualQPanel);
                            }
                        } else {
                            JLabel noQuestionsLabel = new JLabel("לא נוצרו שאלות. אנא נסה נושא/פרומפט אחר.");
                            noQuestionsLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
                            aiQuestionsContainerPanel.add(noQuestionsLabel);
                        }
                        aiQuestionsContainerPanel.revalidate();
                        aiQuestionsContainerPanel.repaint();
                        manualPollInputPanel.revalidate();
                        manualPollInputPanel.repaint();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        aiQuestionsContainerPanel.removeAll();
                        JLabel errorLabel = new JLabel("שגיאה ביצירת שאלות: " + ex.getMessage());
                        errorLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
                        aiQuestionsContainerPanel.add(errorLabel);
                        aiQuestionsContainerPanel.revalidate();
                        aiQuestionsContainerPanel.repaint();
                        JOptionPane.showMessageDialog(MainFrame.this, "Error generating questions: " + ex.getMessage(), "API Error", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }
        }.execute();
    }

    private JPanel createLaunchControlsPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JPanel sendOptionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sendOptionsPanel.setBorder(BorderFactory.createTitledBorder("Send Options"));
        ButtonGroup sendOptionsGroup = new ButtonGroup();
        immediateSendRadio = new JRadioButton("Send Immediately");
        delayedSendRadio = new JRadioButton("Delayed Send (minutes):");
        sendOptionsGroup.add(immediateSendRadio);
        sendOptionsGroup.add(delayedSendRadio);
        sendOptionsPanel.add(immediateSendRadio);
        sendOptionsPanel.add(delayedSendRadio);

        delayMinutesField = new JTextField("1", 3);
        delayMinutesField.setEnabled(false);
        delayCountdownLabel = new JLabel("");
        sendOptionsPanel.add(delayMinutesField);
        sendOptionsPanel.add(delayCountdownLabel);

        immediateSendRadio.setSelected(true);
        immediateSendRadio.addActionListener(e -> {
            delayMinutesField.setEnabled(false);
            delayCountdownLabel.setText("");
            if (delayedSendTimer != null) delayedSendTimer.stop();
        });
        delayedSendRadio.addActionListener(e -> delayMinutesField.setEnabled(true));

        bottomPanel.add(sendOptionsPanel, BorderLayout.NORTH);

        JPanel actionButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton launchPollButton = new JButton("Launch Poll (שגר סקר)");
        launchPollButton.addActionListener(this::launchPoll);
        actionButtonsPanel.add(launchPollButton);

        JButton resetPollButton = new JButton("ביטול / מחיקת סקר");
        resetPollButton.addActionListener(this::resetPoll);
        actionButtonsPanel.add(resetPollButton);
        
        bottomPanel.add(actionButtonsPanel, BorderLayout.SOUTH);

        return bottomPanel;
    }

    private void launchPoll(ActionEvent e) {
        if (!pollManager.canStartPoll()) {
            JOptionPane.showMessageDialog(this, "Cannot launch poll: Minimum 3 community members required.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (pollManager.isPollActive()) {
            JOptionPane.showMessageDialog(this, "Another poll is already active. Please wait for it to finish.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<PollQuestion> questions;
        try {
            questions = new ArrayList<>();
            List<QuestionInputPanel> activePanels = manualCreationRadio.isSelected() ? questionInputPanels : aiQuestionInputPanels;
            
            if (activePanels != null && !activePanels.isEmpty()) {
                for (QuestionInputPanel qPanel : activePanels) {
                    questions.add(qPanel.buildPollQuestion());
                }
            } else if (!manualCreationRadio.isSelected() && lastGeneratedQuestions != null && !lastGeneratedQuestions.isEmpty()) {
                questions = lastGeneratedQuestions;
            } else {
                throw new IllegalArgumentException("No poll questions available in input fields. Please create or generate questions first.");
            }
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "Poll Validation Error: " + ex.getMessage(), "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (immediateSendRadio.isSelected()) {
            startPollAndSend(questions);
        } else {
            try {
                int delayMinutes = Integer.parseInt(delayMinutesField.getText());
                if (delayMinutes <= 0) throw new NumberFormatException("Delay must be a positive number.");
                startDelayedPoll(questions, delayMinutes);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid delay minutes: " + ex.getMessage(), "Validation Error", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void resetPoll(ActionEvent e) {
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to clear/delete the current poll (if active) and reset input fields?", "Confirm Reset", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            pollManager.resetOrDeletePoll();

            if (delayedSendTimer != null) {
                delayedSendTimer.stop();
                delayedSendTimer = null;
            }
            if (activePollTimer != null) {
                activePollTimer.stop();
                activePollTimer = null;
            }
            
            questionInputPanels.clear();
            manualPollInputPanel.removeAll();
            JButton addQuestionButton = new JButton("Add Question");
            addQuestionButton.addActionListener(ev -> addQuestionInputPanel());
            manualPollInputPanel.add(addQuestionButton);
            addQuestionInputPanel();
            manualPollInputPanel.revalidate();
            manualPollInputPanel.repaint();

            if (aiQuestionInputPanels != null) aiQuestionInputPanels.clear();
            if (aiQuestionsContainerPanel != null) {
                aiQuestionsContainerPanel.removeAll();
                aiQuestionsContainerPanel.revalidate();
                aiQuestionsContainerPanel.repaint();
            }

            aiTopicField.setText("");
            lastGeneratedQuestions = null;

            delayMinutesField.setText("1");
            delayMinutesField.setEnabled(false);
            delayCountdownLabel.setText("");
            immediateSendRadio.setSelected(true);

            updateActivePollMonitoringPanel(); 
            
            JOptionPane.showMessageDialog(this, "Poll state and input fields have been reset.", "Poll Reset", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void startDelayedPoll(List<PollQuestion> questions, int delayMinutes) {
        final long delayMillis = delayMinutes * 60 * 1000L;
        final long endTime = System.currentTimeMillis() + delayMillis;

        delayedSendTimer = new Timer(1000, e -> {
            long remainingMillis = endTime - System.currentTimeMillis();

            if (remainingMillis <= 0) {
                ((Timer) e.getSource()).stop();
                SwingUtilities.invokeLater(() -> {
                    delayCountdownLabel.setText("Launching...");
                    startPollAndSend(questions);
                });
            } else {
                long seconds = remainingMillis / 1000;
                long minutes = seconds / 60;
                seconds %= 60;
                final String timeString = String.format("Launching in: %02d:%02d", minutes, seconds);
                SwingUtilities.invokeLater(() -> delayCountdownLabel.setText(timeString));
            }
        });
        delayedSendTimer.setInitialDelay(0);
        delayedSendTimer.start();
        JOptionPane.showMessageDialog(this, "Poll scheduled to launch in " + delayMinutes + " minutes.", "Poll Scheduled", JOptionPane.INFORMATION_MESSAGE);
    }

    private void startPollAndSend(List<PollQuestion> questions) {
        try {
            pollManager.startPoll(questions);
            telegramBot.sendPollQuestions(pollManager.getActivePoll().getParticipants());
            JOptionPane.showMessageDialog(this, "Poll launched successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, "Error launching poll: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel createActivePollMonitoringPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel metricsPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        metricsPanel.setBorder(BorderFactory.createTitledBorder("Poll Metrics"));
        totalParticipantsLabel = new JLabel("Total Participants: 0");
        completedCountLabel = new JLabel("Completed: 0");
        pendingCountLabel = new JLabel("Pending: 0");
        timeRemainingLabel = new JLabel("Time Remaining: --:--");
        metricsPanel.add(totalParticipantsLabel);
        metricsPanel.add(completedCountLabel);
        metricsPanel.add(pendingCountLabel);
        metricsPanel.add(timeRemainingLabel);
        panel.add(metricsPanel, BorderLayout.NORTH);

        String[] columnNames = {"Name", "Progress", "Status"};
        participantsTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable participantsTable = new JTable(participantsTableModel);
        participantsTable.setFillsViewportHeight(true);
        JScrollPane scrollPane = new JScrollPane(participantsTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void startActivePollTimer() {
        if (activePollTimer != null && activePollTimer.isRunning()) {
            activePollTimer.stop();
        }
        activePollTimer = new Timer(1000, new ActionListener() {
            private boolean reminderSent = false;

            @Override
            public void actionPerformed(ActionEvent e) {
                Poll activePoll = pollManager.getActivePoll();
                if (activePoll == null || !activePoll.isActive() || pollStartTime == null) {
                    stopActivePollTimer();
                    return;
                }

                long elapsedSeconds = Duration.between(pollStartTime, Instant.now()).getSeconds();
                long remainingSeconds = MAX_POLL_DURATION_SECONDS - elapsedSeconds;

                if (remainingSeconds <= 0) {
                    pollManager.endPoll();
                    return;
                }

                if (!reminderSent && remainingSeconds <= REMINDER_TRIGGER_SECONDS) {
                    telegramBot.sendReminderMessages(activePoll.getParticipants());
                    reminderSent = true;
                }

                boolean allCompleted = activePoll.getParticipants().stream()
                        .allMatch(p -> p.getCompletionStatus().equals("השלים"));
                if (allCompleted) {
                    pollManager.endPoll();
                    return;
                }

                final long minutes = remainingSeconds / 60;
                final long seconds = remainingSeconds % 60;
                SwingUtilities.invokeLater(() -> {
                    timeRemainingLabel.setText(String.format("Time Remaining: %02d:%02d", minutes, seconds));
                    updateActivePollMonitoringPanel();
                });
            }
        });
        activePollTimer.start();
    }

    private void stopActivePollTimer() {
        if (activePollTimer != null) {
            activePollTimer.stop();
        }
        SwingUtilities.invokeLater(() -> timeRemainingLabel.setText("Time Remaining: --:--"));
    }

    private void updateActivePollMonitoringPanel() {
        SwingUtilities.invokeLater(() -> {
            Poll activePoll = pollManager.getActivePoll();
            if (activePoll == null) {
                totalParticipantsLabel.setText("Total Participants: 0");
                completedCountLabel.setText("Completed: 0");
                pendingCountLabel.setText("Pending: 0");
                participantsTableModel.setRowCount(0);
                return;
            }

            List<PollParticipant> participants = activePoll.getParticipants();
            int total = participants.size();
            long completed = participants.stream().filter(p -> p.getCompletionStatus().equals("השלים")).count();
            long pending = total - completed;

            totalParticipantsLabel.setText("Total Participants: " + total);
            completedCountLabel.setText("Completed: " + completed);
            pendingCountLabel.setText("Pending: " + pending);

            participantsTableModel.setRowCount(0);
            for (PollParticipant p : participants) {
                String progress = String.format("%d/%d", p.getAnsweredQuestionIndices().size(), activePoll.getQuestions().size());
                participantsTableModel.addRow(new Object[]{p.getMember().getFirstName(), progress, p.getCompletionStatus()});
            }
        });
    }

    private void showPollResultsDialog(Poll poll) {
        JDialog resultsDialog = new JDialog(this, "Poll Results", true);
        resultsDialog.setSize(600, 400);
        resultsDialog.setLocationRelativeTo(this);
        resultsDialog.setLayout(new BorderLayout(10, 10));

        JTextArea resultsArea = new JTextArea();
        resultsArea.setEditable(false);
        resultsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(resultsArea);
        resultsDialog.add(scrollPane, BorderLayout.CENTER);

        StringBuilder sb = new StringBuilder("Poll Results (Started: ");
        sb.append(poll.getStartTimestamp().atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss"))).append(")\n\n");

        Map<String, List<PollOption>> finalResults = poll.getFinalResults();
        for (Map.Entry<String, List<PollOption>> entry : finalResults.entrySet()) {
            sb.append("Question: ").append(entry.getKey()).append("\n");
            int totalVotesForQuestion = entry.getValue().stream().mapToInt(PollOption::getVoteCount).sum();

            for (PollOption option : entry.getValue()) {
                double percentage = (totalVotesForQuestion == 0) ? 0 : (double) option.getVoteCount() / totalVotesForQuestion * 100;
                sb.append(String.format("  - %-25s: %d votes (%5.2f%%)\n", option.getOptionText(), option.getVoteCount(), percentage));
            }
            sb.append("\n");
        }
        resultsArea.setText(sb.toString());

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> resultsDialog.dispose());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(closeButton);
        resultsDialog.add(buttonPanel, BorderLayout.SOUTH);

        resultsDialog.setVisible(true);
    }

    private static class QuestionInputPanel extends JPanel {
        private final MainFrame parentFrame;
        private final JTextField questionField;
        private final JPanel optionsPanel;
        private final List<JTextField> optionFields;

        public QuestionInputPanel(MainFrame parentFrame) {
            this.parentFrame = parentFrame;
            setLayout(new BorderLayout(5, 5));
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Question"), new EmptyBorder(5, 5, 5, 5)));

            JPanel questionHeader = new JPanel(new BorderLayout());
            questionField = new JTextField(30);
            questionHeader.add(questionField, BorderLayout.CENTER);
            JButton removeQuestionButton = new JButton("X");
            removeQuestionButton.addActionListener(e -> parentFrame.removeQuestionInputPanel(this));
            questionHeader.add(removeQuestionButton, BorderLayout.EAST);
            add(questionHeader, BorderLayout.NORTH);

            optionsPanel = new JPanel();
            optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
            optionsPanel.setBorder(BorderFactory.createTitledBorder("Options (2-4)"));
            optionFields = new ArrayList<>();

            JButton addOptionButton = new JButton("Add Option");
            addOptionButton.addActionListener(e -> addOptionField());
            optionsPanel.add(addOptionButton);

            addOptionField();
            addOptionField();

            add(optionsPanel, BorderLayout.CENTER);
        }

        public void setQuestionText(String text) {
            questionField.setText(text);
        }

        public void setOptions(List<PollOption> options) {
            optionFields.clear();
            optionsPanel.removeAll();

            JButton addOptionButton = new JButton("Add Option");
            addOptionButton.addActionListener(e -> addOptionField());
            optionsPanel.add(addOptionButton);

            if (options != null) {
                for (PollOption opt : options) {
                    addOptionFieldWithText(opt.getOptionText());
                }
            }
            optionsPanel.revalidate();
            optionsPanel.repaint();
        }

        private void addOptionFieldWithText(String initialText) {
            if (optionFields.size() >= 4) return;
            JPanel optionRow = new JPanel(new BorderLayout(5, 5));
            JTextField optionField = new JTextField(initialText, 25);
            optionFields.add(optionField);
            optionRow.add(optionField, BorderLayout.CENTER);

            JButton removeOptionButton = new JButton("-");
            removeOptionButton.addActionListener(e -> removeOptionField(optionRow, optionField));
            optionRow.add(removeOptionButton, BorderLayout.EAST);

            optionsPanel.add(optionRow);
        }

        private void addOptionField() {
            if (optionFields.size() >= 4) {
                JOptionPane.showMessageDialog(parentFrame, "Maximum 4 options allowed per question.", "Limit Reached", JOptionPane.WARNING_MESSAGE);
                return;
            }
            JPanel optionRow = new JPanel(new BorderLayout(5, 5));
            JTextField optionField = new JTextField(25);
            optionFields.add(optionField);
            optionRow.add(optionField, BorderLayout.CENTER);

            JButton removeOptionButton = new JButton("-");
            removeOptionButton.addActionListener(e -> removeOptionField(optionRow, optionField));
            optionRow.add(removeOptionButton, BorderLayout.EAST);

            optionsPanel.add(optionRow);
            optionsPanel.revalidate();
            optionsPanel.repaint();
        }

        private void removeOptionField(JPanel optionRow, JTextField optionField) {
            if (optionFields.size() <= 2) {
                JOptionPane.showMessageDialog(parentFrame, "Minimum 2 options required per question.", "Limit Reached", JOptionPane.WARNING_MESSAGE);
                return;
            }
            optionFields.remove(optionField);
            optionsPanel.remove(optionRow);
            optionsPanel.revalidate();
            optionsPanel.repaint();
        }

        public PollQuestion buildPollQuestion() throws IllegalArgumentException {
            String questionText = questionField.getText().trim();
            if (questionText.isEmpty()) {
                throw new IllegalArgumentException("Question text cannot be empty.");
            }
            List<String> optionTexts = optionFields.stream()
                    .map(JTextField::getText)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            if (optionTexts.size() < 2 || optionTexts.size() > 4) {
                throw new IllegalArgumentException("Each question must have between 2 and 4 non-empty options.");
            }
            List<PollOption> options = optionTexts.stream().map(PollOption::new).collect(Collectors.toList());
            return new PollQuestion(questionText, options);
        }
    }
}
