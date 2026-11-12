package com.mansuri.billing;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.CancellationSignal;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import android.util.Base64;
import android.os.ParcelFileDescriptor;
import android.webkit.ValueCallback;
import android.content.Context;

public class MainActivity extends Activity {
    private static final int REQ_WRITE_STORAGE = 1001;
    private WebView webView;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new NativeBridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private PrintAttributes printAttributes() {
        return new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(new PrintAttributes.Resolution("mansuri_pdf", "Mansuri PDF", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build();
    }

    private PrintDocumentAdapter adapter() {
        return webView.createPrintDocumentAdapter("Mansuri_Fruit_Bill");
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
    }

    @SuppressWarnings("deprecation")
    private void startNativePrint() {
        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        if (printManager == null) {
            showToast("Printing is not available on this phone.");
            return;
        }
        printManager.print("Mansuri Fruit Bill", adapter(), printAttributes());
    }

    private void savePdfToDownloads() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_WRITE_STORAGE);
            return;
        }

        webView.evaluateJavascript(
                "(function(){var e=document.getElementById('billContainer');" +
                "if(!e || !e.innerHTML.trim()) return 'EMPTY';" +
                "document.body.setAttribute('data-pdf-mode','1');" +
                "return String(e.scrollHeight);})()",
                value -> {
                    if ("\"EMPTY\"".equals(value)) {
                        webView.evaluateJavascript("document.body.removeAttribute('data-pdf-mode')", null);
                        showToast("Please generate the bill first!");
                        return;
                    }
                    try {
                        int contentHeight = Integer.parseInt(value.replace("\\\"", "").replace("\"", ""));
                        createPdfFromWebView(contentHeight);
                    } catch (Exception e) {
                        webView.evaluateJavascript("document.body.removeAttribute('data-pdf-mode')", null);
                        showToast("PDF save failed: " + e.getMessage());
                    }
                });
    }

    private void createPdfFromWebView(int contentHeight) {
        final int viewWidth = webView.getWidth();
        if (viewWidth <= 0 || contentHeight <= 0) {
            webView.evaluateJavascript("document.body.removeAttribute('data-pdf-mode')", null);
            showToast("PDF creation failed: invalid bill size");
            return;
        }

        // A4 in PDF points: 595 x 842. Keep the bill at the same proportions as the WebView.
        final int pageWidth = 595;
        final int pageHeight = 842;
        final float scale = (float) pageWidth / (float) viewWidth;
        final int renderedHeight = Math.max(contentHeight, webView.getHeight());
        final int totalPages = (int) Math.ceil((renderedHeight * scale) / pageHeight);

        try {
            android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();
            webView.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(viewWidth, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(renderedHeight, android.view.View.MeasureSpec.EXACTLY));
            webView.layout(0, 0, viewWidth, renderedHeight);

            for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
                android.graphics.pdf.PdfDocument.PageInfo pageInfo =
                        new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber + 1).create();
                android.graphics.pdf.PdfDocument.Page page = document.startPage(pageInfo);
                android.graphics.Canvas canvas = page.getCanvas();
                canvas.scale(scale, scale);
                canvas.translate(0, -(pageNumber * pageHeight / scale));
                webView.draw(canvas);
                document.finishPage(page);
            }

            final String fileName = "Mansuri_Fruit_Bill.pdf";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Mansuri Billing");
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IOException("Could not create download file");

                try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "w")) {
                    if (pfd == null) throw new IOException("Could not open PDF output");
                    try (FileOutputStream ignored = new FileOutputStream(pfd.getFileDescriptor())) {
                        document.writeTo(ignored);
                    }
                } catch (Exception e) {
                    getContentResolver().delete(uri, null, null);
                    throw e;
                }

                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(uri, done, null, null);
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create Downloads folder");
                File out = new File(dir, fileName);
                try (FileOutputStream output = new FileOutputStream(out)) {
                    document.writeTo(output);
                }
                android.media.MediaScannerConnection.scanFile(
                        MainActivity.this, new String[]{out.getAbsolutePath()},
                        new String[]{"application/pdf"}, null);
            }

            document.close();
            webView.evaluateJavascript("document.body.removeAttribute('data-pdf-mode')", null);
            showToast("PDF saved in Downloads/Mansuri Billing");
        } catch (Exception e) {
            webView.evaluateJavascript("document.body.removeAttribute('data-pdf-mode')", null);
            showToast("PDF save failed: " + e.getMessage());
        }
    }

    public class NativeBridge {
        @JavascriptInterface
        public void saveExcel(String fileName, String base64Data) {
            runOnUiThread(() -> {
                try {
                    String safeName = (fileName == null ? "Mansuri_Bills.xlsx" : fileName)
                            .replaceAll("[\\\\/:*?\"<>|]", "_");
                    if (!safeName.toLowerCase().endsWith(".xlsx")) safeName += ".xlsx";
                    byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        String relativePath = Environment.DIRECTORY_DOWNLOADS + "/Mansuri Billing";
                        String selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?";
                        String[] args = new String[]{safeName, relativePath + "/"};
                        getContentResolver().delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, selection, args);
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                        values.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                        values.put(MediaStore.Downloads.RELATIVE_PATH, relativePath);
                        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        if (uri == null) throw new IOException("Could not create Excel file");
                        try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                            if (out == null) throw new IOException("Could not open Excel file");
                            out.write(bytes);
                            out.flush();
                        }
                    } else {
                        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Mansuri Billing");
                        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create folder");
                        File outFile = new File(dir, safeName);
                        try (FileOutputStream out = new FileOutputStream(outFile)) { out.write(bytes); }
                        android.media.MediaScannerConnection.scanFile(MainActivity.this, new String[]{outFile.getAbsolutePath()},
                                new String[]{"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"}, null);
                    }
                    showToast("Excel saved: Downloads/Mansuri Billing/" + safeName);
                } catch (Exception e) {
                    showToast("Excel save failed: " + e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void shareText(String message) {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, message == null ? "" : message);
                    startActivity(Intent.createChooser(intent, "Share customer ledger"));
                } catch (Exception e) { showToast("Share failed: " + e.getMessage()); }
            });
        }

        @JavascriptInterface
        public void shareExcel(String fileName) {
            runOnUiThread(() -> {
                try {
                    Uri uri = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        String selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?";
                        try (Cursor cursor = getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                new String[]{MediaStore.Downloads._ID}, selection,
                                new String[]{fileName, Environment.DIRECTORY_DOWNLOADS + "/Mansuri Billing/"}, null)) {
                            if (cursor != null && cursor.moveToFirst()) {
                                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID));
                                uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, String.valueOf(id));
                            }
                        }
                    }
                    if (uri == null) throw new IOException("Excel file not found. Save Excel first.");
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, "Share Mansuri Excel"));
                } catch (Exception e) { showToast("Excel share failed: " + e.getMessage()); }
            });
        }

        @JavascriptInterface
        public void printBill() {
            runOnUiThread(() -> {
                webView.evaluateJavascript(
                        "(function(){var e=document.getElementById('billContainer');" +
                        "if(!e || !e.innerHTML.trim()) return false;" +
                        "document.body.setAttribute('data-pdf-mode','1'); return true;})()",
                        value -> {
                            if ("false".equals(value)) {
                                showToast("Please generate the bill first!");
                                return;
                            }
                            webView.postDelayed(() -> startNativePrint(), 250);
                        });
            });
        }

        @JavascriptInterface
        public void downloadPDF() {
            runOnUiThread(() -> webView.postDelayed(() -> savePdfToDownloads(), 150));
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WRITE_STORAGE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            savePdfToDownloads();
        } else if (requestCode == REQ_WRITE_STORAGE) {
            showToast("Storage permission is needed to save the PDF.");
        }
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
