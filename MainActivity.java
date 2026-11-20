package com.sanku.mobilecinema;

import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.widget.FrameLayout;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebChromeClient;

public class MainActivity extends BridgeActivity {

    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private int originalSystemUiVisibility;
    private boolean wasFullscreen;

    @Override
    protected void load() {
        super.load();

        getBridge().getWebView().setWebChromeClient(
            new FullscreenChromeClient(getBridge())
        );
    }

    private class FullscreenChromeClient extends BridgeWebChromeClient {

        FullscreenChromeClient(com.getcapacitor.Bridge bridge) {
            super(bridge);
        }

        @Override
        public void onShowCustomView(
            View view,
            WebChromeClient.CustomViewCallback callback
        ) {
            if (fullscreenView != null) {
                callback.onCustomViewHidden();
                return;
            }

            fullscreenView = view;
            fullscreenCallback = callback;

            View decor = getWindow().getDecorView();
            originalSystemUiVisibility = decor.getSystemUiVisibility();
            wasFullscreen = (getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;

            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            );

            decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );

            ((ViewGroup) decor).addView(
                view,
                new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            );
        }

        @Override
        public void onHideCustomView() {
            if (fullscreenView == null) return;

            ((ViewGroup) getWindow().getDecorView())
                .removeView(fullscreenView);

            fullscreenView = null;
            fullscreenCallback = null;

            getWindow().getDecorView().setSystemUiVisibility(
                originalSystemUiVisibility
            );

            if (!wasFullscreen) {
                getWindow().clearFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
                );
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (fullscreenView != null && fullscreenCallback != null) {
            fullscreenCallback.onCustomViewHidden();
            return;
        }

        super.onBackPressed();
    }
}
