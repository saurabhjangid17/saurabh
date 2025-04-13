@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1')
import groovyx.net.http.RESTClient
import groovyx.net.http.ContentType
import groovy.json.JsonOutput

def issueKey = args[0]
def issueType = args[1]

def jiraBaseUrl = 'https://your-domain.atlassian.net'
def jiraAuthHeader = "Basic ${System.getenv("JIRA_AUTH")}"
def servicenowUrl = System.getenv("SERVICENOW_URL")

def jira = new RESTClient(jiraBaseUrl)
jira.headers['Authorization'] = jiraAuthHeader
jira.headers['Accept'] = 'application/json'

def response = jira.get(
    path: "/rest/api/3/issue/${issueKey}",
    query: [expand: 'changelog']
)

def changelog = response.data.changelog?.histories

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

def payload = [
    event  : 'issue_change_log',
    key    : issueKey,
    type   : issueType,
    changes: simplifiedChanges
]

def servicenow = new RESTClient(servicenowUrl)
servicenow.headers['Content-Type'] = 'application/json'

def result = servicenow.post(
    body: JsonOutput.toJson(payload),
    requestContentType: ContentType.JSON
)

println "✅ Changelog sent to ServiceNow for ${issueKey}, response: ${result.status}"
