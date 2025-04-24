#!/usr/bin/env groovy

@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1')
import groovyx.net.http.RESTClient
import groovyx.net.http.ContentType
import static groovyx.net.http.Method.*

def ISSUE_KEY = args[0]
def urgency = args[1]
def impact = args[2]
def JIRA_BASE_URL = "https://atcisaurabhdemo.atlassian.net"
def JIRA_AUTH = System.getenv("JIRA_AUTH")

if (!ISSUE_KEY || !urgency || !impact || !JIRA_AUTH) {
    println "❌ Missing required input(s): issue key, urgency, impact or JIRA_AUTH"
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
    "Critical-Extensive / Widespread" : ["Highest", "Sev-1"],
    "Critical-Significant / Large"    : ["Highest", "Sev-1"],
    "Critical-Moderate / Limited"     : ["High",    "Sev-2"],
    "Critical-Minor / Localized"      : ["High",    "Sev-2"],
    "High-Extensive / Widespread"     : ["Highest", "Sev-1"],
    "High-Significant / Large"        : ["High",    "Sev-2"],
    "High-Moderate / Limited"         : ["High",    "Sev-2"],
    "High-Minor / Localized"          : ["Medium",  "Sev-3"],
    "Medium-Extensive / Widespread"   : ["High",    "Sev-2"],
    "Medium-Significant / Large"      : ["Medium",  "Sev-3"],
    "Medium-Moderate / Limited"       : ["Medium",  "Sev-3"],
    "Medium-Minor / Localized"        : ["Medium",  "Sev-3"],
    "Low-Extensive / Widespread"      : ["Low",     "Sev-4"],
    "Low-Significant / Large"         : ["Low",     "Sev-4"],
    "Low-Moderate / Limited"          : ["Low",     "Sev-4"],
    "Low-Minor / Localized"           : ["Low",     "Sev-4"]
]

def combo = "${urgency}-${impact}"
def result = matrix[combo]

println "ℹ️  Issue: ${ISSUE_KEY}, Urgency: ${urgency}, Impact: ${impact}"

if (!result) {
    println "❌ No matching Priority/Severity for combination: ${combo}"
    System.exit(1)
}

def (priorityName, severityValue) = result
def priorityId = priorityMap[priorityName]

println "✅ Mapped Priority: ${priorityName} (ID: ${priorityId}), Severity: ${severityValue}"

// Update Jira issue
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
