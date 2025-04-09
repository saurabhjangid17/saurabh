@Grab('com.google.code.gson:gson:2.8.6')
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

def json = System.getenv("INPUT_ATTACHMENTS")
def issueKey = System.getenv("INPUT_ISSUE_KEY")

println "🔹 INPUT_ISSUE_KEY: ${issueKey}"
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

def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SX")

// Sort by newest
attachments.sort { a, b ->
    ZonedDateTime.parse(b.created, formatter) <=> ZonedDateTime.parse(a.created, formatter)
}

// Group by latest minute
def latestTimestamp = ZonedDateTime.parse(attachments[0].created, formatter).truncatedTo(ChronoUnit.MINUTES)
def latestAttachments = attachments.findAll {
    ZonedDateTime.parse(it.created, formatter).truncatedTo(ChronoUnit.MINUTES).isEqual(latestTimestamp)
}

println "📦 Found ${latestAttachments.size()} attachment(s) created at ${latestTimestamp}"

// Build the payload
def attachmentArray = latestAttachments.collect { att ->
    [
        id       : att.id,
        filename : att.filename,
        mimeType : att.mimeType,
        content  : att.content,
        created  : att.created
    ]
}

def payload = [
    event: "attachment_added",
    key  : issueKey,
    fields: [
        attachment         : attachmentArray,
        customfield_10511  : ""
    ]
]

println "\n🚀 Sending payload to webhook:"
println gson.toJson(payload)

try {
    def connection = new URL("https://webhook-test.com/322cb6f50793b78c66e6facd5432a6f1").openConnection()
    connection.setRequestMethod("POST")
    connection.setDoOutput(true)
    connection.setRequestProperty("Content-Type", "application/json")
    connection.outputStream.withWriter("UTF-8") { writer ->
        writer << gson.toJson(payload)
    }

    def responseCode = connection.responseCode
    println "✅ Webhook responded with HTTP ${responseCode}"
} catch (Exception ex) {
    println "❌ Error sending to webhook: ${ex.message}"
}
