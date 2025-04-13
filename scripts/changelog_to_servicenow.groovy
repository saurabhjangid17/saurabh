#!/usr/bin/env groovy

@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1')
import groovyx.net.http.RESTClient
import groovyx.net.http.ContentType
import static groovyx.net.http.Method.GET
import static groovyx.net.http.Method.POST
import groovy.json.JsonOutput

def issueKey = args[0]
def issueType = args[1]

// Config (store securely for prod use)
def jiraUrl = "https://atcisaurabhdemo.atlassian.net"
def jiraAuth = "Basic c2F1cmFiaGphbmdpZG1hdHJpeEBnbWFpbC5jb206QVRBVFQzeEZmR0YwWnprWVFLeE80alNSODdyeUcwM3U5dVpLS1BxWkJUa1hOc1VIc2pER3ZjcE5fNHIzd2dfVnJGRG5lY0lfSXhqODBoYkl0TDRhYTdISDVZc2FZVnQxa1hFaS0yQXlTVGwzMktTQUVWRExGUUZia3hSMUEweU1ZV0JPSlc2cTYtUEZTbHFSS2lTR2tnc21TSTZBdzhodlRxQ3dxRXJja3dmSzA0RnVCTkZXbk9JPUU3RUJBMDA3"
def servicenowUrl = "https://webhook-test.com/fafa7f2a39e39b5a6ed89cd99d890a0e"

// Initialize REST client
def client = new RESTClient(jiraUrl)
client.headers['Authorization'] = jiraAuth
client.headers['Content-Type'] = 'application/json'

// Fetch issue with changelog
def issueResp = client.get(
    path: "/rest/api/3/issue/${issueKey}",
    query: [expand: "changelog"],
    requestContentType: ContentType.JSON
)

def issue = issueResp.data
def changelog = issue.changelog?.histories ?: []
def updatedFields = [:]

// Determine changed fields
changelog.each { history ->
    history.items.each { item ->
        def field = item.field
        switch (field) {
            case "summary":
                updatedFields["summary"] = item.toString
                break
            case "description":
                updatedFields["description"] = [text: item.toString]
                break
            case "priority":
                updatedFields["priority"] = [
                    id  : issue.fields.priority?.id,
                    name: issue.fields.priority?.name
                ]
                break
            case "reporter":
                updatedFields["reporter"] = [
                    accountId    : issue.fields.reporter?.accountId,
                    displayName  : issue.fields.reporter?.displayName,
                    emailAddress : issue.fields.reporter?.emailAddress
                ]
                break
            case "assignee":
                updatedFields["assignee"] = [
                    accountId    : issue.fields.assignee?.accountId,
                    displayName  : issue.fields.assignee?.displayName,
                    emailAddress : issue.fields.assignee?.emailAddress
                ]
                break
            case "customfield_10045": // Urgency
            case "customfield_10004": // Impact
                updatedFields["urgencyImpact"] = true
                break
            case "customfield_10108":
                updatedFields["customfield_10108"] = [
                    value: issue.fields.customfield_10108
                ]
                break
            case "customfield_10544":
                updatedFields["customfield_10544"] = [
                    value: issue.fields.customfield_10544
                ]
                break
            case "customfield_10478":
                updatedFields["customfield_10478"] = issue.fields.customfield_10478
                break
            case "customfield_10066":
                updatedFields["customfield_10066"] = [
                    value: issue.fields.customfield_10066?.value,
                    id   : issue.fields.customfield_10066?.id,
                    child: [
                        value: issue.fields.customfield_10066?.child?.value,
                        id   : issue.fields.customfield_10066?.child?.id
                    ]
                ]
                break
            case "customfield_10512":
                updatedFields["customfield_10512"] = issue.fields.customfield_10512
                break
            case "customfield_10029":
                updatedFields["customfield_10029"] = [
                    requestType: [
                        name: issue.fields.customfield_10029
                    ]
                ]
                break
        }
    }
}

// Always send both urgency and impact if either is updated
if (updatedFields["urgencyImpact"]) {
    updatedFields["customfield_10045"] = [
        value: issue.fields.customfield_10045
    ]
    updatedFields["customfield_10004"] = [
        value: issue.fields.customfield_10004
    ]
    updatedFields.remove("urgencyImpact")
}

// Final payload
def payload = [
    event: "issue_updated",
    key  : issueKey,
    fields: updatedFields
]

println "Sending payload to ServiceNow:\n" + JsonOutput.prettyPrint(JsonOutput.toJson(payload))

// Send to ServiceNow (assuming no auth needed for demo)
def snow = new RESTClient(servicenowUrl)
snow.headers['Content-Type'] = 'application/json'

def response = snow.post(
    body: payload,
    requestContentType: ContentType.JSON
)

println "ServiceNow response: ${response.status} - ${response.statusLine.reasonPhrase}"
