package io.empowerbits.sightflight.ApiResponse;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class ErrorResponse implements Serializable {
    private String message;
    private Map<String, List<String>> errors;

    public String getMessage() { return message; }
    public Map<String, List<String>> getErrors() { return errors; }
}
