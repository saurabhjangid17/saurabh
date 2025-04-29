import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.Duration

// Hardcoded URLs
def jiraUrl = "https://atcisaurabhdemo.atlassian.net"
def servicenowIncidentUrl = "https://webhook.site/4b6a8c55-a5db-4d1a-a351-7ddd90cc1dd7"
def servicenowSrUrl = "https://webhook-test.com/4c334bbf5265c44d4e66049c1497144f"

def jiraAuth = System.getenv("JIRA_AUTH")
def issueKeysJson = System.getenv("ISSUE_DATA")

def jsonSlurper = new JsonSlurper()
def issueKeys = jsonSlurper.parseText(issueKeysJson) // should be an array of strings like ["DP-62"]

issueKeys.each { issueKey ->
    def issue = fetchIssue(issueKey)
    def comments = fetchJSMComments(issueKey)
    def attachments = filterRecentAttachments(issue.fields.attachment)

    def issueType = issue.fields.issuetype.name
    def issueUrl = (issueType == "Incident") ? servicenowIncidentUrl : servicenowSrUrl

    def payload = [
        event: "comment_created",
        key: issueKey,
        fields: [
            comment: [
                comments: comments
            ],
            attachment: attachments
        ]
    ]

    sendToServiceNow(issueUrl, payload, issueKey)
}

def fetchIssue(issueKey) {
    def connection = new URL("${jiraUrl}/rest/api/3/issue/${issueKey}?expand=renderedFields").openConnection()
    connection.setRequestProperty("Authorization", jiraAuth)
    connection.setRequestProperty("Accept", "application/json")
    new JsonSlurper().parse(connection.inputStream)
}

def fetchJSMComments(issueKey) {
    def connection = new URL("${jiraUrl}/rest/servicedeskapi/request/${issueKey}/comment").openConnection()
    connection.setRequestProperty("Authorization", jiraAuth)
    connection.setRequestProperty("Accept", "application/json")
    def result = new JsonSlurper().parse(connection.inputStream)

    def now = Instant.now()
    def formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

    result.comments.findAll { comment ->
        def createdTime = Instant.parse(comment.created.replaceAll(/Z$/, "+0000"))
        Duration.between(createdTime, now).toMinutes() <= 30
    }.collect { comment ->
        [
            body       : comment.body,
            displayName: comment.author.displayName,
            created    : comment.created,
            updated    : comment.updated,
            internal   : !comment.public
        ]
    }
}

def filterRecentAttachments(attachments) {
    def now = Instant.now()
    attachments.findAll { att ->
        def createdTime = Instant.parse(att.created)
        Duration.between(createdTime, now).toMinutes() <= 30
    }.collect { att ->
        [
            id      : att.id,
            filename: att.filename,
            mimeType: att.mimeType,
            content : att.content,
            created : att.created
        ]
    }
}

def sendToServiceNow(url, payload, issueKey) {
    def connection = new URL(url).openConnection()
    connection.setRequestMethod("POST")
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("Authorization", jiraAuth)
    connection.setDoOutput(true)

    connection.outputStream.write(new JsonBuilder(payload).toString().getBytes("UTF-8"))
    connection.connect()

    if (connection.responseCode == 200) {
        println "Payload successfully sent to ${url} for issue ${issueKey}"
    } else {
        println "Failed to send payload to ${url} for issue ${issueKey}. Response: ${connection.responseCode}"
    }
}
