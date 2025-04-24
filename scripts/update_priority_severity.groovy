@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1')
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovyx.net.http.RESTClient

// === Configuration ===
def JIRA_BASE_URL = "https://atcisaurabhdemo.atlassian.net"
def JIRA_AUTH = System.getenv("JIRA_AUTH") // Base64 encoded string

def ISSUE_KEY = args[0]

def matrix = [
    "Critical|Extensive"   : ["Highest", "Sev-1"],
    "Critical|Significant" : ["Highest", "Sev-1"],
    "Critical|Moderate"   : ["High", "Sev-2"],
    "Critical|Minor"      : ["High", "Sev-2"],
    "High|Extensive"      : ["Highest", "Sev-1"],
    "High|Significant"    : ["High", "Sev-2"],
    "High|Moderate"       : ["High", "Sev-2"],
    "High|Minor"          : ["Medium", "Sev-3"],
    "Medium|Extensive"    : ["High", "Sev-2"],
    "Medium|Significant"  : ["Medium", "Sev-3"],
    "Medium|Moderate"     : ["Medium", "Sev-3"],
    "Medium|Minor"        : ["Medium", "Sev-3"],
    "Low|Extensive"       : ["Low", "Sev-4"],
    "Low|Significant"     : ["Low", "Sev-4"],
    "Low|Moderate"        : ["Low", "Sev-4"],
    "Low|Minor"           : ["Low", "Sev-4"]
]

def client = new RESTClient("${JIRA_BASE_URL}")
client.headers['Authorization'] = "Basic ${JIRA_AUTH}"
client.headers['Content-Type'] = 'application/json'

// Get issue details
def response = client.get(path: "/rest/api/3/issue/${ISSUE_KEY}")
def fields = response.data.fields

def urgency = fields.customfield_10045?.value
def impact = fields.customfield_10004?.value
def key = "${urgency}|${impact}"

if (!matrix.containsKey(key)) {
    println "❌ Mapping not found for Urgency: '${urgency}', Impact: '${impact}'"
    return
}

def (priority, severity) = matrix[key]

def updateBody = JsonOutput.toJson([
    fields: [
        priority           : [name: priority],
        customfield_10051  : severity
    ]
])

client.put(path: "/rest/api/3/issue/${ISSUE_KEY}", body: updateBody)
println "✅ Updated ${ISSUE_KEY} with Priority: '${priority}' and Severity: '${severity}'"
