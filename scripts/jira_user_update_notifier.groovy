@Grab(group='com.sun.mail', module='jakarta.mail', version='2.0.1')
import jakarta.mail.*
import jakarta.mail.internet.*
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.util.Properties

// Fetching the input data (the issue key from the webhook payload)
def issueData = new JsonSlurper().parseText(System.getenv('ISSUE_DATA'))[0]
def issueKey = issueData.key

println "🔄 Processing issue: $issueKey"

// Fetching the custom fields for Assignee Email and Reporter Email
def assigneeEmail = issueData.customfield_10208
def reporterEmail = issueData.customfield_10208

// Function to call the Jira API and retrieve the accountId for the provided email
def getAccountIdByEmail(String email) {
    def url = "https://atcisaurabhdemo.atlassian.net/rest/api/3/user/search?query=$email"
    def connection = new URL(url).openConnection()
    connection.setRequestProperty("Authorization", System.getenv("JIRA_AUTH"))
    connection.setRequestMethod("GET")
    connection.connect()

    def response = connection.inputStream.text
    def jsonResponse = new JsonSlurper().parseText(response)

    if (jsonResponse.size() > 0) {
        return jsonResponse[0].accountId
    } else {
        return null
    }
}

// Check if Assignee email has changed and update Assignee field
if (assigneeEmail) {
    def assigneeAccountId = getAccountIdByEmail(assigneeEmail)
    if (assigneeAccountId) {
        println "Assignee found: $assigneeAccountId"
        // Code to update Assignee goes here
    }
}

// Check if Reporter email has changed and update Reporter field
if (reporterEmail) {
    def reporterAccountId = getAccountIdByEmail(reporterEmail)
    if (reporterAccountId) {
        println "Reporter found: $reporterAccountId"
        // Code to update Reporter goes here
    }
}

// Assignment Group Handling - You can customize this based on your needs
def assignmentGroup = issueData.customfield_10066
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

// === Email Body ===
def body = """
Hi Team,

There is an open issue **$issueKey**. Below are the details of the issue:
---
**Issue Summary**: ${issueData.summary}
**Issue Key**: $issueKey
**Issue Type**: ${issueData.issueType}
**Assignment Group**: $parent - $child
**Status**: ${issueData.status}
**Assignee**: ${issueData.assignee}
**Priority**: ${issueData.priority}
**Project**: ${issueData.project}

You can view the full details of the issue by clicking the following link:
[${issueData.summary}](https://atcisaurabhdemo.atlassian.net/browse/$issueKey)

Thank you,  
Jira
"""

// === Email Setup ===
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
