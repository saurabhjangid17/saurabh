@Grab('com.google.code.gson:gson:2.8.6')
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

def json = System.getenv("INPUT_ATTACHMENTS")
def issueKey = System.getenv("INPUT_ISSUE_KEY")
def issueType = System.getenv("INPUT_ISSUETYPE")

println "🔹 INPUT_ISSUE_KEY: ${issueKey}"
println "🧾 INPUT_ISSUETYPE: ${issueType}"
println "🔹 Raw INPUT_ATTACHMENTS JSON:\n${json}"

if (json == null || json.trim().isEmpty()) {
    println "❌ No attachment JSON provided."
    return
}

def gson = new Gson()
def type = new TypeToken<List<Map>>() {}.getType()

List<Map> attachments = []
try {
    attachments = gson.fromJson(json, type)
    println "✅ Parsed ${attachments.size()} attachments."
} catch (Exception e) {
    println "❌ Failed to parse attachments JSON: ${e.message}"
    return
}

if (!attachments || attachments.isEmpty()) {
    println "❌ No attachments found for issue: $issueKey"
    return
}

// Use formatter with optional fractional seconds
def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.S][.SS][.SSS]X")

// Sort attachments by creation date (newest first)
attachments.sort { a, b ->
    ZonedDateTime.parse(b.created, formatter) <=> ZonedDateTime.parse(a.created, formatter)
}

// Get latest minute-level timestamp
def latestTimestamp = ZonedDateTime.parse(attachments[0].created, formatter)
def latestMinute = latestTimestamp.truncatedTo(ChronoUnit.MINUTES)

println "📆 Matching all attachments created at: ${latestMinute} (ignoring seconds/millis)"

def latestAttachments = attachments.findAll {
    def attachmentTime = ZonedDateTime.parse(it.created, formatter).truncatedTo(ChronoUnit.MINUTES)
    attachmentTime.isEqual(latestMinute)
}

println "📦 Found ${latestAttachments.size()} attachment(s) in the latest minute group."

// Construct final payload in required structure
def finalPayload = [
    event : "attachment_added",
    key   : issueKey,
    fields: [
        attachment: latestAttachments.collect { att ->
            [
                id      : att.id,
                filename: att.filename,
                mimeType: att.mimeType,
                content : att.content,
                created : att.created
            ]
        }
    ]
]

println "\n🚀 Final webhook payload:"
println gson.toJson(finalPayload)

// Determine webhook URL based on issue type
def webhookUrl = issueType == "Incident"
    ? "https://webhook-test.com/59c5e24d727e72eb99dce94f458ab103"
    : "https://webhook.site/131b00c0-70fd-45aa-994e-3889be371724"

println "📡 Sending payload to: ${webhookUrl}"

// Send payload to selected webhook
try {
    def connection = new URL(webhookUrl).openConnection()
    connection.setRequestMethod("POST")
    connection.setDoOutput(true)
    connection.setRequestProperty("Content-Type", "application/json")
    connection.outputStream.withWriter("UTF-8") { writer ->
        writer << gson.toJson(finalPayload)
    }

    def responseCode = connection.responseCode
    println "✅ Webhook responded with HTTP ${responseCode}"
} catch (Exception ex) {
    println "❌ Error sending attachment to webhook: ${ex.message}"
}
