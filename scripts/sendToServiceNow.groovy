import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

// Hardcoded URLs
def jiraUrl = "https://atcisaurabhdemo.atlassian.net"  // Your Jira URL
def servicenowIncidentUrl = "https://webhook.site/4b6a8c55-a5db-4d1a-a351-7ddd90cc1dd7"  // ServiceNow Incident URL
def servicenowSrUrl = "https://webhook-test.com/4c334bbf5265c44d4e66049c1497144f"  // ServiceNow Service Request URL

// Fetch credentials and input from environment
def jiraAuth = System.getenv("JIRA_AUTH")
def issueData = System.getenv("ISSUE_DATA")  // Should be a JSON string like '[{"key": "DP-62"}, {"key": "DP-63"}]'

def jsonSlurper = new JsonSlurper()
def issues = jsonSlurper.parseText(issueData)

issues.each { issue ->
    def issueKey = issue.key
    println "Processing Issue: ${issueKey}"

    // Fetch issue details from Jira
    def connection = new URL("${jiraUrl}/rest/api/3/issue/${issueKey}?expand=renderedFields").openConnection()
    connection.setRequestMethod("GET")
    connection.setRequestProperty("Authorization", jiraAuth)
    connection.setRequestProperty("Content-Type", "application/json")
    connection.connect()

    if (connection.responseCode != 200) {
        println "Failed to fetch issue ${issueKey}. Response code: ${connection.responseCode}"
        return
    }

    def issueDetails = new JsonSlurper().parse(connection.inputStream)

    def issueType = issueDetails.fields.issuetype.name
    def targetUrl = (issueType == "Incident") ? servicenowIncidentUrl : servicenowSrUrl

    // Build payload
    def payload = [
        event: "issue_created",
        key: issueKey,
        fields: [
            summary    : issueDetails.fields.summary,
            description: issueDetails.fields.description,
            priority   : issueDetails.fields.priority?.name ?: "Medium",
            assignee   : issueDetails.fields.assignee?.displayName ?: "Unassigned",
            comment    : getRecentComments(issueDetails.fields.comment.comments),
            attachment : getRecentAttachments(issueDetails.fields.attachment)
        ]
    ]

    // Send payload to ServiceNow
    def postConnection = new URL(targetUrl).openConnection()
    postConnection.setRequestMethod("POST")
    postConnection.setRequestProperty("Authorization", jiraAuth)
    postConnection.setRequestProperty("Content-Type", "application/json")
    postConnection.setDoOutput(true)

    def payloadJson = new JsonBuilder(payload).toPrettyString()
    postConnection.outputStream.write(payloadJson.getBytes("UTF-8"))
    postConnection.connect()

    if (postConnection.responseCode == 200 || postConnection.responseCode == 201) {
        println "Successfully sent payload for ${issueKey} to ServiceNow (${targetUrl})"
    } else {
        println "Failed to send payload for ${issueKey} to ServiceNow. Response: ${postConnection.responseCode}"
    }
}

// -------- Helper functions --------

// Helper: Filter comments created within last 30 minutes
def getRecentComments(comments) {
    def formattedComments = []
    if (!comments) return formattedComments

    def now = new Date()
    def thirtyMinutesAgo = new Date(now.time - (30 * 60 * 1000)) // 30 minutes ago

    comments.each { comment ->
        def createdDate = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", comment.created)
        if (createdDate.after(thirtyMinutesAgo)) {
            formattedComments << [
                body        : comment.body,
                displayName : comment.author.displayName,
                created     : comment.created,
                updated     : comment.updated,
                internal    : comment.internal
            ]
        }
    }
    return formattedComments
}

// Helper: Filter attachments created within last 30 minutes
def getRecentAttachments(attachments) {
    def formattedAttachments = []
    if (!attachments) return formattedAttachments

    def now = new Date()
    def thirtyMinutesAgo = new Date(now.time - (30 * 60 * 1000))

    attachments.each { att ->
        def createdDate = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", att.created)
        if (createdDate.after(thirtyMinutesAgo)) {
            formattedAttachments << [
                id      : att.id,
                filename: att.filename,
                mimeType: att.mimeType,
                content : att.content,
                created : att.created
            ]
        }
    }
    return formattedAttachments
}
