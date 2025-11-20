package io.empowerbits.sightflight.ApiResponse;

import io.empowerbits.sightflight.models.User;

import java.io.Serializable;

public class LoginResponse implements Serializable {
    private User user;
    private String token;

    public User getUser() { return user; }
    public String getToken() { return token; }
}
