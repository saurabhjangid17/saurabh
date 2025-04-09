import groovy.json.*

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

// Step 2: Get all attachments with that timestamp
def latestAttachments = attachments.findAll { it.created == latestTime }

println "📎 Found ${latestAttachments.size()} new attachment(s)"

// Step 3: Send to dummy webhook
latestAttachments.each { att ->
    def payload = [
        issueKey: issueKey,
        filename: att.filename,
        mimeType: att.mimeType,
        content: att.content,
        created: att.created
    ]

    def webhookUrl = 'https://webhook-test.com/322cb6f50793b78c66e6facd5432a6f1'
    def connection = new URL(webhookUrl).openConnection()
    connection.setRequestMethod("POST")
    connection.setRequestProperty("Content-Type", "application/json")
    connection.doOutput = true

    def json = JsonOutput.toJson(payload)
    connection.outputStream.withWriter("UTF-8") { it.write(json) }

    def responseCode = connection.responseCode
    println "📤 Sent ${att.filename} to webhook. Response: $responseCode"
}
