package org.example.models;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Poll {
    private final List<PollQuestion> questions; // 1 to 3 questions
    private final List<PollParticipant> participants;
    private final Instant startTimestamp;
    private boolean active;

    public Poll(List<PollQuestion> questions, List<PollParticipant> participants) {
        if (questions.isEmpty() || questions.size() > 3) {
            throw new IllegalArgumentException("A poll must have between 1 and 3 questions.");
        }
        this.questions = questions;
        this.participants = participants;
        this.startTimestamp = Instant.now();
        this.active = true;
    }

    public List<PollQuestion> getQuestions() {
        return questions;
    }

    public List<PollParticipant> getParticipants() {
        return participants;
    }

    public Instant getStartTimestamp() {
        return startTimestamp;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Calculates the final results of the poll, sorted by vote percentage in descending order.
     *
     * @return A map where the key is the question and the value is a sorted list of options by vote count.
     */
    public Map<String, List<PollOption>> getFinalResults() {
        return questions.stream()
                .collect(Collectors.toMap(
                        PollQuestion::getQuestionText,
                        question -> question.getOptions().stream()
                                .sorted(Comparator.comparingInt(PollOption::getVoteCount).reversed())
                                .collect(Collectors.toList())
                ));
    }

    @Override
    public String toString() {
        return "Poll{" +
               "questions=" + questions +
               ", participants=" + participants +
               ", startTimestamp=" + startTimestamp +
               ", active=" + active +
               '}';
    }
}
