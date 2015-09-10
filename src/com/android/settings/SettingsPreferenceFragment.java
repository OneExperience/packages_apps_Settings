/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceGroupAdapter;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.android.settings.applications.LayoutPreference;
import com.android.settings.widget.FloatingActionButton;

import java.util.UUID;

/**
 * Base class for Settings fragments, with some helper functions and dialog management.
 */
public abstract class SettingsPreferenceFragment extends InstrumentedPreferenceFragment
        implements DialogCreatable {

    private static final String TAG = "SettingsPreferenceFragment";

    private static final int DELAY_HIGHLIGHT_DURATION_MILLIS = 600;

    private static final String SAVE_HIGHLIGHTED_KEY = "android:preference_highlighted";

    private SettingsDialogFragment mDialogFragment;

    private String mHelpUri;

    // Cache the content resolver for async callbacks
    private ContentResolver mContentResolver;

    private String mPreferenceKey;
    private boolean mPreferenceHighlighted = false;
    private Drawable mHighlightDrawable;

    private RecyclerView.Adapter mCurrentRootAdapter;
    private boolean mIsDataSetObserverRegistered = false;
    private RecyclerView.AdapterDataObserver mDataSetObserver =
            new RecyclerView.AdapterDataObserver() {
        @Override
        public void onChanged() {
            onDataSetChanged();
        }
    };

    private ViewGroup mPinnedHeaderFrameLayout;
    private FloatingActionButton mFloatingActionButton;
    private ViewGroup mButtonBar;

    private LayoutPreference mHeader;

    private LayoutPreference mFooter;
    private View mEmptyView;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (icicle != null) {
            mPreferenceHighlighted = icicle.getBoolean(SAVE_HIGHLIGHTED_KEY);
        }

        // Prepare help url and enable menu if necessary
        int helpResource = getHelpResource();
        if (helpResource != 0) {
            mHelpUri = getResources().getString(helpResource);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View root = super.onCreateView(inflater, container, savedInstanceState);
        mPinnedHeaderFrameLayout = (ViewGroup) root.findViewById(R.id.pinned_header);
        mFloatingActionButton = (FloatingActionButton) root.findViewById(R.id.fab);
        mButtonBar = (ViewGroup) root.findViewById(R.id.button_bar);
        return root;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    }

    public FloatingActionButton getFloatingActionButton() {
        return mFloatingActionButton;
    }

    public ViewGroup getButtonBar() {
        return mButtonBar;
    }

    public View setPinnedHeaderView(int layoutResId) {
        final LayoutInflater inflater = getActivity().getLayoutInflater();
        final View pinnedHeader =
                inflater.inflate(layoutResId, mPinnedHeaderFrameLayout, false);
        setPinnedHeaderView(pinnedHeader);
        return pinnedHeader;
    }

    public void setPinnedHeaderView(View pinnedHeader) {
        mPinnedHeaderFrameLayout.addView(pinnedHeader);
        mPinnedHeaderFrameLayout.setVisibility(View.VISIBLE);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(SAVE_HIGHLIGHTED_KEY, mPreferenceHighlighted);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (!TextUtils.isEmpty(mHelpUri)) {
            setHasOptionsMenu(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        final Bundle args = getArguments();
        if (args != null) {
            mPreferenceKey = args.getString(SettingsActivity.EXTRA_FRAGMENT_ARG_KEY);
            highlightPreferenceIfNeeded();
        }
    }

    @Override
    protected void onBindPreferences() {
        registerObserverIfNeeded();
    }

    @Override
    protected void onUnbindPreferences() {
        unregisterObserverIfNeeded();
    }

    @Override
    public void onStop() {
        super.onStop();

        unregisterObserverIfNeeded();
    }

    public void showLoadingWhenEmpty() {
        View loading = getView().findViewById(R.id.loading_container);
        setEmptyView(loading);
    }

    public void registerObserverIfNeeded() {
        if (!mIsDataSetObserverRegistered) {
            if (mCurrentRootAdapter != null) {
                mCurrentRootAdapter.unregisterAdapterDataObserver(mDataSetObserver);
            }
            mCurrentRootAdapter = getListView().getAdapter();
            mCurrentRootAdapter.registerAdapterDataObserver(mDataSetObserver);
            mIsDataSetObserverRegistered = true;
        }
    }

    public void unregisterObserverIfNeeded() {
        if (mIsDataSetObserverRegistered) {
            if (mCurrentRootAdapter != null) {
                mCurrentRootAdapter.unregisterAdapterDataObserver(mDataSetObserver);
                mCurrentRootAdapter = null;
            }
            mIsDataSetObserverRegistered = false;
        }
    }

    public void highlightPreferenceIfNeeded() {
        if (isAdded() && !mPreferenceHighlighted &&!TextUtils.isEmpty(mPreferenceKey)) {
            highlightPreference(mPreferenceKey);
        }
    }

    private void onDataSetChanged() {
        highlightPreferenceIfNeeded();
        updateEmptyView();
    }

    private Drawable getHighlightDrawable() {
        if (mHighlightDrawable == null) {
            mHighlightDrawable = getActivity().getDrawable(R.drawable.preference_highlight);
        }
        return mHighlightDrawable;
    }

    public LayoutPreference getHeaderView() {
        return mHeader;
    }

    public LayoutPreference getFooterView() {
        return mFooter;
    }

    protected void setHeaderView(int resource) {
        mHeader = new LayoutPreference(getPrefContext(), resource);
        mHeader.setOrder(-1);
        if (getPreferenceScreen() != null) {
            getPreferenceScreen().addPreference(mHeader);
        }
    }

    protected void setFooterView(int resource) {
        setFooterView(resource != 0 ? new LayoutPreference(getPrefContext(), resource) : null);
    }

    protected void setFooterView(View v) {
        setFooterView(v != null ? new LayoutPreference(getPrefContext(), v) : null);
    }

    private void setFooterView(LayoutPreference footer) {
        if (getPreferenceScreen() != null && mFooter != null) {
            getPreferenceScreen().removePreference(mFooter);
        }
        if (footer != null) {
            mFooter = footer;
            mFooter.setOrder(Integer.MAX_VALUE);
            if (getPreferenceScreen() != null) {
                getPreferenceScreen().addPreference(mFooter);
            }
        } else {
            mFooter = null;
        }
    }

    @Override
    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
        super.setPreferenceScreen(preferenceScreen);
        if (preferenceScreen != null) {
            if (mHeader != null) {
                preferenceScreen.addPreference(mHeader);
            }
            if (mFooter != null) {
                preferenceScreen.addPreference(mFooter);
            }
        }
    }

    private void updateEmptyView() {
        if (mEmptyView == null) return;
        if (getPreferenceScreen() != null) {
            boolean show = (getPreferenceScreen().getPreferenceCount()
                    - (mHeader != null ? 1 : 0)
                    - (mFooter != null ? 1 : 0)) <= 0;
            mEmptyView.setVisibility(show ? View.VISIBLE : View.GONE);
        } else {
            mEmptyView.setVisibility(View.VISIBLE);
        }
    }

    public void setEmptyView(View v) {
        mEmptyView = v;
        updateEmptyView();
    }

    public View getEmptyView() {
        return mEmptyView;
    }

    /**
     * Return a valid ListView position or -1 if none is found
     */
    private int canUseListViewForHighLighting(String key) {
        if (getListView() == null) {
            return -1;
        }

        RecyclerView listView = getListView();
        RecyclerView.Adapter adapter = listView.getAdapter();

        if (adapter != null && adapter instanceof PreferenceGroupAdapter) {
            return findListPositionFromKey((PreferenceGroupAdapter) adapter, key);
        }

        return -1;
    }

    private void highlightPreference(String key) {
        final Drawable highlight = getHighlightDrawable();

        final int position = canUseListViewForHighLighting(key);
        if (position >= 0) {
            mPreferenceHighlighted = true;

            // TODO: Need to find a way to scroll to and highlight search items now
            // that we are using RecyclerView instead.
//            final RecyclerView listView = getListView();
//            final RecyclerView.Adapter adapter = listView.getAdapter();
//
////            ((PreferenceGroupAdapter) adapter).setHighlightedDrawable(highlight);
////            ((PreferenceGroupAdapter) adapter).setHighlighted(position);
//
//            listView.post(new Runnable() {
//                @Override
//                public void run() {
//                    listView.setSelection(position);
//                    listView.postDelayed(new Runnable() {
//                        @Override
//                        public void run() {
//                            final int index = position - listView.getFirstVisiblePosition();
//                            if (index >= 0 && index < listView.getChildCount()) {
//                                final View v = listView.getChildAt(index);
//                                final int centerX = v.getWidth() / 2;
//                                final int centerY = v.getHeight() / 2;
//                                highlight.setHotspot(centerX, centerY);
//                                v.setPressed(true);
//                                v.setPressed(false);
//                            }
//                        }
//                    }, DELAY_HIGHLIGHT_DURATION_MILLIS);
//                }
//            });
        }
    }

    private int findListPositionFromKey(PreferenceGroupAdapter adapter, String key) {
        final int count = adapter.getItemCount();
        for (int n = 0; n < count; n++) {
            final Preference preference = adapter.getItem(n);
            final String preferenceKey = preference.getKey();
            if (preferenceKey != null && preferenceKey.equals(key)) {
                return n;
            }
        }
        return -1;
    }

    protected void removePreference(String key) {
        Preference pref = findPreference(key);
        if (pref != null) {
            getPreferenceScreen().removePreference(pref);
        }
    }

    /**
     * Override this if you want to show a help item in the menu, by returning the resource id.
     * @return the resource id for the help url
     */
    protected int getHelpResource() {
        return R.string.help_uri_default;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (mHelpUri != null && getActivity() != null) {
            HelpUtils.prepareHelpMenuItem(getActivity(), menu, mHelpUri, getClass().getName());
        }
    }

    /*
     * The name is intentionally made different from Activity#finish(), so that
     * users won't misunderstand its meaning.
     */
    public final void finishFragment() {
        getActivity().onBackPressed();
    }

    // Some helpers for functions used by the settings fragments when they were activities

    /**
     * Returns the ContentResolver from the owning Activity.
     */
    protected ContentResolver getContentResolver() {
        Context context = getActivity();
        if (context != null) {
            mContentResolver = context.getContentResolver();
        }
        return mContentResolver;
    }

    /**
     * Returns the specified system service from the owning Activity.
     */
    protected Object getSystemService(final String name) {
        return getActivity().getSystemService(name);
    }

    /**
     * Returns the PackageManager from the owning Activity.
     */
    protected PackageManager getPackageManager() {
        return getActivity().getPackageManager();
    }

    @Override
    public void onDetach() {
        if (isRemoving()) {
            if (mDialogFragment != null) {
                mDialogFragment.dismiss();
                mDialogFragment = null;
            }
        }
        super.onDetach();
    }

    // Dialog management

    protected void showDialog(int dialogId) {
        if (mDialogFragment != null) {
            Log.e(TAG, "Old dialog fragment not null!");
        }
        mDialogFragment = new SettingsDialogFragment(this, dialogId);
        mDialogFragment.show(getChildFragmentManager(), Integer.toString(dialogId));
    }

    public Dialog onCreateDialog(int dialogId) {
        return null;
    }

    protected void removeDialog(int dialogId) {
        // mDialogFragment may not be visible yet in parent fragment's onResume().
        // To be able to dismiss dialog at that time, don't check
        // mDialogFragment.isVisible().
        if (mDialogFragment != null && mDialogFragment.getDialogId() == dialogId) {
            mDialogFragment.dismiss();
        }
        mDialogFragment = null;
    }

    /**
     * Sets the OnCancelListener of the dialog shown. This method can only be
     * called after showDialog(int) and before removeDialog(int). The method
     * does nothing otherwise.
     */
    protected void setOnCancelListener(DialogInterface.OnCancelListener listener) {
        if (mDialogFragment != null) {
            mDialogFragment.mOnCancelListener = listener;
        }
    }

    /**
     * Sets the OnDismissListener of the dialog shown. This method can only be
     * called after showDialog(int) and before removeDialog(int). The method
     * does nothing otherwise.
     */
    protected void setOnDismissListener(DialogInterface.OnDismissListener listener) {
        if (mDialogFragment != null) {
            mDialogFragment.mOnDismissListener = listener;
        }
    }

    public void onDialogShowing() {
        // override in subclass to attach a dismiss listener, for instance
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (preference.getKey() == null) {
            // Auto-key preferences that don't have a key, so the dialog can find them.
            preference.setKey(UUID.randomUUID().toString());
        }
        DialogFragment f = null;
        if (preference instanceof CustomListPreference) {
            f = CustomListPreference.CustomListPreferenceDialogFragment
                    .newInstance(preference.getKey());
        } else if (preference instanceof CustomDialogPreference) {
            f = CustomDialogPreference.CustomPreferenceDialogFragment
                    .newInstance(preference.getKey());
        } else if (preference instanceof CustomEditTextPreference) {
            f = CustomEditTextPreference.CustomPreferenceDialogFragment
                    .newInstance(preference.getKey());
        } else {
            super.onDisplayPreferenceDialog(preference);
            return;
        }
        f.setTargetFragment(this, 0);
        f.show(getFragmentManager(), "dialog_preference");
        onDialogShowing();
    }

    public static class SettingsDialogFragment extends DialogFragment {
        private static final String KEY_DIALOG_ID = "key_dialog_id";
        private static final String KEY_PARENT_FRAGMENT_ID = "key_parent_fragment_id";

        private int mDialogId;

        private Fragment mParentFragment;

        private DialogInterface.OnCancelListener mOnCancelListener;
        private DialogInterface.OnDismissListener mOnDismissListener;

        public SettingsDialogFragment() {
            /* do nothing */
        }

        public SettingsDialogFragment(DialogCreatable fragment, int dialogId) {
            mDialogId = dialogId;
            if (!(fragment instanceof Fragment)) {
                throw new IllegalArgumentException("fragment argument must be an instance of "
                        + Fragment.class.getName());
            }
            mParentFragment = (Fragment) fragment;
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            if (mParentFragment != null) {
                outState.putInt(KEY_DIALOG_ID, mDialogId);
                outState.putInt(KEY_PARENT_FRAGMENT_ID, mParentFragment.getId());
            }
        }

        @Override
        public void onStart() {
            super.onStart();

            if (mParentFragment != null && mParentFragment instanceof SettingsPreferenceFragment) {
                ((SettingsPreferenceFragment) mParentFragment).onDialogShowing();
            }
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            if (savedInstanceState != null) {
                mDialogId = savedInstanceState.getInt(KEY_DIALOG_ID, 0);
                mParentFragment = getParentFragment();
                int mParentFragmentId = savedInstanceState.getInt(KEY_PARENT_FRAGMENT_ID, -1);
                if (mParentFragment == null) {
                    mParentFragment = getFragmentManager().findFragmentById(mParentFragmentId);
                }
                if (!(mParentFragment instanceof DialogCreatable)) {
                    throw new IllegalArgumentException(
                            (mParentFragment != null
                                    ? mParentFragment.getClass().getName()
                                    : mParentFragmentId)
                                    + " must implement "
                                    + DialogCreatable.class.getName());
                }
                // This dialog fragment could be created from non-SettingsPreferenceFragment
                if (mParentFragment instanceof SettingsPreferenceFragment) {
                    // restore mDialogFragment in mParentFragment
                    ((SettingsPreferenceFragment) mParentFragment).mDialogFragment = this;
                }
            }
            return ((DialogCreatable) mParentFragment).onCreateDialog(mDialogId);
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            super.onCancel(dialog);
            if (mOnCancelListener != null) {
                mOnCancelListener.onCancel(dialog);
            }
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            if (mOnDismissListener != null) {
                mOnDismissListener.onDismiss(dialog);
            }
        }

        public int getDialogId() {
            return mDialogId;
        }

        @Override
        public void onDetach() {
            super.onDetach();

            // This dialog fragment could be created from non-SettingsPreferenceFragment
            if (mParentFragment instanceof SettingsPreferenceFragment) {
                // in case the dialog is not explicitly removed by removeDialog()
                if (((SettingsPreferenceFragment) mParentFragment).mDialogFragment == this) {
                    ((SettingsPreferenceFragment) mParentFragment).mDialogFragment = null;
                }
            }
        }
    }

    protected boolean hasNextButton() {
        return ((ButtonBarHandler)getActivity()).hasNextButton();
    }

    protected Button getNextButton() {
        return ((ButtonBarHandler)getActivity()).getNextButton();
    }

    public void finish() {
        Activity activity = getActivity();
        if (activity != null) {
            activity.onBackPressed();
        }
    }

    protected final Context getPrefContext() {
        return getPreferenceManager().getContext();
    }

    public boolean startFragment(Fragment caller, String fragmentClass, int titleRes,
            int requestCode, Bundle extras) {
        final Activity activity = getActivity();
        if (activity instanceof SettingsActivity) {
            SettingsActivity sa = (SettingsActivity) activity;
            sa.startPreferencePanel(fragmentClass, extras, titleRes, null, caller, requestCode);
            return true;
        } else if (activity instanceof PreferenceActivity) {
            PreferenceActivity sa = (PreferenceActivity) activity;
            sa.startPreferencePanel(fragmentClass, extras, titleRes, null, caller, requestCode);
            return true;
        } else {
            Log.w(TAG,
                    "Parent isn't SettingsActivity nor PreferenceActivity, thus there's no way to "
                    + "launch the given Fragment (name: " + fragmentClass
                    + ", requestCode: " + requestCode + ")");
            return false;
        }
    }
}
