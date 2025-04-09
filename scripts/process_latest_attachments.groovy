@Grab('com.google.code.gson:gson:2.8.6')
import com.google.gson.*
import java.time.*
import java.time.format.DateTimeFormatter

def json = System.getenv("INPUT_ATTACHMENTS")
def issueKey = System.getenv("INPUT_ISSUE_KEY")

def gson = new Gson()
def attachments = gson.fromJson(json, List)

if (attachments == null || attachments.isEmpty()) {
    println "No attachments found for issue: $issueKey"
    return
}

// Parse and sort by creation time
def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SX")

attachments.sort { a, b ->
    ZonedDateTime.parse(b.created, formatter) <=> ZonedDateTime.parse(a.created, formatter)
}

// Get the most recent ones (could be more than one if timestamps are equal)
def latestTime = ZonedDateTime.parse(attachments[0].created, formatter)
def recentAttachments = attachments.findAll {
    ZonedDateTime.parse(it.created, formatter).isEqual(latestTime)
}

// Send all recent attachments to webhook
recentAttachments.each {
    def payload = [
        issueKey: issueKey,
        filename: it.filename,
        mimeType: it.mimeType,
        content: it.content,
        created: it.created
    ]

    def post = new URL("https://webhook-test.com/322cb6f50793b78c66e6facd5432a6f1").openConnection()
    post.setRequestMethod("POST")
    post.setDoOutput(true)
    post.setRequestProperty("Content-Type", "application/json")
    post.outputStream.withWriter { writer ->
        writer << gson.toJson(payload)
    }

    println "Sent attachment: ${it.filename}"
}
