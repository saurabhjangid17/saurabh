#!/usr/bin/env groovy

@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1')
@Grab(group='org.apache.httpcomponents', module='httpclient', version='4.5.13')
@Grab(group='org.apache.httpcomponents', module='httpcore', version='4.4.13')
@Grab(group='commons-logging', module='commons-logging', version='1.2')

import groovy.json.JsonOutput
import groovyx.net.http.RESTClient
import groovyx.net.http.ContentType

// === Configuration ===
def jiraUrl = 'https://atcisaurabhdemo.atlassian.net'
def jiraCred = 'Basic c2F1cmFiaGphbmdpZG1hdHJpeEBnbWFpbC5jb206QVRBVFQzeEZmR0YwWnprWVFLeE80alNSODdyeUcwM3U5dVpLS1BxWkJUa1hOc1VIc2pER3ZjcE5fNHIzd2dfVnJGRG5lY0lfSXhqODBoYkl0TDRhYTdISDVZc2FZVnQxa1hFaS0yQXlTVGwzMktTQUVWRExGUUZia3hSMUEweU1ZV0JPSlc2cTYtUEZTbHFSS2lTR2tnc21TSTZBdzhodlRxQ3dxRXJja3dmSzA0RnVCTkZXbk9JPUU3RUJBMDA3' // Use actual base64 string
def servicenowUrl = 'https://webhook-test.com/fafa7f2a39e39b5a6ed89cd99d890a0e'

// === Script Inputs ===
def issueKey = args[0]
def issueType = args[1]

// === Call Jira API ===
def client = new RESTClient(jiraUrl)
client.headers['Authorization'] = jiraCred
client.headers['Accept'] = 'application/json'

println "🔍 Fetching changelog for ${issueKey}..."

def response = client.get(
    path : "/rest/api/3/issue/${issueKey}",
    query: [expand: 'changelog']
)

def changelog = response.data.changelog
def lastChange = changelog?.histories?.sort { it.created }?.last()

if (!lastChange) {
    println "⚠️ No changelog found for issue."
    System.exit(0)
}

// === Extract Changed Fields ===
def updatedFields = [:]
lastChange.items.each { item ->
    if (item?.toString) {
        updatedFields[item.field] = item.toString
    }
}

if (updatedFields.isEmpty()) {
    println "⚠️ No updated fields with 'toString' value."
    System.exit(0)
}

// === Build Payload ===
def payload = [
    event : "issue_updated",
    key   : issueKey,
    fields: updatedFields
]

println "📦 Final Payload:\n" + JsonOutput.prettyPrint(JsonOutput.toJson(payload))

// === Send to ServiceNow ===
def snow = new RESTClient(servicenowUrl)
snow.headers['Content-Type'] = 'application/json'
snow.parser.'application/json' = snow.parser.'text/plain' // Prevent JSON parse exception

def result = snow.post(
    body: JsonOutput.toJson(payload),
    requestContentType: ContentType.JSON
)

println "✅ Payload sent to ServiceNow. Response code: ${result.status}"
