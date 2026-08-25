package org.example.models;

import java.util.List;

public class PollQuestion {
    private final String questionText;
    private final List<PollOption> options; // 2 to 4 options

    public PollQuestion(String questionText, List<PollOption> options) {
        if (options.size() < 2 || options.size() > 4) {
            throw new IllegalArgumentException("A poll question must have between 2 and 4 options.");
        }
        this.questionText = questionText;
        this.options = options;
    }

    public String getQuestionText() {
        return questionText;
    }

    public List<PollOption> getOptions() {
        return options;
    }

    @Override
    public String toString() {
        return "PollQuestion{" +
               "questionText='" + questionText + '\'' +
               ", options=" + options +
               '}';
    }
}
