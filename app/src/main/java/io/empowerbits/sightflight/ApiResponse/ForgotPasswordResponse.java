package io.empowerbits.sightflight.ApiResponse;

import java.io.Serializable;

public class ForgotPasswordResponse implements Serializable {
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
