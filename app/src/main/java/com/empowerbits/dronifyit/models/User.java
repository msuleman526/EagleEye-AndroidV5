package com.empowerbits.dronifyit.models;

import java.io.Serializable;

public class User implements Serializable {
    public int id;
    public String first_name;
    public String last_name;
    public String email;
    private String full_name;
    public String role;
    public String phone;
    public boolean verified;
    public boolean su;
    public boolean status;
    public String country;
    public int organization_id;
    public String created_at;
    public Organization organization;
}
