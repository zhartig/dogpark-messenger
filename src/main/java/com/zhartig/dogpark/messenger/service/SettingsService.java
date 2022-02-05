package com.zhartig.dogpark.messenger.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.zhartig.dogpark.messenger.dto.Group;
import com.zhartig.dogpark.messenger.dto.Patron;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
@Slf4j
public class SettingsService {
    private static final String USER_PATH = "users/patrons.csv";
    private static final String GROUP_PATH = "groups/groups.csv";
    private static final String SMS_PATH = "config/sms.txt";
    private static final String MMS_PATH = "config/mms.txt";

    private String LIST_MESSAGE_FILTER;
    private String MMS_MESSAGE_FILTER;

    @SneakyThrows
    public List<Patron> loadPatrons() {
        try (Reader reader = Files.newBufferedReader(Paths.get(USER_PATH))) {
            CsvToBean<Patron> csvToBean = new CsvToBeanBuilder<Patron>(reader)
                    .withType(Patron.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();

            Iterator<Patron> csvUserIterator = csvToBean.iterator();
            List<Patron> patrons = new ArrayList<>();
            while (csvUserIterator.hasNext()) {
                Patron csvUser = csvUserIterator.next();
                patrons.add(csvUser);
            }

            return patrons;
        }
    }

    @SneakyThrows
    public void savePatrons(List<Patron> patrons) {
        try (Writer writer = Files.newBufferedWriter(Paths.get(USER_PATH))) {
            StatefulBeanToCsv<Patron> beanToCsv = new StatefulBeanToCsvBuilder<Patron>(writer)
                    .build();

            beanToCsv.write(patrons);
        }
    }

    @SneakyThrows
    public List<Group> loadGroups() {
        try (Reader reader = Files.newBufferedReader(Paths.get(GROUP_PATH))) {
            CsvToBean<Group> csvToBean = new CsvToBeanBuilder<Group>(reader)
                    .withType(Group.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();

            Iterator<Group> csvUserIterator = csvToBean.iterator();
            List<Group> patrons = new ArrayList<>();
            while (csvUserIterator.hasNext()) {
                Group csvUser = csvUserIterator.next();
                patrons.add(csvUser);
            }

            return patrons;
        }
    }

    @SneakyThrows
    public void saveGroups(List<Group> groups) {
        try (Writer writer = Files.newBufferedWriter(Paths.get(GROUP_PATH))) {
            StatefulBeanToCsv<Group> beanToCsv = new StatefulBeanToCsvBuilder<Group>(writer)
                    .build();

            beanToCsv.write(groups);
        }
    }

    @SneakyThrows
    public void loadFilters() {
        List<String> smsHosts = Files.readAllLines(Paths.get(SMS_PATH));
        List<String> mmsHosts = Files.readAllLines(Paths.get(MMS_PATH));
        LIST_MESSAGE_FILTER = String.format("is:unread AND (from:%s)", String.join(" OR from:", smsHosts));
        log.info("plain text filter: " + LIST_MESSAGE_FILTER);
        MMS_MESSAGE_FILTER = String.format("is:unread AND (from:%s", String.join(" OR from:", mmsHosts));
        log.info("attachment filter: " + MMS_MESSAGE_FILTER);
    }

    @SneakyThrows
    public void addMmsHost(String host) {
        List<String> smsHosts = Files.readAllLines(Paths.get(SMS_PATH));
        List<String> mmsHosts = Files.readAllLines(Paths.get(MMS_PATH));
        smsHosts.remove(host);
        mmsHosts.remove(host);
        mmsHosts.add(host);
        LIST_MESSAGE_FILTER = String.format("is:unread AND (from:%s)", String.join(" OR from:", smsHosts));
        log.info("plain text filter: " + LIST_MESSAGE_FILTER);
        MMS_MESSAGE_FILTER = String.format("is:unread AND (from:%s", String.join(" OR from:", mmsHosts));
        log.info("attachment filter: " + MMS_MESSAGE_FILTER);
        Files.write(Paths.get(SMS_PATH), smsHosts);
        Files.write(Paths.get(MMS_PATH), mmsHosts);
    }

    @SneakyThrows
    public void addSmsHost(String host) {
        List<String> smsHosts = Files.readAllLines(Paths.get(SMS_PATH));
        List<String> mmsHosts = Files.readAllLines(Paths.get(MMS_PATH));
        smsHosts.remove(host);
        smsHosts.add(host);
        mmsHosts.remove(host);
        LIST_MESSAGE_FILTER = String.format("is:unread AND (from:%s)", String.join(" OR from:", smsHosts));
        log.info("plain text filter: " + LIST_MESSAGE_FILTER);
        MMS_MESSAGE_FILTER = String.format("is:unread AND (from:%s", String.join(" OR from:", mmsHosts));
        log.info("attachment filter: " + MMS_MESSAGE_FILTER);
        Files.write(Paths.get(SMS_PATH), smsHosts);
        Files.write(Paths.get(MMS_PATH), mmsHosts);
    }

    public String getSmsFilter() {
        return this.LIST_MESSAGE_FILTER;
    }

    public String getMmsFilter() {
        return this.MMS_MESSAGE_FILTER;
    }
}
