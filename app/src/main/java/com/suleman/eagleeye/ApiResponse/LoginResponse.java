package com.suleman.eagleeye.ApiResponse;

import com.suleman.eagleeye.models.User;

import java.io.Serializable;

public class LoginResponse implements Serializable {
    private com.suleman.eagleeye.models.User user;
    private String token;

    public User getUser() { return user; }
    public String getToken() { return token; }
}
