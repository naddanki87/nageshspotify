import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Builds and signs the NageshSpotify APK without Gradle or Android Studio, using the Android
 * command-line tools directly: aapt2 (resources), javac (Java), d8 or dx (dex) and apksigner.
 *
 * <p>Run it from the {@code android} folder with Java 17 or newer:
 *
 * <pre>
 *   java tools/BuildApk.java                         # uses $ANDROID_HOME
 *   java tools/BuildApk.java --android-jar PATH      # or point at any API 34 android.jar
 * </pre>
 *
 * The app's id, SDK levels and version are read from {@code app/build.gradle}, so the Gradle and
 * command-line builds stay in sync. The signed APK is written to {@code build/NageshSpotify.apk}.
 */
public class BuildApk {

    private static final Path ROOT = Paths.get("").toAbsolutePath();
    private static final Path APP = ROOT.resolve("app");
    private static final Path SRC = APP.resolve("src/main");
    private static final Path OUT = ROOT.resolve("build/apk");
    private static final Path KEYSTORE = ROOT.resolve("signing/nageshspotify.keystore");
    private static final String KEY_PASSWORD = "nageshspotify";

    public static void main(String[] args) throws Exception {
        if (!Files.isDirectory(SRC)) {
            fail("Run this from the android folder: cd android && java tools/BuildApk.java");
        }
        String gradle = Files.readString(APP.resolve("build.gradle"));
        String namespace = gradleValue(gradle, "namespace");
        String applicationId = gradleValue(gradle, "applicationId");
        String compileSdk = gradleValue(gradle, "compileSdk");
        String minSdk = gradleValue(gradle, "minSdk");
        String targetSdk = gradleValue(gradle, "targetSdk");
        String versionCode = gradleValue(gradle, "versionCode");
        String versionName = gradleValue(gradle, "versionName");

        Path androidJar = androidJar(args, compileSdk);
        String aapt2 = tool("aapt2");
        String zipalign = tool("zipalign");
        String apksigner = tool("apksigner");
        String d8 = optionalTool("d8");
        String dx = d8 != null ? null : optionalTool("dalvik-exchange", "dx");
        if (d8 == null && dx == null) {
            fail("Couldn't find d8 (Android build-tools) or dx. Install the Android SDK build-tools.");
        }

        deleteRecursively(OUT);
        Path gen = Files.createDirectories(OUT.resolve("gen"));
        Path classes = Files.createDirectories(OUT.resolve("classes"));
        Path dexDir = Files.createDirectories(OUT.resolve("dex"));

        step("Compiling resources");
        Path resZip = OUT.resolve("resources.zip");
        run(aapt2, "compile", "--dir", SRC.resolve("res").toString(), "-o", resZip.toString());

        step("Linking resources and manifest");
        Path manifest = OUT.resolve("AndroidManifest.xml");
        String source = Files.readString(SRC.resolve("AndroidManifest.xml"));
        Files.writeString(manifest, source.replaceFirst("<manifest ", "<manifest package=\"" + namespace + "\" "));
        Path baseApk = OUT.resolve("base.apk");
        run(aapt2, "link", "-I", androidJar.toString(), "--manifest", manifest.toString(),
                "--min-sdk-version", minSdk, "--target-sdk-version", targetSdk,
                "--version-code", versionCode, "--version-name", versionName,
                "--rename-manifest-package", applicationId,
                "--java", gen.toString(), "-o", baseApk.toString(), resZip.toString());

        step("Compiling Java");
        List<String> sources;
        try (Stream<Path> files = Stream.concat(Files.walk(SRC.resolve("java")), Files.walk(gen))) {
            sources = files.filter(p -> p.toString().endsWith(".java")).map(Path::toString).collect(Collectors.toList());
        }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) {
            fail("No Java compiler found. Run this with a JDK, not a JRE.");
        }
        List<String> javacArgs = new ArrayList<>(Arrays.asList("--release", "8", "-nowarn", "-Xlint:-options",
                "-classpath", androidJar.toString(), "-d", classes.toString()));
        javacArgs.addAll(sources);
        if (javac.run(null, null, null, javacArgs.toArray(new String[0])) != 0) {
            fail("Java compilation failed");
        }

        step("Converting to dex");
        if (d8 != null) {
            List<String> cmd = new ArrayList<>(Arrays.asList(d8, "--release", "--min-api", minSdk,
                    "--lib", androidJar.toString(), "--output", dexDir.toString()));
            try (Stream<Path> files = Files.walk(classes)) {
                files.filter(p -> p.toString().endsWith(".class")).forEach(p -> cmd.add(p.toString()));
            }
            run(cmd.toArray(new String[0]));
        } else {
            run(dx, "--dex", "--min-sdk-version=" + minSdk, "--output=" + dexDir.resolve("classes.dex"),
                    classes.toString());
        }

