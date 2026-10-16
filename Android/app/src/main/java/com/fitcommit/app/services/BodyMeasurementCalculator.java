package com.fitcommit.app.services;

import com.fitcommit.app.models.BodyMeasurement;
import com.google.mediapipe.tasks.components.containers.Landmark;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.List;

public final class BodyMeasurementCalculator {
    private static final int NOSE = 0;
    private static final int LEFT_SHOULDER = 11;
    private static final int RIGHT_SHOULDER = 12;
    private static final int LEFT_ELBOW = 13;
    private static final int RIGHT_ELBOW = 14;
    private static final int LEFT_WRIST = 15;
    private static final int RIGHT_WRIST = 16;
    private static final int LEFT_HIP = 23;
    private static final int RIGHT_HIP = 24;
    private static final int LEFT_KNEE = 25;
    private static final int RIGHT_KNEE = 26;
    private static final int LEFT_ANKLE = 27;
    private static final int RIGHT_ANKLE = 28;
    private static final int[] REQUIRED = {
            NOSE, LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW,
            LEFT_WRIST, RIGHT_WRIST, LEFT_HIP, RIGHT_HIP, LEFT_KNEE,
            RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE
    };
    private static final float MIN_VISIBILITY = 0.65f;
    private static final float HEAD_TOP_OFFSET_METERS = 0.10f;

    private BodyMeasurementCalculator() {
    }

    public static BodyMeasurement calculate(PoseLandmarkerResult result) {
        if (result == null || result.worldLandmarks().isEmpty() || result.landmarks().isEmpty()) {
            return null;
        }
        List<Landmark> world = result.worldLandmarks().get(0);
        List<NormalizedLandmark> normalized = result.landmarks().get(0);
        if (world.size() < 33 || normalized.size() < 33 || !isReliable(normalized)) return null;

        Point nose = point(world.get(NOSE));
        Point leftShoulder = point(world.get(LEFT_SHOULDER));
        Point rightShoulder = point(world.get(RIGHT_SHOULDER));
        Point leftElbow = point(world.get(LEFT_ELBOW));
        Point rightElbow = point(world.get(RIGHT_ELBOW));
        Point leftWrist = point(world.get(LEFT_WRIST));
        Point rightWrist = point(world.get(RIGHT_WRIST));
        Point leftHip = point(world.get(LEFT_HIP));
        Point rightHip = point(world.get(RIGHT_HIP));
        Point leftKnee = point(world.get(LEFT_KNEE));
        Point rightKnee = point(world.get(RIGHT_KNEE));
        Point leftAnkle = point(world.get(LEFT_ANKLE));
        Point rightAnkle = point(world.get(RIGHT_ANKLE));

        Point middleAnkle = midpoint(leftAnkle, rightAnkle);
        float height = distance(nose, middleAnkle) + HEAD_TOP_OFFSET_METERS;
        float shoulder = distance(leftShoulder, rightShoulder);
        float hip = distance(leftHip, rightHip);
        float leftArm = distance(leftShoulder, leftElbow) + distance(leftElbow, leftWrist);
        float rightArm = distance(rightShoulder, rightElbow) + distance(rightElbow, rightWrist);
        float leftLeg = distance(leftHip, leftKnee) + distance(leftKnee, leftAnkle);
        float rightLeg = distance(rightHip, rightKnee) + distance(rightKnee, rightAnkle);

        if (!plausible(height, shoulder, hip)) return null;
        String shape = classifyBodyShape(shoulder, hip);
        return new BodyMeasurement(
                System.currentTimeMillis(),
                height,
                shoulder,
                hip,
                (leftArm + rightArm) / 2f,
                (leftLeg + rightLeg) / 2f,
                shape,
                recommendationFor(shape)
        );
    }

    public static String classifyBodyShape(float shoulderWidth, float hipWidth) {
        if (hipWidth <= 0f) return "측정 불가";
        float ratio = shoulderWidth / hipWidth;
        if (ratio > 1.25f) return "역삼각형";
        if (ratio < 0.95f) return "삼각형";
        return "직사각형/스탠다드";
    }

    public static String recommendationFor(String shape) {
        if ("역삼각형".equals(shape)) {
            return "시선을 아래로 분산하고 하체 볼륨을 살리는 와이드 팬츠나 카고 팬츠를 추천해요. 상의는 브이넥이나 깊은 U넥으로 균형을 잡아보세요.";
        }
        if ("삼각형".equals(shape)) {
            return "상체에 볼륨을 더하는 크롭 재킷이나 드롭 숄더 오버핏 셔츠를 활용하면 전체적인 균형이 좋아 보여요.";
        }
        if ("직사각형/스탠다드".equals(shape)) {
            return "허리선을 강조하는 벨티드 재킷이나 크롭 상의를 활용해 비율을 살려보세요. 대부분의 레귤러 핏과 잘 어울려요.";
        }
        return "전신이 화면에 들어오도록 자세를 바로잡고 다시 측정해주세요.";
    }

    private static boolean isReliable(List<NormalizedLandmark> landmarks) {
        for (int index : REQUIRED) {
            NormalizedLandmark landmark = landmarks.get(index);
            if (landmark.visibility().orElse(0f) < MIN_VISIBILITY) return false;
            if (landmark.x() < 0.02f || landmark.x() > 0.98f
                    || landmark.y() < 0.02f || landmark.y() > 0.98f) return false;
        }
        return true;
    }

    private static boolean plausible(float height, float shoulder, float hip) {
        return height >= 1.0f && height <= 2.3f
                && shoulder >= 0.18f && shoulder <= 0.75f
                && hip >= 0.15f && hip <= 0.70f;
    }

    private static Point point(Landmark landmark) {
        return new Point(landmark.x(), landmark.y(), landmark.z());
    }

    private static Point midpoint(Point first, Point second) {
        return new Point(
                (first.x + second.x) / 2f,
                (first.y + second.y) / 2f,
                (first.z + second.z) / 2f
        );
    }

    private static float distance(Point first, Point second) {
        float x = first.x - second.x;
        float y = first.y - second.y;
        float z = first.z - second.z;
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static final class Point {
        final float x;
        final float y;
        final float z;

        Point(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
