@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1')

import groovy.json.*
import groovyx.net.http.RESTClient
import static groovyx.net.http.ContentType.JSON

// Load input values
def issueKey = new File("issue_key.txt").text.trim()
def attachmentsFile = new File("attachments.json")
def attachments = new JsonSlurper().parseText(attachmentsFile.text)

if (!attachments || attachments.isEmpty()) {
    println "❌ No attachments found in input"
    System.exit(0)
}

// Step 1: Get the latest 'created' timestamp
def latestTime = attachments*.created.max()
println "✅ Latest attachment timestamp: $latestTime"

// Step 2: Get all attachments with that exact timestamp
def latestAttachments = attachments.findAll { it.created == latestTime }

println "📎 Found ${latestAttachments.size()} new attachment(s)"

// Step 3: Send each to ServiceNow
latestAttachments.each { att ->
    def payload = [
        issueKey: issueKey,
        filename: att.filename,
        mimeType: att.mimeType,
        content: att.content,
        created: att.created
    ]

    println "📤 Sending attachment: ${att.filename}"

    def client = new RESTClient(System.getenv("SERVICENOW_URL"))
    client.auth.basic System.getenv("SERVICENOW_USER"), System.getenv("SERVICENOW_PASS")

    try {
        def resp = client.post(
            path: "/api/now/table/incident", // change this to your correct SN endpoint
            body: JsonOutput.toJson(payload),
            requestContentType: JSON
        )
        println "✅ Uploaded to ServiceNow: ${att.filename} (status: ${resp.status})"
    } catch (Exception e) {
        println "❌ Failed to send ${att.filename} - ${e.message}"
    }
}
