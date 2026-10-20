package com.fitcommit.app.activities;

import com.fitcommit.app.R;

public class SpaceScanActivity extends BaseCameraActivity {
    @Override
    protected String getScreenTitle() {
        return getString(R.string.space_scan_title);
    }

    @Override
    protected String getGuideText() {
        return getString(R.string.space_scan_guide);
    }

    @Override
    protected String getAnalysisTitle() {
        return getString(R.string.space_scan_status);
    }

    @Override
    protected String getAnalysisBody() {
        return getString(R.string.space_scan_ready);
    }
}
