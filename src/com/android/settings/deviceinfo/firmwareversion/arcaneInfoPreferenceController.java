/*
 * Copyright (C) 2020 arcane-OS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.deviceinfo.firmwareversion;

import android.content.Context;
import android.os.Build;
import android.os.SystemProperties;
import android.widget.TextView;

import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.widget.LayoutPreference;

public class arcaneInfoPreferenceController extends AbstractPreferenceController {

    private static final String KEY_ARCANE_INFO = "arcane_info";

    private static final String PROP_ARCANE_VERSION = "ro.arcane.version";
    private static final String PROP_ARCANE_VERSION_CODE = "org.arcane.version";
    private static final String PROP_ARCANE_RELEASETYPE = "org.arcane.build_type";
    private static final String PROP_ARCANE_MAINTAINER = "ro.arcane.maintainer";
    private static final String PROP_ARCANE_DEVICE = "ro.arcane.device_name";

    public arcaneInfoPreferenceController(Context context) {
        super(context);
    }

    private String getDeviceName() {
        String device = SystemProperties.get(PROP_ARCANE_DEVICE, "");
        if (device.equals("")) {
            device = Build.MANUFACTURER + " " + Build.MODEL;
        }
        return device;
    }

    private String getarcaneVersion() {
        final String version = SystemProperties.get(PROP_ARCANE_VERSION,
                this.mContext.getString(R.string.device_info_default));
        final String versionCode = SystemProperties.get(PROP_ARCANE_VERSION_CODE,
                this.mContext.getString(R.string.device_info_default));

        return version + " | " + versionCode;
    }

    private String getarcaneReleaseType() {
        final String releaseType = SystemProperties.get(PROP_ARCANE_RELEASETYPE,
                this.mContext.getString(R.string.device_info_default));

        return releaseType.substring(0, 1).toUpperCase() +
                 releaseType.substring(1).toLowerCase();
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        final LayoutPreference arcaneInfoPreference = screen.findPreference(KEY_ARCANE_INFO);
        final TextView version = (TextView) arcaneInfoPreference.findViewById(R.id.version_message);
        final TextView device = (TextView) arcaneInfoPreference.findViewById(R.id.device_message);
        final TextView releaseType = (TextView) arcaneInfoPreference.findViewById(R.id.release_type_message);
        final TextView maintainer = (TextView) arcaneInfoPreference.findViewById(R.id.maintainer_message);
        final String arcaneVersion = getarcaneVersion();
        final String arcaneDevice = getDeviceName();
        final String arcaneReleaseType = getarcaneReleaseType();
        final String arcaneMaintainer = SystemProperties.get(PROP_ARCANE_MAINTAINER,
                this.mContext.getString(R.string.device_info_default));
        version.setText(arcaneVersion);
        device.setText(arcaneDevice);
        releaseType.setText(arcaneReleaseType);
        maintainer.setText(arcaneMaintainer);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_ARCANE_INFO;
    }
}
