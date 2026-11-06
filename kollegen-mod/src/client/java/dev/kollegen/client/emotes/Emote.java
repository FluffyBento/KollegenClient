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
                rightArm.xRot = (float) Math.toRadians(-115 * p);
                rightArm.yRot = (float) Math.toRadians(-35 * p);
                rightArm.zRot = (float) Math.toRadians(65 * p);
                leftArm.xRot = (float) Math.toRadians(-80 * p);
                leftArm.yRot = (float) Math.toRadians(55 * p);
                leftArm.zRot = (float) Math.toRadians(-45 * p);
                head.xRot = (float) Math.toRadians(25 * p);
                head.yRot = (float) Math.toRadians(45 * p);
                head.zRot = (float) Math.toRadians(-15 * p);
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
                break;
            }
            case "point": {
                float p = (float) Math.sin(Math.min(1.0f, timeSec / 2.0f) * Math.PI);
                rightArm.xRot = (float) Math.toRadians(-90 * p);
                rightArm.yRot = (float) Math.toRadians(-10 * p);
                head.xRot = (float) Math.toRadians(-5 * p);
                break;
            }
            case "facepalm": {
                float p = (float) Math.sin(Math.min(1.0f, timeSec / 2.2f) * Math.PI);
                rightArm.xRot = (float) Math.toRadians(-110 * p);
                rightArm.yRot = (float) Math.toRadians(35 * p);
                rightArm.zRot = (float) Math.toRadians(-30 * p);
                head.xRot = (float) Math.toRadians(25 * p);
                head.yRot = (float) Math.toRadians(10 * p);
                break;
            }
            case "sit": {
                body.y += 10.0f;
                head.y += 10.0f;
                rightArm.y += 10.0f;
                leftArm.y += 10.0f;
                rightLeg.y += 10.0f;
                leftLeg.y += 10.0f;
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
                body.y += 19.0f;
                body.xRot = (float) Math.toRadians(90);
                head.y += 19.0f;
                head.xRot = (float) Math.toRadians(80);
                rightLeg.y += 19.0f;
                leftLeg.y += 19.0f;
                rightArm.y += 19.0f;
                leftArm.y += 19.0f;
                rightArm.zRot = (float) Math.toRadians(25);
                leftArm.zRot = (float) Math.toRadians(-25);
                break;
            }
            case "squat": {
                float cycle = (float) ((Math.sin(timeSec * 3.5f) + 1.0f) / 2.0f);
                float dip = cycle * 6.0f;
                body.y += dip;
                head.y += dip;
                rightArm.y += dip;
                leftArm.y += dip;
                rightLeg.y += dip;
                leftLeg.y += dip;
                rightLeg.xRot = -cycle * 0.9f;
                leftLeg.xRot = -cycle * 0.9f;
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
                rightLeg.xRot = kick * 0.5f;
                leftLeg.xRot = -kick * 0.5f;
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
                break;
            }
            case "breakdance": {
                float rot = timeSec * 5.0f;
                body.y += 12.0f;
                head.y += 12.0f;
                rightArm.y += 12.0f;
                leftArm.y += 12.0f;
                rightLeg.y += 12.0f;
                leftLeg.y += 12.0f;
                body.yRot = rot;
                head.yRot = rot;
                rightArm.xRot = (float) Math.toRadians(-80);
                leftArm.xRot = (float) Math.toRadians(-80);
                rightLeg.zRot = (float) Math.toRadians(45) + (float) Math.sin(rot * 2.0f) * 0.4f;
                leftLeg.zRot = (float) Math.toRadians(-45) - (float) Math.sin(rot * 2.0f) * 0.4f;
                break;
            }
            case "backflip": {
                float p = Math.min(1.0f, timeSec / 1.5f);
                float jump = (float) Math.sin(p * Math.PI) * 14.0f;
                body.y -= jump;
                head.y -= jump;
                rightArm.y -= jump;
                leftArm.y -= jump;
                rightLeg.y -= jump;
                leftLeg.y -= jump;
                float flip = (float) (-p * Math.PI * 2.0);
                body.xRot = flip;
                head.xRot = flip * 0.8f;
                rightLeg.xRot = (float) Math.toRadians(-30);
                leftLeg.xRot = (float) Math.toRadians(-30);
                rightArm.xRot = (float) Math.toRadians(-150);
                leftArm.xRot = (float) Math.toRadians(-150);
                break;
            }
            case "jumpingjacks": {
                float cycle = (float) Math.sin(timeSec * 5.0f);
                float open = (float) Math.max(0.0, cycle);
                body.y -= open * 3.5f;
                head.y -= open * 3.5f;
                rightArm.y -= open * 3.5f;
                leftArm.y -= open * 3.5f;
                rightLeg.y -= open * 3.5f;
                leftLeg.y -= open * 3.5f;
                rightArm.zRot = open * (float) Math.toRadians(120);
                leftArm.zRot = -open * (float) Math.toRadians(120);
                rightLeg.zRot = open * (float) Math.toRadians(30);
                leftLeg.zRot = -open * (float) Math.toRadians(30);
                break;
            }
            case "robot": {
                float step = (float) Math.floor(timeSec * 4.0f) % 4.0f;
                if (step == 0) {
                    rightArm.xRot = (float) Math.toRadians(-90);
                    leftArm.xRot = 0.0f;
                    head.yRot = (float) Math.toRadians(30);
                } else if (step == 1) {
                    rightArm.xRot = (float) Math.toRadians(-90);
                    leftArm.xRot = (float) Math.toRadians(-90);
                    head.yRot = 0.0f;
                } else if (step == 2) {
                    rightArm.xRot = 0.0f;
                    leftArm.xRot = (float) Math.toRadians(-90);
                    head.yRot = (float) Math.toRadians(-30);
                } else {
                    rightArm.xRot = 0.0f;
                    leftArm.xRot = 0.0f;
                    head.yRot = 0.0f;
                }
                break;
            }
        }
    }
}
