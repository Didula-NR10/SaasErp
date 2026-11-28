import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.jar.Manifest;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Standalone CI helper, run by the existing JDK 21; no application code or Maven involved. */
public class PrepareBootScan {
    private static final String LIB = "BOOT-INF/lib/";
    private static final long MAX_ENTRY = 128L * 1024 * 1024;
    private static final long MAX_TOTAL = 1024L * 1024 * 1024;

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected target directory, new scan directory, new inventory file");
        Path target = Path.of(args[0]), scan = Path.of(args[1]), inventory = Path.of(args[2]);
        List<Path> jars;
        try (var files = Files.list(target)) {
            jars = files.filter(p -> p.getFileName().toString().endsWith(".jar")).toList();
        }
        require(jars.size() == 1, "Expected exactly one packaged application JAR");
        Path jar = jars.getFirst();
        require(Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS), "Application JAR must be a regular file");
        require(Files.size(jar) <= MAX_TOTAL, "Application JAR exceeds CI size limit");
        try (var zip = new ZipFile(jar.toFile())) {
            var entries = new HashMap<String, ZipEntry>();
            var libraries = new ArrayList<ZipEntry>();
            long total = 0;
            for (var iterator = zip.entries(); iterator.hasMoreElements();) {
                var entry = iterator.nextElement();
                String name = entry.getName();
                String normalized = entry.isDirectory() ? name.substring(0, name.length() - 1) : name;
                require(!normalized.isEmpty() && !normalized.startsWith("/")
                    && !normalized.contains("\\") && !normalized.contains(":")
                    && normalized.chars().noneMatch(c -> c < 32 || c == 127), "Unsafe archive path: " + name);
                for (String part : normalized.split("/", -1)) {
                    require(!part.isEmpty() && !part.equals(".") && !part.equals(".."), "Unsafe archive path: " + name);
                }
                require(entries.putIfAbsent(normalized, entry) == null, "Duplicate archive path: " + name);
                require(entries.size() <= 100_000, "Too many archive entries");
                require(entry.getSize() >= 0 && entry.getSize() <= MAX_ENTRY, "Invalid archive entry size: " + name);
                total += entry.getSize();
                require(total <= MAX_TOTAL, "Archive exceeds CI expanded size limit");
                if (name.startsWith(LIB) && !name.equals(LIB)) {
                    require(!entry.isDirectory() && name.substring(LIB.length()).matches("[A-Za-z0-9][A-Za-z0-9._+\\-]*\\.jar"),
                        "Unexpected embedded library entry: " + name);
                    require(entry.getSize() > 0, "Empty embedded dependency: " + name);
                    libraries.add(entry);
                }
            }
            // Reject a file posing as an ancestor directory, even if it would not be extracted.
            for (String name : entries.keySet()) {
                for (int slash = name.indexOf('/'); slash >= 0; slash = name.indexOf('/', slash + 1)) {
                    ZipEntry parent = entries.get(name.substring(0, slash));
                    require(parent == null || parent.isDirectory(), "Archive file/directory collision: " + name);
                }
            }
            require(!libraries.isEmpty(), "Expected nonempty BOOT-INF/lib dependency inventory");
            ZipEntry manifestEntry = entries.get("META-INF/MANIFEST.MF");
            require(manifestEntry != null && manifestEntry.getSize() <= 65536, "Missing or oversized application manifest");
            Manifest manifest;
            try (var input = zip.getInputStream(manifestEntry)) { manifest = new Manifest(input); }
            var attributes = manifest.getMainAttributes();
            String launcher = attributes.getValue("Main-Class"), start = attributes.getValue("Start-Class");
            require("org.springframework.boot.loader.launch.JarLauncher".equals(launcher)
                || "org.springframework.boot.loader.JarLauncher".equals(launcher), "Expected executable Spring Boot JarLauncher");
            require(start != null && start.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+"),
                "Missing or invalid application Start-Class");
            requireClass(zip, entries.get(launcher.replace('.', '/') + ".class"));
            requireClass(zip, entries.get("BOOT-INF/classes/" + start.replace('.', '/') + ".class"));

            // A fresh directory and CREATE_NEW regular files: never restore archive permissions,
            // links or other filesystem metadata; never use an archive extraction command.
            Files.createDirectory(scan);
            Files.createDirectories(scan.resolve(LIB));
            var expected = new ArrayList<String>();
            libraries.sort(java.util.Comparator.comparing(ZipEntry::getName));
            for (var entry : libraries) {
                Path destination = scan.resolve(entry.getName());
                CRC32 crc = new CRC32();
                long count = 0;
                try (var input = zip.getInputStream(entry);
                     var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    byte[] buffer = new byte[65536];
                    for (int n; (n = input.read(buffer)) != -1;) {
                        count += n;
                        require(count <= entry.getSize(), "Expanded dependency exceeds declared size");
                        crc.update(buffer, 0, n);
                        output.write(buffer, 0, n);
                    }
                }
                require(count == entry.getSize() && crc.getValue() == entry.getCrc(), "Dependency size/CRC mismatch: " + entry.getName());
                try (var dependency = new ZipFile(destination.toFile())) {
                    require(dependency.size() > 0, "Expected nonempty dependency JAR: " + entry.getName());
                }
                expected.add(entry.getName());
            }
            Files.write(inventory, expected, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            System.out.println("Prepared " + expected.size() + " embedded dependency JARs from " + jar);
        }
    }

    private static void requireClass(ZipFile zip, ZipEntry entry) throws IOException {
        require(entry != null && !entry.isDirectory() && entry.getSize() >= 8, "Missing compiled launcher or application class");
        try (InputStream input = zip.getInputStream(entry)) {
            byte[] magic = input.readNBytes(4);
            require(java.util.Arrays.equals(magic, new byte[]{(byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe}),
                "Invalid compiled class header");
        }
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
}
