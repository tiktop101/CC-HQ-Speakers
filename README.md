# CC:HQ Speaker's

# A mod for Minecraft, CC Tweaked Speaker's

Why you need this mod?

* It gives the ability of wayyy higher quality Audio, From the SAME CC Speaker's

uses cc tweaked's current speaker's and gives them a signif "Upgrade"
adds mp3 support, Wav, Ogg, PCM obv, HLS, TS.
function's

Playback
playAudio(samples) playAudio(samples, volume)
playNote(instrument, volume, pitch) playSound(soundName) playSound(soundName, volume) playSound(soundName, volume, pitch)
speakerPlay(audio) speakerPlay(audio, volume)
speakerStop()
speakerVolume(volume)
setLooping(true) setLooping(false)

Pause/Resume
speakerPause() speakerResume() speakerIsPaused()

Seek/Skip
speakerSeek(deltaSeconds)
speakerSkip() speakerSkip(seconds)
speakerSkipBack() speakerSkipBack(seconds)

Status/Info
speakerIsPlaying()

speakerQueueSize()

speakerProgress() 

speakerElapsedSamples()

speakerTotalSamples()

speakerSampleRate() 

speakerMaxSamples()

speakerMaxAudioBytes()

speakerMaxOggBytes()

speakerMaxFileBytes()

speakerSupportedFiles()

Streaming
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

Misc
getPeripheralType() getPos()

Multi-speaker
getSpeakerCount() getSpeakers() getSpeakerPos(index)

All (every speaker on the computer)
playNoteAll(instrument, volume, pitch) playSoundAll(soundName) playSoundAll(soundName, volume) playSoundAll(soundName, volume, pitch) playAudioAll(samples) playAudioAll(samples, volume)
speakerPlayAll(audio) speakerPlayAll(audio, volume) speakerStopAll() speakerVolumeAll(volume) setLoopingAll(true) setLoopingAll(false)
speakerPauseAll() speakerResumeAll() speakerSeekAll(deltaSeconds) speakerSkipAll() speakerSkipAll(seconds) speakerSkipBackAll() speakerSkipBackAll(seconds)

At (one speaker by index)
playNoteAt(index, instrument, volume, pitch) playSoundAt(index, soundName) playSoundAt(index, soundName, volume) playSoundAt(index, soundName, volume, pitch) playAudioAt(index, samples) playAudioAt(index, samples, volume)
speakerPlayAt(index, audio) speakerPlayAt(index, audio, volume) speakerStopAt(index)
speakerPauseAt(index) speakerResumeAt(index) speakerIsPausedAt(index) speakerSeekAt(index, deltaSeconds) speakerSkipAt(index) speakerSkipAt(index, seconds) speakerSkipBackAt(index) speakerSkipBackAt(index, seconds) speakerProgressAt(index)
