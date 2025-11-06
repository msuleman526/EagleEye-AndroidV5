package com.empowerbits.dronifyit.ApiResponse;

import com.empowerbits.dronifyit.models.User;

import java.io.Serializable;

public class LoginResponse implements Serializable {
    private com.empowerbits.dronifyit.models.User user;
    private String token;

    public User getUser() { return user; }
    public String getToken() { return token; }
}
