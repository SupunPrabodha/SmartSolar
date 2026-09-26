package com.smartsolar.mobile.data.remote.dto;

import java.util.List;
import java.util.Map;

public class ProblemDetailsResponse {
    private String type;
    private String title;
    private int status;
    private String detail;
    private String instance;
    private String traceId;
    private Map<String, List<String>> errors;

    public String getType() { return type; }
    public String getTitle() { return title; }
    public int getStatus() { return status; }
    public String getDetail() { return detail; }
    public String getInstance() { return instance; }
    public String getTraceId() { return traceId; }
    public Map<String, List<String>> getErrors() { return errors; }
}
