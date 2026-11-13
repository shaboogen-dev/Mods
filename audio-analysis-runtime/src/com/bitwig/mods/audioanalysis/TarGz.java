package com.bitwig.mods.audioanalysis;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPInputStream;

/**
 * Unpacks a .tar.gz (ustar, GNU long names, pax paths) into a folder, for the Python build.
 * Keeps the executable bits and symbolic links on macOS and Linux. Refuses any entry or link that
 * would land outside the folder. No Bitwig names, no external tools.
 */
public final class TarGz {
    private TarGz() { }

    private record Link(Path at, String target, boolean symbolic) { }

    public static void extract(Path archive, Path dest, BooleanSupplier cancelled) throws IOException {
        Path root = dest.toAbsolutePath().normalize();
        Files.createDirectories(root);
        boolean posix = Files.getFileAttributeView(root, PosixFileAttributeView.class) != null;
        List<Link> links = new ArrayList<>();
        try (InputStream in = new BufferedInputStream(new GZIPInputStream(Files.newInputStream(archive), 1 << 16), 1 << 16)) {
            byte[] header = new byte[512];
            String longName = null, longLink = null, paxPath = null, paxLink = null;
            while (true) {
                if (cancelled != null && cancelled.getAsBoolean()) throw new IOException("cancelled");
                if (!readFully(in, header)) break;
                if (isZero(header)) break;
                long size = number(header, 124, 12);
                char type = (char) header[156];
                if (type == 'L' || type == 'K' || type == 'x' || type == 'g') {
                    byte[] data = readData(in, size);
                    if (type == 'L') longName = cString(data, 0, data.length);
                    else if (type == 'K') longLink = cString(data, 0, data.length);
                    else if (type == 'x') {
                        for (String[] kv : pax(data)) {
                            if (kv[0].equals("path")) paxPath = kv[1];
                            else if (kv[0].equals("linkpath")) paxLink = kv[1];
                        }
                    }
                    continue;
                }
                String name = paxPath != null ? paxPath : longName != null ? longName : ustarName(header);
                String link = paxLink != null ? paxLink : longLink != null ? longLink : cString(header, 157, 100);
                longName = longLink = paxPath = paxLink = null;
                int mode = (int) number(header, 100, 8);
                Path out = inside(root, name);
                switch (type) {
                    case '5' -> Files.createDirectories(out);
                    case '2', '1' -> {
                        skip(in, padded(size));
                        if (type == '2') {
                            if (link.startsWith("/") || link.startsWith("\\")) throw new IOException("absolute link in archive: " + name);
                            inside(root, root.relativize(out.getParent()).resolve(link).toString());
                        } else {
                            inside(root, link);
                        }
                        links.add(new Link(out, link, type == '2'));
                    }
                    case '0', '\0', '7' -> {
                        Files.createDirectories(out.getParent());
                        try (OutputStream o = Files.newOutputStream(out)) {
                            copy(in, o, size);
                        }
                        skip(in, padded(size) - size);
                        if (posix) Files.setPosixFilePermissions(out, permissions(mode));
                    }
                    default -> skip(in, padded(size)); // devices, fifos: not in a Python build
                }
            }
        }
        // links last, so their targets exist (hard links copy; symbolic links fall back to a copy
        // where the file system can't make them)
        for (Link l : links) {
            Files.createDirectories(l.at.getParent());
            Files.deleteIfExists(l.at);
            Path target = l.symbolic ? l.at.getParent().resolve(l.target).normalize() : inside(root, l.target);
            if (l.symbolic && posix) {
                Files.createSymbolicLink(l.at, Path.of(l.target));
            } else if (Files.exists(target)) {
                if (Files.isDirectory(target)) continue; // a directory link: not in a Python build
                Files.copy(target, l.at, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

    /** {@code root/name}, refused when it isn't inside {@code root}. */
    static Path inside(Path root, String name) throws IOException {
        if (name == null || name.isEmpty()) throw new IOException("empty name in archive");
        String n = name.replace('\\', '/');
        if (n.startsWith("/") || n.matches("^[A-Za-z]:.*")) throw new IOException("absolute path in archive: " + name);
        Path p = root.resolve(n).normalize();
        if (!p.startsWith(root)) throw new IOException("path outside the folder in archive: " + name);
        return p;
    }

    static Set<PosixFilePermission> permissions(int mode) {
        Set<PosixFilePermission> s = EnumSet.noneOf(PosixFilePermission.class);
        PosixFilePermission[] all = {
            PosixFilePermission.OTHERS_EXECUTE, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_READ,
            PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_READ,
            PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_READ};
        for (int i = 0; i < all.length; i++) if ((mode & (1 << i)) != 0) s.add(all[i]);
        s.add(PosixFilePermission.OWNER_READ);
        s.add(PosixFilePermission.OWNER_WRITE); // so Remove can delete it
        return s;
    }

    private static String ustarName(byte[] h) {
        String name = cString(h, 0, 100);
        String magic = new String(h, 257, 5, StandardCharsets.US_ASCII);
        if (magic.equals("ustar")) {
            String prefix = cString(h, 345, 155);
            if (!prefix.isEmpty()) return prefix + "/" + name;
        }
        return name;
    }

    private static List<String[]> pax(byte[] data) {
        List<String[]> out = new ArrayList<>();
        int i = 0;
        while (i < data.length) {
            int space = i;
            while (space < data.length && data[space] != ' ') space++;
            if (space >= data.length) break;
            int len = Integer.parseInt(new String(data, i, space - i, StandardCharsets.US_ASCII).trim());
            if (len <= 0 || i + len > data.length) break;
            String record = new String(data, space + 1, i + len - space - 2, StandardCharsets.UTF_8);
            int eq = record.indexOf('=');
            if (eq > 0) out.add(new String[] {record.substring(0, eq), record.substring(eq + 1)});
            i += len;
        }
        return out;
    }

    static long number(byte[] h, int off, int len) {
        if ((h[off] & 0x80) != 0) { // GNU base-256
            long v = h[off] & 0x7f;
            for (int i = 1; i < len; i++) v = (v << 8) | (h[off + i] & 0xff);
            return v;
        }
        String s = cString(h, off, len).trim();
        return s.isEmpty() ? 0 : Long.parseLong(s, 8);
    }

    private static String cString(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) end++;
        return new String(b, off, end - off, StandardCharsets.UTF_8);
    }

    private static boolean isZero(byte[] b) {
        for (byte x : b) if (x != 0) return false;
        return true;
    }

    private static long padded(long size) {
        return (size + 511) / 512 * 512;
    }

    private static boolean readFully(InputStream in, byte[] b) throws IOException {
        int off = 0;
        while (off < b.length) {
            int n = in.read(b, off, b.length - off);
            if (n < 0) {
                if (off == 0) return false;
                throw new EOFException("truncated archive");
            }
            off += n;
        }
        return true;
    }

    private static byte[] readData(InputStream in, long size) throws IOException {
        if (size > 1 << 20) throw new IOException("archive header too large");
        byte[] data = new byte[(int) size];
        if (size > 0 && !readFully(in, data)) throw new EOFException("truncated archive");
        skip(in, padded(size) - size);
        return data;
    }

    private static void copy(InputStream in, OutputStream out, long size) throws IOException {
        byte[] buffer = new byte[1 << 16];
        long left = size;
        while (left > 0) {
            int n = in.read(buffer, 0, (int) Math.min(buffer.length, left));
            if (n < 0) throw new EOFException("truncated archive");
            out.write(buffer, 0, n);
            left -= n;
        }
    }

    private static void skip(InputStream in, long n) throws IOException {
        while (n > 0) {
            long s = in.skip(n);
            if (s <= 0) {
                if (in.read() < 0) throw new EOFException("truncated archive");
                s = 1;
            }
            n -= s;
        }
    }
}
