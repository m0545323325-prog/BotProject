package org.example.models;

import java.util.HashSet;
import java.util.Set;

public class PollParticipant {
    private final CommunityMember member;
    private final Set<Integer> answeredQuestionIndices;
    private String completionStatus; // "השלים", "בתהליך", "טרם ענה"
    private boolean reminderSent;

    public PollParticipant(CommunityMember member) {
        this.member = member;
        this.answeredQuestionIndices = new HashSet<>();
        this.completionStatus = "טרם ענה";
        this.reminderSent = false;
    }

    public CommunityMember getMember() {
        return member;
    }

    public Set<Integer> getAnsweredQuestionIndices() {
        return answeredQuestionIndices;
    }

    public String getCompletionStatus() {
        return completionStatus;
    }

    public void setCompletionStatus(String completionStatus) {
        this.completionStatus = completionStatus;
    }

    public boolean isReminderSent() {
        return reminderSent;
    }

    public void setReminderSent(boolean reminderSent) {
        this.reminderSent = reminderSent;
    }

    public void addAnsweredQuestion(int questionIndex) {
        this.answeredQuestionIndices.add(questionIndex);
    }

    @Override
    public String toString() {
        return "PollParticipant{" +
               "member=" + member +
               ", answeredQuestionIndices=" + answeredQuestionIndices +
               ", completionStatus='" + completionStatus + '\'' +
               ", reminderSent=" + reminderSent +
               '}';
    }
}
