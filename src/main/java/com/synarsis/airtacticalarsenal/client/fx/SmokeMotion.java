package com.synarsis.airtacticalarsenal.client.fx;

public final class SmokeMotion {

    public static final double CAP_BELOW = 0.05;
    public static final double CAP_SPREAD = 1.0;

    private final SmokeParams params;
    private final double buoyancy;
    private final double originX;
    private final double originZ;
    private final double capRoll;
    private final double capAngle;

    public double vx;
    public double vy;
    public double vz;
    private int age;
    private boolean capped;

    public SmokeMotion(SmokeParams params, double vx, double vy, double vz, double originX, double originZ,
            double capRoll, double capAngle) {
        this.params = params;
        this.buoyancy = params.buoyancy();
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.originX = originX;
        this.originZ = originZ;
        this.capRoll = capRoll;
        this.capAngle = capAngle;
    }

    public int age() {
        return this.age;
    }

    public boolean capped() {
        return this.capped;
    }

    public void accelerate(double windX, double windZ, double x, double z) {
        double d = this.params.drag();
        double wind = this.params.wind();
        this.vx = this.vx * d + windX * wind * (1.0 - d);
        this.vz = this.vz * d + windZ * wind * (1.0 - d);
        double lift = this.age >= this.params.riseDelay() ? this.buoyancy : 0.0;
        this.vy = this.vy * d + lift;

        if (this.params.capPush() > 0.0f && !this.capped && this.age > 0 && this.vy < CAP_BELOW) {
            this.capped = true;
            double push = this.params.capPush() * (1.0 + CAP_SPREAD * this.capRoll);
            double dx = x - this.originX;
            double dz = z - this.originZ;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0.01) {
                this.vx += dx / len * push;
                this.vz += dz / len * push;
            } else {
                this.vx += Math.cos(this.capAngle) * push;
                this.vz += Math.sin(this.capAngle) * push;
            }
        }
        this.age++;
    }

    public record Journey(double rise, double maxRise, double side, double maxSide, double maxLift) {
    }

    public static Journey simulate(SmokeParams params, double vx, double vy, double vz, double capRoll) {
        SmokeMotion m = new SmokeMotion(params, vx, vy, vz, 0.0, 0.0, capRoll, 0.0);
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        double maxRise = 0.0;
        double maxSide = 0.0;
        double maxLift = 0.0;
        for (int i = 0; i < params.lifetime(); i++) {
            m.accelerate(0.0, 0.0, x, z);
            x += m.vx;
            y += m.vy;
            z += m.vz;
            maxRise = Math.max(maxRise, y);
            maxSide = Math.max(maxSide, Math.hypot(x, z));
            maxLift = Math.max(maxLift, m.vy);
        }
        return new Journey(y, maxRise, Math.hypot(x, z), maxSide, maxLift);
    }
}
