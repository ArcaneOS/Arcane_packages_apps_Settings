
package com.android.settings.deviceinfo;

import android.os.SystemProperties;

public class VersionUtils {
    public static String getarcaneVersion(){
        return SystemProperties.get("org.arcane.version.display","");
    }
}
