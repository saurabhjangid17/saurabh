@Grab('com.google.code.gson:gson:2.8.6')
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.time.*
import java.time.format.DateTimeFormatter

def json = System.getenv("INPUT_ATTACHMENTS")
def issueKey = System.getenv("INPUT_ISSUE_KEY")

if (json == null || json.trim().isEmpty()) {
    println "No attachment JSON provided."
    return
}

def gson = new Gson()

// Convert JSON string into a List of Maps
def type = new TypeToken<List<Map>>() {}.getType()
def attachments = gson.fromJson(json, type)

if (!attachments || attachments.isEmpty()) {
    println "No attachments found for issue: $issueKey"
    return
}

def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SX")

// Sort attachments by timestamp descending
attachments.sort { a, b ->
    ZonedDateTime.parse(b.created, formatter) <=> ZonedDateTime.parse(a.created, formatter)
}

// Get the most recent timestamp
def latestTimestamp = ZonedDateTime.parse(attachments[0].created, formatter)

// Filter attachments with the same timestamp
def latestAttachments = attachments.findAll {
    ZonedDateTime.parse(it.created, formatter).isEqual(latestTimestamp)
}

// Send each recent attachment to your webhook
latestAttachments.each { att ->
    def payload = [
        issueKey : issueKey,
        filename : att.filename,
        mimeType : att.mimeType,
        content  : att.content,
        created  : att.created
    ]

    println "Sending attachment: ${att.filename} (Created: ${att.created})"

    def connection = new URL("https://webhook-test.com/322cb6f50793b78c66e6facd5432a6f1").openConnection()
    connection.setRequestMethod("POST")
    connection.setDoOutput(true)
    connection.setRequest
