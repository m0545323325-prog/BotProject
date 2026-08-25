package org.example.models;

import java.util.concurrent.atomic.AtomicInteger;

public class PollOption {
    private final String optionText;
    private final AtomicInteger voteCount;

    public PollOption(String optionText) {
        this.optionText = optionText;
        this.voteCount = new AtomicInteger(0);
    }

    public String getOptionText() {
        return optionText;
    }

    public int getVoteCount() {
        return voteCount.get();
    }

    public void incrementVoteCount() {
        this.voteCount.incrementAndGet();
    }

    @Override
    public String toString() {
        return "PollOption{" +
               "optionText='" + optionText + '\'' +
               ", voteCount=" + voteCount +
               '}';
    }
}
