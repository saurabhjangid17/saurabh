@Grab(group='com.google.code.gson', module='gson', version='2.10.1')
import com.google.gson.*
import java.time.*
import java.time.format.DateTimeFormatter

def issueKey = System.getenv("INPUT_ISSUE_KEY")
def comment = System.getenv("INPUT_COMMENT")
def customField = System.getenv("INPUT_CUSTOMFIELD_10511")
def attachmentsJson = System.getenv("INPUT_ATTACHMENTS")
def webhookUrl = System.getenv("WEBHOOK_URL")

println "🔹 Issue Key: ${issueKey}"
println "🔹 Comment: ${comment}"
println "🔹 Custom Field: ${customField}"
println "🔹 Raw Attachments JSON: ${attachmentsJson}"

if (!attachmentsJson) {
    println "❌ No attachment JSON provided."
    System.exit(1)
}

def gson = new Gson()
def allAttachments = gson.fromJson(attachmentsJson, List)

if (!allAttachments || allAttachments.isEmpty()) {
    println "❌ No valid attachments found."
    System.exit(1)
}

// Extract latest timestamp up to minutes
def latestTimestamp = allAttachments
    .collect { it.created }
    .max()
    .substring(0, 16)

def latestAttachments = allAttachments.findAll {
    it.created.startsWith(latestTimestamp)
}

println "✅ Latest attachments (${latestAttachments.size()}):"
latestAttachments.each { println "- ${it.filename}" }

def payload = [
    event: "attachment_added",
    key: issueKey,
    fields: [
        comment: comment ?: "",
        attachment: latestAttachments,
        customfield_10511: customField ?: ""
    ]
]

def jsonPayload = gson.toJson(payload)
println "📦 Final Payload:\n${jsonPayload}"

// Send to webhook
def url = new URL(webhookUrl)
def connection = url.openConnection()
connection.setRequestMethod("POST")
connection.setRequestProperty("Content-Type", "application/json")
connection.setDoOutput(true)

connection.outputStream.withWriter("UTF-8") { writer ->
    writer.write(jsonPayload)
}

def responseCode = connection.getResponseCode()
println "📨 Webhook response: ${responseCode} ${connection.getResponseMessage()}"

if (responseCode >= 400) {
    println "❌ Error sending payload: ${connection.errorStream.text}"
    System.exit(1)
}
