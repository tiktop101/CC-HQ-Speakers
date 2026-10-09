# CC:HQ Speaker's

# A mod for Minecraft, CC Tweaked Speaker's

Why you need this mod?

* It gives the ability of wayyy higher quality Audio, From the SAME CC Speaker's

* If Using sound physics remastered, set update moving sounds to true so it muffles it through walls etc etc

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

Channels (8 per speaker by default, channel 1 = normal speaker)
speakerPlayChannel(channel, audio) speakerPlayChannel(channel, audio, volume) speakerPlayChannel(channel, audio, volume, loop)
speakerChannel(channel, "stop") speakerChannel(channel, "pause") speakerChannel(channel, "resume") speakerChannel(channel, "volume", volume)
speakerChannelStatus(channel) > { playing, paused, looping }
channel 0 in speakerChannel = every channel
event: hqspeaker_channel_empty, name, channel

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

Channel examples
speakerPlayChannel(2, bytes, 1.0, true) > loops music on channel 2
speakerPlayChannel(3, sfx) > sfx on top
speakerChannel(2, "volume", 0.3) > turns channel 2 down
speakerChannel(0, "pause") > pauses all channels

Config (hqspeaker-server.toml)
[formats] pcmPlayable, mp3Playable, oggPlayable, hlsPlayable, tsPlayable, wavPlayable
[limits] maxAudioFileSizeMB (default 8), maxVolume (default 1.0, max 16), maxChannels (default 8, max 32)
