package com.cloudgpu.hardwareinspector;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends Activity {
    private static final int PHONE_PERMISSION_REQUEST = 41;
    private static final long STORAGE_TEST_BYTES = 256L * 1024L * 1024L; // 256 MiB
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout results;
    private Button runButton;
    private String gpuResult = "Checking graphics hardware…";
    private boolean testRunning = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        startGpuProbe();
        addResult("Ready", "Press START FULL DEVICE TEST. The test works offline.");
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(24), dp(20), dp(28));
        page.setBackgroundColor(0xFFF4F6FA);

        TextView heading = new TextView(this);
        heading.setText("CloudGPU Hardware Inspector");
        heading.setTextSize(23);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setTextColor(0xFF14213D);
        page.addView(heading);

        TextView intro = new TextView(this);
        intro.setText("Checks the Android device's reported hardware and runs local tests. No internet download is needed.");
        intro.setTextSize(14);
        intro.setTextColor(0xFF455166);
        intro.setPadding(0, dp(8), 0, dp(16));
        page.addView(intro);

        runButton = new Button(this);
        runButton.setText("START FULL DEVICE TEST");
        runButton.setAllCaps(false);
        runButton.setTextSize(16);
        page.addView(runButton);

        TextView privacy = new TextView(this);
        privacy.setText("Privacy: this app does not open photos, messages, documents, passwords, or personal files. Its storage test creates and deletes its own temporary test file.");
        privacy.setTextSize(12);
        privacy.setTextColor(0xFF526078);
        privacy.setPadding(0, dp(12), 0, dp(12));
        page.addView(privacy);

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        page.addView(results);

        scroll.addView(page);
        setContentView(scroll);

        runButton.setOnClickListener(v -> beginTest());
    }

    private void startGpuProbe() {
        GLSurfaceView glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(2);
        glView.setRenderer(new GLSurfaceView.Renderer() {
            @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                String vendor = gl.glGetString(GL10.GL_VENDOR);
                String renderer = gl.glGetString(GL10.GL_RENDERER);
                String version = gl.glGetString(GL10.GL_VERSION);
                gpuResult = "GPU vendor: " + safe(vendor) + "\nGPU renderer: " + safe(renderer)
                        + "\nOpenGL ES: " + safe(version);
            }
            @Override public void onSurfaceChanged(GL10 gl, int width, int height) {}
            @Override public void onDrawFrame(GL10 gl) {}
        });
        // Keep the view attached so Android creates a real OpenGL context.
        glView.setLayoutParams(new LinearLayout.LayoutParams(1, 1));
        ((android.view.ViewGroup) findViewById(android.R.id.content)).addView(glView);
    }

    private void beginTest() {
        if (testRunning) return;
        testRunning = true;
        runButton.setEnabled(false);
        results.removeAllViews();
        addResult("Test running", "Please keep the app open. The storage test uses up to 256 MiB of temporary space and deletes it afterwards.");

        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, PHONE_PERMISSION_REQUEST);
        } else {
            runAllTests();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PHONE_PERMISSION_REQUEST) runAllTests();
    }

    private void runAllTests() {
        worker.execute(() -> {
            StringBuilder report = new StringBuilder();
            report.append("DEVICE IDENTITY\n").append(deviceInfo()).append("\n\n");
            report.append("RAM\n").append(ramInfo()).append("\n\n");
            report.append("STORAGE\n").append(storageInfo()).append("\n\n");
            report.append("PROCESSOR\n").append(cpuInfo()).append("\n\n");
            report.append("GRAPHICS\n").append(gpuResult).append("\n\n");
            report.append("DISPLAY\n").append(displayInfo()).append("\n\n");
            report.append("MOBILE NETWORK\n").append(networkInfo()).append("\n\n");
            report.append("CPU PERFORMANCE\n").append(cpuBenchmark()).append("\n\n");
            report.append("STORAGE READ/WRITE TEST\n").append(storageBenchmark()).append("\n\n");
            report.append("IMPORTANT LIMIT\n");
            report.append("These readings come from Android and can be falsified by modified firmware. A successful 256 MiB test does NOT prove a claimed 1 TB capacity. This app does not certify that hardware is genuine.");

            runOnUiThread(() -> {
                results.removeAllViews();
                String[] sections = report.toString().split("\n\n");
                for (String section : sections) {
                    int split = section.indexOf('\n');
                    String title = split >= 0 ? section.substring(0, split) : "Result";
                    String body = split >= 0 ? section.substring(split + 1) : section;
                    addResult(title, body);
                }
                runButton.setEnabled(true);
                testRunning = false;
            });
        });
    }

    private String deviceInfo() {
        return "Brand: " + Build.BRAND + "\nManufacturer: " + Build.MANUFACTURER
                + "\nModel: " + Build.MODEL + "\nAndroid: " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")\nBuild fingerprint: " + Build.FINGERPRINT;
    }

    private String ramInfo() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long procMem = readMemInfoKb("MemTotal");
        long swap = readMemInfoKb("SwapTotal");
        StringBuilder s = new StringBuilder();
        s.append("Android total memory: ").append(formatBytes(mi.totalMem));
        if (procMem > 0) s.append("\n/proc/meminfo MemTotal: ").append(formatBytes(procMem * 1024));
        s.append("\nCurrently available to Android: ").append(formatBytes(mi.availMem));
        s.append("\nSwap reported by Android: ").append(swap >= 0 ? formatBytes(swap * 1024) : "Not readable");
        s.append("\nAssessment: OS-reported figures, not independent proof of physical RAM.");
        return s.toString();
    }

    private long readMemInfoKb(String key) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/meminfo")))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(key + ":")) {
                    String[] parts = line.trim().split("\\s+");
                    return Long.parseLong(parts[1]);
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private String storageInfo() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long total = stat.getTotalBytes();
            long free = stat.getAvailableBytes();
            return "Android data partition total: " + formatBytes(total)
                    + "\nAvailable space: " + formatBytes(free)
                    + "\nNote: this is the Android data partition, not necessarily the tablet's full advertised physical storage.";
        } catch (Exception e) {
            return "Could not read Android data partition: " + e.getMessage();
        }
    }

    private String cpuInfo() {
        String soc = Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : "Not exposed by this Android version";
        String cpu = readCpuModel();
        return "System-on-chip model: " + soc + "\nHardware board: " + Build.HARDWARE
                + "\nCPU description: " + cpu + "\nCPU cores visible to Android: " + Runtime.getRuntime().availableProcessors()
                + "\nSupported app architectures: " + String.join(", ", Build.SUPPORTED_ABIS);
    }

    private String readCpuModel() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/cpuinfo")))) {
            String line;
            while ((line = br.readLine()) != null) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.startsWith("model name") || lower.startsWith("hardware") || lower.startsWith("processor")) {
                    return line.trim();
                }
            }
        } catch (Exception ignored) {}
        return "Not exposed by Android";
    }

    private String displayInfo() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        return "App display size: " + dm.widthPixels + " × " + dm.heightPixels + " pixels"
                + "\nDensity: " + dm.densityDpi + " dpi"
                + "\nNote: Android may report a logical app resolution rather than the panel's native specification.";
    }

    private String networkInfo() {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return "Phone permission was not granted. Mobile network type could not be checked.";
        }
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            if (tm == null) return "No telephony service reported. This does not prove the device lacks cellular hardware.";
            int type = tm.getDataNetworkType();
            String name = networkTypeName(type);
            return "Android mobile data network type: " + name
                    + "\nA 5G NR result means Android currently reports a 5G connection. LTE/4G may simply mean 5G is unavailable at this location or with this SIM.";
        } catch (SecurityException e) {
            return "Android denied access to mobile network information.";
        } catch (Exception e) {
            return "Could not read mobile network type: " + e.getMessage();
        }
    }

    private String networkTypeName(int t) {
        switch (t) {
            case TelephonyManager.NETWORK_TYPE_NR: return "5G NR";
            case TelephonyManager.NETWORK_TYPE_LTE: return "LTE / 4G";
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_UMTS: return "3G";
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE: return "2G";
            case TelephonyManager.NETWORK_TYPE_UNKNOWN: return "Unknown / not connected";
            default: return "Other network type (code " + t + ")";
        }
    }

    private String cpuBenchmark() {
        long start = System.nanoTime();
        long count = 0;
        for (int i = 2; i < 220000; i++) {
            boolean prime = true;
            for (int j = 2; j * j <= i; j++) {
                if (i % j == 0) { prime = false; break; }
            }
            if (prime) count++;
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        return "Prime-number calculation completed.\nPrimes counted: " + count
                + "\nTime: " + ms + " ms\nThis is a simple comparison test, not a certified benchmark.";
    }

    private String storageBenchmark() {
        File testFile = new File(getCacheDir(), "hardware_inspector_storage_test.tmp");
        long start = System.nanoTime();
        try {
            MessageDigest writeHash = MessageDigest.getInstance("SHA-256");
            byte[] block = new byte[1024 * 1024];
            long written = 0;
            try (FileOutputStream out = new FileOutputStream(testFile)) {
                while (written < STORAGE_TEST_BYTES) {
                    int length = (int)Math.min(block.length, STORAGE_TEST_BYTES - written);
                    for (int i = 0; i < length; i++) {
                        block[i] = (byte)((i * 31L + written * 7L + (i >>> 8)) & 0xFF);
                    }
                    out.write(block, 0, length);
                    writeHash.update(block, 0, length);
                    written += length;
                }
                out.getFD().sync();
            }
            byte[] expected = writeHash.digest();
            MessageDigest readHash = MessageDigest.getInstance("SHA-256");
            long read = 0;
            byte[] readBuffer = new byte[1024 * 1024];
            try (FileInputStream in = new FileInputStream(testFile)) {
                int n;
                while ((n = in.read(readBuffer)) != -1) {
                    readHash.update(readBuffer, 0, n);
                    read += n;
                }
            }
            boolean matches = read == STORAGE_TEST_BYTES && MessageDigest.isEqual(expected, readHash.digest());
            long ms = (System.nanoTime() - start) / 1_000_000;
            return "Temporary data written: " + formatBytes(written)
                    + "\nData read back: " + formatBytes(read)
                    + "\nData integrity: " + (matches ? "PASS — read-back data matched" : "FAIL — data mismatch")
                    + "\nCombined write/read time: " + ms + " ms"
                    + "\nTemporary test file: " + (testFile.delete() ? "deleted" : "delete pending");
        } catch (Exception e) {
            // Always try to remove the app's temporary test file.
            //noinspection ResultOfMethodCallIgnored
            testFile.delete();
            return "Storage test could not finish: " + e.getClass().getSimpleName() + " — " + e.getMessage();
        }
    }

    private void addResult(String title, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(9);
        results.addView(card, cp);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setTextColor(0xFF17345C);
        card.addView(t);

        TextView b = new TextView(this);
        b.setText(body);
        b.setTextSize(13);
        b.setTextColor(0xFF263449);
        b.setPadding(0, dp(6), 0, 0);
        card.addView(b);
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "Unavailable";
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 1) return String.format(Locale.US, "%.2f GiB", gb);
        double mb = bytes / (1024.0 * 1024.0);
        return String.format(Locale.US, "%.1f MiB", mb);
    }

    private String safe(String value) {
        return value == null ? "Not reported" : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }
}
