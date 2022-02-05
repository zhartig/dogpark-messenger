package com.zhartig.dogpark.messenger;

import com.google.api.client.util.Base64;
import com.google.api.services.gmail.model.*;
import com.google.api.services.gmail.model.Message;
import com.zhartig.dogpark.messenger.dto.Group;
import com.zhartig.dogpark.messenger.dto.Patron;
import com.zhartig.dogpark.messenger.service.GmailService;
import com.zhartig.dogpark.messenger.service.SettingsService;
import com.zhartig.dogpark.messenger.service.UserService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MailService {

    @Autowired
    private GmailService gmailService;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private UserService userService;

    //private static final Map<String, String> PROVIDER_TO_DOMAIN_MAP = new HashMap<String, String>();
    private List<Patron> patrons = new ArrayList<>();
    private List<Group> groups = new ArrayList<>();
    private final Map<String, Patron> registering = new HashMap<>();

    private static final String GROUP_REGEX = "zhartig.bot.messenger(\\+(.+))?@gmail.com";
    private static final Pattern GROUP_PATTERN = Pattern.compile(GROUP_REGEX);

    public MailService() {
        Patron inProgress = new Patron();
        inProgress.setPhoneNumber("4106151707@vtext.com");
        Patron inProgress2 = new Patron();
        inProgress2.setPhoneNumber("3019190240@mms.att.net");
        registering.put("4106151707@vtext.com", inProgress);
        registering.put("3019190240@mms.att.net", inProgress2);

    }

    @PostConstruct
    public void postContruct() {
        this.patrons = this.settingsService.loadPatrons();
        settingsService.loadFilters();
        this.groups = this.settingsService.loadGroups();
    }

    @Scheduled(fixedDelay = 5000)
    public void receiveMessage() throws IOException {
        ListMessagesResponse mms = this.gmailService.getMessages(settingsService.getMmsFilter());

        if(mms != null && mms.getMessages() != null) {
            for(Message message: mms.getMessages()) {
                log.info("new available message: " + message.toPrettyString());
                Message fullMessage = this.gmailService.getMessage(message.getId());
                String to = fullMessage.getPayload().getHeaders().stream().filter(h -> h.getName().equalsIgnoreCase("to")).map(MessagePartHeader::getValue).findAny().orElse("zhartig.bot.messenger@gmail.com");
                //Group group = groups.stream()
                //        .filter(g -> GROUP_PATTERN.matcher(to).groupCount() > 2)
                //        .filter(g -> g.getName().equalsIgnoreCase(GROUP_PATTERN.matcher(to).group(2)))
                //        .findAny().orElse(null);
                String from = fullMessage.getPayload().getHeaders().stream().filter(h -> h.getName().equalsIgnoreCase("from")).map(MessagePartHeader::getValue).findAny().orElse("unknown");
                String messageString = extractMessage(from, message);
                sendMessage(from, null, messageString);
                gmailService.removeUnreadLabel(message.getId());
            }
        }
    }

    private String extractMessage(String from, Message message) {
        Message fullMessage = this.gmailService.getMessage(message.getId());
        String messageString;
        if(fullMessage.getPayload().getBody().getSize() > 0) {
            //the data is in the message
            byte[] messageBytes = fullMessage.getPayload().getBody().decodeData();
            messageString = new String(messageBytes).trim();
            log.info("message detail: from={}, body={}",
                    from, messageString);
        } else if(fullMessage.getPayload().getParts().size() > 2
                && "text/html".equals(fullMessage.getPayload().getParts().get(0).getMimeType())
                && "text/plain".equals(fullMessage.getPayload().getParts().get(1).getMimeType())
                && fullMessage.getPayload().getParts().get(1).getBody().getAttachmentId() != null) {
            log.info("Tmobile style message 1 part is html, 1 part is text doc");
            MessagePartBody attachment = this.gmailService.getAttachment(message.getId(), fullMessage.getPayload().getParts().get(1).getBody().getAttachmentId());//this.gmail.users().messages().attachments().get("zhartig.bot.messenger@gmail.com", message.getId(), fullMessage.getPayload().getParts().get(0).getBody().getAttachmentId()).execute();
            messageString = new String(attachment.decodeData()).trim();
        } else if(fullMessage.getPayload().getParts().get(0).getBody().getAttachmentId() == null) {
            //attachment id is null, the body is the decode data on this
            byte[] messageBytes = fullMessage.getPayload().getParts().get(0).getBody().decodeData();
            messageString = new String(messageBytes).trim();
            log.info("message detail: from={}, body={}",
                    from, messageString);
        } else {
            log.info("message detail (from attachemnt): from={}, attachment={}",
                    from,
                    fullMessage.getPayload().getParts().get(0).getBody().getAttachmentId());
            MessagePartBody attachment = this.gmailService.getAttachment(message.getId(), fullMessage.getPayload().getParts().get(0).getBody().getAttachmentId());//this.gmail.users().messages().attachments().get("zhartig.bot.messenger@gmail.com", message.getId(), fullMessage.getPayload().getParts().get(0).getBody().getAttachmentId()).execute();
            messageString = new String(attachment.decodeData()).trim();
        }
        log.info("Parsed message: {}", messageString);
        return messageString;
    }

    private void register(String from, String message) {
        if(registering.containsKey(from)) {
            // this is not the first message
            Patron currRegisterUser = registering.get(from);
            if(currRegisterUser.getOwnerName() == null) {
                currRegisterUser.setOwnerName(message.trim());

                invokeGmailSend(from, "To finish registration, respond with your dog's name");
            } else {
                currRegisterUser.setDogName(message.trim());
                patrons.add(currRegisterUser);
                settingsService.savePatrons(patrons);
                invokeGmailSend(from, "Thanks for completing registration, you will now receive messages from other patrons. Use !list for a list of commands and !help for how to use them");

                Patron tmp  = new Patron();
                tmp.setOwnerName("System");
                tmp.setDogName("-");
                publishToAll(tmp, String.format("Please welcome %s and %s to the group", currRegisterUser.getOwnerName(), currRegisterUser.getDogName()));

                registering.remove(from);
            }
        } else {
            Patron newRegister = new Patron();
            newRegister.setPhoneNumber(from);
            registering.put(from, newRegister);

            invokeGmailSend(from, "Hi, welcome to the unofficial Barc Park message service. Message and data rates may apply. (1/2)");
            invokeGmailSend(from, "To register your number, please respond to this with your name (2/2)");
        }

    }

    private void sendMessage(String from, Group group, String message) {
        Optional<Patron> source = this.patrons.stream().filter(p -> p.getPhoneNumber().equalsIgnoreCase(from)).findAny();
        if(!source.isPresent()) {
            log.info("Received message that is not associated with a registered phone number, passing to registeration");
            register(from, message);
            return;
        }
        if(message.startsWith("!")) {
            processCommand(from, group, message);
            return;
        }
        if(group == null) {
            log.info("Group is null, publishing to all");
            publishToAll(source.get(), message);
        } else {
            publishToGroup(source.get(), group, message);
        }
    }

    private void processCommand(String from, Group group, String message) {
        if(message.startsWith("!help")) {
            if(message.split(" ").length > 1) {
                //there is an argument
                switch (message.split(" ")[1]) {
                    case "!list":
                    case "list":
                    case "!commands":
                    case "commands":
                        invokeGmailSend(from, "Lists all available commands");
                        break;
                    case "!mms":
                    case "mms":
                    case "!sms":
                    case "sms":
                        invokeGmailSend(from, "Lists all available commands");
                        break;
                    case "!leave":
                    case "leave":
                    case "!bye":
                    case "bye":
                    case "!unsubscribe":
                    case "unsubscribe":
                        invokeGmailSend(from, "Removes you from the group chat");
                        invokeGmailSend(from, "Usage: !leave");
                        break;
                    case "!name":
                    case "name":
                    case "!changename":
                    case "changename":
                    case "!setname":
                    case "setname":
                        invokeGmailSend(from, "Changes your name that is displayed when you send messages");
                        invokeGmailSend(from, "Usage: !name <your name>");
                        break;
                    case "!dog":
                    case "dog":
                    case "!changedog":
                    case "changedog":
                    case "!setdog":
                    case "setdog":
                        invokeGmailSend(from, "Change your dog's name that is displayed to the group");
                        invokeGmailSend(from, "Usage: !dog <dog's name>");
                        break;
                    case "!block":
                    case "block":
                    case "!blacklist":
                    case "blacklist":
                        invokeGmailSend(from, "Blocks a user so that you no longer send or receive messages from them");
                        invokeGmailSend(from, "Usage: !block <owner's name> <dog's name>");
                        break;
                    case "!unblock":
                    case "unblock":
                    case "!whitelist":
                    case "whitelist":
                        invokeGmailSend(from, "Unblocks a user so that you can now send or receive messages from them");
                        invokeGmailSend(from, "Usage: !unblock <owner's name> <dog's name>");
                        break;
                }
            } else {
                invokeGmailSend(from, "Use \"!help <command name>\" to describe how to use a command. Use \"!list\" to list available commands");
            }
        } else if(message.startsWith("!list") || message.startsWith("!commands")) {
            invokeGmailSend(from, "The available commands are !help, !list, !leave, !name, !dog, !block, !unblock, !users");
        } else if(message.startsWith("!mms")) {
            if(userService.isAdmin(from)) {
                settingsService.addMmsHost(message.split(" ")[1]);
            }
        } else if(message.startsWith("!sms")){
            if(userService.isAdmin(from)) {
                settingsService.addSmsHost(message.split(" ")[1]);
            }
        } else if(message.startsWith("!broadcast") || message.startsWith("!announcement")) {
            if(userService.isAdmin(from)) {
                Patron tmp = new Patron();
                tmp.setOwnerName("System");
                tmp.setDogName("-");
                publishToAll(tmp, message.split(" ", 2)[1]);
            }
        } else if(message.startsWith("!leave") || message.startsWith("!bye") || message.startsWith("!unsubscribe")) {
            patrons = patrons.stream().filter(p -> !p.getPhoneNumber().equals(from)).collect(Collectors.toList());
            settingsService.savePatrons(patrons);
            invokeGmailSend(from, "You have been removed from the message group");
        } else if (message.startsWith("!name") || message.startsWith("!changename") || message.startsWith("!setname")) {
            Patron curr = patrons.stream().filter(p -> p.getPhoneNumber().equalsIgnoreCase(from)).findAny().get();
            String[] messageSplit = message.split(" ", 2);
            curr.setOwnerName(messageSplit[1]);
            settingsService.savePatrons(patrons);
            invokeGmailSend(from, "Your name has been updated to " + messageSplit[1]);
        } else if (message.startsWith("!dog") || message.startsWith("!changedog") || message.startsWith("!setdog")) {
            Patron curr = patrons.stream().filter(p -> p.getPhoneNumber().equalsIgnoreCase(from)).findAny().get();
            String[] messageSplit = message.split(" ", 2);
            curr.setDogName(messageSplit[1]);
            settingsService.savePatrons(patrons);
            invokeGmailSend(from, "Your dog's has been updated to " + messageSplit[1]);
        } else if (message.startsWith("!block") || message.startsWith("!blacklist")) {
            Patron curr = patrons.stream().filter(p -> p.getPhoneNumber().equalsIgnoreCase(from)).findAny().get();
            Optional<Patron> toBlock = patrons.stream().filter(p -> p.getOwnerName().equalsIgnoreCase(message.split(" ")[1]) && p.getDogName().equalsIgnoreCase(message.split(" ")[2])).findAny();
            if(toBlock.isPresent()) {
                if(curr.getBlockedList() == null) {
                    curr.setBlockedList(new ArrayList<>());
                }
                curr.getBlockedList().add(toBlock.get().getPhoneNumber());
                settingsService.savePatrons(patrons);
                invokeGmailSend(from, String.format("You will no longer send or receive messages from %s(%s)", toBlock.get().getOwnerName(), toBlock.get().getDogName()));
            } else {
                invokeGmailSend(from, String.format("Could not find a user with name \"%s\" and dog \"%s\"", message.split(" ")[1], message.split(" ")[2]));
            }
        } else if(message.startsWith("!unblock") || message.startsWith("!whitelist")) {
            Patron curr = patrons.stream().filter(p -> p.getPhoneNumber().equalsIgnoreCase(from)).findAny().get();
            Optional<Patron> toUnblock = patrons.stream().filter(p -> p.getOwnerName().equalsIgnoreCase(message.split(" ")[1]) && p.getDogName().equalsIgnoreCase(message.split(" ")[2])).findAny();
            if(toUnblock.isPresent()) {
                if(curr.getBlockedList() == null) {
                    curr.setBlockedList(new ArrayList<>());
                }
                curr.getBlockedList().remove(toUnblock.get().getPhoneNumber());
                settingsService.savePatrons(patrons);
                invokeGmailSend(from, String.format("You will send or receive messages from %s(%s) again", toUnblock.get().getOwnerName(), toUnblock.get().getDogName()));
            } else {
                invokeGmailSend(from, String.format("Could not find a user with name \"%s\" and dog \"%s\"", message.split(" ")[1], message.split(" ")[2]));
            }
        } else if(message.startsWith("!users") || message.startsWith("!members")) {
            invokeGmailSend(from, patrons.stream().map(Patron::getOwnerName).collect(Collectors.joining(", ")));
        } else if(message.startsWith("!group")) {
            String groupName = message.split(" ")[1];
            if(groups.stream().anyMatch(g -> g.getName().equalsIgnoreCase(groupName))) {
                //there exists a group of this name already
                invokeGmailSend(from, String.format("Group name %s is already in use", groupName));
            } else {
                Group newGroup = new Group();
                List<String> member = new ArrayList<>();
                member.add(from);
                newGroup.setMembers(member);
                newGroup.setName(groupName);
                newGroup.setOwner(from);

                groups.add(newGroup);
                settingsService.saveGroups(groups);
                invokeGmailSend(from, groupName, "Group created, use !invite <person name> <dog name> to add people to this group");
            }
        } else if(message.startsWith("!invite")) {
            if(group != null) {
                //valid add
                if(group.getOwner().equalsIgnoreCase(from)) {
                    //it is the owner
                    Optional<Patron> toInvite = patrons.stream().filter(p -> p.getOwnerName().equalsIgnoreCase(message.split(" ")[1]) && p.getDogName().equalsIgnoreCase(message.split(" ")[2])).findAny();
                    if(toInvite.isPresent()) {
                        group.getMembers().add(toInvite.get().getPhoneNumber());
                    } else {
                        invokeGmailSend(from, String.format("Could not find a user with name \"%s\" and dog \"%s\" to invite", message.split(" ")[1], message.split(" ")[2]));
                    }
                } else {
                    invokeGmailSend(from, "Only the owner of this group can invite new members");
                }
            } else {
                invokeGmailSend(from, "This is the general chat, the invite command is only available in group chat");
            }
        }
    }

    private void publishToAll(Patron from, String body) {
        String formattedMessage = String.format("%s(%s): %s", from.getOwnerName(), from.getDogName(), body);
        this.patrons.stream().filter(p -> !p.getPhoneNumber().equalsIgnoreCase(from.getPhoneNumber()))
                //dont send to people you have blocked
                .filter(p -> (from.getBlockedList() == null || !from.getBlockedList().contains(p.getPhoneNumber())
                        //don't send to people who have blocked you
                        && (p.getBlockedList() == null || !p.getBlockedList().contains(from.getPhoneNumber()))))
                .forEach((patron -> invokeGmailSend(patron.getPhoneNumber(), formattedMessage)));
    }

    private void publishToGroup(Patron from, Group to, String body) {
        String formattedMessage = String.format("%s(%s): %s", from.getOwnerName(), from.getDogName(), body);
        to.getMembers().stream().filter(p -> StringUtils.isNotBlank(p)).forEach(p -> invokeGmailSend(p, to.getName(), formattedMessage));
    }

    @SneakyThrows
    private void invokeGmailSend(String to, String group, String body) {
        MimeMessage mimeMessage = createEmail(to, group, body);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        mimeMessage.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
        log.info(encodedEmail);
        Message gmailMessage = new Message();
        gmailMessage.setRaw(encodedEmail);

        Message sentMessage = this.gmailService.sendMessage(gmailMessage);//this.gmail.users().messages().send("zhartig.bot.messenger@gmail.com", gmailMessage).execute();
        log.info("Sent message {}", sentMessage.getId());
    }

    @SneakyThrows
    private void invokeGmailSend(String to, String body) {
        MimeMessage mimeMessage = createEmail(to, null, body);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        mimeMessage.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
        Message gmailMessage = new Message();
        gmailMessage.setRaw(encodedEmail);

        Message sentMessage = this.gmailService.sendMessage(gmailMessage);//this.gmail.users().messages().send("zhartig.bot.messenger@gmail.com", gmailMessage).execute();
        log.info("Sent message {}", sentMessage.getId());
    }

    /**
     * Create a MimeMessage using the parameters provided.
     *
     * @param to Email address of the receiver.
     * @param bodyText Body text of the email.
     * @return MimeMessage to be used to send email.
     */
    @SneakyThrows
    private static MimeMessage createEmail(String to, String from, String bodyText) {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);

        String fromAddr = from == null ? "zhartig.bot.messenger@gmail.com" : String.format("zhartig.bot.messenger+%s@gmail.com", from);
        email.setFrom(new InternetAddress(fromAddr, false));
        email.addRecipient(javax.mail.Message.RecipientType.TO,
                new InternetAddress(to));

        MimeBodyPart mimeBodyPart = new MimeBodyPart();
        mimeBodyPart.setContent(bodyText, "text/plain");

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(mimeBodyPart);
        email.setContent(multipart);

        return email;
    }
}
