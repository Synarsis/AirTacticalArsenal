package com.synarsis.airtacticalarsenal.client.fx.light;

import com.synarsis.airtacticalarsenal.client.fx.light.EntityShadowMath.Box;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Entity shadows: a ray against a box")
class EntityShadowMathTest {

    private static Box cube(double x, double y, double z, double half, int owner) {
        return new Box(x, y, z, half, half, half, 1.0, 0.0, owner);
    }

    @Test
    @DisplayName("a box between the point and the light stops the light, a box beside the ray does not")
    void blocksAndMisses() {
        Box box = cube(0.0, 0.0, 0.0, 0.5, 7);
        assertEquals(0.0, EntityShadowMath.transmittance(box, new double[] {-3, 0, 0}, new double[] {3, 0, 0}), 0.0);
        assertEquals(0.0, EntityShadowMath.transmittance(box, new double[] {-3, 0.4, 0.4}, new double[] {3, 0.4, 0.4}), 0.0);
        assertEquals(1.0, EntityShadowMath.transmittance(box, new double[] {-3, 0, 0.9}, new double[] {3, 0, 0.9}), 0.0);
        assertEquals(1.0, EntityShadowMath.transmittance(box, new double[] {-3, 2.0, 0}, new double[] {3, 2.0, 0}), 0.0);
    }

    @Test
    @DisplayName("a box behind the point or beyond the light does not shadow it")
    void segmentOnly() {
        Box box = cube(10.0, 0.0, 0.0, 0.5, 7);
        assertEquals(1.0, EntityShadowMath.transmittance(box, new double[] {-3, 0, 0}, new double[] {3, 0, 0}), 0.0);
        assertEquals(1.0, EntityShadowMath.transmittance(box, new double[] {20, 0, 0}, new double[] {30, 0, 0}), 0.0);
        assertEquals(1.0, EntityShadowMath.transmittance(box, new double[] {0, 0, 0}, new double[] {5, 0, 0}), 0.0);
    }

    @Test
    @DisplayName("the edge is soft over 0.15 blocks: on the surface it is dark, 0.075 out it is half, 0.15 out it is light")
    void softEdge() {
        Box box = cube(0.0, 0.0, 0.0, 0.5, 7);
        double[] from = {-3, 0, 0};
        double[] to = {3, 0, 0};
        double[] onFace = {0.0, 0.0, 0.0};
        for (double[] offset : new double[][] {{0.0, 0.0, 0.5 + 0.0001}, {0.0, 0.0, 0.5 + 0.075}, {0.0, 0.0, 0.5 + 0.15},
                {0.0, 0.0, 0.5 + 0.3}}) {
            double v = EntityShadowMath.transmittance(box, new double[] {from[0], from[1], offset[2]},
                    new double[] {to[0], to[1], offset[2]});
            if (offset[2] < 0.51) {
                assertTrue(v < 0.01, "just outside the surface " + v);
            } else if (offset[2] < 0.6) {
                assertEquals(0.5, v, 1.0e-4);
            } else {
                assertEquals(1.0, v, 1.0e-9);
            }
        }
        assertEquals(0.0, onFace[0]);
        double a = EntityShadowMath.transmittance(box, new double[] {-3, 0, 0.55}, new double[] {3, 0, 0.55});
        double b = EntityShadowMath.transmittance(box, new double[] {-3, 0, 0.60}, new double[] {3, 0, 0.60});
        double c = EntityShadowMath.transmittance(box, new double[] {-3, 0, 0.64}, new double[] {3, 0, 0.64});
        assertTrue(a < b && b < c && c < 1.0, a + " " + b + " " + c);
    }

    @Test
    @DisplayName("the box turns with its heading: a plate across the ray stops it, a plate along the ray lets it pass beside")
    void rotated() {
        Box plate = new Box(0.0, 0.0, 0.0, 2.0, 1.0, 0.1, 0.0, 1.0, 3);
        assertEquals(0.0, EntityShadowMath.transmittance(plate, new double[] {-3, 0, 1.5}, new double[] {3, 0, 1.5}), 0.0);
        assertEquals(1.0, EntityShadowMath.transmittance(plate, new double[] {1.0, 0, -3}, new double[] {1.0, 0, 3}), 0.0);
        assertEquals(0.0, EntityShadowMath.transmittance(plate, new double[] {0.0, 0, -3}, new double[] {0.0, 0, 3}), 0.0);
        Box straight = new Box(0.0, 0.0, 0.0, 2.0, 1.0, 0.1, 1.0, 0.0, 3);
        assertEquals(0.0, EntityShadowMath.transmittance(straight, new double[] {1.0, 0, -3}, new double[] {1.0, 0, 3}), 0.0);
        assertEquals(1.0, EntityShadowMath.transmittance(straight, new double[] {-3, 0, 1.5}, new double[] {3, 0, 1.5}), 0.0);
    }

