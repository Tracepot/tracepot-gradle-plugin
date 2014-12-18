package com.tracepot.plugins.gradle

import org.gradle.api.*;

class TracepotExtension {

    private String apiKey
    private String groupId
    private String apiEndpoint = "https://api.tracepot.com"

    TracepotExtension(Project project) {
    }

    String getApiKey() {
        return apiKey
    }

    void setApiKey(String apiKey) {
        this.apiKey = apiKey
    }

    String getGroupId() {
        return groupId
    }

    void setGroupId(String groupId) {
        this.groupId = groupId
    }

    String getApiEndpoint() {
        return apiEndpoint
    }

    void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint
    }
}
