/*
 * Copyright (C) 2013 Slimroms
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
import android.os.Handler;
import com.android.systemui.Dependency;
import android.os.PowerManager;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;

public class RebootTile extends QSTileImpl<BooleanState> {

    private boolean mRebootToRecovery = false;

    private boolean mListening;
    private final KeyguardMonitor mKeyguard;
    private final KeyguardCallback mKeyguardCallback = new KeyguardCallback();
    
    public RebootTile(QSHost host) {
        super(host);
        mKeyguard = Dependency.get(KeyguardMonitor.class);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleClick() {
        mRebootToRecovery = !mRebootToRecovery;
        refreshState();
    }

    private void handleLongClickInner() {
        mHost.collapsePanels();
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                PowerManager pm =
                    (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                if (mRebootToRecovery)
                    pm.rebootCustom("recovery");
                else
                    pm.reboot("");
            }
        }, 500);
    }

    @Override
    protected void handleLongClick() {
        if (mKeyguard.isSecure() && mKeyguard.isShowing()) {
            Dependency.get(ActivityStarter.class).postQSRunnableDismissingKeyguard(() -> {
                mHost.openPanels();
                handleLongClickInner();
            });
            return;
        }
        handleLongClickInner();
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.global_action_reboot);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.RESURRECTED;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (mRebootToRecovery) {
            state.label = mContext.getString(R.string.global_action_reboot_recovery);
            state.icon = ResourceIcon.get(R.drawable.ic_qs_reboot_recovery);
        } else {
            state.label = mContext.getString(R.string.global_action_reboot);
            state.icon = ResourceIcon.get(R.drawable.ic_qs_reboot);
        }
    }

    @Override
    public void handleSetListening(boolean listening) {
         if (mListening == listening) return;
         mListening = listening;
         if (listening) {
            mKeyguard.addCallback(mKeyguardCallback);
        } else {
            mKeyguard.removeCallback(mKeyguardCallback);
        }
    }
   
    private final class KeyguardCallback implements KeyguardMonitor.Callback {
        @Override
        public void onKeyguardShowingChanged() {
            refreshState();
        }
    };
}
