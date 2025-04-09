@Grab(group='com.google.code.gson', module='gson', version='2.8.9')
import com.google.gson.*
import java.time.*
import java.time.format.DateTimeFormatter

def issueKey = System.getenv("INPUT_ISSUE_KEY")
def comment = System.getenv("INPUT_COMMENT")
def attachmentJson = System.getenv("INPUT_ATTACHMENTS")

println "🔹 INPUT_ISSUE_KEY: ${issueKey}"
println "🔹 INPUT_COMMENT: ${comment}"
println "🔹 Raw INPUT_ATTACHMENTS JSON:\n${attachmentJson}"

if (!attachmentJson) {
    println "❌ No attachment JSON provided."
    System.exit(1)
}

def gson = new Gson()
def allAttachments = gson.fromJson(attachmentJson, List)

def recentTime = allAttachments.max { 
    ZonedDateTime.parse(it.created) 
}?.created?.substring(0,16)

def recentAttachments = allAttachments.findAll {
    it.created?.startsWith(recentTime)
}

def finalPayload = [
  event: "attachment_added",
  key: issueKey,
  fields: [
    comment: comment,
    attachment: recentAttachments
  ]
]

def webhookUrl = "https://webhook-test.com/322cb6f50793b78c66e6facd5432a6f1"

def payloadJson = gson.toJson(finalPayload)
println "📦 Final Payload:\n${payloadJson}"

def url = new URL(webhookUrl)
def conn = url.openConnection()
conn.setRequestMethod("POST")
conn.setDoOutput(true)
conn.setRequestProperty("Content-Type", "application/json")

conn.outputStream.withWriter("UTF-8") { it.write(payloadJson) }

def responseCode = conn.responseCode
println "✅ Sent payload to webhook. Response Code: ${responseCode}"
println conn.inputStream.text
