package bot.example.telegrambotbeta.model;

import java.util.HashMap;
import java.util.Map;

public class EventDTO {
    private String day;
    private String time;
    private String location;

    private double amount;
    private String eventKey;

    private Map<Long, User> participants = new HashMap<>();

    public Map<Long, User> getParticipants() {
        return participants;
    }

    public void addParticipant(User user) {
        participants.put(user.getChatId(), user);
    }

    public void removeParticipant(Long chatId) {
        participants.remove(chatId);
    }

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
