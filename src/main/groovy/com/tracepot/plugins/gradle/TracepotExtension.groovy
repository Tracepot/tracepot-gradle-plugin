package com.tracepot.plugins.gradle

class TracepotExtension {

    private String   apiKey
    private String   groupId
    private String   apiEndpoint = "http://api.tracepot.com:1234"
    private String[] enabledFor

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

    String[] getEnabledFor() {
        return enabledFor
    }

    void setEnabledFor(String[] enabledFor) {
        this.enabledFor = enabledFor
    }
}
