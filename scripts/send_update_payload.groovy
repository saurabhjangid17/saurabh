#!/usr/bin/env groovy

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

// ========== Configuration ==========
def jiraUrl = "https://atcisaurabhdemo.atlassian.net"
def servicenowIncidentUrl = "https://webhook.site/4b6a8c55-a5db-4d1a-a351-7ddd90cc1dd7"
def servicenowRequestUrl = "https://webhook-test.com/38ed89e877bf373619617df76366f4c9"
def JIRA_AUTH = System.getenv("JIRA_AUTH")

// ========== Inputs ==========
def issueKeysInput = System.getenv("ISSUE_KEYS") // comma-separated issue keys
if (!issueKeysInput) {
    println "No issue keys provided!"
    System.exit(1)
}
def issueKeys = issueKeysInput.split(",")

// ========== Functions ==========

// HTTP GET helper
def getFromJira(String path) {
    def url = new URL("${jiraUrl}${path}")
    def connection = url.openConnection()
    connection.setRequestProperty("Authorization", JIRA_AUTH)
    connection.setRequestProperty("Accept", "application/json")
    connection.connect()
    return new JsonSlurper().parse(connection.inputStream)
}

// HTTP POST helper
def postToServiceNow(String urlStr, Map payload) {
    def url = new URL(urlStr)
    def connection = url.openConnection()
    connection.setRequestMethod("POST")
    connection.setDoOutput(true)
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("Accept", "application/json")
    connection.outputStream.withWriter { writer ->
        writer << JsonOutput.toJson(payload)
    }
    def responseCode = connection.responseCode
    println "POST to ${urlStr} - Response code: ${responseCode}"
    if (responseCode >= 400) {
        println "Error: " + connection.errorStream.text
    }
}

// Helper to calculate time difference
def minutesBetween(Date d1, Date d2) {
    return Math.abs(d1.time - d2.time) / (1000 * 60)
}

// ========== Main Logic ==========

issueKeys.each { issueKey ->
    try {
        println "Processing issue: ${issueKey}"
        def issueData = getFromJira("/rest/api/2/issue/${issueKey}?expand=changelog")
        
        def fields = [:]
        def now = new Date()
        def changelog = issueData?.changelog?.histories ?: []
        
        changelog.each { history ->
            def createdTime = history.created ? Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", history.created) : null
            if (createdTime && minutesBetween(now, createdTime) <= 50) {
                history.items.each { item ->
                    if (item.field == "summary") {
                        fields["summary"] = item.toString ?: ""
                    }
                    if (item.field == "description") {
                        fields["description"] = [
                            type: "doc",
                            version: 1,
                            content: [
                                [
                                    type: "paragraph",
                                    content: [
                                        [
                                            type: "text",
                                            text: item.toString ?: ""
                                        ]
                                    ]
                                ]
                            ]
                        ]
                    }
                }
            }
        }

        if (fields) {
            def payload = [
                event: "issue_updated",
                key: issueKey,
                fields: fields
            ]
            
            def issueType = issueData.fields?.issuetype?.name
            def targetUrl = (issueType in ["Service Request", "Service Request with Approval"]) ? servicenowRequestUrl : servicenowIncidentUrl

            println "Sending payload for ${issueKey} to ServiceNow..."
            println JsonOutput.prettyPrint(JsonOutput.toJson(payload))
            postToServiceNow(targetUrl, payload)
        } else {
            println "No recent changes within 50 minutes for issue ${issueKey}."
        }

    } catch (Exception e) {
        println "Error processing issue ${issueKey}: ${e.message}"
        e.printStackTrace()
    }
}
