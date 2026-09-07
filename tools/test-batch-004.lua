local args = { ... }
local action = args[1]
local speakerName = args[2]

if action ~= "play" and action ~= "stop" then
    error("usage: test-batch-004 <play|stop> <speaker peripheral name> [wav path]", 0)
end

if not speakerName then error("speaker peripheral name is required", 0) end
local speaker = peripheral.wrap(speakerName)
if not speaker or not speaker.highAudioUploadBegin then
    error("the named peripheral is not a placed speaker with HighAudio methods", 0)
end

if action == "stop" then
    print(speaker.highAudioStop() and "HighAudio session stopped" or "No active HighAudio session")
    return
end

local path = args[3]
if not path or not fs.exists(path) or fs.isDir(path) then error("a readable WAV file path is required", 0) end

local size = fs.getSize(path)
local uploadId = speaker.highAudioUploadBegin(size)
local handle = fs.open(path, "rb")
if not handle then
    speaker.highAudioUploadAbort(uploadId)
    error("could not open WAV file", 0)
end

local ok, result = pcall(function()
    while true do
        local chunk = handle.read(16 * 1024)
        if not chunk then break end
        speaker.highAudioUploadWrite(uploadId, chunk)
    end
    handle.close()
    local contentId = speaker.highAudioUploadFinish(uploadId)
    local sessionId = speaker.highAudioPlay(contentId)
    return { contentId = contentId, sessionId = sessionId }
end)

if not ok then
    pcall(handle.close)
    pcall(speaker.highAudioUploadAbort, uploadId)
    error(result, 0)
end

print("ContentId: " .. result.contentId)
print("SessionId: " .. result.sessionId)
print("Run 'test-batch-004 stop " .. speakerName .. "' to test authoritative stop.")
