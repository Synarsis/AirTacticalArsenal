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

    private static final String[] GEO = {
            "/assets/ata/geo/patriot_m983_tractor.geo.json",
            "/assets/ata/geo/patriot_pac2_launcher.geo.json",
            "/assets/ata/geo/patriot_anmpq65_radar.geo.json",
    };
    private static final String[] ANIMATION = {
            "/assets/ata/animations/patriot_m983_tractor.animation.json",
            "/assets/ata/animations/patriot_pac2_launcher.animation.json",
            "/assets/ata/animations/patriot_anmpq65_radar.animation.json",
    };
    private static final String[] TEXTURE = {
            "/assets/ata/textures/entity/patriot_m983.png",
            "/assets/ata/textures/entity/patriot_pac2.png",
            "/assets/ata/textures/entity/patriot_anmpq65.png",
    };
    private static final int[][] BONES_AND_CUBES = {{20, 405}, {37, 413}, {25, 315}};
    private static final int[] CLIPS = {8, 21, 13};

    private static InputStream resource(String path) {
        InputStream in = PatriotModelsTest.class.getResourceAsStream(path);
        assertNotNull(in, path);
        return in;
    }

    private static BakedGeoModel bake(String path) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(resource(path), StandardCharsets.UTF_8)) {
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

    private static TelAnimSampler sampler(String path) throws IOException {
        return TelAnimSampler.load(resource(path));
    }

    private static JsonObject animationFile(String path) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(resource(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
        }
    }

    @Test
    @DisplayName("GeckoLib bakes all three models with the bone and cube counts of the export")
    void geckoLibBakesTheModels() throws IOException {
        for (int i = 0; i < GEO.length; i++) {
            BakedGeoModel baked = bake(GEO[i]);
            Set<String> names = new HashSet<>();
            int[] cubes = new int[1];
            for (GeoBone top : baked.topLevelBones()) {
                collect(top, names, cubes);
            }
            assertEquals(BONES_AND_CUBES[i][0], names.size(), "bones of " + GEO[i]);
            assertEquals(BONES_AND_CUBES[i][1], cubes[0], "cubes of " + GEO[i]);
            assertTrue(baked.getBone("root").isPresent(), "root of " + GEO[i]);
        }
    }

    @Test
    @DisplayName("the texture sizes match the atlas size of the models")
    void textureSizes() throws IOException {
        for (int i = 0; i < GEO.length; i++) {
            BufferedImage image = ImageIO.read(resource(TEXTURE[i]));
            JsonObject geo;
            try (InputStreamReader reader = new InputStreamReader(resource(GEO[i]), StandardCharsets.UTF_8)) {
                geo = JsonParser.parseReader(reader).getAsJsonObject();
            }
            JsonObject description = geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                    .getAsJsonObject("description");
            assertEquals(description.get("texture_width").getAsInt(), image.getWidth(), "width of " + TEXTURE[i]);
            assertEquals(description.get("texture_height").getAsInt(), image.getHeight(), "height of " + TEXTURE[i]);
        }
    }

    @Test
    @DisplayName("GeckoLib bakes every clip of all three files")
    void geckoLibBakesTheClips() throws IOException, ReflectiveOperationException {
        Method bake = BakedAnimationsAdapter.class.getDeclaredMethod("bakeBoneAnimations", JsonObject.class);
        bake.setAccessible(true);
        for (int i = 0; i < ANIMATION.length; i++) {
            JsonObject clips = animationFile(ANIMATION[i]);
            assertEquals(CLIPS[i], clips.size(), "clips of " + ANIMATION[i]);
            for (Map.Entry<String, com.google.gson.JsonElement> clip : clips.entrySet()) {
                Object baked = bake.invoke(new BakedAnimationsAdapter(),
                        clip.getValue().getAsJsonObject().getAsJsonObject("bones"));
                assertNotNull(baked, clip.getKey());
            }
        }
    }

    @Test
    @DisplayName("the sampler opens every clip, and its length matches the file")
    void samplerOpensTheClips() throws IOException {
        for (int i = 0; i < ANIMATION.length; i++) {
            TelAnimSampler sampler = sampler(ANIMATION[i]);
            JsonObject file = animationFile(ANIMATION[i]);
            for (String clip : file.keySet()) {
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
        for (int i = 0; i < GEO.length; i++) {
            BakedGeoModel baked = bake(GEO[i]);
            for (Map.Entry<String, com.google.gson.JsonElement> clip : animationFile(ANIMATION[i]).entrySet()) {
                for (String bone : clip.getValue().getAsJsonObject().getAsJsonObject("bones").keySet()) {
                    assertTrue(baked.getBone(bone).isPresent(), GEO[i] + " " + clip.getKey() + " animates " + bone);
                }
            }
        }
    }
}
