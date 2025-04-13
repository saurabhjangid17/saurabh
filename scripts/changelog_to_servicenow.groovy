@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1')
import groovyx.net.http.RESTClient
import groovyx.net.http.ContentType
import groovy.json.JsonOutput

// === Inputs from GitHub Actions ===
def issueKey = args[0]
def issueType = args[1]

// === Hardcoded Configuration ===
def jiraBaseUrl = 'https://atcisaurabhdemo.atlassian.net'
def jiraAuthHeader = 'Basic c2F1cmFiaGphbmdpZG1hdHJpeEBnbWFpbC5jb206QVRBVFQzeEZmR0YwWnprWVFLeE80alNSODdyeUcwM3U5dVpLS1BxWkJUa1hOc1VIc2pER3ZjcE5fNHIzd2dfVnJGRG5lY0lfSXhqODBoYkl0TDRhYTdISDVZc2FZVnQxa1hFaS0yQXlTVGwzMktTQUVWRExGUUZia3hSMUEweU1ZV0JPSlc2cTYtUEZTbHFSS2lTR2tnc21TSTZBdzhodlRxQ3dxRXJja3dmSzA0RnVCTkZXbk9JPUU3RUJBMDA3' // <-- Base64 of username:api_token
def servicenowUrl = 'https://webhook-test.com/fafa7f2a39e39b5a6ed89cd99d890a0e'

// === Jira API Client ===
def jira = new RESTClient(jiraBaseUrl)
jira.headers['Authorization'] = jiraAuthHeader
jira.headers['Accept'] = 'application/json'

// === Fetch changelog ===
def response = jira.get(
    path: "/rest/api/3/issue/${issueKey}",
    query: [expand: 'changelog']
)

def changelog = response.data.changelog?.histories

// === Extract 'to' values only
def simplifiedChanges = changelog?.collect { history ->
    def updates = history.items.findAll { it.toString != null }.collect { item ->
        [ field: item.field, to: item.toString ]
    }
    return updates ? [
        author : history.author?.displayName,
        created: history.created,
        updates: updates
    ] : null
}?.findAll { it != null }

// === Prepare Payload
def payload = [
    event  : 'issue_change_log',
    key    : issueKey,
    type   : issueType,
    changes: simplifiedChanges
]

// === Send to ServiceNow (no auth)
def servicenow = new RESTClient(servicenowUrl)
servicenow.headers['Content-Type'] = 'application/json'

def result = servicenow.post(
    body: JsonOutput.toJson(payload),
    requestContentType: ContentType.JSON
)

println "✅ Changelog sent to ServiceNow for ${issueKey}, response: ${result.status}"
