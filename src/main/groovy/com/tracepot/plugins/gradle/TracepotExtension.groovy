package com.tracepot.plugins.gradle

class TracepotExtension {

    private String   apiGroupKey
    private String   apiEndpoint = "https://api.tracepot.com"
    private String[] enabledFor

    String getApiGroupKey() {
        return apiGroupKey
    }

    void setApiGroupKey(String apiGroupKey) {
        this.apiGroupKey = apiGroupKey
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
