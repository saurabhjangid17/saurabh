@Grab(group='com.sun.mail', module='jakarta.mail', version='2.0.1')
import jakarta.mail.*
import jakarta.mail.internet.*
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.util.Properties

// 🔹 Jira Auth
def jiraAuth = System.getenv("JIRA_AUTH")
def jiraBaseUrl = "https://atcisaurabhdemo.atlassian.net"

// 🔹 Parse input issue key
def issueData = new JsonSlurper().parseText(System.getenv('ISSUE_DATA'))[0]
def issueKey = issueData.key
println "🔄 Processing issue: $issueKey"

// 🔹 Fetch full issue details
def fetchIssueDetails = { key ->
    def url = "${jiraBaseUrl}/rest/api/3/issue/$key"
    def conn = new URL(url).openConnection()
    conn.setRequestMethod("GET")
    conn.setRequestProperty("Authorization", jiraAuth)
    conn.setRequestProperty("Accept", "application/json")
    conn.connect()

    def response = conn.inputStream.text
    return new JsonSlurper().parseText(response)
}

def issue = fetchIssueDetails(issueKey)
def fields = issue.fields

// 🔹 Get custom fields
def assigneeEmail = fields.customfield_10208
def reporterEmail = fields.customfield_10775
def assignmentGroup = fields.customfield_10066
def parent = assignmentGroup?.value ?: "N/A"
def child = assignmentGroup?.child?.value ?: "N/A"
def groupKey = "${parent} - ${child}"
def recipients = []

switch (groupKey) {
    case "Saurabh - Jangid":
    case "Mohit - Sharma":
        recipients = ["saurabhjangidmatrix@gmail.com", "saurabh.jangid@accenture.com"]
        break
    case "Rakhi - Suthar":
        recipients = ["saurabhjangidmatrix@gmail.com"]
        break
    default:
        println "⚠️ No recipients defined for assignment group: ${groupKey}"
        return
}

// 🔹 Fetch accountId from email
def getAccountIdByEmail = { email ->
    def url = "${jiraBaseUrl}/rest/api/3/user/search?query=$email"
    def conn = new URL(url).openConnection()
    conn.setRequestProperty("Authorization", jiraAuth)
    conn.setRequestMethod("GET")
    conn.connect()

    def response = conn.inputStream.text
    def jsonResponse = new JsonSlurper().parseText(response)
    return jsonResponse ? jsonResponse[0].accountId : null
}

// === Function to update assignee ===
def updateAssignee(issueKey, accountId) {
    def url = "https://atcisaurabhdemo.atlassian.net/rest/api/3/issue/$issueKey/assignee"
    def connection = new URL(url).openConnection()
    connection.setRequestMethod("PUT")
    connection.setRequestProperty("Authorization", System.getenv("JIRA_AUTH"))
    connection.setRequestProperty("Content-Type", "application/json")
    connection.doOutput = true

    def payload = JsonOutput.toJson([accountId: accountId])
    connection.outputStream.withWriter("UTF-8") { it.write(payload) }

    if (connection.responseCode == 204) {
        println "✅ Assignee updated successfully."
    } else {
        println "❌ Failed to update assignee. Response: ${connection.errorStream?.text}"
    }
}

// === Function to update reporter ===
def updateReporter(issueKey, accountId) {
    def url = "https://atcisaurabhdemo.atlassian.net/rest/api/3/issue/$issueKey"
    def connection = new URL(url).openConnection()
    connection.setRequestMethod("PUT")
    connection.setRequestProperty("Authorization", System.getenv("JIRA_AUTH"))
    connection.setRequestProperty("Content-Type", "application/json")
    connection.doOutput = true

    def payload = JsonOutput.toJson([
        fields: [reporter: [id: accountId]]
    ])
    connection.outputStream.withWriter("UTF-8") { it.write(payload) }

    if (connection.responseCode == 204) {
        println "✅ Reporter updated successfully."
    } else {
        println "❌ Failed to update reporter. Response: ${connection.errorStream?.text}"
    }
}

// === Update Assignee ===
if (assigneeEmail) {
    def assigneeAccountId = getAccountIdByEmail(assigneeEmail)
    if (assigneeAccountId) {
        println "🔹 Assignee accountId: $assigneeAccountId"
        updateAssignee(issueKey, assigneeAccountId)
    } else {
        println "❌ Assignee email not found in Jira."
    }
}

// === Update Reporter ===
if (reporterEmail) {
    def reporterAccountId = getAccountIdByEmail(reporterEmail)
    if (reporterAccountId) {
        println "🔹 Reporter accountId: $reporterAccountId"
        updateReporter(issueKey, reporterAccountId)
    } else {
        println "❌ Reporter email not found in Jira."
    }
}

// 🔹 Build email body
def summary = fields.summary
def issueType = fields.issuetype.name
def status = fields.status.name
def assignee = fields.assignee?.displayName ?: "Unassigned"
def priority = fields.priority?.name ?: "None"
def project = fields.project.name

def body = """
Hi Team,

There is an open issue **$issueKey**. Below are the details of the issue:
---
**Issue Summary**: $summary  
**Issue Key**: $issueKey  
**Issue Type**: $issueType  
**Assignment Group**: $parent - $child  
**Status**: $status  
**Assignee**: $assignee  
**Priority**: $priority  
**Project**: $project  

You can view the full details of the issue by clicking the following link:  
[$summary](${jiraBaseUrl}/browse/$issueKey)

Thank you,  
Jira
"""

// 🔹 Send email
def props = new Properties()
props.put("mail.smtp.host", "smtp.gmail.com")
props.put("mail.smtp.port", "587")
props.put("mail.smtp.auth", "true")
props.put("mail.smtp.starttls.enable", "true")

def session = Session.getInstance(props, new Authenticator() {
    protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(System.getenv("SMTP_EMAIL"), System.getenv("SMTP_PASSWORD"))
    }
})

def message = new MimeMessage(session)
message.setFrom(new InternetAddress(System.getenv("SMTP_EMAIL"), "Jira Automation"))
recipients.each { message.addRecipient(Message.RecipientType.TO, new InternetAddress(it)) }
message.setSubject("New Ticket - RCYC - Jira : $issueKey")
message.setText(body)

Transport.send(message)
println "✅ Email sent to: ${recipients.join(', ')}"
