package dev.kollegen.client.emotes;

import net.minecraft.client.model.geom.ModelPart;

public class Emote {

    public final String id;
    public final String name;
    public final String description;
    public final EmoteType type;
    public final boolean isDefault;
    public final int price;
    public final long durationMs;
    public final String icon;

    public Emote(String id, String name, String description, EmoteType type, boolean isDefault, int price, long durationMs, String icon) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.isDefault = isDefault;
        this.price = price;
        this.durationMs = durationMs;
        this.icon = icon;
    }

    public void animate(ModelPart head, ModelPart body, ModelPart rightArm, ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg, float timeSec) {
        switch (this.id) {
            case "wave": {
                rightArm.xRot = (float) Math.toRadians(-140);
                rightArm.zRot = (float) Math.toRadians(-20) + (float) Math.sin(timeSec * 10.0f) * 0.35f;
                head.zRot = (float) Math.toRadians(-10);
                break;
            }
            case "dab": {
                float p = Math.min(1.0f, Math.max(0.0f, timeSec < 0.3f ? timeSec / 0.3f : (timeSec > 1.6f ? (2.0f - timeSec) / 0.4f : 1.0f)));
                rightArm.xRot = (float) Math.toRadians(-40 * p);
                rightArm.yRot = (float) Math.toRadians(-15 * p);
                rightArm.zRot = (float) Math.toRadians(115 * p);
                leftArm.xRot = (float) Math.toRadians(-35 * p);
                leftArm.yRot = (float) Math.toRadians(-15 * p);
                leftArm.zRot = (float) Math.toRadians(110 * p);
                head.xRot = (float) Math.toRadians(35 * p);
                head.yRot = (float) Math.toRadians(40 * p);
                head.zRot = (float) Math.toRadians(-15 * p);
                body.yRot = (float) Math.toRadians(-15 * p);
                body.zRot = (float) Math.toRadians(-5 * p);
                rightLeg.xRot = (float) Math.toRadians(10 * p);
                rightLeg.zRot = (float) Math.toRadians(5 * p);
                leftLeg.xRot = (float) Math.toRadians(-10 * p);
                leftLeg.zRot = (float) Math.toRadians(-5 * p);
                break;
            }
            case "clap": {
                float clap = (float) Math.abs(Math.sin(timeSec * 12.0f));
                rightArm.xRot = (float) Math.toRadians(-75);
                rightArm.yRot = (float) Math.toRadians(-25) - clap * 0.25f;
                rightArm.zRot = (float) Math.toRadians(-35) + clap * 0.2f;
                leftArm.xRot = (float) Math.toRadians(-75);
                leftArm.yRot = (float) Math.toRadians(25) + clap * 0.25f;
                leftArm.zRot = (float) Math.toRadians(35) - clap * 0.2f;
                float bounce = clap * 0.06f;
                rightLeg.xRot = bounce;
                leftLeg.xRot = -bounce;
                break;
            }
            case "bow": {
                float p = (float) Math.sin(Math.min(1.0f, timeSec / 2.5f) * Math.PI);
                body.xRot = (float) Math.toRadians(45 * p);
                head.xRot = (float) Math.toRadians(20 * p);
                rightArm.xRot = (float) Math.toRadians(-25 * p);
                rightArm.zRot = (float) Math.toRadians(15 * p);
                leftArm.xRot = (float) Math.toRadians(-25 * p);
                leftArm.zRot = (float) Math.toRadians(-15 * p);
                rightLeg.xRot = (float) Math.toRadians(-10 * p);
                leftLeg.xRot = (float) Math.toRadians(-10 * p);
                body.y += 2.0f * p;
                break;
            }
            case "point": {
                float p = (float) Math.sin(Math.min(1.0f, timeSec / 2.0f) * Math.PI);
                rightArm.xRot = (float) Math.toRadians(-90 * p);
                rightArm.yRot = (float) Math.toRadians(-10 * p);
                head.xRot = (float) Math.toRadians(-5 * p);
                rightLeg.zRot = 0.08f * p;
                leftLeg.zRot = -0.08f * p;
                break;
            }
            case "facepalm": {
                float p = (float) Math.sin(Math.min(1.0f, timeSec / 2.2f) * Math.PI);
                rightArm.xRot = (float) Math.toRadians(-135 * p);
                rightArm.yRot = (float) Math.toRadians(-18 * p);
                rightArm.zRot = (float) Math.toRadians(32 * p);
                head.xRot = (float) Math.toRadians(25 * p);
                head.yRot = (float) Math.toRadians(8 * p);
                body.xRot = (float) Math.toRadians(8 * p);
                rightLeg.xRot = (float) Math.toRadians(4 * p);
                break;
            }
            case "sit": {
                float sitY = 10.0f;
                body.y += sitY;
                head.y += sitY;
                rightArm.y += sitY;
                leftArm.y += sitY;
                rightLeg.y += sitY;
                leftLeg.y += sitY;
                rightLeg.xRot = (float) Math.toRadians(-85);
                leftLeg.xRot = (float) Math.toRadians(-85);
                rightLeg.yRot = (float) Math.toRadians(15);
                leftLeg.yRot = (float) Math.toRadians(-15);
                rightArm.xRot = (float) Math.toRadians(-20);
                leftArm.xRot = (float) Math.toRadians(-20);
                rightArm.zRot = (float) Math.toRadians(10);
                leftArm.zRot = (float) Math.toRadians(-10);
                break;
            }
            case "liedown": {
                float floorY = 22.0f;
                body.y = floorY;
                body.xRot = (float) Math.toRadians(-90);
                head.y = floorY;
                head.z = 0.0f;
                head.xRot = (float) Math.toRadians(-85);
                head.yRot = 0.0f;
                head.zRot = 0.0f;
                rightArm.x = -5.0f;
                rightArm.y = floorY;
                rightArm.z = -2.0f;
                rightArm.xRot = (float) Math.toRadians(-90);
                rightArm.zRot = (float) Math.toRadians(12);
                leftArm.x = 5.0f;
                leftArm.y = floorY;
                leftArm.z = -2.0f;
                leftArm.xRot = (float) Math.toRadians(-90);
                leftArm.zRot = (float) Math.toRadians(-12);
                rightLeg.x = -1.9f;
                rightLeg.y = floorY;
                rightLeg.z = -12.0f;
                rightLeg.xRot = (float) Math.toRadians(-90);
                rightLeg.yRot = (float) Math.toRadians(8);
                rightLeg.zRot = (float) Math.toRadians(4);
                leftLeg.x = 1.9f;
                leftLeg.y = floorY;
                leftLeg.z = -12.0f;
                leftLeg.xRot = (float) Math.toRadians(-90);
                leftLeg.yRot = (float) Math.toRadians(-8);
                leftLeg.zRot = (float) Math.toRadians(-4);
                break;
            }
            case "squat": {
                float cycle = (float) ((Math.sin(timeSec * 3.5f) + 1.0f) / 2.0f);
                float dip = cycle * 8.0f;
                body.y += dip;
                head.y += dip;
                rightArm.y += dip;
                leftArm.y += dip;
                rightLeg.y += dip;
                leftLeg.y += dip;
                rightLeg.xRot = -cycle * 0.95f;
                leftLeg.xRot = -cycle * 0.95f;
                rightLeg.zRot = cycle * 0.22f;
                leftLeg.zRot = -cycle * 0.22f;
                rightLeg.yRot = cycle * 0.15f;
                leftLeg.yRot = -cycle * 0.15f;
                body.xRot = cycle * 0.35f;
                head.xRot = -cycle * 0.25f;
                rightArm.xRot = (float) Math.toRadians(-75);
                leftArm.xRot = (float) Math.toRadians(-75);
                break;
            }
            case "tpose": {
                rightArm.xRot = 0.0f;
                rightArm.yRot = 0.0f;
                rightArm.zRot = (float) Math.toRadians(90);
                leftArm.xRot = 0.0f;
                leftArm.yRot = 0.0f;
                leftArm.zRot = (float) Math.toRadians(-90);
                head.xRot = 0.0f;
                head.yRot = 0.0f;
                rightLeg.xRot = 0.0f;
                leftLeg.xRot = 0.0f;
                rightLeg.zRot = 0.05f;
                leftLeg.zRot = -0.05f;
                break;
            }
            case "floss": {
                float beat = timeSec * 6.283f * 1.5f;
                float swing = (float) Math.sin(beat);
                float hip = (float) Math.sin(beat) * 0.25f;
                body.zRot = hip;
                body.yRot = (float) Math.cos(beat) * 0.15f;
                rightArm.xRot = (float) Math.toRadians(-25) + swing * 0.4f;
                rightArm.zRot = (float) Math.toRadians(20) + swing * 0.6f;
                leftArm.xRot = (float) Math.toRadians(-25) - swing * 0.4f;
                leftArm.zRot = (float) Math.toRadians(-20) + swing * 0.6f;
                head.yRot = -body.yRot * 0.8f;
                rightLeg.zRot = -hip * 0.7f;
                leftLeg.zRot = -hip * 0.7f;
                rightLeg.xRot = (float) Math.sin(beat) * 0.12f;
                leftLeg.xRot = -(float) Math.sin(beat) * 0.12f;
                break;
            }
            case "meditate": {
                float hover = (float) Math.sin(timeSec * 2.2f) * 3.0f - 8.0f;
                body.y += hover;
                head.y += hover;
                rightArm.y += hover;
                leftArm.y += hover;
                rightLeg.y += hover;
                leftLeg.y += hover;
                rightLeg.xRot = (float) Math.toRadians(-80);
                rightLeg.yRot = (float) Math.toRadians(35);
                rightLeg.zRot = (float) Math.toRadians(25);
                leftLeg.xRot = (float) Math.toRadians(-80);
                leftLeg.yRot = (float) Math.toRadians(-35);
                leftLeg.zRot = (float) Math.toRadians(-25);
                rightArm.xRot = (float) Math.toRadians(-30);
                rightArm.yRot = (float) Math.toRadians(20);
                rightArm.zRot = (float) Math.toRadians(20);
                leftArm.xRot = (float) Math.toRadians(-30);
                leftArm.yRot = (float) Math.toRadians(-20);
                leftArm.zRot = (float) Math.toRadians(-20);
                head.xRot = 0.08f;
                break;
            }
            case "takethel": {
                float kick = (float) Math.sin(timeSec * 7.0f);
                rightArm.xRot = (float) Math.toRadians(-125);
                rightArm.yRot = (float) Math.toRadians(30);
                rightArm.zRot = (float) Math.toRadians(-25);
                leftArm.xRot = (float) Math.toRadians(-20) + kick * 0.3f;
                leftArm.zRot = (float) Math.toRadians(-20);
                rightLeg.xRot = kick * 0.65f;
                leftLeg.xRot = -kick * 0.65f;
                rightLeg.zRot = (float) Math.toRadians(12);
                leftLeg.zRot = (float) Math.toRadians(-12);
                head.zRot = (float) Math.sin(timeSec * 3.5f) * 0.15f;
                break;
            }
            case "guitar": {
                float rock = (float) Math.sin(timeSec * 8.0f);
                leftArm.xRot = (float) Math.toRadians(-70);
                leftArm.yRot = (float) Math.toRadians(-35);
                leftArm.zRot = (float) Math.toRadians(40);
                rightArm.xRot = (float) Math.toRadians(-50) + rock * 0.35f;
                rightArm.yRot = (float) Math.toRadians(20);
                rightArm.zRot = (float) Math.toRadians(-15) + rock * 0.25f;
                head.xRot = (float) Math.abs(Math.sin(timeSec * 8.0f)) * 0.3f;
                body.xRot = 0.12f;
                rightLeg.xRot = (float) Math.toRadians(-22) + rock * 0.12f;
                leftLeg.xRot = (float) Math.toRadians(20) - rock * 0.12f;
                rightLeg.zRot = (float) Math.toRadians(14);
                leftLeg.zRot = (float) Math.toRadians(-14);
                break;
            }
            case "breakdance": {
                float spin = timeSec * 7.5f;
                float floorY = 21.0f;
                float bounce = (float) Math.abs(Math.sin(spin)) * 0.8f;
                body.y = floorY - bounce;
                body.xRot = (float) Math.toRadians(-80);
                body.yRot = spin;
                body.zRot = (float) Math.sin(spin * 2.0f) * 0.15f;

                head.y = floorY - 1.0f - bounce;
                head.xRot = (float) Math.toRadians(-65);
                head.yRot = spin;

                float sinS = (float) Math.sin(spin);
                float cosS = (float) Math.cos(spin);
                float waistY = floorY + 2.08f - bounce;
                float waistZ = -11.82f * cosS;
                float waistX = -11.82f * sinS;

                rightLeg.y = waistY;
                rightLeg.x = waistX - 1.9f * cosS;
                rightLeg.z = waistZ + 1.9f * sinS;
                rightLeg.yRot = spin;
                rightLeg.xRot = (float) Math.toRadians(-75) + (float) Math.sin(spin * 2.0f) * 0.55f;
                rightLeg.zRot = (float) Math.toRadians(45) + (float) Math.cos(spin * 2.0f) * 0.2f;

                leftLeg.y = waistY;
                leftLeg.x = waistX + 1.9f * cosS;
                leftLeg.z = waistZ - 1.9f * sinS;
                leftLeg.yRot = spin;
                leftLeg.xRot = (float) Math.toRadians(-75) - (float) Math.sin(spin * 2.0f) * 0.55f;
                leftLeg.zRot = (float) Math.toRadians(-45) - (float) Math.cos(spin * 2.0f) * 0.2f;

                rightArm.y = floorY - bounce;
                rightArm.x = -5.0f * cosS;
                rightArm.z = 5.0f * sinS;
                rightArm.yRot = spin;
                rightArm.xRot = (float) Math.toRadians(-90) + (float) Math.sin(spin) * 0.35f;
                rightArm.zRot = (float) Math.toRadians(25);

                leftArm.y = floorY - bounce;
                leftArm.x = 5.0f * cosS;
                leftArm.z = -5.0f * sinS;
                leftArm.yRot = spin;
                leftArm.xRot = (float) Math.toRadians(-90) - (float) Math.sin(spin) * 0.35f;
                leftArm.zRot = (float) Math.toRadians(-25);
                break;
            }
            case "backflip": {
                float p = Math.min(1.0f, timeSec / 1.5f);
                float jump = (float) Math.sin(p * Math.PI) * 16.0f;
                float flip = (float) (-p * Math.PI * 2.0);
                float cy = 10.0f;
                float cosF = (float) Math.cos(flip);
                float sinF = (float) Math.sin(flip);
                float tuck = (float) Math.sin(p * Math.PI) * 0.9f;

                head.y = cy - 10.0f * cosF - jump;
                head.z = -10.0f * sinF;
                head.xRot = flip + (float) Math.toRadians(-20) * (float) Math.sin(p * Math.PI);

                body.y = cy - 6.0f * cosF - jump;
                body.z = -6.0f * sinF;
                body.xRot = flip;

                rightArm.y = cy - 8.0f * cosF - jump;
                rightArm.z = -8.0f * sinF;
                rightArm.xRot = flip + (float) Math.toRadians(-120) * (float) Math.sin(p * Math.PI);
                leftArm.y = cy - 8.0f * cosF - jump;
                leftArm.z = -8.0f * sinF;
                leftArm.xRot = flip + (float) Math.toRadians(-120) * (float) Math.sin(p * Math.PI);

                rightLeg.y = cy + 6.0f * cosF - jump;
                rightLeg.z = 6.0f * sinF;
                rightLeg.xRot = flip - tuck;
                leftLeg.y = cy + 6.0f * cosF - jump;
                leftLeg.z = 6.0f * sinF;
                leftLeg.xRot = flip - tuck;
                break;
            }
            case "jumpingjacks": {
                float cycle = (float) Math.sin(timeSec * 6.0f);
                float open = (float) Math.max(0.0, cycle);
                float dip = open * 3.5f;
                body.y -= dip;
                head.y -= dip;
                rightArm.y -= dip;
                leftArm.y -= dip;
                rightLeg.y -= dip;
                leftLeg.y -= dip;
                rightArm.zRot = open * (float) Math.toRadians(120);
                leftArm.zRot = -open * (float) Math.toRadians(120);
                rightLeg.zRot = open * (float) Math.toRadians(30);
                leftLeg.zRot = -open * (float) Math.toRadians(30);
                rightLeg.xRot = open * 0.08f;
                leftLeg.xRot = open * 0.08f;
                break;
            }
            case "robot": {
                float step = (float) Math.floor(timeSec * 4.0f) % 4.0f;
                if (step == 0) {
                    rightArm.xRot = (float) Math.toRadians(-90);
                    leftArm.xRot = 0.0f;
                    head.yRot = (float) Math.toRadians(30);
                    rightLeg.xRot = (float) Math.toRadians(-25);
                    leftLeg.xRot = 0.0f;
                } else if (step == 1) {
                    rightArm.xRot = (float) Math.toRadians(-90);
                    leftArm.xRot = (float) Math.toRadians(-90);
                    head.yRot = 0.0f;
                    rightLeg.xRot = 0.0f;
                    leftLeg.xRot = 0.0f;
                } else if (step == 2) {
                    rightArm.xRot = 0.0f;
                    leftArm.xRot = (float) Math.toRadians(-90);
                    head.yRot = (float) Math.toRadians(-30);
                    rightLeg.xRot = 0.0f;
                    leftLeg.xRot = (float) Math.toRadians(-25);
                } else {
                    rightArm.xRot = 0.0f;
                    leftArm.xRot = 0.0f;
                    head.yRot = 0.0f;
                    rightLeg.xRot = 0.0f;
                    leftLeg.xRot = 0.0f;
                }
                break;
            }
        }
    }
}
