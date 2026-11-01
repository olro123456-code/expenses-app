package com.olro.expenses;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final String HOME = "https://appassets.androidplatform.net/assets/index.html";
    private static final int REQ_FILE = 7;

    private static MainActivity current;
    private WebView web;
    private WebViewAssetLoader loader;
    private ValueCallback<Uri[]> fileCb;
    private Uri camUri;
    private final Handler ui = new Handler(Looper.getMainLooper());

    /** Called by the notification service when something new arrived. */
    public static void ping() {
        final MainActivity a = current;
        if (a == null) return;
        a.ui.post(new Runnable() {
            @Override
            public void run() {
                if (a.web != null) a.web.evaluateJavascript("window.androidPing&&window.androidPing()", null);
            }
        });
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        current = this;

        loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView v, String url, String message, final JsResult r) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (d, w) -> r.confirm())
                        .setOnCancelListener(d -> r.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String url, String message, final JsResult r) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (d, w) -> r.confirm())
                        .setNegativeButton(android.R.string.cancel, (d, w) -> r.cancel())
                        .setOnCancelListener(d -> r.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                camUri = null;
                boolean wantsImage = false;
                String[] acc = p.getAcceptTypes();
                if (acc != null) for (String a : acc) if (a != null && a.startsWith("image")) wantsImage = true;
                Intent cam = null;
                try {
                    if (!wantsImage) throw new Exception("no camera");
                    File dir = new File(getCacheDir(), "cam");
                    dir.mkdirs();
                    File f = File.createTempFile("cap", ".jpg", dir);
                    camUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fp", f);
                    cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    cam.putExtra(MediaStore.EXTRA_OUTPUT, camUri);
                    cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception e) {
                    cam = null;
                    camUri = null;
                }
                try {
                    Intent go;
                    if (p.isCaptureEnabled() && cam != null) {
                        go = cam;
                    } else {
                        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                        pick.addCategory(Intent.CATEGORY_OPENABLE);
                        pick.setType(wantsImage ? "image/*" : "*/*");
                        go = Intent.createChooser(pick, "");
                        if (cam != null) go.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
                    }
                    startActivityForResult(go, REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    cb.onReceiveValue(null);
                    return false;
                }
                return true;
            }
        });

        web.loadUrl(HOME);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_FILE || fileCb == null) return;
        Uri[] out = null;
        if (res == RESULT_OK) {
            if (data != null && data.getData() != null) out = new Uri[]{data.getData()};
            else if (camUri != null) out = new Uri[]{camUri};
        }
        fileCb.onReceiveValue(out);
        fileCb = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
            web.evaluateJavascript("window.androidPing&&window.androidPing()", null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onDestroy() {
        if (current == this) current = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private void toast(final String m) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Functions the page can call. */
    public class Bridge {
        @JavascriptInterface
        public String drain() {
            return NotifQueue.drain(MainActivity.this);
        }

        @JavascriptInterface
        public boolean isListenerEnabled() {
            String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            return s != null && s.contains(getPackageName());
        }

        @JavascriptInterface
        public void openNotificationAccess() {
            try {
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void openBatterySettings() {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public boolean saveFile(String name, String text) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return false;
                OutputStream os = getContentResolver().openOutputStream(uri);
                if (os == null) return false;
                os.write(text.getBytes(StandardCharsets.UTF_8));
                os.close();
                toast("הגיבוי נשמר בתיקיית ההורדות: " + name);
                return true;
            } catch (Exception e) {
                toast("שמירת הגיבוי נכשלה");
                return false;
            }
        }
    }
}
