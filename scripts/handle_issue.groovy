import groovy.json.JsonSlurper
import groovy.json.JsonOutput

// Helper: Extract plain text from Jira ADF
def extractPlainTextFromADF(adfNode) {
    if (!adfNode) return ""
    def result = ""

    if (adfNode instanceof Map) {
        if (adfNode.type == "text") {
            result += adfNode.text
        } else if (adfNode.type == "hardBreak") {
            result += "\n"
        } else if (adfNode.content) {
            adfNode.content.each { child ->
                result += extractPlainTextFromADF(child)
            }
            if (adfNode.type == "paragraph") {
                result += "\n"
            }
        }
    } else if (adfNode instanceof List) {
        adfNode.each { child ->
            result += extractPlainTextFromADF(child)
        }
    }

    return result.trim()
}

// Helper: Rebuild ADF using one paragraph and entire plain-text content
def buildSingleParagraphADF(String plainText) {
    return [
        type   : "doc",
        version: 1,
        content: [
            [
                type   : "paragraph",
                content: [
                    [
                        type: "text",
                        text: plainText.replaceAll("\r\n", "\n")  // Normalize line breaks
                    ]
                ]
            ]
        ]
    ]
}

// Read the input payload
def payload = new JsonSlurper().parse(new File(args[0]))
def issueKey = payload.issue_key

// Base64-encoded Basic Auth credentials (hardcoded)
def jiraAuth = "Basic c2F1cmFiaGphbmdpZG1hdHJpeEBnbWFpbC5jb206QVRBVFQzeEZmR0YwZWFMcXd5ZlpfdVBSb1F1T0NYVzFTLXBqUXJ2blBlZWZFVFVWSUg1WjNUUTFYYzFnVHdNb2NRelRBSVJ6cHp6QmtiSmMzdDhaLVFDTHVvcXgxSmV5N19rd1RZLUhhVUhSdUduTnRkNTFIWk53Y2xIU3ZFVUVmZ3RWQXQ1LUNOeWpEcmpLaUtmU2dKYTg5LVZ5UG9wRk12cWJQbVgzV2lDRy10b3h2cXhCcjZZPTE1QUVDRDI0"

def JIRA_BASE_URL = 'https://atcisaurabhdemo.atlassian.net'
def SERVICENOW_INCIDENT_URL = 'https://webhook-test.com/d9e68fb04aeeb7be5e0454b17db6612d'
def SERVICENOW_REQUEST_URL = 'https://webhook-test.com/224924fd6f28ff1056b57fa40a6bd7d6'

// Step 1: Fetch issue data from Jira
def jiraUrl = "$JIRA_BASE_URL/rest/api/3/issue/$issueKey"
def jiraConn = new URL(jiraUrl).openConnection()
jiraConn.setRequestProperty("Authorization", jiraAuth)
jiraConn.setRequestProperty("Accept", "application/json")

def issueData = new JsonSlurper().parse(jiraConn.inputStream)

// Step 2: Determine target URL and auth based on issue type
def issueType = issueData.fields.issuetype.name
def targetUrl

if (issueType == "[System] Incident") {
    targetUrl = SERVICENOW_INCIDENT_URL
    targetAuth = incidentAuth
} else if (issueType in ["[System] Service request", "[System] Service request with approvals"]) {
    targetUrl = SERVICENOW_REQUEST_URL
    targetAuth = requestAuth
} else {
    println "Issue type [$issueType] is not supported for ServiceNow sync."
    System.exit(1)  // Or handle differently if you want
}

// Step 3: Extract plain-text description, rebuild as ADF with one paragraph
def plainDescription = extractPlainTextFromADF(issueData.fields.description)
def singleParagraphADF = buildSingleParagraphADF(plainDescription)
issueData.fields.description = singleParagraphADF

// Step 4: Prepare and send POST request to ServiceNow
def conn = new URL(targetUrl).openConnection()
conn.setRequestMethod("POST")
conn.doOutput = true
conn.setRequestProperty("Content-Type", "application/json")

def body = JsonOutput.toJson(issueData)
println "Payload being sent to ServiceNow:\n" + JsonOutput.prettyPrint(body)

conn.outputStream.withWriter("UTF-8") { it.write(body) }

def responseCode = conn.responseCode
println "ServiceNow responded with [$responseCode]: ${conn.inputStream.text}"
