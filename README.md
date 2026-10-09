# CC:HQ Speaker's

### A mod for Minecraft, CC Tweaked Speaker's

## Why you need this mod?

* It gives the ability of wayyy higher quality Audio, From the SAME CC Speaker's
* If using Sound Physics Remastered, set **update moving sounds** to `true` so it muffles it through walls etc etc

Uses CC Tweaked's current speaker's and gives them a signif "Upgrade".

Adds MP3 support, WAV, OGG, PCM obv, HLS, TS.

---

## Functions

### Playback

```lua
playAudio(samples)
playAudio(samples, volume)

playNote(instrument, volume, pitch)

playSound(soundName)
playSound(soundName, volume)
playSound(soundName, volume, pitch)

speakerPlay(audio)
speakerPlay(audio, volume)

speakerStop()
speakerVolume(volume)

setLooping(true)
setLooping(false)
```

### Channels

8 per speaker by default. Channel 1 = normal speaker.

```lua
speakerPlayChannel(channel, audio)
speakerPlayChannel(channel, audio, volume)
speakerPlayChannel(channel, audio, volume, loop)

speakerChannel(channel, "stop")
speakerChannel(channel, "pause")
speakerChannel(channel, "resume")
speakerChannel(channel, "volume", volume)

speakerChannelStatus(channel) -- { playing, paused, looping }
```

* `channel` 0 in `speakerChannel` = every channel
* Event: `hqspeaker_channel_empty`, name, channel

**Examples**

```lua
speakerPlayChannel(2, bytes, 1.0, true)  -- loops music on channel 2
speakerPlayChannel(3, sfx)               -- sfx on top
speakerChannel(2, "volume", 0.3)         -- turns channel 2 down
speakerChannel(0, "pause")               -- pauses all channels
```

### Pause/Resume

```lua
speakerPause()
speakerResume()
speakerIsPaused()
```

### Seek/Skip

```lua
speakerSeek(deltaSeconds)

speakerSkip()
speakerSkip(seconds)

speakerSkipBack()
speakerSkipBack(seconds)
```

### Status/Info

```lua
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
```

### Streaming

```lua
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
```

### Misc

```lua
getPeripheralType()
getPos()
```

### Multi-speaker

```lua
getSpeakerCount()
getSpeakers()
getSpeakerPos(index)
```

### All (every speaker on the computer)

```lua
playNoteAll(instrument, volume, pitch)

playSoundAll(soundName)
playSoundAll(soundName, volume)
playSoundAll(soundName, volume, pitch)

playAudioAll(samples)
playAudioAll(samples, volume)

speakerPlayAll(audio)
speakerPlayAll(audio, volume)

speakerStopAll()
speakerVolumeAll(volume)

setLoopingAll(true)
setLoopingAll(false)

speakerPauseAll()
speakerResumeAll()

speakerSeekAll(deltaSeconds)

speakerSkipAll()
speakerSkipAll(seconds)

speakerSkipBackAll()
speakerSkipBackAll(seconds)
```

### At (one speaker by index)

```lua
playNoteAt(index, instrument, volume, pitch)

playSoundAt(index, soundName)
playSoundAt(index, soundName, volume)
playSoundAt(index, soundName, volume, pitch)

playAudioAt(index, samples)
playAudioAt(index, samples, volume)

speakerPlayAt(index, audio)
speakerPlayAt(index, audio, volume)

speakerStopAt(index)

speakerPauseAt(index)
speakerResumeAt(index)
speakerIsPausedAt(index)

speakerSeekAt(index, deltaSeconds)

speakerSkipAt(index)
speakerSkipAt(index, seconds)

speakerSkipBackAt(index)
speakerSkipBackAt(index, seconds)

speakerProgressAt(index)
```

---

## Config

`hqspeaker-server.toml`

| Section | Option | Default | Notes |
|---|---|---|---|
| `[formats]` | `pcmPlayable` | `true` | |
| `[formats]` | `mp3Playable` | `true` | |
| `[formats]` | `oggPlayable` | `true` | |
| `[formats]` | `hlsPlayable` | `true` | |
| `[formats]` | `tsPlayable` | `true` | |
| `[formats]` | `wavPlayable` | `true` | |
| `[limits]` | `maxAudioFileSizeMB` | `8` | max 128 |
| `[limits]` | `maxVolume` | `1.0` | max 16 |
| `[limits]` | `maxChannels` | `8` | max 32 |
