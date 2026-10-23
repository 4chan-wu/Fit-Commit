package com.fitcommit.app.services;

import static org.junit.Assert.assertEquals;

import com.fitcommit.app.models.BodyMeasurement;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BodyMeasurementServiceTest {
    @Test
    public void mergeWithLocal_deduplicatesMatchingTimestampsAndKeepsNewestFirst() {
        BodyMeasurement serverNewest = measurement("server-new", 3_000L);
        BodyMeasurement serverDuplicate = measurement("server-existing", 2_000L);
        BodyMeasurement localDuplicate = measurement("", 2_500L);
        BodyMeasurement localOnly = measurement("", 1_000L);

        List<BodyMeasurement> merged = BodyMeasurementService.mergeWithLocal(
                Arrays.asList(serverNewest, serverDuplicate),
                Arrays.asList(localDuplicate, localOnly)
        );

        assertEquals(3, merged.size());
        assertEquals("server-new", merged.get(0).recordId);
        assertEquals("server-existing", merged.get(1).recordId);
        assertEquals(1_000L, merged.get(2).measuredAt);
    }

    @Test
    public void mergeWithLocal_keepsOfflineMeasurement() {
        BodyMeasurement local = measurement("", 5_000L);
        List<BodyMeasurement> merged = BodyMeasurementService.mergeWithLocal(
                Collections.emptyList(),
                Collections.singletonList(local)
        );

        assertEquals(1, merged.size());
        assertEquals(5_000L, merged.get(0).measuredAt);
    }

    private BodyMeasurement measurement(String id, long measuredAt) {
        return new BodyMeasurement(
                id,
                measuredAt,
                1.75f,
                0.43f,
                0.38f,
                0.62f,
                0.91f,
                "rectangle",
                "",
                "ar_raw_depth",
                0.9f,
                "android-v1"
        );
    }
}
