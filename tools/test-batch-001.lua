local requestedName = ...

local speaker
local name

if requestedName then
    if not peripheral.isPresent(requestedName) then
        error("No peripheral named " .. requestedName, 0)
    end
    if not peripheral.hasType(requestedName, "speaker") then
        error(requestedName .. " is not a speaker peripheral", 0)
    end
    speaker = peripheral.wrap(requestedName)
    name = requestedName
else
    speaker = peripheral.find("speaker")
    if not speaker then error("No speaker peripheral found", 0) end
    name = peripheral.getName(speaker)
end

local methods = peripheral.getMethods(name) or {}
table.sort(methods)

local seen = {}
for _, method in ipairs(methods) do seen[method] = true end

local required = { "highAudioProbe", "playNote", "playSound", "playAudio", "stop" }
local missing = {}
for _, method in ipairs(required) do
    if not seen[method] then table.insert(missing, method) end
end

print(("TEST-BATCH-001 target=%s type=%s"):format(name, table.concat({ peripheral.getType(name) }, ",")))
print("methods=" .. table.concat(methods, ","))
if #missing > 0 then
    error("Missing required methods: " .. table.concat(missing, ", "), 0)
end

local function retryBoolean(label, fn)
    for attempt = 1, 5 do
        local ok, result = pcall(fn)
        if not ok then
            error(label .. " raised an error: " .. tostring(result), 0)
        end
        if result ~= false then
            print(("%s accepted on attempt %d result=%s"):format(label, attempt, tostring(result)))
            return result
        end
        print(("%s busy on attempt %d; retrying"):format(label, attempt))
        sleep(0.2)
    end
    error(label .. " returned false on every retry", 0)
end

local before = speaker.highAudioProbe()
print("probe.before=" .. textutils.serialize(before, { compact = true }))

local noteOk = retryBoolean("playNote", function()
    return speaker.playNote("harp", 0.5, 12)
end)
sleep(0.2)
speaker.stop()
sleep(0.2)

local soundOk = retryBoolean("playSound", function()
    return speaker.playSound("minecraft:block.note_block.pling", 0.5, 1.0)
end)
sleep(0.25)
speaker.stop()
sleep(0.2)

local audio = {}
for i = 1, 4800 do
    audio[i] = (i % 48 < 24) and 32 or -32
end
local audioOk = retryBoolean("playAudio", function()
    return speaker.playAudio(audio, 0.4)
end)
sleep(0.2)
speaker.stop()
sleep(0.2)

local after = speaker.highAudioProbe()
print("probe.after=" .. textutils.serialize(after, { compact = true }))

local identityStable = before.nativeSource == after.nativeSource
    and before.identityHash == after.identityHash
    and before.runtimeClass == after.runtimeClass

local summary = {
    target = name,
    integration = after.integration,
    highAudioProbe = true,
    nativeMethodsPresent = #missing == 0,
    playNote = noteOk,
    playSound = soundOk,
    playAudio = audioOk,
    stopCalled = true,
    identityStableWithinRun = identityStable,
    emitterKind = after.emitterKind,
    runtimeClass = after.runtimeClass,
    nativeSource = after.nativeSource,
    identityHash = after.identityHash,
    computerId = after.computerId,
    attachment = after.attachment,
}

print("summary=" .. textutils.serialize(summary, { compact = true }))

if not identityStable then
    error("Peripheral identity changed within one uninterrupted test run; inspect summary and server log", 0)
end

print("TEST-BATCH-001 local checks PASS")
