package com.fitcommit.app.services;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BodyMeasurementCalculatorTest {
    @Test
    public void classifiesInvertedTriangle() {
        assertEquals("역삼각형", BodyMeasurementCalculator.classifyBodyShape(0.51f, 0.40f));
    }

    @Test
    public void classifiesTriangle() {
        assertEquals("삼각형", BodyMeasurementCalculator.classifyBodyShape(0.37f, 0.40f));
    }

    @Test
    public void classifiesBalancedShapeAtBoundaries() {
        assertEquals("직사각형/스탠다드", BodyMeasurementCalculator.classifyBodyShape(0.50f, 0.40f));
        assertEquals("직사각형/스탠다드", BodyMeasurementCalculator.classifyBodyShape(0.38f, 0.40f));
    }

    @Test
    public void rejectsMissingHipWidth() {
        assertEquals("측정 불가", BodyMeasurementCalculator.classifyBodyShape(0.40f, 0f));
    }
}
