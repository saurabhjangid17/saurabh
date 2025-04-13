#!/usr/bin/env groovy

@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1')
@Grab(group='org.apache.httpcomponents', module='httpclient', version='4.5.13')
@Grab(group='org.apache.httpcomponents', module='httpcore', version='4.4.13')
@Grab(group='commons-logging', module='commons-logging', version='1.2')

import groovy.json.JsonOutput
import groovyx.net.http.RESTClient
import groovyx.net.http.ContentType

def jiraUrl = 'https://atcisaurabhdemo.atlassian.net'
def jiraCred = 'Basic c2F1cmFiaGphbmdpZG1hdHJpeEBnbWFpbC5jb206QVRBVFQzeEZmR0YwWnprWVFLeE80alNSODdyeUcwM3U5dVpLS1BxWkJUa1hOc1VIc2pER3ZjcE5fNHIzd2dfVnJGRG5lY0lfSXhqODBoYkl0TDRhYTdISDVZc2FZVnQxa1hFaS0yQXlTVGwzMktTQUVWRExGUUZia3hSMUEweU1ZV0JPSlc2cTYtUEZTbHFSS2lTR2tnc21TSTZBdzhodlRxQ3dxRXJja3dmSzA0RnVCTkZXbk9JPUU3RUJBMDA3'
def issueKey = args[0]
def issueType = args[1]

def client = new RESTClient("${jiraUrl}/rest/api/3/issue/${issueKey}?expand=changelog")
client.headers['Authorization'] = jiraCred
client.headers['Accept'] = 'application/json'

def response = client.get()
def changelog = response.data.changelog
def lastChange = changelog?.histories?.sort { it.created }?.last()

def updatedFields = [:]
lastChange?.items?.each { item ->
    if (item?.toString) {
        updatedFields[item.field] = item.toString
    }
}

if (!updatedFields.isEmpty()) {
    def payload = [
        event : "issue_updated",
        key   : issueKey,
        fields: updatedFields
    ]

    println "📦 Final Payload:\n" + JsonOutput.prettyPrint(JsonOutput.toJson(payload))

    def snow = new RESTClient('https://webhook-test.com/fafa7f2a39e39b5a6ed89cd99d890a0e')
    snow.headers['Content-Type'] = 'application/json'
    snow.parser.'application/json' = snow.parser.'text/plain'

    def result = snow.post(
        body: JsonOutput.toJson(payload),
        requestContentType: ContentType.JSON
    )
    println "✅ Payload sent. Response status: ${result.status}"
} else {
    println "⚠️ No updatable fields found in the latest changelog."
}
