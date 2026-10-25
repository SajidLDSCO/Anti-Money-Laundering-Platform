package com.example.aml.screening;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fuzzy name comparison for sanctions screening.
 * <p>
 * Names are normalised (accents stripped, punctuation removed, honorifics and legal-form
 * suffixes dropped) and split into tokens. Each token of the shorter name is aligned with
 * its closest Jaro-Winkler match in the longer name, so word order does not matter,
 * typos and transliteration variants still score high, extra middle names cost only a
 * little, and a shared first name cannot hide a different surname.
 * <p>
 * Pure Java with no dependencies, so it can be unit-tested and swapped for a commercial matcher later.
 */
public final class NameMatcher {

    private static final Set<String> NOISE_TOKENS = Set.of(
            "mr", "mrs", "ms", "dr", "sir", "hon", "the", "of", "and",
            "llc", "ltd", "limited", "inc", "incorporated", "corp", "corporation", "co", "company",
            "plc", "gmbh", "sa", "ag", "fze", "fzco", "jsc", "pjsc", "ooo", "bv", "pte", "pvt");

    private NameMatcher() {
    }

    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String s = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace(".", "")              // "L.L.C." -> "llc", "I. Petrenko" -> "i petrenko"
                .replaceAll("[^a-z0-9 ]", " ");
        return Arrays.stream(s.trim().split("\\s+"))
                .filter(t -> !t.isEmpty() && !NOISE_TOKENS.contains(t))
                .collect(Collectors.joining(" "));
    }

    /** Similarity in [0, 1]. */
    public static double similarity(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) {
            return 0.0;
        }
        if (na.equals(nb)) {
            return 1.0;
        }
        String[] ta = na.split(" ");
        String[] tb = nb.split(" ");
        if (ta.length == 1 && tb.length == 1) {
            return jaroWinkler(na, nb);
        }
        return tokenAligned(ta, tb);
    }

    /**
     * Every token of the shorter name is aligned with its closest token in the longer name.
     * Token scores are squared before averaging (weighted by token length) so one weak
     * token, e.g. a different surname, drags the result down instead of being averaged
     * away by a shared first name. Each extra unmatched token in the longer name costs a
     * little, because added words add doubt.
     */
    private static double tokenAligned(String[] ta, String[] tb) {
        String[] shorter = ta.length <= tb.length ? ta : tb;
        String[] longer = ta.length <= tb.length ? tb : ta;
        boolean[] used = new boolean[longer.length];
        double weighted = 0;
        double weights = 0;
        for (String s : shorter) {
            double best = 0;
            int bestIdx = -1;
            for (int i = 0; i < longer.length; i++) {
                if (used[i]) {
                    continue;
                }
                double sim = jaroWinkler(s, longer[i]);
                if (sim > best) {
                    best = sim;
                    bestIdx = i;
                }
            }
            if (bestIdx >= 0) {
                used[bestIdx] = true;
            }
            double tokenScore = best < 0.75 ? 0.0 : best * best;
            weighted += tokenScore * s.length();
            weights += s.length();
        }
        int extra = longer.length - shorter.length;
        return (weighted / weights) * Math.max(0.0, 1.0 - 0.04 * extra);
    }

    public static double jaroWinkler(String s1, String s2) {
        double jaro = jaro(s1, s2);
        int prefix = 0;
        for (int i = 0; i < Math.min(4, Math.min(s1.length(), s2.length())); i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                prefix++;
            } else {
                break;
            }
        }
        return jaro + prefix * 0.1 * (1 - jaro);
    }

    static double jaro(String s1, String s2) {
        if (s1.equals(s2)) {
            return 1.0;
        }
        int len1 = s1.length();
        int len2 = s2.length();
        if (len1 == 0 || len2 == 0) {
            return 0.0;
        }
        int matchDistance = Math.max(0, Math.max(len1, len2) / 2 - 1);
        boolean[] m1 = new boolean[len1];
        boolean[] m2 = new boolean[len2];
        int matches = 0;
        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchDistance);
            int end = Math.min(i + matchDistance + 1, len2);
            for (int j = start; j < end; j++) {
                if (!m2[j] && s1.charAt(i) == s2.charAt(j)) {
                    m1[i] = true;
                    m2[j] = true;
                    matches++;
                    break;
                }
            }
        }
        if (matches == 0) {
            return 0.0;
        }
        int transpositions = 0;
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (m1[i]) {
                while (!m2[k]) {
                    k++;
                }
                if (s1.charAt(i) != s2.charAt(k)) {
                    transpositions++;
                }
                k++;
            }
        }
        double m = matches;
        return (m / len1 + m / len2 + (m - transpositions / 2.0) / m) / 3.0;
    }
}
