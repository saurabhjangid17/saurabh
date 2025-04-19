@Grab(group='com.sun.mail', module='javax.mail', version='1.6.2')
import javax.mail.*
import javax.mail.internet.*
import groovy.json.JsonSlurper

// === CONFIG ===
def JIRA_BASE_URL = "https://atcisaurabhdemo.atlassian.net"
def JIRA_AUTH = "Basic c2F1cmFiaGphbmdpZG1hdHJpeEBnbWFpbC5jb206QVRBVFQzeEZmR0YwWnprWVFLeE80alNSODdyeUcwM3U5dVpLS1BxWkJUa1hOc1VIc2pER3ZjcE5fNHIzd2dfVnJGRG5lY0lfSXhqODBoYkl0TDRhYTdISDVZc2FZVnQxa1hFaS0yQXlTVGwzMktTQUVWRExGUUZia3hSMUEweU1ZV0JPSlc2cTYtUEZTbHFSS2lTR2tnc21TSTZBdzhodlRxQ3dxRXJja3dmSzA0RnVCTkZXbk9JPUU3RUJBMDA3"

def issueKey = System.getenv("ISSUE_KEY")
if (!issueKey) {
    println "ISSUE_KEY environment variable is missing"
    return
}

def connection = new URL("${JIRA_BASE_URL}/rest/api/3/issue/${issueKey}").openConnection()
connection.setRequestProperty("Authorization", JIRA_AUTH)
connection.setRequestProperty("Accept", "application/json")

def response = new JsonSlurper().parse(connection.inputStream)
def fields = response.fields

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
        recipients = ["saurabhjangidmatrix@gmail.com", "saurabh.jangid@accenture.com"]
        break
    case "Mohit - Sharma":
        recipients = ["saurabhjangidmatrix@gmail.com"]
        break
    case "Rakhi - Suthar":
        recipients = ["saurabh.jangid@accenture.com"]
        break
    default:
        println "No recipients defined for assignment group: ${groupKey}"
        return
}

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
[${issueKey} - ${summary}](${JIRA_BASE_URL}/browse/${issueKey})


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
message.setFrom(new InternetAddress(System.getenv("SMTP_EMAIL")))
recipients.each { message.addRecipient(Message.RecipientType.TO, new InternetAddress(it)) }
message.setSubject("New Ticket - RCYC - Jira : ${issueKey}")
message.setText(body)

Transport.send(message)
println "Email sent to: ${recipients.join(', ')}"
