// Define global constants
def jiraUrl = "https://atcisaurabhdemo.atlassian.net"  // Ensure this is defined globally
def servicenowIncidentUrl = "https://webhook.site/4b6a8c55-a5db-4d1a-a351-7ddd90cc1dd7"
def servicenowRequestUrl = "https://webhook-test.com/4c334bbf5265c44d4e66049c1497144f"

// Fetch JIRA_AUTH from environment variables
def jiraAuth = System.getenv("JIRA_AUTH")

// Ensure JIRA_AUTH is set properly
if (!jiraAuth) {
    throw new RuntimeException("JIRA_AUTH environment variable is not set!")
}

def issueData = System.getenv("ISSUE_DATA")
println "Starting Groovy script execution"
def issues = new JsonSlurper().parseText(issueData)

issues.each { issue ->
    def issueKey = issue.key
    def issueDetails = fetchIssue(issueKey, jiraAuth, jiraUrl)  // Passing jiraUrl and jiraAuth
    def recentComments = getRecentComments(issueDetails.fields.comment.comments, issueKey, jiraUrl, jiraAuth)  // Passing jiraUrl and jiraAuth
    def recentAttachments = getRecentAttachments(issueDetails.fields.attachment, jiraUrl, jiraAuth)  // Passing jiraUrl and jiraAuth

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

def getRecentComments(comments, issueKey, jiraUrl, jiraAuth) {
    def recent = []
    def now = new Date()
    comments.each { c ->
        def created = parseDate(c.created)
        if ((now.time - created.time) <= 30 * 60 * 1000) {
            // Fetch properties for each comment to determine the internal flag
            def commentDetail = fetchCommentProperties(issueKey, c.id, jiraUrl, jiraAuth)  // Passing jiraUrl and jiraAuth
            def internal = commentDetail?.internal ?: false
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

def fetchCommentProperties(issueKey, commentId, jiraUrl, jiraAuth) {
    def conn = new URL("${jiraUrl}/rest/api/3/issue/${issueKey}/comment/${commentId}?expand=properties").openConnection()
    conn.setRequestProperty("Authorization", "${jiraAuth}")
    conn.setRequestProperty("Accept", "application/json")
    conn.connect()
    def response = conn.inputStream.text
    def commentDetail = new JsonSlurper().parseText(response)
    return commentDetail?.properties?.find { it.key == "sd.public.comment" }?.value
}

def getRecentAttachments(attachments, jiraUrl, jiraAuth) {
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
