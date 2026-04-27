#  CC:HQ Speakers

# A mod for Minecraft, CC Tweaked Speaker's

uses cc tweaked's current speaker's and gives them a signif "Upgrade"

adds mp3 support, Wav, Ogg, PCM obv, HLS, TS.

function's 

playAudio(samples)
playAudio(samples, volume)

playNote(instrument, volume, pitch)
playSound(soundName)
playSound(soundName, volume)
playSound(soundName, volume, pitch)

speakPCM(samples)
speakPCM(samples, volume)

speakAudio(bytes)
speakAudio(bytes, volume)

speakFile(bytes)
speakFile(bytes, volume)

speakPacked(bytes)
speakPacked(bytes, volume)

speakWav(bytes)
speakWav(bytes, volume)

speakOgg(bytes)
speakOgg(bytes, volume)

speakMp3(bytes)
speakMp3(bytes, volume)

speakStream(url)
speakStream(url, volume)

speakHLS(url)
speakHLS(url, volume)

speakTS(url)
speakTS(url, volume)

speakStop()
speakStopAll()

speakVolume(volume)

setLooping(true)
setLooping(false)

speakIsPlaying()
speakQueueSize()

speakSampleRate()
speakMaxSamples()
speakMaxAudioBytes()
speakMaxOggBytes()
speakMaxFileBytes()
speakSupportedFiles()

isStreaming()
getStreamUrl()
getStreamFormats()
getStreamMeta()
getStreamTitle()
getStreamArtist()
getStreamSong()
getStreamStation()
getStreamGenre()
getStreamMetaSerial()

getPeripheralType()
getPos()

getSpeakerCount()
getSpeakers()
getSpeakerPos(index)

playNoteAll(instrument, volume, pitch)
playSoundAll(soundName)
playSoundAll(soundName, volume)
playSoundAll(soundName, volume, pitch)
playAudioAll(samples)
playAudioAll(samples, volume)

speakPCMAll(samples)
speakPCMAll(samples, volume)
speakAudioAll(bytes)
speakAudioAll(bytes, volume)
speakFileAll(bytes)
speakFileAll(bytes, volume)
speakPackedAll(bytes)
speakPackedAll(bytes, volume)
speakWavAll(bytes)
speakWavAll(bytes, volume)
speakOggAll(bytes)
speakOggAll(bytes, volume)
speakMp3All(bytes)
speakMp3All(bytes, volume)
speakVolumeAll(volume)
setLoopingAll(true)
setLoopingAll(false)
speakStreamAll(url)
speakStreamAll(url, volume)
speakHLSAll(url)
speakHLSAll(url, volume)
speakTSAll(url)
speakTSAll(url, volume)

playNoteAt(index, instrument, volume, pitch)
playSoundAt(index, soundName)
playSoundAt(index, soundName, volume)
playSoundAt(index, soundName, volume, pitch)
playAudioAt(index, samples)
playAudioAt(index, samples, volume)

speakPCMAt(index, samples)
speakPCMAt(index, samples, volume)
speakAudioAt(index, bytes)
speakAudioAt(index, bytes, volume)
speakFileAt(index, bytes)
speakFileAt(index, bytes, volume)
speakPackedAt(index, bytes)
speakPackedAt(index, bytes, volume)
speakWavAt(index, bytes)
speakWavAt(index, bytes, volume)
speakOggAt(index, bytes)
speakOggAt(index, bytes, volume)
speakMp3At(index, bytes)
speakMp3At(index, bytes, volume)
speakStreamAt(index, url)
speakStreamAt(index, url, volume)
speakHLSAt(index, url)
speakHLSAt(index, url, volume)
speakTSAt(index, url)
speakTSAt(index, url, volume)
speakStopAt(index)
