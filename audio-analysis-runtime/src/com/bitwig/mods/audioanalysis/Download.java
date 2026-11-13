package com.bitwig.mods.audioanalysis;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/**
 * Downloads one file over HTTPS and checks its SHA-256 before it is kept: written to
 * {@code <target>.part}, hashed while it arrives, and moved to {@code target} only when the hash
 * (and the size, when known) match. Cancellable between reads; a stalled connection times out.
 * No Bitwig names.
 */
public final class Download {
    private Download() { }

    /** Thrown when the hash or size doesn't match; the partial file is deleted. */
    public static final class Mismatch extends IOException {
        public Mismatch(String message) { super(message); }
    }

    public static void fetch(String url, Path target, String sha256, long expectedSize,
                             BooleanSupplier cancelled, LongConsumer progress) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("only https downloads: " + url);
        Path part = target.resolveSibling(target.getFileName() + ".part");
        Files.createDirectories(target.toAbsolutePath().getParent());
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setInstanceFollowRedirects(true); // GitHub answers with a redirect to its file host (https)
        c.setConnectTimeout(30_000);
        c.setReadTimeout(60_000);
        c.setRequestProperty("User-Agent", "BML-Audio-Analysis");
        try {
            int status = c.getResponseCode();
            if (status != 200) throw new IOException("download failed: HTTP " + status + " for " + url);
            MessageDigest digest = sha256();
            long total = 0;
            try (InputStream in = c.getInputStream(); OutputStream out = Files.newOutputStream(part)) {
                byte[] buffer = new byte[1 << 16];
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    if (cancelled != null && cancelled.getAsBoolean()) throw new IOException("cancelled");
                    out.write(buffer, 0, n);
                    digest.update(buffer, 0, n);
                    total += n;
                    if (progress != null) progress.accept(total);
                }
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (expectedSize > 0 && total != expectedSize)
                throw new Mismatch("the download has " + total + " bytes, expected " + expectedSize);
            if (!actual.equals(sha256.toLowerCase(Locale.ROOT)))
                throw new Mismatch("the download's SHA-256 is " + actual + ", expected " + sha256);
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            c.disconnect();
            Files.deleteIfExists(part);
        }
    }

    /** The SHA-256 of a file, as lower-case hex. */
    public static String sha256(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[1 << 16];
            int n;
            while ((n = in.read(buffer)) >= 0) digest.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
