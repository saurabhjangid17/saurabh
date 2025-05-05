import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.text.SimpleDateFormat

def jiraUrl = "https://atcisaurabhdemo.atlassian.net"
def serviceNowUrl = "https://webhook-test.com/ffda71125915222a89bccf3569f404b8"
def jiraAuth = System.getenv("JIRA_AUTH")
def issueDataJson = System.getenv("ISSUE_DATA")
if (!issueDataJson) throw new RuntimeException("Missing ISSUE_DATA!")

def issueData = new JsonSlurper().parseText(issueDataJson)
def issueKeys = issueData*.key
if (!issueKeys || issueKeys.isEmpty()) throw new RuntimeException("No issue keys found in ISSUE_DATA!")

if (!jiraAuth) throw new RuntimeException("Missing JIRA_AUTH!")

issueKeys.each { issueKey ->
    def issue = fetchIssue(issueKey, jiraAuth, jiraUrl)
    def changelogItems = issue.changelog?.histories?.findAll {
        isWithinLast30Minutes(it.created)
    }?.collectMany { it.items } ?: []

    if (changelogItems.isEmpty()) {
        println "No recent field changes for ${issueKey}"
        return
    }

    def updatedFields = [:]
    changelogItems.each { item ->
        def fieldName = item.field
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
            default:
                println "Ignoring unsupported field: ${fieldName}"
        }
    }

    if (!updatedFields.isEmpty()) {
        def payload = [
            event : "issue_updated",
            key   : issueKey,
            fields: updatedFields
        ]
        println JsonOutput.prettyPrint(JsonOutput.toJson(payload))
        sendPayload(serviceNowUrl, payload)
    }
}

def fetchIssue(key, auth, jiraUrl) {
    def url = "${jiraUrl}/rest/api/3/issue/${key}?expand=changelog"
    def conn = new URL(url).openConnection()
    conn.setRequestProperty("Authorization", auth)
    conn.setRequestProperty("Accept", "application/json")
    conn.connect()
    return new JsonSlurper().parseText(conn.inputStream.text)
}

def sendPayload(url, data) {
    def conn = new URL(url).openConnection()
    conn.setRequestMethod("POST")
    conn.setDoOutput(true)
    conn.setRequestProperty("Content-Type", "application/json")
    def writer = new OutputStreamWriter(conn.outputStream)
    writer.write(JsonOutput.toJson(data))
    writer.flush()
    writer.close()

    def responseCode = conn.responseCode
    def responseText = conn.inputStream.text
    println "Sent to ServiceNow: ${responseCode}"
    println responseText
}

def isWithinLast30Minutes(String isoDate) {
    def created = parseDate(isoDate)
    def now = new Date()
    return (now.time - created.time) <= 30 * 60 * 1000
}

def parseDate(dateStr) {
    return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(dateStr.replaceAll(/:(\d\d)$/, '$1'))
}

def flattenADF(adf) {
    if (!adf?.content) return adf
    def text = extractTextFromADF(adf).replaceAll("\\s+", " ").trim()
    return [
        type   : "doc",
        version: 1,
        content: [[type: "paragraph", content: [[type: "text", text: text]]]]
    ]
}

def extractTextFromADF(body) {
    if (!body?.content) return ""
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

def wrapValue(field) {
    return field ? [value: field.value] : null
}
