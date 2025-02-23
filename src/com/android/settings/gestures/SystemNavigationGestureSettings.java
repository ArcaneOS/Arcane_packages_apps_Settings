/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.gestures;

import static android.os.UserHandle.USER_CURRENT;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_2BUTTON_OVERLAY;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_3BUTTON_OVERLAY;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL_OVERLAY;

import static com.android.settings.widget.RadioButtonPreferenceWithExtraWidget.EXTRA_WIDGET_VISIBILITY_GONE;
import static com.android.settings.widget.RadioButtonPreferenceWithExtraWidget.EXTRA_WIDGET_VISIBILITY_SETTING;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.SettingsTutorialDialogWrapperActivity;
import com.android.settings.dashboard.suggestions.SuggestionFeatureProvider;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.actionbar.SearchMenuController;
import com.android.settings.support.actionbar.HelpMenuController;
import com.android.settings.support.actionbar.HelpResourceProvider;
import com.android.settings.utils.CandidateInfoExtra;
import com.android.settings.widget.RadioButtonPickerFragment;
import com.android.settings.widget.RadioButtonPreferenceWithExtraWidget;
import com.android.settings.widget.VideoPreference;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.widget.CandidateInfo;
import com.android.settingslib.widget.RadioButtonPreference;

import java.util.ArrayList;
import java.util.List;

import com.android.settings.custom.buttons.LegacyNavigationSettingsFragment;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

