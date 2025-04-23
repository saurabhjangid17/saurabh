@Grab(group='com.sun.mail', module='jakarta.mail', version='2.0.1')
import jakarta.mail.*
import jakarta.mail.internet.*
import groovy.json.JsonSlurper
import java.util.Properties

// === Config ===
def issueKey = System.getenv("ISSUE_KEY")
if (!issueKey) {
    println "❌ ISSUE_KEY environment variable is missing"
    return
}

def JIRA_BASE_URL = "https://atcisaurabhdemo.atlassian.net"
def JIRA_AUTH = System.getenv("JIRA_AUTH") // base64 encoded string as 'Basic xxx'

// === API Call ===
def url = new URL("${JIRA_BASE_URL}/rest/api/3/issue/${issueKey}")
def connection = url.openConnection()
connection.setRequestProperty("Authorization", JIRA_AUTH)
connection.setRequestProperty("Accept", "application/json")

def responseCode = connection.responseCode
println "🔍 Jira API Response Code: ${responseCode}"

if (responseCode != 200) {
    println "❌ Failed to fetch issue: ${connection.responseMessage}"
    connection.getErrorStream()?.withReader { reader ->
        println reader.text
    }
    return
}

def response = new JsonSlurper().parse(connection.inputStream)
def fields = response.fields

// === Extract Fields ===
def summary = fields.summary
def issueType = fields.issuetype.name
def status = fields.status.name
def assignee = fields.assignee?.displayName ?: "Unassigned"
def priority = fields.priority.name
def project = fields.project.name
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

// === Email Body ===
def body = """
Hi Team,

There is an open issue **${issueKey}**. Below are the details of the issue:
---
**Issue Summary**: ${summary}
**Issue Key**: ${issueKey}
**Issue Type**: ${issueType}
**Assignment Group**: ${parent} - ${child}
**Status**: ${status}
**Assignee**: ${assignee}
**Priority**: ${priority}
**Project**: ${project}

You can view the full details of the issue by clicking the following link:
[${issueKey} - ${summary}](https://atcisaurabhdemo.atlassian.net/browse/${issueKey})

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
message.setSubject("New Ticket - RCYC - Jira : ${issueKey}")
message.setText(body)

Transport.send(message)
println "✅ Email sent to: ${recipients.join(', ')}"
