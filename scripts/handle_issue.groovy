import groovy.json.JsonSlurper
import groovy.json.JsonOutput

// Read the input payload
def payload = new JsonSlurper().parse(new File(args[0]))
def issueKey = payload.issue_key
def jiraAuth = payload.jira_auth

def JIRA_BASE_URL = 'https://atcisaurabhdemo.atlassian.net'
def SERVICENOW_INCIDENT_URL = 'https://webhook-test.com/d9e68fb04aeeb7be5e0454b17db6612d'
def SERVICENOW_REQUEST_URL = 'https://webhook.site/ce4db067-719c-4aab-a833-d71c330f41d6'

// Step 1: Fetch issue data from Jira
def jiraUrl = "$JIRA_BASE_URL/rest/api/3/issue/$issueKey"
def jiraConn = new URL(jiraUrl).openConnection()
jiraConn.setRequestProperty("Authorization", jiraAuth)
jiraConn.setRequestProperty("Accept", "application/json")

def issueData = new JsonSlurper().parse(jiraConn.inputStream)

// Step 2: Determine target URL based on issue type
def issueType = issueData.fields.issuetype.name
def targetUrl = issueType == "Incident" ? SERVICENOW_INCIDENT_URL : SERVICENOW_REQUEST_URL

println "Issue [$issueKey] type is [$issueType] → Sending to [$targetUrl]"

// Step 3: Send the issue data to ServiceNow
def conn = new URL(targetUrl).openConnection()
conn.setRequestMethod("POST")
conn.doOutput = true
conn.setRequestProperty("Content-Type", "application/json")

def body = JsonOutput.toJson(issueData)
conn.outputStream.withWriter("UTF-8") { it.write(body) }

def responseCode = conn.responseCode
println "ServiceNow responded with [$responseCode]: ${conn.inputStream.text}"
