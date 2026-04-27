package com.tom.hqspeaker.client.audio;

import com.tom.hqspeaker.HQSpeakerMod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HLSPlaylistParser {

    private static final Pattern BANDWIDTH_PATTERN = Pattern.compile("BANDWIDTH=(\\d+)");
    private static final Pattern CODECS_PATTERN = Pattern.compile("CODECS=\"([^\"]+)\"");
    private static final Pattern RESOLUTION_PATTERN = Pattern.compile("RESOLUTION=(\\d+)x(\\d+)");

    public enum PlaylistType {
        MASTER,     
        MEDIA       
    }

    public static class VariantStream {
        public final String url;
        public final int bandwidth;
        public final String codecs;
        public final int width;
        public final int height;

        public VariantStream(String url, int bandwidth, String codecs, int width, int height) {
            this.url = url;
            this.bandwidth = bandwidth;
            this.codecs = codecs;
            this.width = width;
            this.height = height;
        }

        @Override
        public String toString() {
            return String.format("VariantStream{bw=%d, %dx%d, codecs=%s}", 
                bandwidth, width, height, codecs);
        }
    }

    public static class MediaSegment {
        public final String url;
        public final double duration;
        public final String title;
        public final boolean discontinuity;

        public MediaSegment(String url, double duration, String title, boolean discontinuity) {
            this.url = url;
            this.duration = duration;
            this.title = title;
            this.discontinuity = discontinuity;
        }

        @Override
        public String toString() {
            return String.format("MediaSegment{dur=%.3f, url=%s}", duration, url);
        }
    }

    public static class Playlist {
        public final PlaylistType type;
        public final List<VariantStream> variants;
        public final List<MediaSegment> segments;
        public final double targetDuration;
        public final boolean endList;
        public final long mediaSequence;
        public final String baseUrl;

        public Playlist(PlaylistType type, List<VariantStream> variants, List<MediaSegment> segments,
                       double targetDuration, boolean endList, long mediaSequence, String baseUrl) {
            this.type = type;
            this.variants = variants;
            this.segments = segments;
            this.targetDuration = targetDuration;
            this.endList = endList;
            this.mediaSequence = mediaSequence;
            this.baseUrl = baseUrl;
        }

        public boolean isLive() {
            return !endList;
        }
    }

    
    public static Playlist fetchAndParse(String playlistUrl) throws IOException {
        HQSpeakerMod.log("HLS: Fetching playlist from " + playlistUrl);
        
        String content = fetchUrl(playlistUrl);
        String baseUrl = extractBaseUrl(playlistUrl);
        
        return parse(content, baseUrl);
    }

    
    public static Playlist parse(String content, String baseUrl) {
        List<VariantStream> variants = new ArrayList<>();
        List<MediaSegment> segments = new ArrayList<>();
        
        double targetDuration = 0;
        boolean endList = false;
        long mediaSequence = 0;
        
        String[] lines = content.split("\\r?\\n");
        
        
        if (lines.length == 0 || !lines[0].trim().equals("#EXTM3U")) {
            HQSpeakerMod.warn("HLS: Invalid playlist - missing #EXTM3U header");
            return new Playlist(PlaylistType.MEDIA, variants, segments, 0, true, 0, baseUrl);
        }

        boolean isMaster = false;
        VariantStream currentVariant = null;
        double currentSegmentDuration = 0;
        String currentSegmentTitle = "";
        boolean currentDiscontinuity = false;

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                
                isMaster = true;
                currentVariant = parseStreamInfo(line, baseUrl);
            } else if (line.startsWith("#EXT-X-TARGETDURATION:")) {
                targetDuration = parseDouble(line.substring(22));
            } else if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                mediaSequence = parseLong(line.substring(22));
            } else if (line.startsWith("#EXT-X-ENDLIST")) {
                endList = true;
            } else if (line.startsWith("#EXTINF:")) {
                
                String info = line.substring(8);
                int commaIdx = info.indexOf(',');
                if (commaIdx >= 0) {
                    currentSegmentDuration = parseDouble(info.substring(0, commaIdx));
                    currentSegmentTitle = info.substring(commaIdx + 1);
                } else {
                    currentSegmentDuration = parseDouble(info);
                    currentSegmentTitle = "";
                }
            } else if (line.startsWith("#EXT-X-DISCONTINUITY")) {
                currentDiscontinuity = true;
            } else if (!line.startsWith("#")) {
                
                String url = resolveUrl(line, baseUrl);
                
                if (isMaster && currentVariant != null) {
                    
                    variants.add(new VariantStream(url, currentVariant.bandwidth, 
                        currentVariant.codecs, currentVariant.width, currentVariant.height));
                    currentVariant = null;
                } else {
                    
                    segments.add(new MediaSegment(url, currentSegmentDuration, 
                        currentSegmentTitle, currentDiscontinuity));
                    currentSegmentDuration = 0;
                    currentSegmentTitle = "";
                    currentDiscontinuity = false;
                }
            }
        }

        PlaylistType type = isMaster ? PlaylistType.MASTER : PlaylistType.MEDIA;
        HQSpeakerMod.log("HLS: Parsed " + (type == PlaylistType.MASTER ? 
            variants.size() + " variants" : segments.size() + " segments"));
        
        return new Playlist(type, variants, segments, targetDuration, endList, mediaSequence, baseUrl);
    }

    private static VariantStream parseStreamInfo(String line, String baseUrl) {
        String info = line.substring(18); 
        
        int bandwidth = 0;
        String codecs = "";
        int width = 0;
        int height = 0;

        Matcher bwMatcher = BANDWIDTH_PATTERN.matcher(info);
        if (bwMatcher.find()) {
            bandwidth = Integer.parseInt(bwMatcher.group(1));
        }

        Matcher codecsMatcher = CODECS_PATTERN.matcher(info);
        if (codecsMatcher.find()) {
            codecs = codecsMatcher.group(1);
        }

        Matcher resMatcher = RESOLUTION_PATTERN.matcher(info);
        if (resMatcher.find()) {
            width = Integer.parseInt(resMatcher.group(1));
            height = Integer.parseInt(resMatcher.group(2));
        }

        return new VariantStream(null, bandwidth, codecs, width, height);
    }

    private static String fetchUrl(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "HQSpeaker-HLS/1.0");
        
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP " + responseCode + " for " + urlStr);
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private static String extractBaseUrl(String url) {
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash > 0) {
            return url.substring(0, lastSlash + 1);
        }
        return url;
    }

    private static String resolveUrl(String url, String baseUrl) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        if (url.startsWith("/")) {
            
            int protocolEnd = baseUrl.indexOf("://");
            if (protocolEnd > 0) {
                int pathStart = baseUrl.indexOf('/', protocolEnd + 3);
                if (pathStart > 0) {
                    return baseUrl.substring(0, pathStart) + url;
                }
            }
            return baseUrl + url;
        }
        
        return baseUrl + url;
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    
    public static VariantStream selectBestVariant(List<VariantStream> variants, long preferredBandwidth) {
        if (variants.isEmpty()) return null;
        
        VariantStream best = variants.get(0);
        
        for (VariantStream v : variants) {
            if (preferredBandwidth == 0) {
                
                if (v.bandwidth < best.bandwidth) best = v;
            } else if (preferredBandwidth == Long.MAX_VALUE) {
                
                if (v.bandwidth > best.bandwidth) best = v;
            } else {
                
                long bestDiff = Math.abs(best.bandwidth - preferredBandwidth);
                long vDiff = Math.abs(v.bandwidth - preferredBandwidth);
                if (vDiff < bestDiff) best = v;
            }
        }
        
        return best;
    }

    
    public static boolean isAudioOnly(String codecs) {
        if (codecs == null || codecs.isEmpty()) return false;
        String lower = codecs.toLowerCase();
        
        
        return !lower.contains("avc") && !lower.contains("hevc") && 
               !lower.contains("vp9") && !lower.contains("av01") &&
               (lower.contains("mp4a") || lower.contains("mp3") || 
                lower.contains("ac-3") || lower.contains("ec-3"));
    }
}
