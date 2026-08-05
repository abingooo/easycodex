package com.abingooo.easycodex;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final String PREFS = "easycodex_connection";
    private static final String PREF_URL = "bridge_url";
    private static final int FILE_CHOOSER_REQUEST = 42;

    private final int blue = Color.rgb(11, 107, 255);
    private final int text = Color.rgb(20, 21, 24);
    private final int muted = Color.rgb(102, 107, 117);
    private final int surface = Color.rgb(247, 248, 250);

    private FrameLayout root;
    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout errorBar;
    private ValueCallback<Uri[]> fileCallback;
    private String currentUrl;
    private volatile boolean networkWasLost;
    private ConnectivityManager connectivityManager;

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onLost(Network network) {
            networkWasLost = true;
        }

        @Override
        public void onAvailable(Network network) {
            if (!networkWasLost) return;
            networkWasLost = false;
            runOnUiThread(() -> {
                if (webView != null && currentUrl != null) webView.reload();
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(surface);
        getWindow().setNavigationBarColor(surface);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        root = new FrameLayout(this);
        root.setBackgroundColor(surface);
        setContentView(root);

        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (RuntimeException ignored) {
            // The WebSocket client still performs its own reconnect loop.
        }

        currentUrl = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_URL, null);
        if (currentUrl == null || currentUrl.trim().isEmpty()) {
            showSetup(null);
        } else {
            showBrowser(currentUrl);
        }
    }

    private void showSetup(String message) {
        destroyWebView();
        root.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(28), dp(44), dp(28), dp(32));
        scroll.addView(page, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        ImageView logo = new ImageView(this);
        logo.setImageResource(com.abingooo.easycodex.R.drawable.app_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        page.addView(logo, linearParams(dp(112), dp(112), 0, 0, 0, 24));

        TextView titleView = label(getString(R.string.connect_title), 28, text, true);
        titleView.setGravity(Gravity.CENTER);
        page.addView(titleView, linearParams(-1, -2, 0, 0, 0, 10));

        TextView subtitle = label(getString(R.string.connect_subtitle), 15, muted, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(0, 1.15f);
        page.addView(subtitle, linearParams(-1, -2, 0, 0, 0, 28));

        EditText urlInput = new EditText(this);
        urlInput.setText(currentUrl == null ? "" : currentUrl);
        urlInput.setHint(R.string.bridge_url_hint);
        urlInput.setTextColor(text);
        urlInput.setHintTextColor(Color.rgb(145, 150, 160));
        urlInput.setTextSize(16);
        urlInput.setSingleLine(true);
        urlInput.setSelectAllOnFocus(false);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlInput.setPadding(dp(16), 0, dp(16), 0);
        urlInput.setBackground(rounded(Color.WHITE, Color.rgb(215, 218, 224), 1, 12));
        page.addView(urlInput, linearParams(-1, dp(56), 0, 0, 0, 14));

        TextView error = label(message == null ? "" : message, 14, Color.rgb(190, 42, 51), false);
        error.setVisibility(message == null ? View.GONE : View.VISIBLE);
        page.addView(error, linearParams(-1, -2, 0, 0, 0, 12));

        Button connect = new Button(this);
        connect.setText(R.string.connect);
        connect.setTextColor(Color.WHITE);
        connect.setTextSize(16);
        connect.setAllCaps(false);
        connect.setGravity(Gravity.CENTER);
        connect.setBackground(rounded(blue, blue, 0, 12));
        page.addView(connect, linearParams(-1, dp(54), 0, 0, 0, 0));

        View.OnClickListener submit = view -> {
            String normalized = normalizeUrl(urlInput.getText().toString());
            if (normalized == null) {
                error.setText(R.string.invalid_url);
                error.setVisibility(View.VISIBLE);
                return;
            }
            currentUrl = normalized;
            getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(PREF_URL, normalized)
                .apply();
            showBrowser(normalized);
        };
        connect.setOnClickListener(submit);
        urlInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submit.onClick(view);
                return true;
            }
            return false;
        });

        root.addView(scroll, frameParams(-1, -1));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showBrowser(String url) {
        destroyWebView();
        root.removeAllViews();

        LinearLayout browser = new LinearLayout(this);
        browser.setOrientation(LinearLayout.VERTICAL);
        browser.setBackgroundColor(Color.WHITE);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(10), 0, dp(4), 0);
        toolbar.setBackgroundColor(surface);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.app_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        toolbar.addView(logo, linearParams(dp(30), dp(30), 0, 0, 10, 0));

        Uri bridgeUri = Uri.parse(url);
        String host = bridgeUri.getHost() == null ? getString(R.string.app_name) : bridgeUri.getHost();
        if (bridgeUri.getPort() > 0) host += ":" + bridgeUri.getPort();
        TextView hostView = label(host, 14, text, true);
        hostView.setSingleLine(true);
        hostView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams hostParams = linearParams(0, -2, 1, 0, 0, 0);
        toolbar.addView(hostView, hostParams);

        ImageButton reload = toolbarButton(android.R.drawable.ic_popup_sync, R.string.reload);
        reload.setOnClickListener(view -> webView.reload());
        toolbar.addView(reload, linearParams(dp(48), dp(48), 0, 0, 0, 0));

        ImageButton edit = toolbarButton(android.R.drawable.ic_menu_edit, R.string.change_connection);
        edit.setOnClickListener(view -> showSetup(null));
        toolbar.addView(edit, linearParams(dp(48), dp(48), 0, 0, 0, 0));
        browser.addView(toolbar, linearParams(-1, dp(48), 0, 0, 0, 0));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.getIndeterminateDrawable().setColorFilter(blue, android.graphics.PorterDuff.Mode.SRC_IN);
        browser.addView(progressBar, linearParams(-1, dp(2), 0, 0, 0, 0));

        errorBar = new LinearLayout(this);
        errorBar.setOrientation(LinearLayout.HORIZONTAL);
        errorBar.setGravity(Gravity.CENTER_VERTICAL);
        errorBar.setPadding(dp(14), dp(8), dp(8), dp(8));
        errorBar.setBackgroundColor(Color.rgb(255, 239, 240));
        TextView errorText = label(getString(R.string.connection_failed), 13, Color.rgb(151, 32, 40), false);
        errorBar.addView(errorText, linearParams(0, -2, 1, 0, 8, 0));
        Button retry = compactButton(R.string.retry);
        retry.setOnClickListener(view -> webView.loadUrl(currentUrl));
        errorBar.addView(retry, linearParams(-2, dp(40), 0, 0, 0, 0));
        errorBar.setVisibility(View.GONE);
        browser.addView(errorBar, linearParams(-1, -2, 0, 0, 0, 0));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setUserAgentString(settings.getUserAgentString() + " EasyCodexAndroid/0.5.0");
        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        WebView.setWebContentsDebuggingEnabled(debuggable);

        final boolean[] mainFrameFailed = {false};
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
                } catch (ActivityNotFoundException ignored) {
                    // Unsupported external schemes stay closed.
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String nextUrl, android.graphics.Bitmap favicon) {
                mainFrameFailed[0] = false;
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String loadedUrl) {
                progressBar.setVisibility(View.GONE);
                if (!mainFrameFailed[0]) errorBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    mainFrameFailed[0] = true;
                    progressBar.setVisibility(View.GONE);
                    errorBar.setVisibility(View.VISIBLE);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException error) {
                    fileCallback = null;
                    return false;
                }
            }
        });

        webView.setDownloadListener((downloadUrl, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)));
            } catch (ActivityNotFoundException ignored) {
                // The bridge can still render downloadable artifacts in the WebView.
            }
        });

        browser.addView(webView, linearParams(-1, 0, 1, 0, 0, 0));
        root.addView(browser, frameParams(-1, -1));
        webView.loadUrl(url);
    }

    private String normalizeUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return null;
        if (!value.contains("://")) value = "http://" + value;
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) return null;
        if (uri.getHost() == null || uri.getHost().trim().isEmpty()) return null;
        return uri.toString();
    }

    private TextView label(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }

    private ImageButton toolbarButton(int icon, int description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setColorFilter(muted);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setContentDescription(getString(description));
        button.setTooltipText(getString(description));
        button.setPadding(dp(13), dp(13), dp(13), dp(13));
        return button;
    }

    private Button compactButton(int stringId) {
        Button button = new Button(this);
        button.setText(stringId);
        button.setTextColor(blue);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(rounded(Color.WHITE, Color.rgb(222, 225, 230), 1, 9));
        return button;
    }

    private GradientDrawable rounded(int fill, int stroke, int strokeWidth, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams linearParams(int width, int height, float weight, int left, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height, weight);
        params.setMargins(dp(left), 0, dp(right), dp(bottom));
        return params;
    }

    private FrameLayout.LayoutParams frameParams(int width, int height) {
        return new FrameLayout.LayoutParams(width, height);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || fileCallback == null) return;
        fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
        fileCallback = null;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    private void destroyWebView() {
        if (webView == null) return;
        webView.stopLoading();
        webView.setWebChromeClient(null);
        webView.setWebViewClient(null);
        webView.destroy();
        webView = null;
    }

    @Override
    protected void onDestroy() {
        destroyWebView();
        if (connectivityManager != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (RuntimeException ignored) {
                // Callback registration can fail on restricted devices.
            }
        }
        super.onDestroy();
    }
}
