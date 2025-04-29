import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset

// Hardcoded URLs
def jiraUrl = "https://atcisaurabhdemo.atlassian.net"  // Your Jira URL
def servicenowIncidentUrl = "https://webhook.site/4b6a8c55-a5db-4d1a-a351-7ddd90cc1dd7"  // ServiceNow Incident URL
def servicenowSrUrl = "https://webhook-test.com/4c334bbf5265c44d4e66049c1497144f"  // ServiceNow Service Request URL

// Fetch the Jira authentication credentials from environment variables
def jiraAuth = System.getenv("JIRA_AUTH")  // Jira credentials from GitHub Secrets

// Parsing the input JSON data (issue data passed as environment variable)
def jsonSlurper = new JsonSlurper()
def issueData = System.getenv("ISSUE_DATA")  // The Jira issue data passed from GitHub Actions
def issues = jsonSlurper.parseText(issueData)

// Get the current UTC time and 30 min before
def now = ZonedDateTime.now(ZoneOffset.UTC)
def thirtyMinutesAgo = now.minusMinutes(30)

// Iterate over the issues to process each one
issues.each { issue ->
    def issueKey = issue.key
    def issueType = issue.fields.issuetype.name
    def issueUrl = ""

    // Logic to choose the ServiceNow URL based on issue type
    if (issueType == "Incident") {
        issueUrl = servicenowIncidentUrl
    } else {
        issueUrl = servicenowSrUrl
    }

    // Now build the payload
    def payload = [
        event: "issue_created",
        key: issueKey,
        fields: [
            summary: issue.fields.summary,
            description: issue.fields.description,
            priority: issue.fields.priority.name,
            assignee: issue.fields.assignee ? issue.fields.assignee.displayName : "Unassigned",
            comment: getRecentComments(issue.fields.comment.comments),
            attachments: getRecentAttachments(issue.fields.attachment)
        ]
    ]

    // Send the data to the appropriate ServiceNow URL based on issue type
    def connection = new URL(issueUrl).openConnection()
    connection.setRequestMethod("POST")
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("Authorization", jiraAuth)
    connection.setDoOutput(true)

    // Convert the payload to JSON and send it
    connection.outputStream.write(new JsonBuilder(payload).toString().getBytes("UTF-8"))
    connection.connect()

    // Check the response
    if (connection.responseCode == 200) {
        println "Payload successfully sent to ${issueUrl} for issue ${issueKey}"
    } else {
        println "Failed to send payload to ${issueUrl} for issue ${issueKey}. Response code: ${connection.responseCode}"
    }
}

// Helper function to filter and format comments added in last 30 min
def getRecentComments(comments) {
    def formattedComments = []
    comments.each { comment ->
        if (comment?.created) {
            def createdTime = ZonedDateTime.parse(comment.created, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
            if (createdTime.isAfter(thirtyMinutesAgo)) {
                formattedComments << [
                    body: comment.body,
                    displayName: comment.author.displayName,
                    created: comment.created,
                    updated: comment.updated,
                    internal: comment.internal
                ]
            }
        }
    }
    return formattedComments
}

// Helper function to filter and format attachments added in last 30 min
def getRecentAttachments(attachments) {
    def formattedAttachments = []
    attachments.each { att ->
        if (att?.created) {
            def attCreated = ZonedDateTime.parse(att.created, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
            if (attCreated.isAfter(thirtyMinutesAgo)) {
                formattedAttachments << [
                    id: att.id,
                    filename: att.filename,
                    mimeType: att.mimeType,
                    content: att.content,
                    created: att.created
                ]
            }
        }
    }
    return formattedAttachments
}
