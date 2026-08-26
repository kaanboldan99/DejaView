package com.skaanb.DejaView.dto;

import java.util.List;

public class AIAnalysisResponse {
    private String description;
    private List<String> tags;
    private String solution;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getSolution() {
        return solution;
    }

    public void setSolution(String solution) {
        this.solution = solution;
    }
}