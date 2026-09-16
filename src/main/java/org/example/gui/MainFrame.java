package org.example.gui;

import com.formdev.flatlaf.FlatClientProperties;
import org.example.bot.MyTelegramBot;
import org.example.gpt.ChatGPTService;
import org.example.models.*;
import org.example.service.PollManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
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

    // Color constants for consistent UI
    private static final Color ACCENT_COLOR = new Color(75, 110, 175);
    private static final Color SUCCESS_COLOR = new Color(80, 160, 90);
    private static final Color WARNING_COLOR = new Color(210, 160, 60);
    private static final Color DANGER_COLOR = new Color(190, 70, 70);
    private static final Color COMPLETED_BG = new Color(35, 60, 35);
    private static final Color IN_PROGRESS_BG = new Color(60, 55, 30);
    private static final Color NOT_STARTED_BG = new Color(60, 35, 35);

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
    private JProgressBar pollProgressBar;
    private DefaultTableModel participantsTableModel;
    private JTable participantsTable;
    private Timer activePollTimer;
    private Instant pollStartTime;
    private JPanel noPollPanel;
    private JPanel activePollContentPanel;
    private JPanel activePollWrapper;
    private static final long MAX_POLL_DURATION_SECONDS = 5 * 60; // 5 minutes

    // Status bar
    private JLabel statusBarLabel;

    public MainFrame(MyTelegramBot telegramBot) {
        this.telegramBot = telegramBot;
        this.pollManager = PollManager.getInstance();
        
        String chatGPTApiKey = "Oi8ugP8d8aqOyc9jRdz0GW1yaI9dX5PlVIKtzKKS8an5VQyzPsoATMBOxgOs2YGU";
        this.chatGPTService = new ChatGPTService(chatGPTApiKey);

        setTitle("📊 מערכת ניהול סקרים — Telegram Bot");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 750);
        setLocationRelativeTo(null);

        initComponents();
        setupListeners();
        updateCommunityPanel(); 
        setStatusMessage("המערכת מוכנה. ממתינה לחברי קהילה...");
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        tabbedPane = new JTabbedPane();
        tabbedPane.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSABLE, false);
        tabbedPane.setFont(new Font("Arial", Font.BOLD, 14));

        tabbedPane.addTab("  👥 קהילה  ", createCommunityPanel());
        tabbedPane.addTab("  ✏️ יצירת סקר  ", createPollCreationPanel());
        tabbedPane.addTab("  📊 סקר פעיל  ", createActivePollMonitoringPanel());

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Status bar at the bottom
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(80, 80, 80)),
                new EmptyBorder(5, 10, 5, 10)
        ));
        statusBarLabel = new JLabel("מוכן");
        statusBarLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        statusBar.add(statusBarLabel, BorderLayout.WEST);
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void setStatusMessage(String message) {
        SwingUtilities.invokeLater(() -> statusBarLabel.setText("💡 " + message));
    }

    private void setupListeners() {
        pollManager.addPollStateListener(this::handlePollStateChange);
        pollManager.addCommunityListener(this);
    }

    @Override
    public void onMemberAdded(CommunityMember member, int totalCount) {
        SwingUtilities.invokeLater(() -> {
            communityTableModel.addRow(new Object[]{
                    member.getFirstName(),
                    member.getUsername() != null ? "@" + member.getUsername() : "—",
                    member.getJoinedTime()
            });
            totalCommunityMembersLabel.setText("  👥 סך חברי הקהילה: " + totalCount);
            setStatusMessage("חבר/ה חדש/ה הצטרף/ה: " + member.getFirstName() + " | סה\"כ: " + totalCount);
        });
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
                showActivePollContent(true);
                setStatusMessage("סקר פעיל! עוקב אחר תשובות...");
            } else {
                stopActivePollTimer();
                if (poll != null && !poll.isActive()) {
                    showActivePollContent(false);
                    showPollResultsDialog(poll);
                    setStatusMessage("הסקר הסתיים. ניתן ליצור סקר חדש.");
                } else {
                    showActivePollContent(false);
                    setStatusMessage("המערכת מוכנה. ניתן ליצור סקר חדש.");
                }
            }
            updateCommunityPanel();
            updateActivePollMonitoringPanel();
        });
    }

    // ===== COMMUNITY PANEL =====

    private JPanel createCommunityPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Header with icon and count
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("  👥 חברי הקהילה");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setForeground(ACCENT_COLOR);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        totalCommunityMembersLabel = new JLabel("  👥 סך חברי הקהילה: 0  ");
        totalCommunityMembersLabel.setFont(new Font("Arial", Font.BOLD, 16));
        totalCommunityMembersLabel.setForeground(SUCCESS_COLOR);
        headerPanel.add(totalCommunityMembersLabel, BorderLayout.EAST);
        headerPanel.setBorder(new EmptyBorder(0, 0, 10, 0));
        panel.add(headerPanel, BorderLayout.NORTH);

        String[] columnNames = {"שם", "Telegram Username", "מועד הצטרפות"};
        communityTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable communityTable = new JTable(communityTableModel);
        communityTable.setFillsViewportHeight(true);
        communityTable.setRowHeight(32);
        communityTable.setFont(new Font("Arial", Font.PLAIN, 14));
        communityTable.setShowGrid(false);
        communityTable.setIntercellSpacing(new Dimension(0, 1));

        JTableHeader header = communityTable.getTableHeader();
        header.setFont(new Font("Arial", Font.BOLD, 14));
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 36));

        JScrollPane scrollPane = new JScrollPane(communityTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70)));
        panel.add(scrollPane, BorderLayout.CENTER);

        // Info footer
        JLabel infoLabel = new JLabel("ℹ️ הרשימה מתעדכנת אוטומטית כאשר משתמשים חדשים מצטרפים לבוט.");
        infoLabel.setFont(new Font("Arial", Font.ITALIC, 12));
        infoLabel.setForeground(new Color(140, 140, 140));
        infoLabel.setBorder(new EmptyBorder(8, 0, 0, 0));
        panel.add(infoLabel, BorderLayout.SOUTH);

        return panel;
    }

    private void updateCommunityPanel() {
        SwingUtilities.invokeLater(() -> {
            communityTableModel.setRowCount(0);
            List<CommunityMember> members = pollManager.getCommunityMembers();
            for (CommunityMember member : members) {
                communityTableModel.addRow(new Object[]{
                        member.getFirstName(),
                        member.getUsername() != null ? "@" + member.getUsername() : "—",
                        member.getJoinedTime()
                });
            }
            totalCommunityMembersLabel.setText("  👥 סך חברי הקהילה: " + members.size());
        });
    }

    // ===== POLL CREATION PANEL =====

    private JPanel createPollCreationPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Title
        JLabel titleLabel = new JLabel("  ✏️ יצירת סקר חדש");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setForeground(ACCENT_COLOR);

        JPanel creationMethodPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        creationMethodPanel.setBorder(BorderFactory.createTitledBorder("שיטת יצירה"));
        ButtonGroup creationMethodGroup = new ButtonGroup();
        manualCreationRadio = new JRadioButton("✍️ יצירה ידנית");
        manualCreationRadio.setFont(new Font("Arial", Font.BOLD, 13));
        aiCreationRadio = new JRadioButton("🤖 יצירה עם ChatGPT");
        aiCreationRadio.setFont(new Font("Arial", Font.BOLD, 13));
        creationMethodGroup.add(manualCreationRadio);
        creationMethodGroup.add(aiCreationRadio);
        creationMethodPanel.add(manualCreationRadio);
        creationMethodPanel.add(aiCreationRadio);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(titleLabel, BorderLayout.NORTH);
        topPanel.add(creationMethodPanel, BorderLayout.CENTER);
        panel.add(topPanel, BorderLayout.NORTH);

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

        JButton addQuestionButton = new JButton("➕ הוסף שאלה");
        addQuestionButton.setFont(new Font("Arial", Font.BOLD, 13));
        addQuestionButton.addActionListener(e -> addQuestionInputPanel());
        panel.add(addQuestionButton);

        return panel;
    }

    private void addQuestionInputPanel() {
        if (questionInputPanels.size() >= 3) {
            JOptionPane.showMessageDialog(this, "⚠️ ניתן ליצור עד 3 שאלות בסקר.", "הגבלה", JOptionPane.WARNING_MESSAGE);
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
                JOptionPane.showMessageDialog(this, "⚠️ סקר חייב להכיל לפחות שאלה אחת.", "הגבלה", JOptionPane.WARNING_MESSAGE);
                return;
            }
            questionInputPanels.remove(panel);
            manualPollInputPanel.remove(panel);
            manualPollInputPanel.revalidate();
            manualPollInputPanel.repaint();
        } else if (aiQuestionInputPanels != null && aiQuestionInputPanels.contains(panel)) {
            if (aiQuestionInputPanels.size() <= 1) {
                JOptionPane.showMessageDialog(this, "⚠️ סקר חייב להכיל לפחות שאלה אחת.", "הגבלה", JOptionPane.WARNING_MESSAGE);
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

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        JLabel topicLabel = new JLabel("🎯 נושא הסקר:");
        topicLabel.setFont(new Font("Arial", Font.BOLD, 14));
        inputPanel.add(topicLabel);
        aiTopicField = new JTextField(25);
        aiTopicField.setFont(new Font("Arial", Font.PLAIN, 14));
        inputPanel.add(aiTopicField);
        JButton generateButton = new JButton("🤖 צור שאלות");
        generateButton.setFont(new Font("Arial", Font.BOLD, 13));
        generateButton.setBackground(ACCENT_COLOR);
        generateButton.setForeground(Color.WHITE);
        generateButton.addActionListener(this::generateAIPollQuestions);
        inputPanel.add(generateButton);
        panel.add(inputPanel, BorderLayout.NORTH);

        aiQuestionsContainerPanel = new JPanel();
        aiQuestionsContainerPanel.setLayout(new BoxLayout(aiQuestionsContainerPanel, BoxLayout.Y_AXIS));
        aiQuestionInputPanels = new ArrayList<>();

        JScrollPane scrollPane = new JScrollPane(aiQuestionsContainerPanel);
        scrollPane.setBorder(BorderFactory.createTitledBorder("📝 שאלות ותשובות שנוצרו ע\"י AI (ניתן לעריכה)"));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void generateAIPollQuestions(ActionEvent e) {
        String topic = aiTopicField.getText().trim();
        if (topic.isEmpty()) {
            JOptionPane.showMessageDialog(this, "⚠️ אנא הזן נושא ליצירת שאלות.", "חסר קלט", JOptionPane.WARNING_MESSAGE);
            return;
        }

        aiQuestionsContainerPanel.removeAll();
        aiQuestionInputPanels.clear();

        JPanel loadingPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JLabel loadingLabel = new JLabel("⏳ יוצר שאלות ותשובות עבור: '" + topic + "'... נא להמתין");
        loadingLabel.setFont(new Font("Arial", Font.ITALIC, 14));
        loadingPanel.add(loadingLabel);
        JProgressBar loadingBar = new JProgressBar();
        loadingBar.setIndeterminate(true);
        loadingBar.setPreferredSize(new Dimension(300, 20));
        loadingPanel.add(loadingBar);
        aiQuestionsContainerPanel.add(loadingPanel);
        aiQuestionsContainerPanel.revalidate();
        aiQuestionsContainerPanel.repaint();
        setStatusMessage("מייצר שאלות עם AI...");

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
                        JButton addQuestionButton = new JButton("➕ הוסף שאלה");
                        addQuestionButton.setFont(new Font("Arial", Font.BOLD, 13));
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
                            setStatusMessage("✅ " + lastGeneratedQuestions.size() + " שאלות נוצרו בהצלחה!");
                        } else {
                            JLabel noQuestionsLabel = new JLabel("⚠️ לא נוצרו שאלות. אנא נסה נושא אחר.");
                            noQuestionsLabel.setFont(new Font("Arial", Font.PLAIN, 14));
                            noQuestionsLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
                            aiQuestionsContainerPanel.add(noQuestionsLabel);
                            setStatusMessage("לא נוצרו שאלות. נסה נושא אחר.");
                        }
                        aiQuestionsContainerPanel.revalidate();
                        aiQuestionsContainerPanel.repaint();
                        manualPollInputPanel.revalidate();
                        manualPollInputPanel.repaint();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        aiQuestionsContainerPanel.removeAll();
                        JLabel errorLabel = new JLabel("❌ שגיאה ביצירת שאלות: " + ex.getMessage());
                        errorLabel.setFont(new Font("Arial", Font.PLAIN, 14));
                        errorLabel.setForeground(DANGER_COLOR);
                        errorLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
                        aiQuestionsContainerPanel.add(errorLabel);
                        aiQuestionsContainerPanel.revalidate();
                        aiQuestionsContainerPanel.repaint();
                        setStatusMessage("❌ שגיאה ביצירת שאלות.");
                        JOptionPane.showMessageDialog(MainFrame.this, "שגיאה ביצירת שאלות: " + ex.getMessage(), "שגיאת API", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }
        }.execute();
    }

    private JPanel createLaunchControlsPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JPanel sendOptionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        sendOptionsPanel.setBorder(BorderFactory.createTitledBorder("⏱️ אפשרויות שליחה"));
        ButtonGroup sendOptionsGroup = new ButtonGroup();
        immediateSendRadio = new JRadioButton("🚀 שליחה מיידית");
        immediateSendRadio.setFont(new Font("Arial", Font.BOLD, 13));
        delayedSendRadio = new JRadioButton("⏳ שליחה מושהית (דקות):");
        delayedSendRadio.setFont(new Font("Arial", Font.BOLD, 13));
        sendOptionsGroup.add(immediateSendRadio);
        sendOptionsGroup.add(delayedSendRadio);
        sendOptionsPanel.add(immediateSendRadio);
        sendOptionsPanel.add(delayedSendRadio);

        delayMinutesField = new JTextField("1", 3);
        delayMinutesField.setFont(new Font("Arial", Font.PLAIN, 14));
        delayMinutesField.setEnabled(false);
        delayCountdownLabel = new JLabel("");
        delayCountdownLabel.setFont(new Font("Arial", Font.BOLD, 14));
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

        JPanel actionButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        
        JButton launchPollButton = new JButton("🚀 שגר סקר");
        launchPollButton.setFont(new Font("Arial", Font.BOLD, 14));
        launchPollButton.setBackground(SUCCESS_COLOR);
        launchPollButton.setForeground(Color.WHITE);
        launchPollButton.setPreferredSize(new Dimension(150, 38));
        launchPollButton.addActionListener(this::launchPoll);
        actionButtonsPanel.add(launchPollButton);

        JButton resetPollButton = new JButton("🗑️ ביטול / מחיקה");
        resetPollButton.setFont(new Font("Arial", Font.BOLD, 14));
        resetPollButton.setBackground(DANGER_COLOR);
        resetPollButton.setForeground(Color.WHITE);
        resetPollButton.setPreferredSize(new Dimension(160, 38));
        resetPollButton.addActionListener(this::resetPoll);
        actionButtonsPanel.add(resetPollButton);
        
        bottomPanel.add(actionButtonsPanel, BorderLayout.SOUTH);

        return bottomPanel;
    }

    private void launchPoll(ActionEvent e) {
        if (!pollManager.canStartPoll()) {
            JOptionPane.showMessageDialog(this,
                    "⚠️ לא ניתן להתחיל סקר: נדרשים לפחות 3 חברים בקהילה.\n\nכרגע יש " + pollManager.getCommunityMembers().size() + " חברים.",
                    "שגיאת אימות", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (pollManager.isPollActive()) {
            JOptionPane.showMessageDialog(this,
                    "⚠️ כבר קיים סקר פעיל.\nיש להמתין לסיומו לפני התחלת סקר חדש.",
                    "שגיאת אימות", JOptionPane.WARNING_MESSAGE);
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
                throw new IllegalArgumentException("אין שאלות זמינות. אנא צור שאלות קודם.");
            }
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "❌ שגיאת אימות: " + ex.getMessage(), "שגיאה", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (immediateSendRadio.isSelected()) {
            startPollAndSend(questions);
        } else {
            try {
                int delayMinutes = Integer.parseInt(delayMinutesField.getText());
                if (delayMinutes <= 0) throw new NumberFormatException("ערך חייב להיות חיובי.");
                startDelayedPoll(questions, delayMinutes);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "❌ מספר דקות לא תקין: " + ex.getMessage(), "שגיאה", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void resetPoll(ActionEvent e) {
        int confirm = JOptionPane.showConfirmDialog(this,
                "⚠️ האם אתה בטוח שברצונך לאפס/למחוק את הסקר הנוכחי ולנקות את כל השדות?",
                "אישור איפוס", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
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
            JButton addQuestionButton = new JButton("➕ הוסף שאלה");
            addQuestionButton.setFont(new Font("Arial", Font.BOLD, 13));
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

            showActivePollContent(false);
            updateActivePollMonitoringPanel(); 
            
            setStatusMessage("✅ כל השדות אופסו. ניתן ליצור סקר חדש.");
            JOptionPane.showMessageDialog(this, "✅ כל השדות אופסו בהצלחה.", "איפוס הושלם", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void startDelayedPoll(List<PollQuestion> questions, int delayMinutes) {
        final long delayMillis = delayMinutes * 60 * 1000L;
        final long endTime = System.currentTimeMillis() + delayMillis;

        delayCountdownLabel.setForeground(WARNING_COLOR);
        delayedSendTimer = new Timer(1000, e -> {
            long remainingMillis = endTime - System.currentTimeMillis();

            if (remainingMillis <= 0) {
                ((Timer) e.getSource()).stop();
                SwingUtilities.invokeLater(() -> {
                    delayCountdownLabel.setText("✅ הסקר נשלח!");
                    delayCountdownLabel.setForeground(SUCCESS_COLOR);
                    startPollAndSend(questions);
                });
            } else {
                long seconds = remainingMillis / 1000;
                long minutes = seconds / 60;
                seconds %= 60;
                final String timeString = String.format("⏳ שליחה בעוד: %02d:%02d", minutes, seconds);
                SwingUtilities.invokeLater(() -> delayCountdownLabel.setText(timeString));
            }
        });
        delayedSendTimer.setInitialDelay(0);
        delayedSendTimer.start();
        setStatusMessage("סקר מתוזמן לשליחה בעוד " + delayMinutes + " דקות.");
        JOptionPane.showMessageDialog(this,
                "⏳ הסקר מתוזמן לשליחה בעוד " + delayMinutes + " דקות.\n\nניתן לעקוב אחר הספירה לאחור בממשק.",
                "סקר תוזמן", JOptionPane.INFORMATION_MESSAGE);
    }

    private void startPollAndSend(List<PollQuestion> questions) {
        try {
            pollManager.startPoll(questions);
            telegramBot.sendPollQuestions(pollManager.getActivePoll().getParticipants());
            setStatusMessage("✅ סקר נשלח ל-" + pollManager.getActivePoll().getParticipants().size() + " משתתפים!");
            JOptionPane.showMessageDialog(this,
                    "✅ הסקר נשלח בהצלחה!\n\n" +
                    "📊 " + questions.size() + " שאלות\n" +
                    "👥 " + pollManager.getActivePoll().getParticipants().size() + " משתתפים\n" +
                    "⏱️ זמן מרבי: 5 דקות",
                    "הסקר נשלח", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, "❌ שגיאה בשליחת הסקר: " + ex.getMessage(), "שגיאה", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ===== ACTIVE POLL MONITORING PANEL =====

    private JPanel createActivePollMonitoringPanel() {
        activePollWrapper = new JPanel(new CardLayout());

        // "No active poll" panel
        noPollPanel = new JPanel(new GridBagLayout());
        JPanel noPollInner = new JPanel();
        noPollInner.setLayout(new BoxLayout(noPollInner, BoxLayout.Y_AXIS));
        
        JLabel noPollIcon = new JLabel("📊");
        noPollIcon.setFont(new Font("Arial", Font.PLAIN, 60));
        noPollIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
        noPollInner.add(noPollIcon);
        noPollInner.add(Box.createVerticalStrut(15));
        
        JLabel noPollLabel = new JLabel("אין סקר פעיל כרגע");
        noPollLabel.setFont(new Font("Arial", Font.BOLD, 22));
        noPollLabel.setForeground(new Color(120, 120, 120));
        noPollLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        noPollInner.add(noPollLabel);
        noPollInner.add(Box.createVerticalStrut(10));
        
        JLabel noPollHint = new JLabel("צור סקר חדש מהטאב \"יצירת סקר\" כדי להתחיל.");
        noPollHint.setFont(new Font("Arial", Font.PLAIN, 14));
        noPollHint.setForeground(new Color(100, 100, 100));
        noPollHint.setAlignmentX(Component.CENTER_ALIGNMENT);
        noPollInner.add(noPollHint);
        
        noPollPanel.add(noPollInner);

        // Active poll content panel
        activePollContentPanel = new JPanel(new BorderLayout(10, 10));
        activePollContentPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Title
        JLabel titleLabel = new JLabel("  📊 מעקב סקר פעיל");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setForeground(ACCENT_COLOR);

        // Metrics panel
        JPanel metricsPanel = new JPanel(new GridLayout(2, 3, 15, 10));
        metricsPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("📈 נתוני סקר"),
                new EmptyBorder(10, 10, 10, 10)
        ));

        totalParticipantsLabel = createMetricLabel("👥 משתתפים: 0", ACCENT_COLOR);
        completedCountLabel = createMetricLabel("✅ השלימו: 0", SUCCESS_COLOR);
        pendingCountLabel = createMetricLabel("⏳ טרם השלימו: 0", WARNING_COLOR);
        timeRemainingLabel = createMetricLabel("⏱️ זמן שנותר: --:--", DANGER_COLOR);
        
        metricsPanel.add(totalParticipantsLabel);
        metricsPanel.add(completedCountLabel);
        metricsPanel.add(pendingCountLabel);
        metricsPanel.add(timeRemainingLabel);

        // Progress bar
        pollProgressBar = new JProgressBar(0, 100);
        pollProgressBar.setStringPainted(true);
        pollProgressBar.setString("0% השלימו");
        pollProgressBar.setFont(new Font("Arial", Font.BOLD, 13));
        pollProgressBar.setPreferredSize(new Dimension(pollProgressBar.getPreferredSize().width, 28));
        pollProgressBar.setForeground(SUCCESS_COLOR);

        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressPanel.add(new JLabel("  📊 התקדמות כללית:"), BorderLayout.WEST);
        progressPanel.add(pollProgressBar, BorderLayout.CENTER);
        progressPanel.setBorder(new EmptyBorder(5, 0, 5, 0));

        metricsPanel.add(progressPanel);

        JPanel topSection = new JPanel(new BorderLayout(5, 5));
        topSection.add(titleLabel, BorderLayout.NORTH);
        topSection.add(metricsPanel, BorderLayout.CENTER);
        activePollContentPanel.add(topSection, BorderLayout.NORTH);

        // Participants table
        String[] columnNames = {"שם", "התקדמות", "מצב"};
        participantsTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        participantsTable = new JTable(participantsTableModel);
        participantsTable.setFillsViewportHeight(true);
        participantsTable.setRowHeight(34);
        participantsTable.setFont(new Font("Arial", Font.PLAIN, 14));
        participantsTable.setShowGrid(false);
        participantsTable.setIntercellSpacing(new Dimension(0, 2));

        // Custom cell renderer for colored status rows
        participantsTable.setDefaultRenderer(Object.class, new StatusCellRenderer());
        
        JTableHeader pHeader = participantsTable.getTableHeader();
        pHeader.setFont(new Font("Arial", Font.BOLD, 14));
        pHeader.setPreferredSize(new Dimension(pHeader.getPreferredSize().width, 36));

        JScrollPane scrollPane = new JScrollPane(participantsTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70)));
        activePollContentPanel.add(scrollPane, BorderLayout.CENTER);

        activePollWrapper.add(noPollPanel, "noPoll");
        activePollWrapper.add(activePollContentPanel, "activePoll");

        showActivePollContent(false);

        return activePollWrapper;
    }

    private JLabel createMetricLabel(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Arial", Font.BOLD, 15));
        label.setForeground(color);
        return label;
    }

    private void showActivePollContent(boolean show) {
        CardLayout cl = (CardLayout) activePollWrapper.getLayout();
        cl.show(activePollWrapper, show ? "activePoll" : "noPoll");
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
                    // Time's up — send closed messages and end poll
                    telegramBot.sendPollClosedMessages(activePoll.getParticipants(), false);
                    pollManager.endPoll();
                    return;
                }

                // Send reminder after 3 minutes ELAPSED (not 3 minutes remaining)
                if (!reminderSent && elapsedSeconds >= 180) {
                    telegramBot.sendReminderMessages(activePoll.getParticipants());
                    reminderSent = true;
                }

                // Check if all completed — end early
                boolean allCompleted = activePoll.getParticipants().stream()
                        .allMatch(p -> p.getCompletionStatus().equals("השלים"));
                if (allCompleted) {
                    // Don't send reminders if poll ends early before 3 min
                    pollManager.endPoll();
                    // sendPollClosedMessages already called from handleCallbackQuery
                    return;
                }

                final long minutes = remainingSeconds / 60;
                final long seconds = remainingSeconds % 60;
                SwingUtilities.invokeLater(() -> {
                    timeRemainingLabel.setText(String.format("⏱️ זמן שנותר: %02d:%02d", minutes, seconds));
                    // Color the time label red when less than 1 minute
                    if (remainingSeconds < 60) {
                        timeRemainingLabel.setForeground(DANGER_COLOR);
                    }
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
        SwingUtilities.invokeLater(() -> timeRemainingLabel.setText("⏱️ זמן שנותר: --:--"));
    }

    private void updateActivePollMonitoringPanel() {
        SwingUtilities.invokeLater(() -> {
            Poll activePoll = pollManager.getActivePoll();
            if (activePoll == null) {
                totalParticipantsLabel.setText("👥 משתתפים: 0");
                completedCountLabel.setText("✅ השלימו: 0");
                pendingCountLabel.setText("⏳ טרם השלימו: 0");
                pollProgressBar.setValue(0);
                pollProgressBar.setString("0% השלימו");
                participantsTableModel.setRowCount(0);
                return;
            }

            List<PollParticipant> participants = activePoll.getParticipants();
            int total = participants.size();
            long completed = participants.stream().filter(p -> p.getCompletionStatus().equals("השלים")).count();
            long pending = total - completed;
            int progressPercent = total > 0 ? (int) ((completed * 100) / total) : 0;

            totalParticipantsLabel.setText("👥 משתתפים: " + total);
            completedCountLabel.setText("✅ השלימו: " + completed);
            pendingCountLabel.setText("⏳ טרם השלימו: " + pending);
            pollProgressBar.setValue(progressPercent);
            pollProgressBar.setString(progressPercent + "% השלימו (" + completed + "/" + total + ")");

            participantsTableModel.setRowCount(0);
            for (PollParticipant p : participants) {
                String progress = String.format("%d/%d", p.getAnsweredQuestionIndices().size(), activePoll.getQuestions().size());
                participantsTableModel.addRow(new Object[]{p.getMember().getFirstName(), progress, p.getCompletionStatus()});
            }
        });
    }

    // ===== RESULTS DIALOG =====

    private void showPollResultsDialog(Poll poll) {
        JDialog resultsDialog = new JDialog(this, "📊 תוצאות הסקר", true);
        resultsDialog.setSize(700, 550);
        resultsDialog.setLocationRelativeTo(this);
        resultsDialog.setLayout(new BorderLayout(10, 10));

        // Title
        JLabel titleLabel = new JLabel("  📊 תוצאות הסקר", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 22));
        titleLabel.setForeground(ACCENT_COLOR);
        titleLabel.setBorder(new EmptyBorder(15, 10, 5, 10));
        resultsDialog.add(titleLabel, BorderLayout.NORTH);

        // Results content
        JPanel resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.setBorder(new EmptyBorder(10, 20, 10, 20));

        // Time info
        JLabel timeLabel = new JLabel("🕐 שעת התחלה: " + poll.getStartTimestamp().atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        timeLabel.setFont(new Font("Arial", Font.PLAIN, 13));
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        resultsPanel.add(timeLabel);

        // Participation summary
        long totalParticipants = poll.getParticipants().size();
        long completedParticipants = poll.getParticipants().stream().filter(p -> p.getCompletionStatus().equals("השלים")).count();
        JLabel participationLabel = new JLabel(String.format("👥 משתתפים: %d  |  ✅ השלימו: %d  |  ⏳ לא השלימו: %d",
                totalParticipants, completedParticipants, totalParticipants - completedParticipants));
        participationLabel.setFont(new Font("Arial", Font.BOLD, 13));
        participationLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        participationLabel.setBorder(new EmptyBorder(5, 0, 15, 0));
        resultsPanel.add(participationLabel);

        // Per-question results
        Map<String, List<PollOption>> finalResults = poll.getFinalResults();
        int questionNum = 0;
        for (Map.Entry<String, List<PollOption>> entry : finalResults.entrySet()) {
            questionNum++;
            JPanel questionPanel = new JPanel();
            questionPanel.setLayout(new BoxLayout(questionPanel, BoxLayout.Y_AXIS));
            questionPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("❓ שאלה " + questionNum),
                    new EmptyBorder(8, 8, 8, 8)
            ));
            questionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel questionLabel = new JLabel(entry.getKey());
            questionLabel.setFont(new Font("Arial", Font.BOLD, 14));
            questionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            questionPanel.add(questionLabel);
            questionPanel.add(Box.createVerticalStrut(8));

            int totalVotesForQuestion = entry.getValue().stream().mapToInt(PollOption::getVoteCount).sum();

            for (PollOption option : entry.getValue()) {
                double percentage = (totalVotesForQuestion == 0) ? 0 : (double) option.getVoteCount() / totalVotesForQuestion * 100;

                JPanel optionRow = new JPanel(new BorderLayout(10, 0));
                optionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
                optionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

                JLabel optionLabel = new JLabel(String.format("%s (%d הצבעות, %.1f%%)", option.getOptionText(), option.getVoteCount(), percentage));
                optionLabel.setFont(new Font("Arial", Font.PLAIN, 13));
                optionRow.add(optionLabel, BorderLayout.NORTH);

                JProgressBar optionBar = new JProgressBar(0, 100);
                optionBar.setValue((int) percentage);
                optionBar.setStringPainted(true);
                optionBar.setString(String.format("%.1f%%", percentage));
                optionBar.setPreferredSize(new Dimension(optionBar.getPreferredSize().width, 22));
                
                // Color the bars: first one (highest) gets green, rest get blue
                if (entry.getValue().indexOf(option) == 0 && option.getVoteCount() > 0) {
                    optionBar.setForeground(SUCCESS_COLOR);
                } else {
                    optionBar.setForeground(ACCENT_COLOR);
                }
                optionRow.add(optionBar, BorderLayout.CENTER);

                questionPanel.add(optionRow);
                questionPanel.add(Box.createVerticalStrut(5));
            }

            resultsPanel.add(questionPanel);
            resultsPanel.add(Box.createVerticalStrut(10));
        }

        JScrollPane scrollPane = new JScrollPane(resultsPanel);
        scrollPane.setBorder(null);
        resultsDialog.add(scrollPane, BorderLayout.CENTER);

        JButton closeButton = new JButton("סגור");
        closeButton.setFont(new Font("Arial", Font.BOLD, 14));
        closeButton.setPreferredSize(new Dimension(120, 35));
        closeButton.addActionListener(e -> resultsDialog.dispose());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setBorder(new EmptyBorder(5, 10, 10, 10));
        buttonPanel.add(closeButton);
        resultsDialog.add(buttonPanel, BorderLayout.SOUTH);

        resultsDialog.setVisible(true);
    }

    // ===== STATUS CELL RENDERER =====

    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected && table.getModel().getColumnCount() > 2) {
                Object statusObj = table.getModel().getValueAt(row, 2);
                String status = statusObj != null ? statusObj.toString() : "";
                
                switch (status) {
                    case "השלים" -> {
                        c.setBackground(COMPLETED_BG);
                        c.setForeground(new Color(130, 210, 130));
                    }
                    case "בתהליך" -> {
                        c.setBackground(IN_PROGRESS_BG);
                        c.setForeground(new Color(220, 200, 100));
                    }
                    case "טרם ענה" -> {
                        c.setBackground(NOT_STARTED_BG);
                        c.setForeground(new Color(210, 130, 130));
                    }
                    default -> {
                        c.setBackground(table.getBackground());
                        c.setForeground(table.getForeground());
                    }
                }
            } else if (isSelected) {
                c.setBackground(table.getSelectionBackground());
                c.setForeground(table.getSelectionForeground());
            }

            ((JLabel) c).setBorder(new EmptyBorder(4, 8, 4, 8));
            setFont(new Font("Arial", Font.PLAIN, 14));
            return c;
        }
    }

    // ===== QUESTION INPUT PANEL =====

    private static class QuestionInputPanel extends JPanel {
        private final MainFrame parentFrame;
        private final JTextField questionField;
        private final JPanel optionsPanel;
        private final List<JTextField> optionFields;

        public QuestionInputPanel(MainFrame parentFrame) {
            this.parentFrame = parentFrame;
            setLayout(new BorderLayout(5, 5));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("❓ שאלה"),
                    new EmptyBorder(8, 8, 8, 8)
            ));

            JPanel questionHeader = new JPanel(new BorderLayout(5, 0));
            JLabel qLabel = new JLabel("  שאלה: ");
            qLabel.setFont(new Font("Arial", Font.BOLD, 13));
            questionHeader.add(qLabel, BorderLayout.WEST);
            questionField = new JTextField(30);
            questionField.setFont(new Font("Arial", Font.PLAIN, 14));
            questionHeader.add(questionField, BorderLayout.CENTER);
            JButton removeQuestionButton = new JButton("✕");
            removeQuestionButton.setFont(new Font("Arial", Font.BOLD, 14));
            removeQuestionButton.setForeground(DANGER_COLOR);
            removeQuestionButton.setToolTipText("הסר שאלה");
            removeQuestionButton.addActionListener(e -> parentFrame.removeQuestionInputPanel(this));
            questionHeader.add(removeQuestionButton, BorderLayout.EAST);
            add(questionHeader, BorderLayout.NORTH);

            optionsPanel = new JPanel();
            optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
            optionsPanel.setBorder(BorderFactory.createTitledBorder("📝 אפשרויות (2-4)"));
            optionFields = new ArrayList<>();

            JButton addOptionButton = new JButton("➕ הוסף אפשרות");
            addOptionButton.setFont(new Font("Arial", Font.PLAIN, 12));
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

            JButton addOptionButton = new JButton("➕ הוסף אפשרות");
            addOptionButton.setFont(new Font("Arial", Font.PLAIN, 12));
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
            optionField.setFont(new Font("Arial", Font.PLAIN, 13));
            optionFields.add(optionField);
            optionRow.add(optionField, BorderLayout.CENTER);

            JButton removeOptionButton = new JButton("—");
            removeOptionButton.setFont(new Font("Arial", Font.BOLD, 14));
            removeOptionButton.setForeground(DANGER_COLOR);
            removeOptionButton.setToolTipText("הסר אפשרות");
            removeOptionButton.addActionListener(e -> removeOptionField(optionRow, optionField));
            optionRow.add(removeOptionButton, BorderLayout.EAST);

            optionsPanel.add(optionRow);
        }

        private void addOptionField() {
            if (optionFields.size() >= 4) {
                JOptionPane.showMessageDialog(parentFrame, "⚠️ ניתן עד 4 אפשרויות לכל שאלה.", "הגבלה", JOptionPane.WARNING_MESSAGE);
                return;
            }
            JPanel optionRow = new JPanel(new BorderLayout(5, 5));
            JTextField optionField = new JTextField(25);
            optionField.setFont(new Font("Arial", Font.PLAIN, 13));
            optionFields.add(optionField);
            optionRow.add(optionField, BorderLayout.CENTER);

            JButton removeOptionButton = new JButton("—");
            removeOptionButton.setFont(new Font("Arial", Font.BOLD, 14));
            removeOptionButton.setForeground(DANGER_COLOR);
            removeOptionButton.setToolTipText("הסר אפשרות");
            removeOptionButton.addActionListener(e -> removeOptionField(optionRow, optionField));
            optionRow.add(removeOptionButton, BorderLayout.EAST);

            optionsPanel.add(optionRow);
            optionsPanel.revalidate();
            optionsPanel.repaint();
        }

        private void removeOptionField(JPanel optionRow, JTextField optionField) {
            if (optionFields.size() <= 2) {
                JOptionPane.showMessageDialog(parentFrame, "⚠️ נדרשות לפחות 2 אפשרויות לכל שאלה.", "הגבלה", JOptionPane.WARNING_MESSAGE);
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
                throw new IllegalArgumentException("שדה השאלה ריק. יש למלא את כל השאלות.");
            }
            List<String> optionTexts = optionFields.stream()
                    .map(JTextField::getText)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            if (optionTexts.size() < 2 || optionTexts.size() > 4) {
                throw new IllegalArgumentException("כל שאלה חייבת לכלול בין 2 ל-4 אפשרויות תשובה שאינן ריקות.");
            }
            List<PollOption> options = optionTexts.stream().map(PollOption::new).collect(Collectors.toList());
            return new PollQuestion(questionText, options);
        }
    }
}
