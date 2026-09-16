package org.example.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.example.models.CommunityMember;
import org.example.models.Poll;
import org.example.models.PollQuestion;

import javax.swing.SwingUtilities;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PollManager {
    private static volatile PollManager instance;
    private final List<CommunityMember> communityMembers;
    private final AtomicReference<Poll> activePoll;
    private final List<Consumer<Poll>> pollStateListeners;
    private final List<CommunityListener> communityListeners;

    private static final String COMMUNITY_MEMBERS_FILE = "community_members.json";
    private final Gson gson;

    public interface CommunityListener {
        void onMemberAdded(CommunityMember member, int totalCount);
        void onCommunityLoaded(List<CommunityMember> members);
    }

    private PollManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.communityMembers = new CopyOnWriteArrayList<>();
        this.activePoll = new AtomicReference<>(null);
        this.pollStateListeners = new CopyOnWriteArrayList<>();
        this.communityListeners = new CopyOnWriteArrayList<>();
        loadCommunityMembers();
    }

    public static PollManager getInstance() {
        if (instance == null) {
            synchronized (PollManager.class) {
                if (instance == null) {
                    instance = new PollManager();
                }
            }
        }
        return instance;
    }

    private void loadCommunityMembers() {
        try (FileReader reader = new FileReader(COMMUNITY_MEMBERS_FILE)) {
            Type listType = new TypeToken<CopyOnWriteArrayList<CommunityMember>>() {}.getType();
            CopyOnWriteArrayList<CommunityMember> loadedMembers = gson.fromJson(reader, listType);
            if (loadedMembers != null) {
                this.communityMembers.addAll(loadedMembers);
                for (CommunityListener listener : communityListeners) {
                    SwingUtilities.invokeLater(() -> listener.onCommunityLoaded(Collections.unmodifiableList(communityMembers)));
                }
            }
        } catch (IOException e) {
            System.out.println("No existing community_members.json found. Starting with empty community.");
        } catch (Exception e) {
            System.err.println("Error loading community members: " + e.getMessage());
        }
    }

    private void saveCommunityMembers() {
        try (FileWriter writer = new FileWriter(COMMUNITY_MEMBERS_FILE)) {
            gson.toJson(communityMembers, writer);
        } catch (IOException e) {
            System.err.println("Error saving community members: " + e.getMessage());
        }
    }

    public boolean addCommunityMember(CommunityMember member) {
        if (communityMembers.stream().anyMatch(m -> m.getChatId() == member.getChatId())) {
            return false;
        }
        communityMembers.add(member);
        saveCommunityMembers();
        int newSize = communityMembers.size();
        for (CommunityListener listener : communityListeners) {
            SwingUtilities.invokeLater(() -> listener.onMemberAdded(member, newSize));
        }
        return true;
    }

    public List<CommunityMember> getCommunityMembers() {
        return new CopyOnWriteArrayList<>(communityMembers);
    }

    public boolean canStartPoll() {
        // Re-enforce the 3-member minimum requirement
        return communityMembers.size() >= 3;
    }

    public boolean isPollActive() {
        Poll poll = activePoll.get();
        return poll != null && poll.isActive();
    }

    public Poll getActivePoll() {
        return activePoll.get();
    }

    public void startPoll(List<PollQuestion> questions) {
        if (!canStartPoll()) {
            throw new IllegalStateException("Cannot start a poll with fewer than 3 community members.");
        }
        if (isPollActive()) {
            throw new IllegalStateException("Another poll is already active.");
        }
        Poll poll = new Poll(questions, communityMembers.stream()
                .map(org.example.models.PollParticipant::new)
                .collect(Collectors.toList()));
        activePoll.set(poll);
        notifyPollStateListeners(poll);
    }

    public void endPoll() {
        Poll poll = activePoll.getAndSet(null);
        if (poll != null) {
            poll.setActive(false);
            notifyPollStateListeners(poll);
        }
    }
    
    public synchronized void resetOrDeletePoll() {
        Poll poll = activePoll.getAndSet(null);
        if (poll != null) {
            poll.setActive(false);
            notifyPollStateListeners(null);
        }
    }

    public void addPollStateListener(Consumer<Poll> listener) {
        pollStateListeners.add(listener);
    }

    public void removePollStateListener(Consumer<Poll> listener) {
        pollStateListeners.remove(listener);
    }

    private void notifyPollStateListeners(Poll poll) {
        pollStateListeners.forEach(listener -> listener.accept(poll));
    }

    public void addCommunityListener(CommunityListener listener) {
        communityListeners.add(listener);
    }

    public void removeCommunityListener(CommunityListener listener) {
        communityListeners.remove(listener);
    }
}
