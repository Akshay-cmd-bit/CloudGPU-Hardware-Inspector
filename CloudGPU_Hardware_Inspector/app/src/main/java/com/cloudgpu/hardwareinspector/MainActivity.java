
package com.cloudgpu.hardwareinspector;

import android.app.Activity;
import android.app.ActivityManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends Activity {

    private static final long MB = 1024L * 1024L;
    private static final long STORAGE_LIMIT = 256L * MB;
    private static final long GPU_TEST_SECONDS = 5L;

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor();

    private LinearLayout results;
    private Button runButton;
    private GLSurfaceView glView;
    private BenchmarkRenderer renderer;

    private volatile boolean testRunning = false;

    private static volatile long benchmarkSink = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();

        addResult(
                "Ready",
                "CloudGPU Hardware Inspector 2.0\n\n" +
                "RAM, storage, CPU and GPU performance tests are ready.\n" +
                "Tests run locally and work offline."
        );
    }

    private void buildScreen() {

        ScrollView scroll = new ScrollView(this);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(22), dp(18), dp(25));
        page.setBackgroundColor(0xFFF1F4F9);

        TextView heading = new TextView(this);
        heading.setText("CloudGPU Hardware Inspector");
        heading.setTextSize(24);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setTextColor(0xFF14213D);
        page.addView(heading);

        TextView version = new TextView(this);
        version.setText("VERSION 2.0  |  HARDWARE BENCHMARK");
        version.setTextSize(11);
        version.setTextColor(0xFF3478C8);
        version.setPadding(0, dp(5), 0, dp(10));
        page.addView(version);

        TextView intro = new TextView(this);
        intro.setText(
                "Run actual workloads to measure memory allocation, " +
                "storage read/write performance, CPU calculations " +
                "and GPU rendering."
        );
        intro.setTextSize(14);
        intro.setTextColor(0xFF455166);
        intro.setPadding(0, 0, 0, dp(12));
        page.addView(intro);

        TextView gpuLabel = new TextView(this);
        gpuLabel.setText("LIVE GPU TEST SURFACE");
        gpuLabel.setTextSize(12);
        gpuLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        gpuLabel.setTextColor(0xFF17345C);
        page.addView(gpuLabel);

        glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(2);

        renderer = new BenchmarkRenderer();
        glView.setRenderer(renderer);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        LinearLayout.LayoutParams glParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(190)
                );

        glParams.topMargin = dp(8);
        glParams.bottomMargin = dp(14);

        page.addView(glView, glParams);

        runButton = new Button(this);
        runButton.setText("START ALL HARDWARE TESTS");
        runButton.setAllCaps(false);
        runButton.setTextSize(16);
        page.addView(runButton);

        TextView privacy = new TextView(this);
        privacy.setText(
                "PRIVACY AND SAFETY\n" +
                "Tests use temporary memory and a temporary file " +
                "inside this app's private storage. The storage test " +
                "uses up to 256 MiB and deletes its test file. " +
                "No personal files are opened."
        );
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

    private void beginTest() {

        if (testRunning) return;

        testRunning = true;
        runButton.setEnabled(false);
        results.removeAllViews();

        addResult(
                "Testing",
                "Please keep the application open.\n" +
                "The GPU test takes approximately 5 seconds."
        );

        worker.execute(this::runAllTests);
    }

    private void runAllTests() {

        List<String[]> sections = new ArrayList<>();

        try {
            sections.add(new String[]{
                    "DEVICE INFORMATION",
                    deviceInfo()
            });

            sections.add(new String[]{
                    "RAM INFORMATION AND TEST",
                    ramBenchmark()
            });

            sections.add(new String[]{
                    "STORAGE INFORMATION",
                    storageInfo()
            });

            sections.add(new String[]{
                    "STORAGE SPEED AND INTEGRITY",
                    storageBenchmark()
            });

            sections.add(new String[]{
                    "PROCESSOR INFORMATION",
                    cpuInfo()
            });

            sections.add(new String[]{
                    "CPU PERFORMANCE",
                    cpuBenchmark()
            });

            sections.add(new String[]{
                    "GPU INFORMATION",
                    renderer.getIdentity()
            });

            sections.add(new String[]{
                    "GPU RENDERING PERFORMANCE",
                    gpuBenchmark()
            });

            sections.add(new String[]{
                    "DISPLAY",
                    displayInfo()
            });

        } catch (Exception e) {

            sections.add(new String[]{
                    "TEST ERROR",
                    e.getClass().getSimpleName() + ": " + e.getMessage()
            });
        }

        sections.add(new String[]{
                "IMPORTANT TEST LIMITATIONS",
                "These are real application workloads, not independent " +
                "measurements of every physical chip.\n\n" +
                "Android and modified firmware can falsify reported " +
                "hardware information.\n\n" +
                "A 256 MiB storage test does not prove that a device " +
                "has 1 TB of physical storage.\n\n" +
                "RAM allocation measures memory available to this app. " +
                "It does not independently certify the device's " +
                "physical RAM chips.\n\n" +
                "GPU results measure this app's OpenGL rendering workload, " +
                "not every possible graphics or compute workload."
        });

        runOnUiThread(() -> {

            results.removeAllViews();

            for (String[] section : sections) {
                addResult(section[0], section[1]);
            }

            runButton.setEnabled(true);
            testRunning = false;
        });
    }

    // --------------------------------------------------
    // DEVICE INFORMATION
    // --------------------------------------------------

    private String deviceInfo() {

        return "Manufacturer: " + Build.MANUFACTURER +
                "\nBrand: " + Build.BRAND +
                "\nModel: " + Build.MODEL +
                "\nDevice: " + Build.DEVICE +
                "\nAndroid version: " + Build.VERSION.RELEASE +
                "\nAndroid API: " + Build.VERSION.SDK_INT +
                "\nHardware board: " + Build.HARDWARE +
                "\nBuild fingerprint: " + Build.FINGERPRINT;
    }

    // --------------------------------------------------
    // RAM TEST
    // --------------------------------------------------

    private String ramBenchmark() {

        ActivityManager manager =
                (ActivityManager) getSystemService(ACTIVITY_SERVICE);

        ActivityManager.MemoryInfo info =
                new ActivityManager.MemoryInfo();

        manager.getMemoryInfo(info);

        Runtime runtime = Runtime.getRuntime();

        long maxHeap = runtime.maxMemory();

        long testBytes = Math.min(
                64L * MB,
                Math.min(maxHeap / 8L, info.availMem / 8L)
        );

        StringBuilder report = new StringBuilder();

        report.append("Android-reported total RAM: ")
                .append(formatBytes(info.totalMem));

        report.append("\nCurrently available RAM: ")
                .append(formatBytes(info.availMem));

        report.append("\nMaximum application heap: ")
                .append(formatBytes(maxHeap));

        report.append("\nMemory class: ")
                .append(manager.getMemoryClass())
                .append(" MB");

        report.append("\nLarge memory class: ")
                .append(manager.getLargeMemoryClass())
                .append(" MB");

        if (testBytes < 4L * MB) {

            report.append("\n\nRAM workload skipped because there " +
                    "is not enough safe memory available.");

            return report.toString();
        }

        byte[] memory;

        long allocationStart = System.nanoTime();

        try {
            memory = new byte[(int) testBytes];
        } catch (OutOfMemoryError error) {
            return report +
                    "\n\nRAM allocation failed. Android could not " +
                    "provide the requested test memory.";
        }

        long allocationTime =
                System.nanoTime() - allocationStart;

        long writeStart = System.nanoTime();

        for (int i = 0; i < memory.length; i++) {
            memory[i] = (byte) ((i * 31L + 7L) & 255);
        }

        long writeTime =
                System.nanoTime() - writeStart;

        long readStart = System.nanoTime();

        long checksum = 0;

        for (int i = 0; i < memory.length; i++) {
            checksum += memory[i] & 255;
        }

        long readTime =
                System.nanoTime() - readStart;

        benchmarkSink = checksum;

        report.append("\n\nACTUAL APP MEMORY ALLOCATION");

        report.append("\nSuccessfully allocated: ")
                .append(formatBytes(memory.length));

        report.append("\nAllocation time: ")
                .append(formatMilliseconds(allocationTime));

        report.append("\nMemory write throughput: ")
                .append(formatSpeed(memory.length, writeTime));

        report.append("\nMemory read throughput: ")
                .append(formatSpeed(memory.length, readTime));

        report.append("\nRead checksum: ")
                .append(checksum);

        report.append("\n\nThis measures Java application memory " +
                "allocation and access speed, not total physical RAM.");

        memory = null;

        System.gc();

        return report.toString();
    }

    // --------------------------------------------------
    // STORAGE INFORMATION
    // --------------------------------------------------

    private String storageInfo() {

        try {

            StatFs stat = new StatFs(
                    android.os.Environment.getDataDirectory().getPath()
            );

            long total = stat.getTotalBytes();
            long free = stat.getAvailableBytes();

            return "Android data partition total: " +
                    formatBytes(total) +
                    "\nAvailable space: " +
                    formatBytes(free) +
                    "\n\nThis is the Android data partition. " +
                    "It may not represent the full physical storage " +
                    "advertised by the manufacturer.";

        } catch (Exception e) {

            return "Storage information unavailable: " +
                    e.getMessage();
        }
    }

    // --------------------------------------------------
    // STORAGE READ / WRITE / VERIFY TEST
    // --------------------------------------------------

    private String storageBenchmark() {

        File testFile = new File(
                getCacheDir(),
                "cloudgpu_storage_benchmark.tmp"
        );

        long available = getCacheDir().getUsableSpace();

        long testBytes = Math.min(
                STORAGE_LIMIT,
                Math.max(0L, available - 32L * MB)
        );

        testBytes = (testBytes / MB) * MB;

        if (testBytes < 16L * MB) {
            return "Storage benchmark skipped.\n" +
                    "At least 16 MiB of safe temporary space is required.";
        }

        byte[] buffer = new byte[(int) MB];

        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = (byte) ((i * 31 + 17) & 255);
        }

        long written = 0;
        long read = 0;

        long writeTime = 0;
        long readTime = 0;

        boolean matches = false;

        try {

            MessageDigest writeDigest =
                    MessageDigest.getInstance("SHA-256");

            long writeStart = System.nanoTime();

            try (FileOutputStream output =
                         new FileOutputStream(testFile)) {

                while (written < testBytes) {

                    int length = (int) Math.min(
                            buffer.length,
                            testBytes - written
                    );

                    output.write(buffer, 0, length);

                    writeDigest.update(buffer, 0, length);

                    written += length;
                }

                output.flush();
                output.getFD().sync();
            }

            writeTime = System.nanoTime() - writeStart;

            byte[] expectedHash = writeDigest.digest();

            MessageDigest readDigest =
                    MessageDigest.getInstance("SHA-256");

            byte[] readBuffer = new byte[1024 * 1024];

            long readStart = System.nanoTime();

            try (FileInputStream input =
                         new FileInputStream(testFile)) {

                int count;

                while ((count = input.read(readBuffer)) != -1) {

                    readDigest.update(readBuffer, 0, count);
                    read += count;
                }
            }

            readTime = System.nanoTime() - readStart;

            matches = read == written &&
                    MessageDigest.isEqual(
                            expectedHash,
                            readDigest.digest()
                    );

            return "Temporary data written: " +
                    formatBytes(written) +

                    "\nData read back: " +
                    formatBytes(read) +

                    "\nWrite speed: " +
                    formatSpeed(written, writeTime) +

                    "\nRead speed: " +
                    formatSpeed(read, readTime) +

                    "\nData integrity: " +
                    (matches ? "PASS" : "FAIL") +

                    "\nSHA-256 verification: " +
                    (matches ? "MATCH" : "MISMATCH") +

                    "\nTemporary test size: " +
                    formatBytes(testBytes) +

                    "\nTemporary file deleted: " +
                    (deleteTestFile(testFile) ? "YES" : "NO") +

                    "\n\nThis verifies only the amount tested. " +
                    "It does not prove the complete advertised storage capacity.";

        } catch (Exception e) {

            deleteTestFile(testFile);

            return "Storage test failed: " +
                    e.getClass().getSimpleName() +
                    "\n" + e.getMessage();
        }
    }

    private boolean deleteTestFile(File file) {

        if (!file.exists()) return true;

        return file.delete() || !file.exists();
    }

    // --------------------------------------------------
    // CPU INFORMATION
    // --------------------------------------------------

    private String cpuInfo() {

        String soc = "Not exposed";

        if (Build.VERSION.SDK_INT >= 31) {
            soc = Build.SOC_MODEL;
        }

        return "System-on-chip: " + soc +
                "\nHardware: " + Build.HARDWARE +
                "\nCPU cores visible to app: " +
                Runtime.getRuntime().availableProcessors() +
                "\nSupported architectures: " +
                String.join(", ", Build.SUPPORTED_ABIS) +
                "\n\nCPU identification is Android-reported information.";
    }

    // --------------------------------------------------
    // CPU SINGLE-CORE AND MULTI-CORE BENCHMARK
    // --------------------------------------------------

    private String cpuBenchmark() throws Exception {

        final long iterations = 12_000_000L;

        long singleStart = System.nanoTime();

        long singleResult = cpuWorkload(
                iterations,
                0x123456789ABCDEFL
        );

        long singleTime =
                System.nanoTime() - singleStart;

        benchmarkSink = singleResult;

        int cores = Math.max(
                1,
                Math.min(
                        8,
                        Runtime.getRuntime().availableProcessors()
                )
        );

        ExecutorService pool =
                Executors.newFixedThreadPool(cores);

        long multiStart = System.nanoTime();

        List<Future<Long>> futures = new ArrayList<>();

        long each = iterations / cores;

        try {

            for (int i = 0; i < cores; i++) {

                final int workerId = i;

                futures.add(pool.submit(() ->
                        cpuWorkload(
                                each,
                                0xABCDEF123456789L + workerId
                        )
                ));
            }

            long combined = 0;

            for (Future<Long> future : futures) {
                combined ^= future.get();
            }

            benchmarkSink = combined;

        } finally {
            pool.shutdownNow();
        }

        long multiTime =
                System.nanoTime() - multiStart;

        return "Single-core workload: " +
                formatNumber(iterations) +
                " iterations" +

                "\nSingle-core time: " +
                formatMilliseconds(singleTime) +

                "\nSingle-core throughput: " +
                formatOperations(iterations, singleTime) +

                "\n\nMulti-core workload: " +
                formatNumber(iterations) +
                " total iterations" +

                "\nWorker threads: " + cores +

                "\nMulti-core time: " +
                formatMilliseconds(multiTime) +

                "\nMulti-core throughput: " +
                formatOperations(iterations, multiTime) +

                "\n\nHigher throughput means more completed " +
                "work per second in this particular CPU test.";
    }

    private long cpuWorkload(long iterations, long seed) {

        long value = seed;

        for (long i = 0; i < iterations; i++) {

            value ^= (value << 13);
            value ^= (value >>> 7);
            value ^= (value << 17);

            value += i * 0x9E3779B97F4A7C15L;
        }

        return value;
    }

    // --------------------------------------------------
    // GPU BENCHMARK
    // --------------------------------------------------

    private String gpuBenchmark() {

        try {

            CountDownLatch latch = new CountDownLatch(1);

            AtomicReference<String> result =
                    new AtomicReference<>("GPU benchmark did not finish.");

            glView.queueEvent(() ->
                    renderer.beginBenchmark(latch, result)
            );

            glView.requestRender();

            boolean completed = latch.await(
                    15,
                    TimeUnit.SECONDS
            );

            if (!completed) {
                return "GPU benchmark timed out. " +
                        "Check OpenGL support and keep the app visible.";
            }

            return result.get();

        } catch (Exception e) {

            return "GPU benchmark error: " +
                    e.getMessage();
        }
    }

    private static class BenchmarkRenderer
            implements GLSurfaceView.Renderer {

        private static final int DRAW_CALLS_PER_FRAME = 80;

        private static final long TEST_DURATION_NS =
                GPU_TEST_SECONDS * 1_000_000_000L;

        private static final String VERTEX_SHADER =
                "attribute vec4 aPosition;" +
                "void main() {" +
                "gl_Position = aPosition;" +
                "}";

        private static final String FRAGMENT_SHADER =
                "precision mediump float;" +
                "uniform vec4 uColor;" +
                "void main() {" +
                "gl_FragColor = uColor;" +
                "}";

        private int program = 0;
        private int positionLocation = -1;
        private int colorLocation = -1;

        private FloatBuffer vertexBuffer;

        private volatile String vendor = "Checking...";
        private volatile String gpu = "Checking...";
        private volatile String version = "Checking...";

        private boolean benchmarkRunning = false;

        private long startTime;
        private int frames;
        private long drawCalls;

        private CountDownLatch finishLatch;
        private AtomicReference<String> finishResult;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {

            vendor = safeGL(GLES20.glGetString(GLES20.GL_VENDOR));
            gpu = safeGL(GLES20.glGetString(GLES20.GL_RENDERER));
            version = safeGL(GLES20.glGetString(GLES20.GL_VERSION));

            float[] vertices = {
                    -0.85f, -0.75f, 0f,
                     0.85f, -0.75f, 0f,
                     0.00f,  0.85f, 0f
            };

            ByteBuffer bytes = ByteBuffer.allocateDirect(
                    vertices.length * 4
            );

            bytes.order(ByteOrder.nativeOrder());

            vertexBuffer = bytes.asFloatBuffer();
            vertexBuffer.put(vertices);
            vertexBuffer.position(0);

            int vertex = compileShader(
                    GLES20.GL_VERTEX_SHADER,
                    VERTEX_SHADER
            );

            int fragment = compileShader(
                    GLES20.GL_FRAGMENT_SHADER,
                    FRAGMENT_SHADER
            );

            if (vertex == 0 || fragment == 0) {
                program = 0;
                return;
            }

            program = GLES20.glCreateProgram();

            GLES20.glAttachShader(program, vertex);
            GLES20.glAttachShader(program, fragment);
            GLES20.glLinkProgram(program);

            int[] status = new int[1];

            GLES20.glGetProgramiv(
                    program,
                    GLES20.GL_LINK_STATUS,
                    status,
                    0
            );

            if (status[0] == 0) {
                GLES20.glDeleteProgram(program);
                program = 0;
                return;
            }

            positionLocation =
                    GLES20.glGetAttribLocation(
                            program,
                            "aPosition"
                    );

            colorLocation =
                    GLES20.glGetUniformLocation(
                            program,
                            "uColor"
                    );

            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        }

        private int compileShader(int type, String source) {

            int shader = GLES20.glCreateShader(type);

            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);

            int[] status = new int[1];

            GLES20.glGetShaderiv(
                    shader,
                    GLES20.GL_COMPILE_STATUS,
                    status,
                    0
            );

            if (status[0] == 0) {
                GLES20.glDeleteShader(shader);
                return 0;
            }

            return shader;
        }

        @Override
        public void onSurfaceChanged(
                GL10 gl,
                int width,
                int height
        ) {
            GLES20.glViewport(0, 0, width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {

            GLES20.glClearColor(
                    0.04f,
                    0.07f,
                    0.13f,
                    1f
            );

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            if (program == 0) return;

            GLES20.glUseProgram(program);

            GLES20.glEnableVertexAttribArray(positionLocation);

            vertexBuffer.position(0);

            GLES20.glVertexAttribPointer(
                    positionLocation,
                    3,
                    GLES20.GL_FLOAT,
                    false,
                    0,
                    vertexBuffer
            );

            GLES20.glUniform4f(
                    colorLocation,
                    0.15f,
                    0.65f,
                    1f,
                    1f
            );

            for (int i = 0; i < DRAW_CALLS_PER_FRAME; i++) {

                GLES20.glDrawArrays(
                        GLES20.GL_TRIANGLES,
                        0,
                        3
                );
            }

            GLES20.glDisableVertexAttribArray(positionLocation);

            // Wait for GPU work to finish before measuring the frame.
            GLES20.glFinish();

            if (benchmarkRunning) {

                frames++;
                drawCalls += DRAW_CALLS_PER_FRAME;

                long elapsed = System.nanoTime() - startTime;

                if (elapsed >= TEST_DURATION_NS) {

                    double seconds = elapsed / 1_000_000_000.0;

                    double fps = frames / seconds;

                    double callsPerSecond = drawCalls / seconds;

                    String output =
                            String.format(
                                    Locale.US,
                                    "Benchmark duration: %.2f seconds" +
                                    "\nRendered frames: %d" +
                                    "\nAverage FPS: %.2f" +
                                    "\nCompleted draw calls: %d" +
                                    "\nDraw calls per second: %.0f" +
                                    "\nDraw calls per frame: %d" +
                                    "\n\nThis is an OpenGL ES rendering " +
                                    "benchmark, not a complete GPU " +
                                    "compute or gaming benchmark.",
                                    seconds,
                                    frames,
                                    fps,
                                    drawCalls,
                                    callsPerSecond,
                                    DRAW_CALLS_PER_FRAME
                            );

                    benchmarkRunning = false;

                    if (finishResult != null) {
                        finishResult.set(output);
                    }

                    if (finishLatch != null) {
                        finishLatch.countDown();
                    }
                }
            }
        }

        public void beginBenchmark(
                CountDownLatch latch,
                AtomicReference<String> result
        ) {

            if (program == 0) {

                result.set(
                        "GPU shader initialization failed. " +
                        "OpenGL ES rendering could not start."
                );

                latch.countDown();
                return;
            }

            finishLatch = latch;
            finishResult = result;

            frames = 0;
            drawCalls = 0;

            startTime = System.nanoTime();

            benchmarkRunning = true;
        }

        public String getIdentity() {

            return "GPU vendor: " + vendor +
                    "\nGPU renderer: " + gpu +
                    "\nOpenGL ES version: " + version;
        }

        private String safeGL(String value) {
            return value == null ? "Not reported" : value;
        }
    }

    // --------------------------------------------------
    // DISPLAY INFORMATION
    // --------------------------------------------------

    private String displayInfo() {

        DisplayMetrics dm =
                getResources().getDisplayMetrics();

        return "Application resolution: " +
                dm.widthPixels + " × " + dm.heightPixels +

                "\nDisplay density: " +
                dm.densityDpi + " dpi" +

                "\nDensity scale: " +
                dm.density +

                "\n\nAndroid may expose a logical application " +
                "resolution instead of the panel's native resolution.";
    }

    // --------------------------------------------------
    // RESULT DISPLAY
    // --------------------------------------------------

    private void addResult(String title, String body) {

        LinearLayout card = new LinearLayout(this);

        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(0xFFFFFFFF);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.topMargin = dp(9);

        results.addView(card, params);

        TextView heading = new TextView(this);

        heading.setText(title);
        heading.setTextSize(15);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setTextColor(0xFF17345C);

        card.addView(heading);

        TextView content = new TextView(this);

        content.setText(body);
        content.setTextSize(13);
        content.setTextColor(0xFF263449);
        content.setPadding(0, dp(7), 0, 0);

        card.addView(content);
    }

    // --------------------------------------------------
    // FORMATTING
    // --------------------------------------------------

    private String formatBytes(long bytes) {

        if (bytes < 0) return "Unavailable";

        double gb = bytes / (1024.0 * 1024.0 * 1024.0);

        if (gb >= 1) {
            return String.format(Locale.US, "%.2f GiB", gb);
        }

        double mb = bytes / (1024.0 * 1024.0);

        return String.format(Locale.US, "%.2f MiB", mb);
    }

    private String formatMilliseconds(long nanos) {

        return String.format(
                Locale.US,
                "%.2f ms",
                nanos / 1_000_000.0
        );
    }

    private String formatSpeed(long bytes, long nanos) {

        if (nanos <= 0) return "Unavailable";

        double seconds = nanos / 1_000_000_000.0;

        double speed = bytes / MB / seconds;

        return String.format(
                Locale.US,
                "%.2f MiB/s",
                speed
        );
    }

    private String formatOperations(long operations, long nanos) {

        if (nanos <= 0) return "Unavailable";

        double seconds = nanos / 1_000_000_000.0;

        double rate = operations / seconds / 1_000_000.0;

        return String.format(
                Locale.US,
                "%.2f million iterations/second",
                rate
        );
    }

    private String formatNumber(long value) {

        return String.format(Locale.US, "%,d", value);
    }

    private int dp(int value) {

        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    // --------------------------------------------------
    // LIFECYCLE
    // --------------------------------------------------

    @Override
    protected void onPause() {
        super.onPause();

        if (glView != null) {
            glView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (glView != null) {
            glView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        worker.shutdownNow();
    }
}