@SearchIndexable
public class SystemNavigationGestureSettings extends RadioButtonPickerFragment implements
        HelpResourceProvider {

    private static final String TAG = "SystemNavigationGesture";

    @VisibleForTesting
    static final String KEY_SYSTEM_NAV_3BUTTONS = "system_nav_3buttons";
    @VisibleForTesting
    static final String KEY_SYSTEM_NAV_2BUTTONS = "system_nav_2buttons";
    @VisibleForTesting
    static final String KEY_SYSTEM_NAV_GESTURAL = "system_nav_gestural";

    public static final String PREF_KEY_SUGGESTION_COMPLETE =
            "pref_system_navigation_suggestion_complete";

    private static final String HIDDEN_OVERLAY_PKG = "com.custom.overlay.systemui.gestural.hidden";

    private IOverlayManager mOverlayManager;

    private VideoPreference mVideoPreference;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        SearchMenuController.init(this /* host */);
        HelpMenuController.init(this /* host */);

        SuggestionFeatureProvider suggestionFeatureProvider = FeatureFactory.getFactory(context)
                .getSuggestionFeatureProvider(context);
        SharedPreferences prefs = suggestionFeatureProvider.getSharedPrefs(context);
        prefs.edit().putBoolean(PREF_KEY_SUGGESTION_COMPLETE, true).apply();

        mOverlayManager = IOverlayManager.Stub.asInterface(
                ServiceManager.getService(Context.OVERLAY_SERVICE));

        mVideoPreference = new VideoPreference(context);
        setIllustrationVideo(mVideoPreference, getDefaultKey());
        mVideoPreference.setHeight( /* Illustration height in dp */
                getResources().getDimension(R.dimen.system_navigation_illustration_height)
                        / getResources().getDisplayMetrics().density);

        migrateOverlaySensitivityToSettings(context, mOverlayManager);
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_GESTURE_SWIPE_UP;
    }

    @Override
    public void updateCandidates() {
        final String defaultKey = getDefaultKey();
        final String systemDefaultKey = getSystemDefaultKey();
        final PreferenceScreen screen = getPreferenceScreen();
        screen.removeAll();
        screen.addPreference(mVideoPreference);

        final List<? extends CandidateInfo> candidateList = getCandidates();
        if (candidateList == null) {
            return;
        }
        for (CandidateInfo info : candidateList) {
            RadioButtonPreferenceWithExtraWidget pref =
                    new RadioButtonPreferenceWithExtraWidget(getPrefContext());
            bindPreference(pref, info.getKey(), info, defaultKey);
            bindPreferenceExtra(pref, info.getKey(), info, defaultKey, systemDefaultKey);
            screen.addPreference(pref);
        }
        mayCheckOnlyRadioButton();
    }

    @Override
    public void bindPreferenceExtra(RadioButtonPreference pref,
            String key, CandidateInfo info, String defaultKey, String systemDefaultKey) {
        if (!(info instanceof CandidateInfoExtra)
                || !(pref instanceof RadioButtonPreferenceWithExtraWidget)) {
            return;
        }

        pref.setSummary(((CandidateInfoExtra) info).loadSummary());

        RadioButtonPreferenceWithExtraWidget p = (RadioButtonPreferenceWithExtraWidget) pref;
        p.setExtraWidgetVisibility(EXTRA_WIDGET_VISIBILITY_SETTING);
        if (info.getKey() == KEY_SYSTEM_NAV_GESTURAL) {
            p.setExtraWidgetOnClickListener((v) -> startActivity(new Intent(
                    GestureNavigationSettingsFragment.GESTURE_NAVIGATION_SETTINGS)));
        } else {
            p.setExtraWidgetOnClickListener((v) -> {
                new SubSettingLauncher(getContext())
                    .setDestination(LegacyNavigationSettingsFragment.class.getName())
                    .setTitleRes(R.string.navigation_bar_title)
                    .setSourceMetricsCategory(MetricsEvent.CUSTOM_SETTINGS)
                    .launch();
            });
        }
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.system_navigation_gesture_settings;
    }

    @Override
    protected List<? extends CandidateInfo> getCandidates() {
        final Context c = getContext();
        List<CandidateInfoExtra> candidates = new ArrayList<>();

        if (SystemNavigationPreferenceController.isOverlayPackageAvailable(c,
                NAV_BAR_MODE_GESTURAL_OVERLAY)) {
            candidates.add(new CandidateInfoExtra(
                    c.getText(R.string.edge_to_edge_navigation_title),
                    c.getText(R.string.edge_to_edge_navigation_summary),
                    KEY_SYSTEM_NAV_GESTURAL, true /* enabled */));
        }
        if (SystemNavigationPreferenceController.isOverlayPackageAvailable(c,
                NAV_BAR_MODE_2BUTTON_OVERLAY)) {
            candidates.add(new CandidateInfoExtra(
                    c.getText(R.string.swipe_up_to_switch_apps_title),
                    c.getText(R.string.swipe_up_to_switch_apps_summary),
                    KEY_SYSTEM_NAV_2BUTTONS, true /* enabled */));
        }
        if (SystemNavigationPreferenceController.isOverlayPackageAvailable(c,
                NAV_BAR_MODE_3BUTTON_OVERLAY)) {
            candidates.add(new CandidateInfoExtra(
                    c.getText(R.string.legacy_navigation_title),
                    c.getText(R.string.legacy_navigation_summary),
                    KEY_SYSTEM_NAV_3BUTTONS, true /* enabled */));
        }

        return candidates;
    }

    @Override
    protected String getDefaultKey() {
        return getCurrentSystemNavigationMode(getContext());
    }

    @Override
    protected boolean setDefaultKey(String key) {
        setCurrentSystemNavigationMode(mOverlayManager, key, false);
        setIllustrationVideo(mVideoPreference, key);
        if (TextUtils.equals(KEY_SYSTEM_NAV_GESTURAL, key) && (
                isAnyServiceSupportAccessibilityButton() || isNavBarMagnificationEnabled())) {
            Intent intent = new Intent(getActivity(), SettingsTutorialDialogWrapperActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        return true;
    }

    static void migrateOverlaySensitivityToSettings(Context context,
            IOverlayManager overlayManager) {
        if (!SystemNavigationPreferenceController.isGestureNavigationEnabled(context)) {
            return;
        }

        OverlayInfo info = null;

        boolean hidden = Settings.Secure.getFloat(context.getContentResolver(),
                Settings.Secure.GESTURE_NAVBAR_LENGTH, 1.0f) == 0.0f;

        try {
            info = overlayManager.getOverlayInfo(hidden ? HIDDEN_OVERLAY_PKG :
                                                  NAV_BAR_MODE_GESTURAL_OVERLAY, USER_CURRENT);
        } catch (RemoteException e) { /* Do nothing */ }
        if (info != null && !info.isEnabled()) {
            // Enable the default gesture nav overlay. Back sensitivity for left and right are
            // stored as separate settings values, and other gesture nav overlays are deprecated.
            setCurrentSystemNavigationMode(overlayManager, KEY_SYSTEM_NAV_GESTURAL, hidden);
            Settings.Secure.putFloat(context.getContentResolver(),
                    Settings.Secure.BACK_GESTURE_INSET_SCALE_LEFT, 1.0f);
            Settings.Secure.putFloat(context.getContentResolver(),
                    Settings.Secure.BACK_GESTURE_INSET_SCALE_RIGHT, 1.0f);
        }
    }

    @VisibleForTesting
    static String getCurrentSystemNavigationMode(Context context) {
        if (SystemNavigationPreferenceController.isGestureNavigationEnabled(context)) {
            return KEY_SYSTEM_NAV_GESTURAL;
        } else if (SystemNavigationPreferenceController.is2ButtonNavigationEnabled(context)) {
            return KEY_SYSTEM_NAV_2BUTTONS;
        } else {
            return KEY_SYSTEM_NAV_3BUTTONS;
        }
    }

    @VisibleForTesting
    static void setCurrentSystemNavigationMode(IOverlayManager overlayManager, String key, boolean hidden) {
        String overlayPackage = NAV_BAR_MODE_GESTURAL_OVERLAY;
        switch (key) {
            case KEY_SYSTEM_NAV_GESTURAL:
                overlayPackage = hidden ? HIDDEN_OVERLAY_PKG : NAV_BAR_MODE_GESTURAL_OVERLAY;
                break;
            case KEY_SYSTEM_NAV_2BUTTONS:
                overlayPackage = NAV_BAR_MODE_2BUTTON_OVERLAY;
                break;
            case KEY_SYSTEM_NAV_3BUTTONS:
                overlayPackage = NAV_BAR_MODE_3BUTTON_OVERLAY;
                break;
        }

        try {
            overlayManager.setEnabledExclusiveInCategory(overlayPackage, USER_CURRENT);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private static void setIllustrationVideo(VideoPreference videoPref, String systemNavKey) {
        videoPref.setVideo(0, 0);
        switch (systemNavKey) {
            case KEY_SYSTEM_NAV_GESTURAL:
                videoPref.setVideo(R.raw.system_nav_fully_gestural,
                        R.drawable.system_nav_fully_gestural);
                break;
            case KEY_SYSTEM_NAV_2BUTTONS:
                videoPref.setVideo(R.raw.system_nav_2_button, R.drawable.system_nav_2_button);
                break;
            case KEY_SYSTEM_NAV_3BUTTONS:
                videoPref.setVideo(R.raw.system_nav_3_button, R.drawable.system_nav_3_button);
                break;
        }
    }

    private boolean isAnyServiceSupportAccessibilityButton() {
        final AccessibilityManager ams = getContext().getSystemService(AccessibilityManager.class);
        final List<String> targets = ams.getAccessibilityShortcutTargets(
                AccessibilityManager.ACCESSIBILITY_BUTTON);
        return !targets.isEmpty();
    }

    private boolean isNavBarMagnificationEnabled() {
        return Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_DISPLAY_MAGNIFICATION_NAVBAR_ENABLED, 0) == 1;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.system_navigation_gesture_settings) {

                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return SystemNavigationPreferenceController.isGestureAvailable(context);
                }
            };

    // From HelpResourceProvider
    @Override
    public int getHelpResource() {
        // TODO(b/146001201): Replace with system navigation help page when ready.
        return R.string.help_uri_default;
    }
}
