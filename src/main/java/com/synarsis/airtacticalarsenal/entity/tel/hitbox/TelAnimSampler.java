package com.synarsis.airtacticalarsenal.entity.tel.hitbox;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TelAnimSampler {

    public record BoneValue(double[] rotation, double[] position, double[] scale) {

        private static final double[] UNIT = {1.0, 1.0, 1.0};

        public double[] scaleOrOne() {
            return this.scale == null ? UNIT : this.scale;
        }

        public double[] geckoRotation() {
            if (this.rotation == null) {
                return new double[3];
            }
            return new double[] {-Math.toRadians(this.rotation[0]), -Math.toRadians(this.rotation[1]),
                    Math.toRadians(this.rotation[2])};
        }

        public double[] geckoPosition() {
            return this.position == null ? new double[3] : this.position.clone();
        }
    }

    private static final class Channel {
        double[] times;
        double[][] values;
        boolean[] easeSine;

        double[] at(double t) {
            int n = this.times.length;
            if (t <= this.times[0]) {
                return this.values[0];
            }
            if (t >= this.times[n - 1]) {
                return this.values[n - 1];
            }
            int i = 0;
            while (i + 1 < n && this.times[i + 1] <= t) {
                i++;
            }
            double span = this.times[i + 1] - this.times[i];
            double a = span > 1.0E-9 ? (t - this.times[i]) / span : 1.0;
            if (this.easeSine[i + 1]) {
                a = -(Math.cos(Math.PI * a) - 1.0) / 2.0;
            }
            double[] from = this.values[i];
            double[] to = this.values[i + 1];
            return new double[] {
                    from[0] + (to[0] - from[0]) * a,
                    from[1] + (to[1] - from[1]) * a,
                    from[2] + (to[2] - from[2]) * a};
        }
    }

    private static final class Clip {
        double length;
        boolean loops;
        final Map<String, Channel[]> bones = new HashMap<>();
    }

    private static final int ROTATION = 0;
    private static final int POSITION = 1;
    private static final int SCALE = 2;

    private final Map<String, Clip> clips = new HashMap<>();

    public static TelAnimSampler load(InputStream in) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            TelAnimSampler sampler = new TelAnimSampler();
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("animations").entrySet()) {
                sampler.clips.put(entry.getKey(), parseClip(entry.getValue().getAsJsonObject()));
            }
            return sampler;
        }
    }

    public boolean has(String clip) {
        return this.clips.containsKey(clip);
    }

    public double length(String clip) {
        Clip c = this.clips.get(clip);
        return c == null ? 0.0 : c.length;
    }

    public Map<String, BoneValue> sample(String clip, double seconds) {
        Clip c = this.clips.get(clip);
        if (c == null) {
            return Map.of();
        }
        double t = clipTime(c, seconds);
        Map<String, BoneValue> out = new HashMap<>();
        for (Map.Entry<String, Channel[]> bone : c.bones.entrySet()) {
            out.put(bone.getKey(), valueOf(bone.getValue(), t));
        }
        return out;
    }

    public BoneValue bone(String clip, String bone, double seconds) {
        Clip c = this.clips.get(clip);
        if (c == null) {
            return null;
        }
        Channel[] channels = c.bones.get(bone);
        return channels == null ? null : valueOf(channels, clipTime(c, seconds));
    }

    private static double clipTime(Clip c, double seconds) {
        if (c.loops && c.length > 0.0) {
            return ((seconds % c.length) + c.length) % c.length;
        }
        return Math.max(0.0, Math.min(seconds, c.length > 0.0 ? c.length : seconds));
    }

    private static BoneValue valueOf(Channel[] channels, double t) {
        return new BoneValue(channels[ROTATION] == null ? null : channels[ROTATION].at(t),
                channels[POSITION] == null ? null : channels[POSITION].at(t),
                channels[SCALE] == null ? null : channels[SCALE].at(t));
    }

    private static Clip parseClip(JsonObject json) {
        Clip clip = new Clip();
        clip.length = json.has("animation_length") ? json.get("animation_length").getAsDouble() : 0.0;
        if (json.has("loop")) {
            JsonElement loop = json.get("loop");
            clip.loops = loop.isJsonPrimitive() && loop.getAsJsonPrimitive().isBoolean() && loop.getAsBoolean();
        }
        if (json.has("bones")) {
            for (Map.Entry<String, JsonElement> bone : json.getAsJsonObject("bones").entrySet()) {
                JsonObject channels = bone.getValue().getAsJsonObject();
                clip.bones.put(bone.getKey(), new Channel[] {
                        channels.has("rotation") ? parseChannel(channels.get("rotation")) : null,
                        channels.has("position") ? parseChannel(channels.get("position")) : null,
                        channels.has("scale") ? parseChannel(channels.get("scale")) : null});
            }
        }
        return clip;
    }

    private static Channel parseChannel(JsonElement json) {
        List<double[]> values = new ArrayList<>();
        List<Double> times = new ArrayList<>();
        List<Boolean> eases = new ArrayList<>();

        if (json.isJsonArray() || json.getAsJsonObject().has("vector")) {
            times.add(0.0);
            values.add(vector(json.isJsonArray() ? json : json.getAsJsonObject().get("vector")));
            eases.add(false);
        } else {
            List<Map.Entry<String, JsonElement>> keys = new ArrayList<>(json.getAsJsonObject().entrySet());
            keys.sort(Comparator.comparingDouble(e -> Double.parseDouble(e.getKey())));
            for (Map.Entry<String, JsonElement> key : keys) {
                times.add(Double.parseDouble(key.getKey()));
                JsonElement v = key.getValue();
                if (v.isJsonArray()) {
                    values.add(vector(v));
                    eases.add(false);
                } else {
                    JsonObject o = v.getAsJsonObject();
                    values.add(vector(o.get("vector")));
                    eases.add(o.has("easing") && "easeInOutSine".equals(o.get("easing").getAsString()));
                }
            }
        }

        Channel channel = new Channel();
        channel.times = times.stream().mapToDouble(Double::doubleValue).toArray();
        channel.values = values.toArray(new double[0][]);
        channel.easeSine = new boolean[eases.size()];
        for (int i = 0; i < channel.easeSine.length; i++) {
            channel.easeSine[i] = eases.get(i);
        }
        return channel;
    }

    private static double[] vector(JsonElement json) {
        JsonArray a = json.getAsJsonArray();
        double[] v = new double[3];
        for (int i = 0; i < 3 && i < a.size(); i++) {
            JsonElement e = a.get(i);
            try {
                v[i] = e.getAsDouble();
            } catch (NumberFormatException | UnsupportedOperationException ex) {
                v[i] = 0.0;
            }
        }
        return v;
    }
}
