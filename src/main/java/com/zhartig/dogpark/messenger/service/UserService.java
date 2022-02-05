package com.zhartig.dogpark.messenger.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class UserService {

    //TODO load this config from file storage
    private List<String> adminUsers = new ArrayList<>();

    public UserService() {
    }

    public boolean isAdmin(String user) {
        return adminUsers.contains(user);
    }
}
