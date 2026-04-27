package com.tom.hqspeaker.peripheral;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class HQSpeakerGroupPeripheral implements IPeripheral {
    private final Level world;
    private final BlockPos root;

    public HQSpeakerGroupPeripheral(Level world, BlockPos root) {
        this.world = world;
        this.root = root.immutable();
    }

    @Nonnull @Override public String getType() { return "speaker"; }
    @Override public boolean equals(@Nullable IPeripheral other) { return this == other; }

    @Override public void attach(@Nonnull IComputerAccess computer) { for (HQSpeakerPeripheral p : localMembers()) p.attach(computer); }
    @Override public void detach(@Nonnull IComputerAccess computer) { for (HQSpeakerPeripheral p : localMembers()) p.detach(computer); }

    private List<HQSpeakerPeripheral> localMembers() {
        List<HQSpeakerPeripheral> out = new ArrayList<>();
        for (BlockPos pos : HQSpeakerCluster.collect(world, root)) out.add(HQSpeakerPeripheralProvider.getOrCreate(world, pos));
        return out;
    }

    private List<HQSpeakerPeripheral> membersFor(@Nullable IComputerAccess computer) {
        List<HQSpeakerPeripheral> members = computer == null ? List.of() : HQSpeakerPeripheral.getSpeakersForComputer(computer.getID());
        return members.isEmpty() ? localMembers() : members;
    }

    private HQSpeakerPeripheral byIndex(@Nullable IComputerAccess computer, int index) throws LuaException {
        List<HQSpeakerPeripheral> ps = membersFor(computer);
        if (index < 1 || index > ps.size()) throw new LuaException("speaker index out of range");
        return ps.get(index - 1);
    }

    private HQSpeakerPeripheral leader(@Nullable IComputerAccess computer) throws LuaException {
        List<HQSpeakerPeripheral> ps = membersFor(computer);
        if (ps.isEmpty()) throw new LuaException("no speakers connected to this computer");
        return ps.get(0);
    }

    private boolean anyTrue(boolean current, boolean next) { return current || next; }

    @LuaFunction public final int getSpeakerCount(IComputerAccess computer) { return membersFor(computer).size(); }

    @LuaFunction
    public final List<Map<String, Object>> getSpeakers(IComputerAccess computer) {
        List<HQSpeakerPeripheral> ps = membersFor(computer);
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < ps.size(); i++) {
            Map<String, Object> e = new HashMap<>(ps.get(i).getPosMap());
            e.put("index", i + 1);
            out.add(e);
        }
        return out;
    }

    @LuaFunction public final Map<String, Object> getSpeakerPos(IComputerAccess computer, int index) throws LuaException { return byIndex(computer, index).getPosMap(); }
    @LuaFunction public final Map<String, Object> getPos(IComputerAccess computer) throws LuaException { return leader(computer).getPosMap(); }

    @LuaFunction public final boolean playNote(IComputerAccess computer, String instrument, double volume, double pitch) throws LuaException {
        boolean ok = false; for (HQSpeakerPeripheral p : membersFor(computer)) ok = anyTrue(ok, p.playNote(instrument, volume, pitch)); return ok;
    }

    @LuaFunction public final boolean playSound(IComputerAccess computer, String soundName, Optional<Double> volume, Optional<Double> pitch) throws LuaException {
        boolean ok = false; for (HQSpeakerPeripheral p : membersFor(computer)) ok = anyTrue(ok, p.playSound(soundName, volume, pitch)); return ok;
    }

    @LuaFunction public final boolean playAudio(IComputerAccess computer, IArguments args) throws LuaException {
        Map<?, ?> table = args.getTable(0); float volume = (float) args.optDouble(1, 1.0);
        int len = 0; while (table.containsKey((long) (len + 1)) || table.containsKey((double) (len + 1))) len++;
        boolean ok = false; for (HQSpeakerPeripheral p : membersFor(computer)) ok = anyTrue(ok, p.playAudioTable(table, len, volume)); return ok;
    }

    @LuaFunction public final boolean speakPCM(IComputerAccess computer, IArguments args) throws LuaException {
        Map<?, ?> table = args.getTable(0); float volume = (float) args.optDouble(1, 1.0);
        int len = 0; while (table.containsKey((long) (len + 1)) || table.containsKey((double) (len + 1))) len++;
        boolean ok = false; for (HQSpeakerPeripheral p : membersFor(computer)) ok = anyTrue(ok, p.speakPCMTable(table, len, volume)); return ok;
    }

    @LuaFunction public final boolean speakOgg(IComputerAccess computer, IArguments args) throws LuaException { boolean ok = false; for (HQSpeakerPeripheral p : membersFor(computer)) ok = anyTrue(ok, p.speakOgg(args)); return ok; }
    @LuaFunction public final boolean speakMp3(IComputerAccess computer, IArguments args) throws LuaException { boolean ok = false; for (HQSpeakerPeripheral p : membersFor(computer)) ok = anyTrue(ok, p.speakMp3(args)); return ok; }
    @LuaFunction public final boolean speakAudio(IComputerAccess computer, IArguments args) throws LuaException { boolean ok = false; for (HQSpeakerPeripheral p : membersFor(computer)) ok = anyTrue(ok, p.speakAudio(args)); return ok; }
    @LuaFunction public final boolean speakFile(IComputerAccess computer, IArguments args) throws LuaException { return speakAudio(computer, args); }
    @LuaFunction public final boolean speakPacked(IComputerAccess computer, IArguments args) throws LuaException { return speakAudio(computer, args); }
    @LuaFunction public final boolean speakWav(IComputerAccess computer, IArguments args) throws LuaException { boolean ok = false; for (HQSpeakerPeripheral p : membersFor(computer)) ok = anyTrue(ok, p.speakWav(args)); return ok; }

    @LuaFunction public final void speakStop(IComputerAccess computer) { for (HQSpeakerPeripheral p : membersFor(computer)) p.speakStop(); }
    @LuaFunction public final void speakStopAll(IComputerAccess computer) { for (HQSpeakerPeripheral p : membersFor(computer)) p.speakStop(); }
    @LuaFunction public final void speakVolume(IComputerAccess computer, IArguments args) throws LuaException { for (HQSpeakerPeripheral p : membersFor(computer)) p.speakVolume(args); }
    @LuaFunction public final void setLooping(IComputerAccess computer, boolean loop) { for (HQSpeakerPeripheral p : membersFor(computer)) p.setLooping(loop); }
    @LuaFunction public final boolean speakIsPlaying(IComputerAccess computer) throws LuaException { return leader(computer).speakIsPlaying(); }
    @LuaFunction public final int speakQueueSize(IComputerAccess computer) throws LuaException { return leader(computer).speakQueueSize(); }
    @LuaFunction public final int speakSampleRate(IComputerAccess computer) throws LuaException { return leader(computer).speakSampleRate(); }
    @LuaFunction public final int speakMaxSamples(IComputerAccess computer) throws LuaException { return leader(computer).speakMaxSamples(); }
    @LuaFunction public final int speakMaxAudioBytes(IComputerAccess computer) throws LuaException { return leader(computer).speakMaxAudioBytes(); }
    @LuaFunction public final int speakMaxOggBytes(IComputerAccess computer) throws LuaException { return leader(computer).speakMaxOggBytes(); }
    @LuaFunction public final int speakMaxFileBytes(IComputerAccess computer) throws LuaException { return leader(computer).speakMaxFileBytes(); }
    @LuaFunction public final String[] speakSupportedFiles(IComputerAccess computer) throws LuaException { return leader(computer).speakSupportedFiles(); }

    @LuaFunction public final boolean speakStream(IComputerAccess computer, String url, Optional<Double> volume) throws LuaException { boolean ok = false; for (HQSpeakerPeripheral p : membersFor(computer)) ok = anyTrue(ok, p.speakStream(url, volume)); return ok; }
    @LuaFunction public final boolean speakHLS(IComputerAccess computer, String url, Optional<Double> volume) throws LuaException { boolean ok = false; for (HQSpeakerPeripheral p : membersFor(computer)) ok = anyTrue(ok, p.speakHLS(url, volume)); return ok; }
    @LuaFunction public final boolean speakTS(IComputerAccess computer, String url, Optional<Double> volume) throws LuaException { boolean ok = false; for (HQSpeakerPeripheral p : membersFor(computer)) ok = anyTrue(ok, p.speakTS(url, volume)); return ok; }

    @LuaFunction public final boolean isStreaming(IComputerAccess computer) throws LuaException { return leader(computer).isStreaming(); }
    @LuaFunction public final Optional<String> getStreamUrl(IComputerAccess computer) throws LuaException { return leader(computer).getStreamUrl(); }
    @LuaFunction public final Map<String, Object> getStreamFormats(IComputerAccess computer) throws LuaException { return leader(computer).getStreamFormats(); }
    @LuaFunction public final Map<String, Object> getStreamMeta(IComputerAccess computer) throws LuaException { return leader(computer).getStreamMeta(); }
    @LuaFunction public final String getStreamTitle(IComputerAccess computer) throws LuaException { return leader(computer).getStreamTitle(); }
    @LuaFunction public final String getStreamArtist(IComputerAccess computer) throws LuaException { return leader(computer).getStreamArtist(); }
    @LuaFunction public final String getStreamSong(IComputerAccess computer) throws LuaException { return leader(computer).getStreamSong(); }
    @LuaFunction public final String getStreamStation(IComputerAccess computer) throws LuaException { return leader(computer).getStreamStation(); }
    @LuaFunction public final String getStreamGenre(IComputerAccess computer) throws LuaException { return leader(computer).getStreamGenre(); }
    @LuaFunction public final long getStreamMetaSerial(IComputerAccess computer) throws LuaException { return leader(computer).getStreamMetaSerial(); }

    @LuaFunction public final boolean playNoteAt(IComputerAccess computer, int index, String instrument, double volume, double pitch) throws LuaException { return byIndex(computer, index).playNote(instrument, volume, pitch); }
    @LuaFunction public final boolean playSoundAt(IComputerAccess computer, int index, String soundName, Optional<Double> volume, Optional<Double> pitch) throws LuaException { return byIndex(computer, index).playSound(soundName, volume, pitch); }
    @LuaFunction public final boolean playAudioAt(IComputerAccess computer, int index, Map<?, ?> audio, Optional<Double> volume) throws LuaException { int len = 0; while (audio.containsKey((long) (len + 1)) || audio.containsKey((double) (len + 1))) len++; return byIndex(computer, index).playAudioTable(audio, len, volume.orElse(1.0).floatValue()); }
    @LuaFunction public final boolean speakPCMAt(IComputerAccess computer, int index, Map<?, ?> audio, Optional<Double> volume) throws LuaException { int len = 0; while (audio.containsKey((long) (len + 1)) || audio.containsKey((double) (len + 1))) len++; return byIndex(computer, index).speakPCMTable(audio, len, volume.orElse(1.0).floatValue()); }
    @LuaFunction public final boolean speakStreamAt(IComputerAccess computer, int index, String url, Optional<Double> volume) throws LuaException { return byIndex(computer, index).speakStream(url, volume); }
    @LuaFunction public final boolean speakHLSAt(IComputerAccess computer, int index, String url, Optional<Double> volume) throws LuaException { return byIndex(computer, index).speakHLS(url, volume); }
    @LuaFunction public final boolean speakTSAt(IComputerAccess computer, int index, String url, Optional<Double> volume) throws LuaException { return byIndex(computer, index).speakTS(url, volume); }
    @LuaFunction public final boolean speakMp3At(IComputerAccess computer, int index, IArguments args) throws LuaException { return byIndex(computer, index).speakMp3(args); }
    @LuaFunction public final boolean speakOggAt(IComputerAccess computer, int index, IArguments args) throws LuaException { return byIndex(computer, index).speakOgg(args); }
    @LuaFunction public final boolean speakAudioAt(IComputerAccess computer, int index, IArguments args) throws LuaException { return byIndex(computer, index).speakAudio(args); }
    @LuaFunction public final boolean speakFileAt(IComputerAccess computer, int index, IArguments args) throws LuaException { return speakAudioAt(computer, index, args); }
    @LuaFunction public final boolean speakPackedAt(IComputerAccess computer, int index, IArguments args) throws LuaException { return speakAudioAt(computer, index, args); }
    @LuaFunction public final boolean speakWavAt(IComputerAccess computer, int index, IArguments args) throws LuaException { return byIndex(computer, index).speakWav(args); }
    @LuaFunction public final void speakStopAt(IComputerAccess computer, int index) throws LuaException { byIndex(computer, index).speakStop(); }
}