    @Test
    @DisplayName("several boxes multiply, and a dark enough ray ends early")
    void several() {
        List<Box> boxes = new ArrayList<>();
        boxes.add(cube(-1.0, 0.0, 0.0, 0.5, 1));
        boxes.add(cube(1.0, 0.0, 0.0, 0.5, 2));
        double[] from = {-3, 0, 0.575};
        double[] to = {3, 0, 0.575};
        double one = EntityShadowMath.transmittance(boxes.get(0), from, to);
        assertTrue(one > 0.0 && one < 1.0);
        double two = EntityShadowMath.transmittance(boxes.get(1), from, to);
        assertEquals(0.25, one * two, 1.0e-3);
        assertEquals(one * two, EntityShadowMath.shadow(boxes, from, to, EntityShadowMath.NO_OWNER), 1.0e-9);
        boxes.add(cube(0.0, 0.0, 0.575, 0.5, 3));
        assertEquals(0.0, EntityShadowMath.shadow(boxes, from, to, EntityShadowMath.NO_OWNER), 0.0);
        assertEquals(1.0, EntityShadowMath.shadow(List.of(), from, to, EntityShadowMath.NO_OWNER), 0.0);
    }

    @Test
    @DisplayName("a vehicle does not shade the light it carries within the first 1.5 blocks, other boxes there still do")
    void ownLight() {
        Box own = new Box(0.0, 1.0, -0.5, 1.0, 1.0, 1.0, 1.0, 0.0, 42);
        double[] lamp = {0.0, 1.0, 0.0};
        double[] ground = {0.0, 0.0, 4.0};
        assertEquals(0.0, EntityShadowMath.shadow(List.of(own), ground, lamp, EntityShadowMath.NO_OWNER), 0.0);
        assertEquals(0.0, EntityShadowMath.shadow(List.of(own), ground, lamp, 7), 0.0);
        assertEquals(1.0, EntityShadowMath.shadow(List.of(own), ground, lamp, 42), 0.0);
        Box someoneElse = new Box(0.0, 0.8, 1.0, 0.3, 0.9, 0.3, 1.0, 0.0, 9);
        assertEquals(0.0, EntityShadowMath.shadow(List.of(own, someoneElse), ground, lamp, 42), 0.0,
                "a player standing in front of the lamp casts a shadow");
        double[] near = EntityShadowMath.shortened(ground, lamp, 1.5);
        double length = Math.sqrt(Math.pow(near[0] - ground[0], 2) + Math.pow(near[1] - ground[1], 2)
                + Math.pow(near[2] - ground[2], 2));
        double full = Math.sqrt(Math.pow(lamp[0] - ground[0], 2) + Math.pow(lamp[1] - ground[1], 2)
                + Math.pow(lamp[2] - ground[2], 2));
        assertEquals(full - 1.5, length, 1.0e-9);
        assertEquals(ground[2], EntityShadowMath.shortened(ground, new double[] {0, 0, 4.5}, 1.5)[2], 0.0,
                "a ray shorter than the free distance collapses to its start");
    }

    @Test
    @DisplayName("the cheap reject keeps every box a ray can touch and drops far ones")
    void mayTouch() {
        Box box = cube(0.0, 0.0, 0.0, 0.5, 1);
        assertTrue(EntityShadowMath.mayTouch(box, new double[] {-3, 0, 0}, new double[] {3, 0, 0}));
        assertTrue(EntityShadowMath.mayTouch(box, new double[] {-3, 0, 0.6}, new double[] {3, 0, 0.6}));
        assertFalse(EntityShadowMath.mayTouch(box, new double[] {-3, 0, 5}, new double[] {3, 0, 5}));
        assertFalse(EntityShadowMath.mayTouch(box, new double[] {5, 0, 0}, new double[] {9, 0, 0}));
        for (int i = 0; i < 400; i++) {
            double y = -2.0 + 0.01 * i;
            double[] from = {-3, y, 0.3};
            double[] to = {3, y * 0.5, -0.2};
            if (EntityShadowMath.transmittance(box, from, to) < 1.0) {
                assertTrue(EntityShadowMath.mayTouch(box, from, to), "y " + y);
            }
        }
    }
}
