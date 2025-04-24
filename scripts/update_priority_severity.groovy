#!/usr/bin/env groovy

@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1')
import groovyx.net.http.RESTClient
import groovyx.net.http.ContentType
import static groovyx.net.http.Method.*

def ISSUE_KEY = args[0]
def JIRA_BASE_URL = "https://atcisaurabhdemo.atlassian.net"
def JIRA_AUTH = System.getenv("JIRA_AUTH")

if (!ISSUE_KEY || !JIRA_AUTH) {
    println "❌ Missing issue key or JIRA_AUTH"
    System.exit(1)
}

def client = new RESTClient("${JIRA_BASE_URL}")
client.setHeaders([
    'Authorization': "${JIRA_AUTH}",
    'Content-Type': 'application/json',
    'Accept': 'application/json'
])

// Priority ID map (names to IDs)
def priorityMap = [
    "Highest": "1",
    "High"   : "2",
    "Medium" : "3",
    "Low"    : "4"
]

// Matrix for Priority and Severity
def matrix = [
    "Critical-Extensive"   : ["Highest", "Sev-1"],
    "Critical-Significant": ["Highest", "Sev-1"],
    "Critical-Moderate"   : ["High",    "Sev-2"],
    "Critical-Minor"      : ["High",    "Sev-2"],
    "High-Extensive"      : ["Highest", "Sev-1"],
    "High-Significant"    : ["High",    "Sev-2"],
    "High-Moderate"       : ["High",    "Sev-2"],
    "High-Minor"          : ["Medium",  "Sev-3"],
    "Medium-Extensive"    : ["High",    "Sev-2"],
    "Medium-Significant"  : ["Medium",  "Sev-3"],
    "Medium-Moderate"     : ["Medium",  "Sev-3"],
    "Medium-Minor"        : ["Medium",  "Sev-3"],
    "Low-Extensive"       : ["Low",     "Sev-4"],
    "Low-Significant"     : ["Low",     "Sev-4"],
    "Low-Moderate"        : ["Low",     "Sev-4"],
    "Low-Minor"           : ["Low",     "Sev-4"]
]

// Step 1: Fetch issue
def response = client.get(path: "/rest/api/2/issue/${ISSUE_KEY}")
def fields = response.data.fields

def urgency = fields.customfield_10045?.value
def impact  = fields.customfield_10004?.value

println "ℹ️  Issue: ${ISSUE_KEY}, Urgency: ${urgency}, Impact: ${impact}"

if (!urgency || !impact) {
    println "❌ Urgency or Impact field is missing"
    System.exit(1)
}

def combo = "${urgency}-${impact}"
def result = matrix[combo]

if (!result) {
    println "❌ No matching Priority/Severity for combination: ${combo}"
    System.exit(1)
}

def (priorityName, severityValue) = result
def priorityId = priorityMap[priorityName]

println "✅ Mapped Priority: ${priorityName} (ID: ${priorityId}), Severity: ${severityValue}"

// Step 2: Update issue
def updateBody = [
    fields: [
        priority         : [id: priorityId],
        customfield_10051: [value: severityValue]
    ]
]

def updateResponse = client.put(
    path : "/rest/api/2/issue/${ISSUE_KEY}",
    body : updateBody,
    requestContentType: ContentType.JSON
)

println "✅ Issue ${ISSUE_KEY} updated successfully."