        step("Packaging");
        Path unaligned = OUT.resolve("unaligned.apk");
        addDex(baseApk, dexDir.resolve("classes.dex"), unaligned);
        Path aligned = OUT.resolve("aligned.apk");
        run(zipalign, "-f", "-p", "4", unaligned.toString(), aligned.toString());

        step("Signing");
        if (!Files.exists(KEYSTORE)) {
            fail("Signing key not found at " + KEYSTORE);
        }
        Path apk = ROOT.resolve("build/NageshSpotify.apk");
        run(apksigner, "sign", "--ks", KEYSTORE.toString(), "--ks-pass", "pass:" + KEY_PASSWORD,
                "--key-pass", "pass:" + KEY_PASSWORD, "--out", apk.toString(), aligned.toString());
        run(apksigner, "verify", apk.toString());
        Files.deleteIfExists(ROOT.resolve("build/NageshSpotify.apk.idsig"));

        System.out.printf("%nBuilt %s (%d KB): %s %s, minSdk %s, targetSdk %s%n", ROOT.relativize(apk),
                Files.size(apk) / 1024, applicationId, versionName, minSdk, targetSdk);
    }

    /** Copies {@code base} and adds {@code dex}, keeping each entry's compression (resources.arsc must stay stored). */
    private static void addDex(Path base, Path dex, Path out) throws IOException {
        try (ZipFile in = new ZipFile(base.toFile());
                ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out.toFile()))) {
            Enumeration<? extends ZipEntry> entries = in.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                ZipEntry copy = new ZipEntry(e.getName());
                copy.setMethod(e.getMethod());
                if (e.getMethod() == ZipEntry.STORED) {
                    copy.setSize(e.getSize());
                    copy.setCompressedSize(e.getSize());
                    copy.setCrc(e.getCrc());
                }
                zip.putNextEntry(copy);
                try (InputStream data = in.getInputStream(e)) {
                    data.transferTo(zip);
                }
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("classes.dex"));
            Files.copy(dex, zip);
            zip.closeEntry();
        }
    }

    private static String gradleValue(String gradle, String key) {
        Matcher m = Pattern.compile("^\\s*" + key + "\\s+\"?([^\"\\s]+)\"?", Pattern.MULTILINE).matcher(gradle);
        if (!m.find()) {
            fail("Couldn't read " + key + " from app/build.gradle");
        }
        return m.group(1);
    }

    private static Path androidJar(String[] args, String compileSdk) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--android-jar")) {
                Path jar = Paths.get(args[i + 1]);
                if (!Files.isRegularFile(jar)) {
                    fail("Not found: " + jar);
                }
                return jar;
            }
        }
        String sdk = androidHome();
        if (sdk != null) {
            Path jar = Paths.get(sdk, "platforms", "android-" + compileSdk, "android.jar");
            if (Files.isRegularFile(jar)) {
                return jar;
            }
        }
        fail("Couldn't find android.jar for API " + compileSdk + ". Install it with the Android SDK"
                + " (sdkmanager \"platforms;android-" + compileSdk + "\") or pass --android-jar PATH.");
        return null;
    }

    private static String androidHome() {
        String sdk = System.getenv("ANDROID_HOME");
        return sdk != null ? sdk : System.getenv("ANDROID_SDK_ROOT");
    }

    private static String tool(String name) {
        String path = optionalTool(name);
        if (path == null) {
            fail("Couldn't find " + name + ". Install the Android SDK build-tools and set ANDROID_HOME,"
                    + " or put " + name + " on your PATH.");
        }
        return path;
    }

    /** Looks for a tool in the newest Android SDK build-tools, then on the PATH. */
    private static String optionalTool(String... names) {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        List<Path> dirs = new ArrayList<>();
        String sdk = androidHome();
        if (sdk != null) {
            File[] versions = Paths.get(sdk, "build-tools").toFile().listFiles(File::isDirectory);
            if (versions != null) {
                Arrays.sort(versions, Comparator.comparing(File::getName).reversed());
                for (File v : versions) {
                    dirs.add(v.toPath());
                }
            }
        }
        for (String p : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
            if (!p.isEmpty()) {
                dirs.add(Paths.get(p));
            }
        }
        for (Path dir : dirs) {
            for (String name : names) {
                for (String ext : windows ? new String[] {".exe", ".bat", ""} : new String[] {""}) {
                    Path candidate = dir.resolve(name + ext);
                    if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                        return candidate.toString();
                    }
                }
            }
        }
        return null;
    }

    private static void run(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        String shown = output.lines().filter(l -> !l.startsWith("Picked up JAVA_TOOL_OPTIONS"))
                .collect(Collectors.joining(System.lineSeparator()));
        if (!shown.isBlank()) {
            System.out.println(shown);
        }
        if (code != 0) {
            fail(new File(cmd[0]).getName() + " failed (exit code " + code + ")");
        }
    }

    private static void step(String message) {
        System.out.println("==> " + message);
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                Files.delete(p);
            }
        }
    }

    private static void fail(String message) {
        System.err.println("error: " + message);
        System.exit(1);
    }
}
