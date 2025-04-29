import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import java.text.SimpleDateFormat
import java.util.TimeZone

// -----------------------
// Configuration
// -----------------------
def jiraUrl = "https://atcisaurabhdemo.atlassian.net"
def servicenowIncidentUrl = "https://webhook.site/4b6a8c55-a5db-4d1a-a351-7ddd90cc1dd7"
def servicenowSrUrl = "https://webhook-test.com/4c334bbf5265c44d4e66049c1497144f"
def jiraAuth = System.getenv("JIRA_AUTH")

// Read issue data
def jsonSlurper = new JsonSlurper()
def issueData = System.getenv("ISSUE_DATA")
def issues = jsonSlurper.parseText(issueData)

issues.each { issue ->
    def issueKey = issue.key
    def issueJson = fetchIssue(issueKey)

    def issueType = issueJson.fields.issuetype.name
    def issueUrl = (issueType == "Incident") ? servicenowIncidentUrl : servicenowSrUrl

    def payload = [
        event: "issue_created",
        key  : issueKey,
        fields: [
            summary    : issueJson.fields.summary,
            description: issueJson.fields.description,
            priority   : issueJson.fields.priority?.name,
            assignee   : issueJson.fields.assignee?.displayName ?: "Unassigned",
            comment    : getRecentComments(issueJson.fields.comment?.comments ?: []),
            attachments: getRecentAttachments(issueJson.fields.attachment ?: [])
        ]
    ]

    def connection = new URL(issueUrl).openConnection()
    connection.setRequestMethod("POST")
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("Authorization", jiraAuth)
    connection.setDoOutput(true)
    connection.outputStream.write(new JsonBuilder(payload).toString().getBytes("UTF-8"))
    connection.connect()

    if (connection.responseCode == 200) {
        println "Successfully sent ${issueKey} to ServiceNow"
    } else {
        println "Failed to send ${issueKey}. Status: ${connection.responseCode}"
        println connection.errorStream.text
    }
}

// -----------------------
// Helper Methods
// -----------------------

def fetchIssue(String issueKey) {
    def url = "${jiraUrl}/rest/api/3/issue/${issueKey}?expand=renderedFields"
    def connection = new URL(url).openConnection()
    connection.setRequestProperty("Authorization", jiraAuth)
    connection.setRequestProperty("Accept", "application/json")

    def response = connection.inputStream.text
    return new JsonSlurper().parseText(response)
}

def getRecentComments(comments) {
    def recent = []
    def now = new Date()
    comments.each { c ->
        def created = parseDate(c.created)
        if ((now.time - created.time) <= 30 * 60 * 1000) {
            recent << [
                body       : c.body,
                displayName: c.author?.displayName,
                created    : c.created,
                updated    : c.updated,
                internal   : c.public == false ? true : false
            ]
        }
    }
    return recent
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

def parseDate(String dateStr) {
    def df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    df.setTimeZone(TimeZone.getTimeZone("UTC"))
    return df.parse(dateStr.replaceAll(":(?=\\d{2}\$)", "")) // Fixes timezone colon issue
}
