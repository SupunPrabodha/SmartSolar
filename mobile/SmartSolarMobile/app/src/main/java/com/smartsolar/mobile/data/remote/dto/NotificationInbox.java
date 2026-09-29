package com.smartsolar.mobile.data.remote.dto;
import java.util.ArrayList;
import java.util.List;
public final class NotificationInbox {
    public int unreadCount;
    public List<Item> items = new ArrayList<>();
    public static final class Item {
        public String id, atUtc, readAtUtc, category, priority, message, action, resourceId;
    }
}
