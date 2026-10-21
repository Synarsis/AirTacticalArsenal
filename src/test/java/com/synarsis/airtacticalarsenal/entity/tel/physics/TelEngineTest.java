package com.synarsis.airtacticalarsenal.entity.tel.physics;

import com.synarsis.airtacticalarsenal.entity.tel.TelInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TEL drivetrain")
class TelEngineTest {

    private static final TelEngine.Params P = TelEngine.DEFAULTS;
    private static final double CRUISE = 0.88 * P.maxFwd();

    private static double[] drive(TelEngineState s, int input, double startSpeed, int ticks) {
        double[] speeds = new double[ticks];
        double v = startSpeed;
        for (int i = 0; i < ticks; i++) {
            TelEngine.Result r = TelEngine.step(s, input,
                    new TelEngine.Context(0, 0, v, 0, 0, 1, true, false, 0, 0, true, P));
            v = r.vz();
            speeds[i] = v;
        }
        return speeds;
    }

    @Test
    @DisplayName("holding forward accelerates monotonically towards 0.88 * maxFwd * power")
    void acceleratesToSteadySpeed() {
        TelEngineState s = new TelEngineState();
        double[] v = drive(s, TelInput.FWD, 0.0, 2000);
        for (int i = 1; i < v.length; i++) {
            assertTrue(v[i] >= v[i - 1] - 1.0E-12, "speed fell at tick " + i);
        }
        assertEquals(1.0, s.power, 1.0E-9);
        assertEquals(0.88 * P.maxFwd() * s.power, v[v.length - 1], 0.005);
    }

    @Test
    @DisplayName("speed follows power closely all the way up")
    void speedTracksPower() {
        TelEngineState s = new TelEngineState();
        TelEngine.Result r = null;
        double v = 0;
        for (int i = 0; i < 500; i++) {
            r = TelEngine.step(s, TelInput.FWD,
                    new TelEngine.Context(0, 0, v, 0, 0, 1, true, false, 0, 0, true, P));
            v = r.vz();
        }
        assertEquals(0.88 * P.maxFwd() * s.power, v, 0.03);
    }

    @Test
    @DisplayName("the afterburner reaches about 1.3 times the speed")
    void boostIsFaster() {
        TelEngineState s = new TelEngineState();
        double[] v = drive(s, TelInput.FWD | TelInput.BOOST, 0.0, 3000);
        assertEquals(1.3, s.power, 1.0E-6);
        assertEquals(0.88 * P.maxFwd() * 1.3, v[v.length - 1], 0.01);
    }

    @Test
    @DisplayName("letting go lets it coast to a stop")
    void releasingStops() {
        TelEngineState s = new TelEngineState();
        double[] up = drive(s, TelInput.FWD, 0.0, 1500);
        double[] down = drive(s, 0, up[up.length - 1], 300);
        for (int i = 1; i < down.length; i++) {
            assertTrue(down[i] <= down[i - 1] + 1.0E-12, "speed rose while coasting at tick " + i);
        }
        assertTrue(down[down.length - 1] < 0.02, "still rolling at " + down[down.length - 1]);
    }

    @Test
    @DisplayName("the handbrake stops it faster than coasting")
    void handbrakeBitesHarder() {
        TelEngineState a = new TelEngineState();
        TelEngineState b = new TelEngineState();
        double start = drive(a, TelInput.FWD, 0.0, 1500)[1499];
        drive(b, TelInput.FWD, 0.0, 1500);
        double coast = drive(a, 0, start, 20)[19];
        double braked = drive(b, TelInput.BRAKE, start, 20)[19];
        assertTrue(braked < coast, "braked " + braked + " vs coasting " + coast);
    }

    @Test
    @DisplayName("steering right turns yaw up, and left turns it down")
    void steeringSigns() {
        assertTrue(turnWhileDriving(TelInput.FWD | TelInput.RIGHT) > 0.0);
        assertTrue(turnWhileDriving(TelInput.FWD | TelInput.LEFT) < 0.0);
    }

