package com.tom.hqspeaker.client;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.network.HQSpeakerAudioPacket;
import com.tom.hqspeaker.vs2.VS2TransformHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4dc;
import org.joml.Vector3d;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class HQSpeakerClientHandler {

    private static final ResourceLocation HQ_SOUND_LOC =
        new ResourceLocation("hqspeaker", "hq_speaker");

    private static final ConcurrentHashMap<UUID, SpeakerState> states =
        new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, SyncGroupState> syncGroups =
        new ConcurrentHashMap<>();

    private static final int MAX_ACTIVE_SPEAKERS = 256;
    private static final int MAX_SYNC_GROUPS = 128;

    public static void receive(HQSpeakerAudioPacket pkt) {
        if (!isPacketSafe(pkt)) return;

        if (!states.containsKey(pkt.source) && states.size() >= MAX_ACTIVE_SPEAKERS) {
            HQSpeakerMod.warn("HQSpeakerClientHandler: dropping audio; too many active speakers");
            return;
        }

        states.computeIfAbsent(pkt.source, id -> new SpeakerState()).push(pkt);
        if (pkt.syncGroupId != null && pkt.syncGroupSize > 0) {
            if (!syncGroups.containsKey(pkt.syncGroupId) && syncGroups.size() >= MAX_SYNC_GROUPS) {
                HQSpeakerMod.warn("HQSpeakerClientHandler: dropping sync group; too many active groups");
                return;
            }
            syncGroups.computeIfAbsent(pkt.syncGroupId, id -> new SyncGroupState(pkt.syncGroupId, pkt.syncGroupSize)).add(pkt.source, pkt.syncGroupSize);
        }
    }

    private static boolean isPacketSafe(HQSpeakerAudioPacket pkt) {
        if (pkt == null || pkt.source == null || pkt.format == null) return false;
        if (!Float.isFinite(pkt.volume) || !Float.isFinite(pkt.x) || !Float.isFinite(pkt.y) || !Float.isFinite(pkt.z)) return false;
        if (pkt.isStreamingFormat()) {
            return pkt.streamUrl != null && !pkt.streamUrl.isBlank() && pkt.streamUrl.length() <= HQSpeakerAudioPacket.MAX_URL_CHARS;
        }
        return pkt.data != null && pkt.data.length > 0 && pkt.data.length <= HQSpeakerAudioPacket.MAX_BYTES;
    }

    public static void tick() {
        Level level = Minecraft.getInstance().level;
        tickSyncGroups(level);
        states.forEach((id, state) -> {
            state.tryStart(level);
            state.tickPosition(level);
            if (state.isDone()) {
                states.remove(id);
                if (state.syncGroupId != null) {
                    SyncGroupState group = syncGroups.get(state.syncGroupId);
                    if (group != null) group.remove(id);
                }
            }
        });
    }

    public static void stop(UUID source) {
        SpeakerState s = states.remove(source);
        if (s != null) {
            if (s.syncGroupId != null) {
                SyncGroupState group = syncGroups.get(s.syncGroupId);
                if (group != null) group.remove(source);
            }
            s.stop();
        }
    }

    public static void stopAll() {
        states.forEach((id, s) -> s.stop());
        states.clear();
        syncGroups.clear();
    }

    public static boolean isPlaying(UUID source) {
        SpeakerState s = states.get(source);
        return s != null && !s.isDone();
    }


    private static void tickSyncGroups(Level level) {
        if (level == null) return;
        final long now = level.getGameTime();
        syncGroups.forEach((groupId, group) -> {
            group.tick(level, now);
            if (group.canRemove()) syncGroups.remove(groupId, group);
        });
    }

    private static final class SyncGroupState {
        private final UUID groupId;
        private volatile int expectedCount;
        private final java.util.Set<UUID> members = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private volatile long armedStartTick = -1L;
        private volatile boolean started = false;

        private SyncGroupState(UUID groupId, int expectedCount) {
            this.groupId = groupId;
            this.expectedCount = Math.max(1, expectedCount);
        }

        void add(UUID source, int expectedCount) {
            this.expectedCount = Math.max(this.expectedCount, expectedCount);
            this.members.add(source);
        }

        void remove(UUID source) {
            this.members.remove(source);
        }

        boolean canRemove() {
            if (!started) return false;
            for (UUID member : members) {
                SpeakerState state = states.get(member);
                if (state != null && !state.isDone()) return false;
            }
            return true;
        }

        void tick(Level level, long now) {
            java.util.ArrayList<SpeakerState> readyMembers = new java.util.ArrayList<>();
            long maxStartTick = 0L;
            int present = 0;
            for (UUID member : members) {
                SpeakerState state = states.get(member);
                if (state == null) continue;
                present++;
                if (state.lastPkt != null) maxStartTick = Math.max(maxStartTick, state.startTick);
                if (state.isReadyToStart()) readyMembers.add(state);
            }

            if (present == 0) return;

            boolean allPresent = present >= expectedCount;
            boolean allReady = readyMembers.size() >= expectedCount;
            if (!started && armedStartTick < 0L && allPresent && allReady) {
                armedStartTick = Math.max(maxStartTick, now + 1L);
            }

            if (!started && armedStartTick >= 0L && now >= armedStartTick) {
                for (SpeakerState state : readyMembers) state.forceStart(level);
                started = true;
                HQSpeakerMod.log("HQSpeakerClientHandler: started sync group " + groupId + " with " + readyMembers.size() + "/" + expectedCount + " speakers at tick " + now);
            }
        }
    }

    
    private static final class SpeakerState {
        private HQAudioStream        stream;
        private HQSpeakerSound       sound;
        private HQSpeakerAudioPacket lastPkt;
        private boolean isStreaming = false;
        private long startTick = 0L;
        private UUID syncGroupId = null;
        private int syncGroupSize = 0;

        void push(HQSpeakerAudioPacket pkt) {
            boolean pktIsStreaming = pkt.isStreamingFormat();
            if (isStreaming != pktIsStreaming && stream != null) {
                stream.close();
                stream = null;
                sound  = null;
            }
            isStreaming = pktIsStreaming;

            if (stream != null && stream.isDrained()) {
                stream = null;
                sound  = null;
            }
            if (stream == null) stream = new HQAudioStream();
            stream.push(pkt);
            lastPkt = pkt;
            startTick = pkt.startTick;
            syncGroupId = pkt.syncGroupId;
            syncGroupSize = pkt.syncGroupSize;

            Minecraft mc = Minecraft.getInstance();
            if (sound != null && mc.getSoundManager().isActive(sound)) {
                sound.update(pkt.volume, pkt.x, pkt.y, pkt.z);
            }
        }

        boolean isReadyToStart() {
            return stream != null && lastPkt != null && stream.isStreamReady();
        }

        void tryStart(Level level) {
            if (syncGroupId != null && syncGroupSize > 1) return;
            if (!isReadyToStart()) return;
            if (level != null && startTick > 0L && level.getGameTime() < startTick) return;

            Minecraft mc = Minecraft.getInstance();
            if (sound != null && mc.getSoundManager().isActive(sound)) return;

            
            float wx = lastPkt.x;
            float wy = lastPkt.y;
            float wz = lastPkt.z;

            sound = new HQSpeakerSound(stream, lastPkt, wx, wy, wz);
            mc.getSoundManager().play(sound);

            HQSpeakerMod.log("HQSpeakerClientHandler: "
                + (isStreaming ? "Started/restarted streaming" : "Started playback")
                + " at (" + wx + "," + wy + "," + wz + ")");
        }

        void forceStart(Level level) {
            if (!isReadyToStart()) return;
            Minecraft mc = Minecraft.getInstance();
            if (sound != null && mc.getSoundManager().isActive(sound)) return;

            float wx = lastPkt.x;
            float wy = lastPkt.y;
            float wz = lastPkt.z;

            sound = new HQSpeakerSound(stream, lastPkt, wx, wy, wz);
            mc.getSoundManager().play(sound);

            HQSpeakerMod.log("HQSpeakerClientHandler: sync-started playback at (" + wx + "," + wy + "," + wz + ")");
        }

        void tickPosition(Level level) {
            if (sound == null || lastPkt == null || level == null) return;
            Minecraft mc = Minecraft.getInstance();
            if (!mc.getSoundManager().isActive(sound)) return;
            if (!VS2TransformHelper.isVS2Loaded()) return;

            BlockPos blockPos = new BlockPos(lastPkt.blockX, lastPkt.blockY, lastPkt.blockZ);
            Object ship = VS2TransformHelper.getShipManagingBlock(level, blockPos);
            if (ship == null) return;

            try {
                Matrix4dc mat = getShipToWorldMatrix(ship);
                if (mat == null) return;
                Vector3d v = new Vector3d(
                    lastPkt.blockX + 0.5,
                    lastPkt.blockY + 0.5,
                    lastPkt.blockZ + 0.5);
                Vector3d w = new Vector3d();
                mat.transformPosition(v, w);
                sound.update((float) w.x, (float) w.y, (float) w.z);
            } catch (Exception e) {
                HQSpeakerMod.warn("HQSpeaker: tickPosition failed: " + e.getMessage());
            }
        }

        void stop() {
            Minecraft mc = Minecraft.getInstance();
            if (sound  != null) { mc.getSoundManager().stop(sound); sound = null; }
            if (stream != null) { stream.closeAndStop(); stream = null; }
            lastPkt = null;
            startTick = 0L;
            syncGroupId = null;
            syncGroupSize = 0;
        }

        boolean isDone() {
            
            
            if (stream != null && stream.isStreaming()) return false;
            return stream != null && stream.isDrained();
        }
    }

    static Matrix4dc getShipToWorldMatrix(Object ship) {
        return VS2TransformHelper.getShipToWorldMatrix(ship);
    }

    
    @OnlyIn(Dist.CLIENT)
    public static final class HQSpeakerSound
            extends AbstractSoundInstance
            implements TickableSoundInstance {

        private final HQAudioStream stream;
        private final boolean isStreaming;

        HQSpeakerSound(HQAudioStream stream, HQSpeakerAudioPacket pkt,
                       float wx, float wy, float wz) {
            super(HQ_SOUND_LOC, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.stream      = stream;
            this.volume      = pkt.volume;
            this.x           = wx;
            this.y           = wy;
            this.z           = wz;
            this.attenuation = Attenuation.LINEAR;
            this.looping     = false;
            this.isStreaming  = pkt.isStreamingFormat();
        }

        void update(float volume, float x, float y, float z) {
            this.volume = volume;
            this.x = x; this.y = y; this.z = z;
        }

        void update(float x, float y, float z) {
            this.x = x; this.y = y; this.z = z;
        }

        @Override public boolean isStopped() { return stream.isDrained(); }
        @Override public void tick() {}

        @Override
        public CompletableFuture<AudioStream> getStream(
                SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
            return CompletableFuture.completedFuture(stream);
        }
    }
}
