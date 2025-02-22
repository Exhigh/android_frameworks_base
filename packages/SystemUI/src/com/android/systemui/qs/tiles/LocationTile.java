/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2016 The ParanoidAndroid Project
 * Copyright (C) 2017 The LineageOS Project
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

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.os.UserManager;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.R.drawable;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.qs.QSHost;
import com.android.systemui.plugins.qs.DetailAdapter;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.LocationController.LocationChangeCallback;
import com.android.systemui.volume.SegmentedButtons;

import com.android.internal.logging.nano.MetricsProto;

/** Quick settings tile: Location **/
public class LocationTile extends QSTileImpl<BooleanState> {
	
	private static final int OFF = Settings.Secure.LOCATION_MODE_OFF;
	private static final int HIGH_ACCURACY = Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
    private static final int BATTERY_SAVING = Settings.Secure.LOCATION_MODE_BATTERY_SAVING;
	private static final int SENSORS_ONLY = Settings.Secure.LOCATION_MODE_SENSORS_ONLY;
	
    private final Icon mIcon = ResourceIcon.get(drawable.ic_signal_location);

    private static final Intent LOCATION_SETTINGS_INTENT =
            new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);

    private final LocationController mController;
    private final LocationDetailAdapter mDetailAdapter;
    private final KeyguardMonitor mKeyguard;
    private final Callback mCallback = new Callback();
    private int mLastState;

    public LocationTile(QSHost host) {
        super(host);
        mController = Dependency.get(LocationController.class);
        mKeyguard = Dependency.get(KeyguardMonitor.class);
        mDetailAdapter = (LocationDetailAdapter) createDetailAdapter();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public boolean isDualTarget() {
        return true;
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return mDetailAdapter;
    }

    @Override
    protected DetailAdapter createDetailAdapter() {
        return new LocationDetailAdapter();
    }


    @Override
    public void handleSetListening(boolean listening) {
        if (mController == null) return;
        if (listening) {
            mController.addCallback(mCallback);
            mKeyguard.addCallback(mCallback);
        } else {
            mController.removeCallback(mCallback);
            mKeyguard.removeCallback(mCallback);
        }
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
    }

    @Override
    protected void handleClick() { 
        switchMode();
    }

    @Override
    protected void handleSecondaryClick() {
        if (mKeyguard.isSecure() && mKeyguard.isShowing()) {
            Dependency.get(ActivityStarter.class).postQSRunnableDismissingKeyguard(() -> {
                mHost.openPanels();
                switchMode();
            });
            return;
        }
        switchMode();
        showDetail(true);
    }
	
	private void switchMode() {
        int currentMode = mController.getLocationCurrentState();
		
        if (currentMode == BATTERY_SAVING) {
            //from battery saving to sensor only
	    mController.setLocationMode(SENSORS_ONLY);
        } else if (currentMode == SENSORS_ONLY) {
            //from sensor only to off
            mController.setLocationMode(OFF);
        } else if (currentMode == HIGH_ACCURACY) {
            //from high precision to battery saving
            mController.setLocationMode(BATTERY_SAVING);
        } else if (currentMode == OFF) {
            //from off to high precision
	    mController.setLocationMode(HIGH_ACCURACY);
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_location_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
         if (state.slash == null) {
            state.slash = new SlashState();
        }
         if (mController == null) {
            return;
        }
		
        // Work around for bug 15916487: don't show location tile on top of lock screen. After the
        // bug is fixed, this should be reverted to only hiding it on secure lock screens:
        // state.visible = !(mKeyguard.isSecure() && mKeyguard.isShowing());
        
        
        state.dualTarget = true;
        checkIfRestrictionEnforcedByAdminOnly(state, UserManager.DISALLOW_SHARE_LOCATION);
        if (state.disabledByPolicy == false) {
            checkIfRestrictionEnforcedByAdminOnly(state, UserManager.DISALLOW_CONFIG_LOCATION);
        }
		final boolean locationEnabled =  mController.isLocationEnabled();
		int currentState = arg instanceof Integer ? (Integer) arg :
                mController.getLocationCurrentState();
                final boolean newValue = currentState != OFF; 
                state.value = newValue;       
                final boolean valueChanged = state.value != newValue;
		int currentMode = mController.getLocationCurrentState();
                state.label = mContext.getString(getStateLabelRes(currentState));
        
        switch (currentMode) {
            case OFF:
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_location_off);
                state.icon = mIcon;
                break;
            case HIGH_ACCURACY:
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_location_high_accuracy);
               state.icon = ResourceIcon.get(R.drawable.ic_qs_location_high_accuracy);
                break;
            case BATTERY_SAVING:
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_location_battery_saving);
                state.icon = ResourceIcon.get(R.drawable.ic_qs_location_battery_saving);
                break;
            case SENSORS_ONLY:
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_location_gps_only);
                state.icon = ResourceIcon.get(R.drawable.ic_qs_location_sensors_only);
                break;
            default:
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_location_on);
                state.icon = mIcon;
        }
        if (valueChanged) {
            fireToggleStateChanged(state.value);
        }
        state.state = state.value ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
        state.expandedAccessibilityClassName = Switch.class.getName();
    }

    private int getStateLabelRes(int currentState) {
        switch (currentState) {
            case OFF:
                return R.string.quick_settings_location_off_label;
            case HIGH_ACCURACY:
                return R.string.quick_settings_location_high_accuracy_label;
            case BATTERY_SAVING:
                return R.string.quick_settings_location_battery_saving_label;
            case SENSORS_ONLY:
                return R.string.quick_settings_location_gps_only_label;
            default:
                return R.string.quick_settings_location_label;
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_LOCATION;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.accessibility_quick_settings_location_changed_on);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_location_changed_off);
        }
    }

    private final class Callback implements LocationChangeCallback,
            KeyguardMonitor.Callback {
        @Override
        public void onLocationSettingsChanged(boolean enabled) {
            refreshState();
        }

        @Override
        public void onKeyguardShowingChanged() {
            refreshState();
        }
    };

    private class LocationDetailAdapter implements DetailAdapter {

        private SegmentedButtons mButtons;
        private ViewGroup mMessageContainer;
        private TextView mMessageText;

        @Override
        public int getMetricsCategory() {
            return MetricsEvent.TILE_LOCATION_DETAIL;
        }

        @Override
        public CharSequence getTitle() {
            return mContext.getString(R.string.quick_settings_location_detail_title);
        }

        @Override
        public Boolean getToggleState() {
            return mState.value;
        }

        @Override
        public Intent getSettingsIntent() {
            return LOCATION_SETTINGS_INTENT;
        }

        @Override
        public void setToggleState(boolean state) {
            MetricsLogger.action(mContext, MetricsEvent.QS_LOCATION, state);
            if (!state) {
                mController.setLocationEnabled(state);
                showDetail(false);
            }
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            final LinearLayout details = convertView != null ? (LinearLayout) convertView
                    : (LinearLayout) LayoutInflater.from(context).inflate(
                            R.layout.location_mode_panel, parent, false);

            mLastState = mController.getLocationCurrentState();

            if (convertView == null) {
                mButtons = (SegmentedButtons) details.findViewById(R.id.location_buttons);
                mButtons.addButton(R.string.quick_settings_location_high_accuracy_label_twoline,
                        R.string.quick_settings_location_high_accuracy_label,
                        Settings.Secure.LOCATION_MODE_HIGH_ACCURACY);
                mButtons.addButton(R.string.quick_settings_location_battery_saving_label_twoline,
                        R.string.quick_settings_location_battery_saving_label,
                        Settings.Secure.LOCATION_MODE_BATTERY_SAVING);
                mButtons.addButton(R.string.quick_settings_location_gps_only_label_twoline,
                        R.string.quick_settings_location_gps_only_label,
                        Settings.Secure.LOCATION_MODE_SENSORS_ONLY);
                mButtons.setCallback(mButtonsCallback);
                mMessageContainer = (ViewGroup) details.findViewById(R.id.location_introduction);
                mMessageText = (TextView) details.findViewById(R.id.location_introduction_message);
            }

            mButtons.setSelectedValue(mLastState, false /* fromClick */);
            refresh(mLastState);

            return details;
        }

        private void refresh(int state) {
            switch (state) {
                case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
                    mMessageText.setText(mContext.getString(
                            R.string.quick_settings_location_detail_mode_high_accuracy_description));
                    mMessageContainer.setVisibility(View.VISIBLE);
                    break;
                case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
                    mMessageText.setText(mContext.getString(
                            R.string.quick_settings_location_detail_mode_battery_saving_description));
                    mMessageContainer.setVisibility(View.VISIBLE);
                    break;
                case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
                    mMessageText.setText(mContext.getString(
                            R.string.quick_settings_location_detail_mode_sensors_only_description));
                    mMessageContainer.setVisibility(View.VISIBLE);
                    break;
                default:
                    mMessageContainer.setVisibility(View.GONE);
                    break;
            }
        }

        private final SegmentedButtons.Callback mButtonsCallback = new SegmentedButtons.Callback() {
            @Override
            public void onSelected(final Object value, boolean fromClick) {
                if (value != null && mButtons.isShown()) {
                    mLastState = (Integer) value;
                    if (fromClick) {
                        MetricsLogger.action(mContext, MetricsEvent.QS_LOCATION, mLastState);
                        mController.setLocationMode(mLastState);
                        refresh(mLastState);
                    }
                }
            }

            @Override
            public void onInteraction() {
            }
        };
    }
}
