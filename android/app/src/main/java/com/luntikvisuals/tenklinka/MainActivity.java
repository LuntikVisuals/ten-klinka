package com.luntikvisuals.tenklinka;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_PICK_IMAGE = 4201;
    private WebView webView;
    // pending <input type=file> callback from the WebView (avatar picker)
    private ValueCallback<Uri[]> fileCallback;

    // Makes Fullscreen API / orientation lock calls from the game resolve harmlessly:
    // the Activity itself is already fullscreen + landscape.
    private static final String SHIM =
        "(function(){try{var d=document,e=d.documentElement;" +
        "var ok=function(){return Promise.resolve();};" +
        "if(e){e.requestFullscreen=ok;e.webkitRequestFullscreen=ok;}" +
        "d.exitFullscreen=ok;d.webkitExitFullscreen=ok;" +
        "if(window.screen&&screen.orientation){try{screen.orientation.lock=ok;screen.orientation.unlock=function(){};}catch(x){}}" +
        "}catch(err){}})();";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        if (Build.VERSION.SDK_INT >= 28) {
            w.getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setHapticFeedbackEnabled(false);
        webView.setOnLongClickListener(v -> true);
        webView.setLongClickable(false);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                // a previous chooser that never returned must be released, or the input stays dead
                if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
                fileCallback = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("image/*");
                Intent chooser = Intent.createChooser(pick, "Выбери фото");
                try {
                    startActivityForResult(chooser, REQ_PICK_IMAGE);
                } catch (ActivityNotFoundException e) {
                    try {
                        Intent alt = new Intent(Intent.ACTION_PICK);
                        alt.setType("image/*");
                        startActivityForResult(alt, REQ_PICK_IMAGE);
                    } catch (ActivityNotFoundException e2) {
                        fileCallback = null;
                        callback.onReceiveValue(null);
                        Toast.makeText(MainActivity.this, "Нет приложения для выбора фото", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                view.evaluateJavascript(SHIM, null);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(SHIM, null);
            }
        });

        setContentView(webView);
        hideSystemUI();
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl("file:///android_asset/index.html");
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) { webView.onPause(); webView.pauseTimers(); }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) { webView.onResume(); webView.resumeTimers(); }
        hideSystemUI();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_PICK_IMAGE) { super.onActivityResult(requestCode, resultCode, data); return; }
        ValueCallback<Uri[]> cb = fileCallback;
        fileCallback = null;
        if (cb == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getData() != null) {
                result = new Uri[]{ data.getData() };
            } else if (data.getClipData() != null && data.getClipData().getItemCount() > 0) {
                result = new Uri[]{ data.getClipData().getItemAt(0).getUri() };
            }
        }
        cb.onReceiveValue(result); // null on cancel — required so the next pick works
    }

    @Override
    public void onBackPressed() {
        if (webView == null) { super.onBackPressed(); return; }
        // let the game navigate its own screens; exit only from the main menu
        webView.evaluateJavascript(
            "(function(){try{return !!(window.__tkBack&&window.__tkBack());}catch(e){return false;}})()",
            value -> {
                if (!"true".equals(value)) {
                    if (webView != null && webView.canGoBack()) webView.goBack();
                    else finish();
                }
            });
    }

    @Override
    protected void onDestroy() {
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
