package com.synarsis.airtacticalarsenal.entity.patriot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelAnimSampler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.BakedAnimationsAdapter;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Patriot models and animations")
class PatriotModelsTest {

    private static final Map<PatriotKind, String[]> CLIPS = Map.of(
            PatriotKind.LAUNCHER, new String[] {"idle", "transport", "display_deployed", "supports_out", "supports_in",
                    "raise", "lower", "traverse_left", "traverse_right", "traverse_center", "wheel_cycle", "steer_left",
                    "steer_right", "doors_open", "doors_close", "coupling_unlock", "coupling_lock", "detach", "attach",
                    "articulate_left", "articulate_right"},
            PatriotKind.RADAR, new String[] {"idle", "transport", "deployed", "supports_out", "supports_in",
                    "antenna_up", "antenna_down", "service_open", "service_close", "wheel_cycle", "steer_left",
                    "steer_right", "doors_open", "doors_close", "coupling_unlock", "coupling_lock", "detach", "attach",
                    "articulate_left", "articulate_right"});

    private static InputStream resource(String path) {
        InputStream in = PatriotModelsTest.class.getResourceAsStream(path);
        assertNotNull(in, path);
        return in;
    }

    private static BakedGeoModel bake(PatriotKind kind) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(resource(kind.geoPath()), StandardCharsets.UTF_8)) {
            Model model = KeyFramesAdapter.GEO_GSON.fromJson(reader, Model.class);
            return BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(model));
        }
    }

    private static void collect(GeoBone bone, Set<String> names, int[] cubes) {
        names.add(bone.getName());
        cubes[0] += bone.getCubes().size();
        for (GeoBone child : bone.getChildBones()) {
            collect(child, names, cubes);
        }
    }

    private static TelAnimSampler sampler(PatriotKind kind) throws IOException {
        return TelAnimSampler.load(resource(kind.animationPath()));
    }

    private static JsonObject animationFile(PatriotKind kind) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(resource(kind.animationPath()), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
        }
    }

    @Test
    @DisplayName("GeckoLib bakes both models with the bone and cube counts of the export")
    void geckoLibBakesTheModels() throws IOException {
        int[][] expected = {{59, 818}, {47, 720}};
        PatriotKind[] kinds = PatriotKind.values();
        for (int i = 0; i < kinds.length; i++) {
            BakedGeoModel baked = bake(kinds[i]);
            Set<String> names = new HashSet<>();
            int[] cubes = new int[1];
            for (GeoBone top : baked.topLevelBones()) {
                collect(top, names, cubes);
            }
            assertEquals(expected[i][0], names.size(), "bones of " + kinds[i]);
            assertEquals(expected[i][1], cubes[0], "cubes of " + kinds[i]);
            assertTrue(baked.getBone("root").isPresent(), "root of " + kinds[i]);
            assertTrue(baked.getBone("tractor_carrier").isPresent(), "tractor_carrier of " + kinds[i]);
        }
    }

    @Test
    @DisplayName("the texture sizes match the atlas size of the model")
    void textureSizes() throws IOException {
        for (PatriotKind kind : PatriotKind.values()) {
            String texture = "/assets/ata/textures/entity/" + kind.textureLocation().getPath()
                    .substring("textures/entity/".length());
            BufferedImage image = ImageIO.read(resource(texture));
            JsonObject geo;
            try (InputStreamReader reader = new InputStreamReader(resource(kind.geoPath()), StandardCharsets.UTF_8)) {
                geo = JsonParser.parseReader(reader).getAsJsonObject();
            }
            JsonObject description = geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                    .getAsJsonObject("description");
            assertEquals(description.get("texture_width").getAsInt(), image.getWidth(), "width of " + kind);
            assertEquals(description.get("texture_height").getAsInt(), image.getHeight(), "height of " + kind);
        }
    }

    @Test
    @DisplayName("GeckoLib bakes every clip of both files")
    void geckoLibBakesTheClips() throws IOException, ReflectiveOperationException {
        Method bake = BakedAnimationsAdapter.class.getDeclaredMethod("bakeBoneAnimations", JsonObject.class);
        bake.setAccessible(true);
        int[] expectedClips = {34, 25};
        PatriotKind[] kinds = PatriotKind.values();
        for (int i = 0; i < kinds.length; i++) {
            JsonObject clips = animationFile(kinds[i]);
            assertEquals(expectedClips[i], clips.size(), "clips of " + kinds[i]);
            for (Map.Entry<String, com.google.gson.JsonElement> clip : clips.entrySet()) {
                Object baked = bake.invoke(new BakedAnimationsAdapter(),
                        clip.getValue().getAsJsonObject().getAsJsonObject("bones"));
                assertNotNull(baked, clip.getKey());
            }
        }
    }

    @Test
    @DisplayName("the sampler opens every clip the poses use, and its lengths match the file")
    void samplerOpensTheClips() throws IOException {
        for (PatriotKind kind : PatriotKind.values()) {
            TelAnimSampler sampler = sampler(kind);
            JsonObject file = animationFile(kind);
            for (String name : CLIPS.get(kind)) {
                String clip = kind.clipPrefix() + name;
                assertTrue(sampler.has(clip), clip);
                assertEquals(file.getAsJsonObject(clip).get("animation_length").getAsDouble(), sampler.length(clip),
                        1.0E-6, clip);
                assertTrue(sampler.sample(clip, 0.0).size() > 0, clip + " has no bones");
            }
        }
    }

    @Test
    @DisplayName("every bone the clips animate exists in the model")
    void clipBonesExist() throws IOException {
        for (PatriotKind kind : PatriotKind.values()) {
            BakedGeoModel baked = bake(kind);
            for (Map.Entry<String, com.google.gson.JsonElement> clip : animationFile(kind).entrySet()) {
                for (String bone : clip.getValue().getAsJsonObject().getAsJsonObject("bones").keySet()) {
                    assertTrue(baked.getBone(bone).isPresent(), kind + " " + clip.getKey() + " animates " + bone);
                }
            }
        }
    }
}
