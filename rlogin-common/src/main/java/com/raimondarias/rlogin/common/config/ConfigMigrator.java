package com.raimondarias.rlogin.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds settings introduced by a new rLogin version to a config.yml that was
 * written by an older one.
 *
 * <p>Without this, upgrading is quietly lossy: the file is only ever created
 * when missing, so a server that already has one never sees a new key. The
 * setting still works — every getter has a default — but it is invisible,
 * undocumented, and impossible to change without knowing it exists. That is
 * how {@code uuid-type} ended up missing from a running server during
 * development, and it is the kind of thing that turns into "your plugin
 * ignores my config" reports.</p>
 *
 * <p>Works on the text, not on a parsed tree, because a parsed tree loses
 * every comment — and the comments are most of what makes rLogin's config
 * worth reading. The user's file is never rewritten or reformatted: new keys
 * are inserted into the section they belong to, carrying the explanation
 * that sits above them in the bundled default, and nothing else is touched.</p>
 */
public final class ConfigMigrator {

    private static final Pattern ENTRY = Pattern.compile("^(\\s*)([A-Za-z0-9_-]+):(.*)$");

    /** A key from the bundled default, together with the comment lines that introduce it. */
    private record Addition(String path, int indent, List<String> lines) {
    }

    private ConfigMigrator() {
    }

    /**
     * @return the settings that were added, in the order they appear in the
     *         bundled default. Empty when the file was already up to date.
     */
    public static List<String> migrate(Path file, String bundledResource) throws IOException {
        if (Files.notExists(file)) {
            return List.of(); // Nothing to migrate; loadOrCreate writes a complete file.
        }
        List<String> bundled = readBundled(bundledResource);
        if (bundled.isEmpty()) {
            return List.of();
        }
        List<String> current = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        Set<String> present = pathsIn(current);

        List<Addition> missing = new ArrayList<>();
        for (Addition candidate : additionsIn(bundled)) {
            if (!present.contains(candidate.path())) {
                missing.add(candidate);
            }
        }
        if (missing.isEmpty()) {
            return List.of();
        }
        for (Addition addition : missing) {
            insert(current, addition);
        }
        Files.write(file, current, StandardCharsets.UTF_8);
        return missing.stream().map(Addition::path).toList();
    }

    /**
     * Puts one setting where it belongs: at the end of the section that owns
     * it, so {@code premium.uuid-type} lands under {@code premium:} and not
     * at the bottom of the file, where YAML would read it as a different
     * setting entirely.
     */
    private static void insert(List<String> lines, Addition addition) {
        int lastDot = addition.path().lastIndexOf('.');
        if (lastDot < 0) {
            appendBlock(lines, addition.lines());
            return;
        }
        String parent = addition.path().substring(0, lastDot);
        int parentEnd = endOfSection(lines, parent);
        if (parentEnd < 0) {
            // The section this setting lives under doesn't exist here at all. It has to be
            // written before the setting is: an indented line with no header above it is not
            // a nested setting, it's a syntax error.
            createSection(lines, parent);
            parentEnd = endOfSection(lines, parent);
        }
        lines.addAll(parentEnd, addition.lines());
    }

    /** Writes {@code a:} / {@code  b:} headers for every level of {@code path} that is missing. */
    private static void createSection(List<String> lines, String path) {
        String[] segments = path.split("\\.");
        StringBuilder walked = new StringBuilder();
        for (int depth = 0; depth < segments.length; depth++) {
            if (depth > 0) {
                walked.append('.');
            }
            walked.append(segments[depth]);
            if (endOfSection(lines, walked.toString()) >= 0) {
                continue;
            }
            String header = "  ".repeat(depth) + segments[depth] + ":";
            if (depth == 0) {
                appendBlock(lines, List.of(header));
            } else {
                String parent = walked.substring(0, walked.lastIndexOf("."));
                lines.add(endOfSection(lines, parent), header);
            }
        }
    }

    private static void appendBlock(List<String> lines, List<String> block) {
        if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
            lines.add("");
        }
        lines.addAll(block);
    }

    /**
     * Index just past the last entry of {@code section}, or -1 when that
     * section doesn't exist in the user's file at all.
     */
    private static int endOfSection(List<String> lines, String parent) {
        int parentIndent = (parent.split("\\.").length - 1) * 2;
        int start = -1;
        List<String> stack = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = ENTRY.matcher(lines.get(i));
            if (!m.matches()) {
                continue;
            }
            int depth = m.group(1).length() / 2;
            while (stack.size() > depth) {
                stack.remove(stack.size() - 1);
            }
            stack.add(m.group(2));
            if (String.join(".", stack).equals(parent)) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return -1;
        }
        // Walk to the last line that is still indented deeper than the parent.
        int end = start + 1;
        int lastContent = start + 1;
        while (end < lines.size()) {
            String line = lines.get(end);
            if (!line.isBlank() && indentOf(line) <= parentIndent) {
                break;
            }
            if (!line.isBlank()) {
                lastContent = end + 1;
            }
            end++;
        }
        return lastContent;
    }

    /** Every setting in the bundled default, with the comment block that introduces it. */
    private static List<Addition> additionsIn(List<String> bundled) {
        List<Addition> additions = new ArrayList<>();
        List<String> stack = new ArrayList<>();
        List<String> pendingComments = new ArrayList<>();

        for (String line : bundled) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                pendingComments.add(line);
                continue;
            }
            if (trimmed.isEmpty()) {
                pendingComments.clear(); // A blank line ends a comment block.
                continue;
            }
            Matcher m = ENTRY.matcher(line);
            if (!m.matches()) {
                // A list item belongs to the entry above it.
                if (!additions.isEmpty() && trimmed.startsWith("-")) {
                    additions.get(additions.size() - 1).lines().add(line);
                }
                pendingComments.clear();
                continue;
            }
            int depth = m.group(1).length() / 2;
            while (stack.size() > depth) {
                stack.remove(stack.size() - 1);
            }
            stack.add(m.group(2));

            List<String> block = new ArrayList<>(pendingComments);
            block.add(line);
            additions.add(new Addition(String.join(".", stack), m.group(1).length(), block));
            pendingComments.clear();
        }
        // Only settings with a value can be added on their own; a bare section header
        // arrives with the first of its children.
        List<Addition> result = new ArrayList<>();
        Map<String, Addition> byPath = new LinkedHashMap<>();
        for (Addition a : additions) {
            byPath.put(a.path(), a);
        }
        for (Addition a : additions) {
            String last = a.lines().get(a.lines().size() - 1);
            Matcher m = ENTRY.matcher(last);
            boolean isSection = m.matches() && m.group(3).trim().isEmpty()
                    && byPath.keySet().stream().anyMatch(k -> k.startsWith(a.path() + "."));
            if (!isSection) {
                result.add(a);
            }
        }
        return result;
    }

    private static Set<String> pathsIn(List<String> lines) {
        Set<String> paths = new LinkedHashSet<>();
        List<String> stack = new ArrayList<>();
        for (String line : lines) {
            Matcher m = ENTRY.matcher(line);
            if (!m.matches() || line.trim().startsWith("#")) {
                continue;
            }
            int depth = m.group(1).length() / 2;
            while (stack.size() > depth) {
                stack.remove(stack.size() - 1);
            }
            stack.add(m.group(2));
            paths.add(String.join(".", stack));
        }
        return paths;
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static List<String> readBundled(String resource) throws IOException {
        try (InputStream in = ConfigMigrator.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return List.of();
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }
}