    @Test
    @DisplayName("backing up inverts the steering, as in a car")
    void reverseInvertsSteering() {
        assertTrue(turnWhileDriving(TelInput.BACK | TelInput.RIGHT) < 0.0);
        assertTrue(turnWhileDriving(TelInput.BACK | TelInput.LEFT) > 0.0);
    }

    @Test
    @DisplayName("it does not turn on the spot")
    void noTurnAtStandstill() {
        TelEngineState s = new TelEngineState();
        TelEngine.Result r = TelEngine.step(s, TelInput.RIGHT,
                new TelEngine.Context(0, 0, 0, 0, 0, 1, true, false, 0, 0, true, P));
        assertEquals(0.0, r.yawDelta(), 1.0E-12);
    }

    @Test
    @DisplayName("nobody at the wheel means no power and no steering")
    void noDriverNoPower() {
        TelEngineState s = new TelEngineState();
        s.power = 0.8;
        TelEngine.Result r = TelEngine.step(s, TelInput.FWD | TelInput.RIGHT,
                new TelEngine.Context(0, 0, 0.5, 0, 0, 1, true, false, 0, 0, false, P));
        assertEquals(0.0, s.power, 0.0);
        assertEquals(0.0, s.holdTick);
        assertTrue(r.vz() < 0.5);
    }

    @Test
    @DisplayName("uphill is slower than downhill at the same power")
    void slopeChangesSpeed() {
        double up = steadySpeed(-15.0);
        double down = steadySpeed(15.0);
        assertTrue(up < down, "uphill " + up + " downhill " + down);
    }

    @Test
    @DisplayName("the wheels turn the right way going forward, and the outer side runs faster")
    void wheelsTurnWithTravel() {
        TelEngineState s = new TelEngineState();
        drive(s, TelInput.FWD, 0.0, 100);
        assertTrue(s.wheelRotL < 0.0 && s.wheelRotR < 0.0);

        TelEngineState t = new TelEngineState();
        t.deltaRot = 2.0;
        TelEngine.advanceWheels(t, 0.5, 0.5, P.wheelRadius());
        assertTrue(t.wheelRotL < t.wheelRotR, "right turn: the driver's left wheels cover more ground");
    }

    @Test
    @DisplayName("a bystander reads power and travel back from the motion")
    void observingEstimatesPower() {
        TelEngineState s = new TelEngineState();
        for (int i = 0; i < 100; i++) {
            TelEngine.observe(s, 0, 0, CRUISE, 0, 0, 1, 0.0, P);
        }
        assertEquals(1.0, s.power, 0.02);
        assertTrue(s.wheelRotL < -50.0);
    }

    private static int ticksToStop(double[] speeds) {
        for (int i = 0; i < speeds.length; i++) {
            if (Math.abs(speeds[i]) < 0.03) {
                return i + 1;
            }
        }
        return -1;
    }

    @Test
    @DisplayName("S at cruising speed stops the launcher in a second or so")
    void serviceBrakeStopsQuickly() {
        TelEngineState s = new TelEngineState();
        double cruise = drive(s, TelInput.FWD, 0.0, 1500)[1499];
        double[] v = drive(s, TelInput.BACK, cruise, 40);
        int stop = ticksToStop(v);
        assertTrue(stop >= 15 && stop <= 30, "stopped after " + stop + " ticks");
        assertTrue(v[9] > 0.1, "should still be moving after half a second, was " + v[9]);
        for (int i = 1; i < 15; i++) {
            assertTrue(v[i] < v[i - 1], "speed should fall every tick of the brake");
        }
    }

    @Test
    @DisplayName("the brake does not swing into reverse by itself on the way down")
    void brakeDoesNotReverseEarly() {
        TelEngineState s = new TelEngineState();
        double cruise = drive(s, TelInput.FWD, 0.0, 1500)[1499];
        double[] v = drive(s, TelInput.BACK, cruise, 18);
        for (double x : v) {
            assertTrue(x > -0.02, "went backwards while braking: " + x);
        }
        assertTrue(s.power >= -0.01);
    }

