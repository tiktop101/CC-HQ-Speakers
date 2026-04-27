package com.tom.hqspeaker.client.audio;

import com.tom.hqspeaker.HQSpeakerMod;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public final class SharedStreamingGroup {
    private static final ConcurrentHashMap<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private SharedStreamingGroup() {}

    public static Tap open(UUID groupId, String url, StreamingAudioSource.StreamType type, float volume, UUID metadataSourceId, int expectedTaps) {
        Session session = SESSIONS.compute(groupId, (id, existing) -> {
            if (existing != null && existing.matches(url, type)) { existing.setExpectedTaps(expectedTaps); return existing; }
            if (existing != null) existing.forceClose();
            Session created = new Session(groupId, url, type, volume, metadataSourceId, Math.max(1, expectedTaps));
            return created;
        });
        return session.addTap();
    }

    private static void remove(UUID groupId, Session session) {
        SESSIONS.remove(groupId, session);
    }

    public static final class Tap {
        private final Session session;
        private final int tapId;
        private final BlockingQueue<byte[]> pcmQueue;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private ByteBuffer carry = null;

        private Tap(Session session, int tapId, BlockingQueue<byte[]> pcmQueue) {
            this.session = session;
            this.tapId = tapId;
            this.pcmQueue = pcmQueue;
        }

        public AudioFormat getFormat() { return session.getFormat(); }
        public boolean isRunning() { return session.isRunning(); }
        public boolean hasData() { return !pcmQueue.isEmpty(); }
        public int getQueueSize() { return pcmQueue.size(); }

        public ByteBuffer readPCM(int maxBytes) {
            if (carry != null && carry.hasRemaining()) {
                int len = Math.min(carry.remaining(), maxBytes);
                ByteBuffer out = ByteBuffer.allocate(len);
                int oldLimit = carry.limit();
                carry.limit(carry.position() + len);
                out.put(carry);
                out.flip();
                carry.limit(oldLimit);
                if (!carry.hasRemaining()) carry = null;
                return out;
            }
            if (!session.isRunning() && pcmQueue.isEmpty()) return null;
            byte[] data = pcmQueue.poll();
            if (data == null) return ByteBuffer.allocateDirect(0).asReadOnlyBuffer();
            ByteBuffer src = ByteBuffer.wrap(data);
            if (src.remaining() <= maxBytes) return src;
            int len = maxBytes;
            ByteBuffer out = ByteBuffer.allocate(len);
            int oldLimit = src.limit();
            src.limit(src.position() + len);
            out.put(src);
            out.flip();
            src.limit(oldLimit);
            carry = src.slice();
            return out;
        }

        public void close() {
            if (closed.compareAndSet(false, true)) {
                session.removeTap(tapId);
                pcmQueue.clear();
            }
        }
    }

    private static final class Session {
        private static final int DISTRIBUTION_CHUNK_BYTES = 9600; 
        private final UUID groupId;
        private final String url;
        private final StreamingAudioSource.StreamType type;
        private final float volume;
        private final UUID metadataSourceId;
        private final StreamingAudioSource source;
        private final ConcurrentHashMap<Integer, BlockingQueue<byte[]>> taps = new ConcurrentHashMap<>();
        private final AtomicInteger nextTapId = new AtomicInteger(1);
        private final AtomicBoolean running = new AtomicBoolean(false);
        private volatile int expectedTaps;
        private final java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        private final AtomicBoolean decodeStarted = new AtomicBoolean(false);
        private Thread distributorThread;

        private Session(UUID groupId, String url, StreamingAudioSource.StreamType type, float volume, UUID metadataSourceId, int expectedTaps) {
            this.groupId = groupId;
            this.url = url;
            this.type = type;
            this.volume = volume;
            this.metadataSourceId = metadataSourceId;
            this.expectedTaps = Math.max(1, expectedTaps);
            this.source = new StreamingAudioSource(url, type, volume);
            this.source.setMetadataListener((rawTitle, station, genre, desc) -> {
                try {
                    com.tom.hqspeaker.network.HQSpeakerNetwork.sendToServer(
                        new com.tom.hqspeaker.network.IcyMetaPacket(metadataSourceId, rawTitle, station, genre, desc));
                } catch (Exception e) {
                    HQSpeakerMod.warn("SharedStreamingGroup: failed to send ICY meta — " + e.getMessage());
                }
            });
        }

        private boolean matches(String url, StreamingAudioSource.StreamType type) {
            return this.type == type && this.url.equals(url);
        }

        private void startDecodeIfReady() {
            if (decodeStarted.get()) return;
            if (taps.size() < expectedTaps) return;
            if (!decodeStarted.compareAndSet(false, true)) return;
            if (running.compareAndSet(false, true)) {
                source.start();
                distributorThread = new Thread(this::distributeLoop, "HQSpeaker-SharedStream-" + groupId);
                distributorThread.setDaemon(true);
                distributorThread.start();
                startLatch.countDown();
                HQSpeakerMod.log("SharedStreamingGroup: started shared session " + groupId + " for " + type + " " + url + " with " + taps.size() + "/" + expectedTaps + " taps");
            }
        }

        private void setExpectedTaps(int expectedTaps) {
            this.expectedTaps = Math.max(this.expectedTaps, expectedTaps);
            startDecodeIfReady();
        }

        private Tap addTap() {
            int id = nextTapId.getAndIncrement();
            BlockingQueue<byte[]> q = new LinkedBlockingQueue<>(400);
            taps.put(id, q);
            startDecodeIfReady();
            return new Tap(this, id, q);
        }

        private void removeTap(int id) {
            taps.remove(id);
            if (taps.isEmpty()) forceClose();
        }

        private AudioFormat getFormat() { return source.getFormat(); }
        private boolean isRunning() { return (running.get() && source.isRunning()) || (!decodeStarted.get() && !taps.isEmpty()); }

        private void distributeLoop() {
            try {
                startLatch.await();
                while (running.get()) {
                    ByteBuffer pcm = source.readPCM(DISTRIBUTION_CHUNK_BYTES);
                    if (pcm == null) break;
                    if (pcm.remaining() <= 0) {
                        try { Thread.sleep(2L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                        continue;
                    }
                    byte[] data = new byte[pcm.remaining()];
                    pcm.get(data);
                    if (data.length == 0) continue;
                    for (BlockingQueue<byte[]> q : taps.values()) {
                        byte[] copy = Arrays.copyOf(data, data.length);
                        while (!q.offer(copy) && running.get()) {
                            q.poll();
                        }
                    }
                }
            } catch (Exception e) {
                if (running.get()) HQSpeakerMod.warn("SharedStreamingGroup: distributor error — " + e.getMessage());
            } finally {
                forceClose();
            }
        }

        private void forceClose() {
            startLatch.countDown();
            if (running.compareAndSet(true, false)) {
                if (distributorThread != null) distributorThread.interrupt();
                source.stop();
                taps.values().forEach(BlockingQueue::clear);
                taps.clear();
                remove(groupId, this);
                HQSpeakerMod.log("SharedStreamingGroup: closed session " + groupId);
            }
        }
    }
}
