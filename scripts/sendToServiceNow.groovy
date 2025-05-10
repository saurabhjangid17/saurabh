import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.text.SimpleDateFormat

// Define global constants
def jiraUrl = "https://atcisaurabhdemo.atlassian.net"
def servicenowIncidentUrl = "https://webhook-test.com/d1b5ae4f3939f1a6efc10ac090f0e924"
def servicenowRequestUrl = "https://webhook-test.com/d1b5ae4f3939f1a6efc10ac090f0e924"


// Fetch JIRA_AUTH from environment variables
def jiraAuth = System.getenv("JIRA_AUTH")
if (!jiraAuth) {
    throw new RuntimeException("JIRA_AUTH environment variable is not set!")
}

def issueData = System.getenv("ISSUE_DATA")
println "Starting Groovy script execution"
def issues = new JsonSlurper().parseText(issueData)

issues.each { issue ->
    def issueKey = issue.key
    def issueDetails = fetchIssue(issueKey, jiraAuth, jiraUrl)
    def updatedFields = [:]
    def recentComments = getRecentComments(issueDetails.fields.comment.comments, issueKey, jiraUrl, jiraAuth)
	if (recentComments) {
    updatedFields.comment = [
        comments: recentComments
    ]
}
    def recentAttachments = getRecentAttachments(issueDetails.fields.attachment, jiraUrl, jiraAuth)
    if (recentAttachments) {
    updatedFields.attachment = recentAttachments
}
	def fields = issueDetails.fields
	def changelogItems = issue.changelog?.histories?.findAll {
        isWithinLast30Minutes(it.created)
    }?.collectMany { it.items } ?: []

    if (changelogItems.isEmpty()) {
        println "No recent field changes for ${issueKey}"
        return
    }
    
    changelogItems.each { item ->
        def fieldName = item.field
          println "Changelog field: ${item.field}"
        switch (fieldName) {
            case "summary":
                updatedFields.summary = issue.fields.summary
                break
            case "description":
                updatedFields.description = flattenADF(issue.fields.description)
                break
            case "priority":
                updatedFields.priority = issue.fields.priority
                break
            case "assignee":
                updatedFields.assignee = issue.fields.assignee ? [
                    accountId    : issue.fields.assignee.accountId,
                    displayName  : issue.fields.assignee.displayName,
                    emailAddress : issue.fields.assignee.emailAddress
                ] : null
                break
            case "reporter":
                updatedFields.reporter = issue.fields.reporter ? [
                    accountId    : issue.fields.reporter.accountId,
                    displayName  : issue.fields.reporter.displayName,
                    emailAddress : issue.fields.reporter.emailAddress
                ] : null
                break
            case "Impact":
                updatedFields["customfield_10004"] = wrapValue(issue.fields.customfield_10004)
                break
            case "Urgency":
                updatedFields["customfield_10045"] = wrapValue(issue.fields.customfield_10045)
                break
            case "Severity":
                updatedFields["customfield_10051"] = wrapValue(issue.fields.customfield_10051)
                break
            case "status":
                updatedFields.status = [
                    id  : issue.fields.status?.id,
                    name: issue.fields.status?.name
                ]
                break
           case "Area": // Area
            def parent = issue.fields.customfield_10066
            def child = parent?.child

            updatedFields.customfield_10066 = [
                value: parent?.value,
                id   : parent?.id ?: "",
                child: child ? [
                    value: child?.value,
                    id   : child?.id ?: ""
                ] : null
            ]
            break
        case "Request Type": // Request Type
            def requestType = issue.fields.customfield_10010
            updatedFields.customfield_10010 = [
                requestType: [
                    name: requestType?.name ?: ""
                ]
            ]
            break
            default:
                println "Ignoring unsupported field: ${fieldName}"
        }
    }


    if (!updatedFields.isEmpty()) {
        def payload = [
            event : "issue_updated",
            key   : issueKey,
            fields: updatedFields,
        ]
    def issueType = fields.issuetype.name
    def (url) = issueType == "[System] Incident" ?
        [servicenowIncidentUrl] :
        [servicenowRequestUrl]

    println JsonOutput.prettyPrint(JsonOutput.toJson(payload))
    sendPayload(url, payload)
    }
}
  


def fetchIssue(key, auth, jiraUrl) {
    def conn = new URL("${jiraUrl}/rest/api/3/issue/${key}?expand=renderedFields,changelog").openConnection()
    conn.setRequestProperty("Authorization", auth)
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
        def updated = parseDate(c.updated)

        if ((now.time - created.time) <= 30 * 60 * 1000 || (now.time - updated.time) <= 30 * 60 * 1000) {
            def commentDetail = fetchCommentProperties(issueKey, c.id, jiraUrl, jiraAuth)
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
    conn.setRequestProperty("Authorization", jiraAuth)
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
    return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(dateStr.replaceAll(/:(\d\d)$/, '$1'))
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

def flattenADF(adf) {
    if (!adf?.content) return adf
    def flatText = extractTextFromADF(adf).replaceAll("\\s+", " ").trim()
    return [
        type   : "doc",
        version: 1,
        content: [
            [
                type   : "paragraph",
                content: [
                    [type: "text", text: flatText]
                ]
            ]
        ]
    ]
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
    println "Response Code: ${responseCode}"
    println "Response: ${response}"
}

def isWithinLast30Minutes(String isoDate) {
    def created = parseDate(isoDate)
    def now = new Date()
    return (now.time - created.time) <= 30 * 60 * 1000
}

def customField(String id) {
    return "customfield_${id}"
}

def wrapValue(field) {
    return field ? [value: field.value] : null
}
