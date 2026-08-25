package org.example.models;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class CommunityMember {
    private final long chatId;
    private final String firstName;
    private final String username;
    private final String joinedTime; // Formatted HH:mm

    public CommunityMember(long chatId, String firstName, String username) {
        this.chatId = chatId;
        this.firstName = firstName;
        this.username = username;
        this.joinedTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    public long getChatId() {
        return chatId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getUsername() {
        return username;
    }

    public String getJoinedTime() {
        return joinedTime;
    }

    @Override
    public String toString() {
        return "CommunityMember{" +
               "chatId=" + chatId +
               ", firstName='" + firstName + '\'' +
               ", username='" + username + '\'' +
               ", joinedTime='" + joinedTime + '\'' +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommunityMember that = (CommunityMember) o;
        return chatId == that.chatId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatId);
    }
}
