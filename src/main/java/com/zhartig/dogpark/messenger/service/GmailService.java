package com.zhartig.dogpark.messenger.service;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@Slf4j
public class GmailService {
    @Autowired
    private Gmail gmail;

    private static final String GMAIL_USER = "zhartig.bot.messenger@gmail.com";

    @SneakyThrows
    public ListMessagesResponse getMessages(String filter) {
        return this.gmail.users().messages().list(GMAIL_USER).setQ(filter).execute();
    }

    @SneakyThrows
    public Message getMessage(String id) {
        return this.gmail.users().messages().get(GMAIL_USER, id).execute();
    }

    @SneakyThrows
    public MessagePartBody getAttachment(String messageId, String attachmentId) {
        return this.gmail.users().messages().attachments().get(GMAIL_USER, messageId, attachmentId).execute();
    }

    @SneakyThrows
    public Message sendMessage(Message message) {
        return this.gmail.users().messages().send(GMAIL_USER, message).execute();
    }

    @SneakyThrows
    public void removeUnreadLabel(String id) {
        ModifyMessageRequest removeUnread = new ModifyMessageRequest();
        removeUnread.setRemoveLabelIds(Collections.singletonList("UNREAD"));
        this.gmail.users().messages().modify(GMAIL_USER, id, removeUnread).execute();
        log.debug("Marked {} as unread", id);
    }
}