    @Test
    @DisplayName("the handbrake stops it faster than S")
    void handbrakeBeatsTheServiceBrake() {
        TelEngineState a = new TelEngineState();
        TelEngineState b = new TelEngineState();
        double cruise = drive(a, TelInput.FWD, 0.0, 1500)[1499];
        drive(b, TelInput.FWD, 0.0, 1500);
        int withS = ticksToStop(drive(a, TelInput.BACK, cruise, 60));
        int withSpace = ticksToStop(drive(b, TelInput.BRAKE, cruise, 60));
        assertTrue(withSpace > 0 && withSpace < withS, "space " + withSpace + " vs S " + withS);
        assertTrue(withSpace <= 20, "handbrake took " + withSpace + " ticks");
    }

    @Test
    @DisplayName("held on after the stop, S backs up")
    void reverseAfterStop() {
        TelEngineState s = new TelEngineState();
        double cruise = drive(s, TelInput.FWD, 0.0, 1500)[1499];
        double[] v = drive(s, TelInput.BACK, cruise, 250);
        assertTrue(v[249] < -0.05, "should be reversing, was " + v[249]);
    }

    @Test
    @DisplayName("W while rolling backwards brakes the same way")
    void forwardKeyBrakesReversing() {
        TelEngineState s = new TelEngineState();
        double[] back = drive(s, TelInput.BACK, 0.0, 600);
        double rolling = back[599];
        assertTrue(rolling < -0.1);
        double[] v = drive(s, TelInput.FWD, rolling, 40);
        assertTrue(ticksToStop(v) > 0 && ticksToStop(v) <= 30);
        assertTrue(v[5] > rolling, "it should be slowing at once");
    }

    @Test
    @DisplayName("pressing forward from rest is not a brake")
    void accelerationFromRestIsUnchanged() {
        TelEngineState s = new TelEngineState();
        double[] v = drive(s, TelInput.FWD, 0.0, 60);
        assertTrue(v[59] > 0.1);
        assertTrue(s.power > 0.3);
    }

    @Test
    @DisplayName("acceleration reaches 90% of cruising speed in about 121 ticks (6 s)")
    void accelerationFeel() {
        TelEngineState s = new TelEngineState();
        double[] v = drive(s, TelInput.FWD, 0.0, 400);
        int at = -1;
        for (int i = 0; i < v.length; i++) {
            if (v[i] >= 0.9 * CRUISE) {
                at = i + 1;
                break;
            }
        }
        double powerTicks = Math.log(1.0 / (1.0 - 0.9 / 1.02)) * 1.02 / P.inc();
        assertTrue(at >= powerTicks && at < powerTicks + 15, "took " + at + " ticks, power alone " + powerTicks);
        assertTrue(at * 0.05 > 5.5 && at * 0.05 < 7.0, "about six seconds, not " + at * 0.05);
    }

    private static double turnWhileDriving(int input) {
        TelEngineState s = new TelEngineState();
        boolean reverse = (input & TelInput.BACK) != 0;
        int straight = reverse ? TelInput.BACK : TelInput.FWD;
        double v = 0;
        for (int i = 0; i < 400; i++) {
            v = TelEngine.step(s, straight,
                    new TelEngine.Context(0, 0, v, 0, 0, 1, true, false, 0, 0, true, P)).vz();
        }
        double yaw = 0;
        for (int i = 0; i < 30; i++) {
            TelEngine.Result r = TelEngine.step(s, input,
                    new TelEngine.Context(0, 0, v, 0, 0, 1, true, false, 0, 0, true, P));
            v = r.vz();
            yaw += r.yawDelta();
        }
        return yaw;
    }

    private static double steadySpeed(double pitchDeg) {
        TelEngineState s = new TelEngineState();
        double v = 0;
        for (int i = 0; i < 1500; i++) {
            v = TelEngine.step(s, TelInput.FWD,
                    new TelEngine.Context(0, 0, v, 0, 0, 1, true, false, 0, pitchDeg, true, P)).vz();
        }
        return v;
    }
}
