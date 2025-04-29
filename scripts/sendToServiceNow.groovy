#!/usr/bin/env groovy

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.text.SimpleDateFormat

// Define global constants
def jiraUrl = "https://atcisaurabhdemo.atlassian.net"
def servicenowIncidentUrl = "https://webhook.site/4b6a8c55-a5db-4d1a-a351-7ddd90cc1dd7"
def servicenowRequestUrl = "https://webhook-test.com/4c334bbf5265c44d4e66049c1497144f"

def issueData = System.getenv("ISSUE_DATA")
def jiraAuth = System.getenv("JIRA_AUTH")

println "Starting Groovy script execution"
def issues = new JsonSlurper().parseText(issueData)

issues.each { issue ->
    def issueKey = issue.key
    def issueDetails = fetchIssue(issueKey, jiraAuth, jiraUrl)
    def recentComments = getRecentComments(issueDetails.fields.comment.comments, issueKey, jiraAuth, jiraUrl)
    def recentAttachments = getRecentAttachments(issueDetails.fields.attachment)

    def payload = [
        event : recentComments.comments ? "comment_created" : "issue_created",
        key   : issueKey,
        fields: [
            comment   : recentComments,
            attachment: recentAttachments
        ]
    ]

    def issueType = issueDetails.fields.issuetype.name
    def url = issueType == "Incident" ? servicenowIncidentUrl : servicenowRequestUrl

    println JsonOutput.prettyPrint(JsonOutput.toJson(payload))
    sendPayload(url, payload)
}

def fetchIssue(key, auth, jiraUrl) {
    def conn = new URL("${jiraUrl}/rest/api/3/issue/${key}?expand=renderedFields,changelog").openConnection()
    conn.setRequestProperty("Authorization", "${auth}")
    conn.setRequestProperty("Accept", "application/json")
    conn.connect()
    def response = conn.inputStream.text
    return new JsonSlurper().parseText(response)
}

def getRecentComments(comments, issueKey, auth, jiraUrl) {
    def recent = []
    def now = new Date()
    comments.each { c ->
        def created = parseDate(c.created)
        if ((now.time - created.time) <= 30 * 60 * 1000) {
            def commentId = c.id
            def internal = fetchInternalFlag(issueKey, commentId, auth, jiraUrl)
            recent << [
                body       : extractTextFromADF(c.body),
                displayName: c.author?.displayName,
                created    : c.created,
                updated    : c.updated,
                internal   : internal
            ]
        }
    }
    return [comments: recent]
}

def fetchInternalFlag(issueKey, commentId, auth, jiraUrl) {
    def url = "${jiraUrl}/rest/api/3/issue/${issueKey}/comment/${commentId}/properties/sd.public.comment"
    try {
        def conn = new URL(url).openConnection()
        conn.setRequestProperty("Authorization", auth)
        conn.setRequestProperty("Accept", "application/json")
        conn.connect()
        def response = conn.inputStream.text
        def json = new JsonSlurper().parseText(response)
        return json.value?.internal ?: false
    } catch (Exception e) {
        println "Failed to fetch comment property for commentId=${commentId}: ${e.message}"
        return false
    }
}

def getRecentAttachments(attachments) {
    def recent = []
    def now = new Date()
    attachments.each { a ->
        def created = parseDate(a.created)
        if ((now.time - created.time) <= 30 * 60 * 1000) {
            recent << [
                id      : a.id,
                filename: a.filename,
                mimeType: a.mimeType,
                content : a.content,
                created : a.created
            ]
        }
    }
    return recent
}

def parseDate(dateStr) {
    return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(dateStr.replaceAll(":(\\d\\d)\$", "\$1"))
}

def extractTextFromADF(body) {
    if (!body || !body.content) return ""
    return body.content.collect { extractTextRecursive(it) }.join("\n").trim()
}

def extractTextRecursive(node) {
    def text = ""
    if (node.type == "paragraph" && node.content) {
        text += node.content.collect { it.text ?: "" }.join("")
    } else if (node.content) {
        text += node.content.collect { extractTextRecursive(it) }.join("")
    }
    return text
}

def sendPayload(url, payload) {
    def conn = new URL(url).openConnection()
    conn.setRequestMethod("POST")
    conn.setDoOutput(true)
    conn.setRequestProperty("Content-Type", "application/json")
    conn.outputStream.withWriter { writer ->
        writer << JsonOutput.toJson(payload)
    }
    def responseCode = conn.responseCode
    def response = conn.inputStream.text
    println "Response Code: $responseCode"
    println "Response: $response"
}
