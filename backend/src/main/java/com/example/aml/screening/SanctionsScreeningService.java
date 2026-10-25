package com.example.aml.screening;

import com.example.aml.api.dto.ImportResultDto;
import com.example.aml.api.dto.SanctionsEntryDto;
import com.example.aml.api.dto.ScreeningMatchDto;
import com.example.aml.api.dto.ScreeningResultDto;
import com.example.aml.common.NotFoundException;
import com.example.aml.config.AmlProperties;
import com.example.aml.domain.SanctionsEntry;
import com.example.aml.repository.SanctionsEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Screens names against the sanctions list. The list is held in memory and refreshed
 * whenever it changes, so screening a transaction never hits the database.
 */
@Service
public class SanctionsScreeningService {

    private static final int MAX_MATCHES = 10;
    /** Matches below this are not worth showing an analyst at all. */
    private static final double DISPLAY_FLOOR = 0.75;

    private final SanctionsEntryRepository repository;
    private final AmlProperties properties;
    private volatile List<SanctionsEntry> cache;

    public SanctionsScreeningService(SanctionsEntryRepository repository, AmlProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public double threshold() {
        return properties.rules().sanctionsMatchThreshold();
    }

    /** All candidate matches at or above {@code minScore}, best first. */
    public List<ScreeningMatchDto> screen(String name, double minScore) {
        if (name == null || NameMatcher.normalize(name).length() < 3) {
            return List.of();
        }
        List<ScreeningMatchDto> matches = new ArrayList<>();
        for (SanctionsEntry entry : entries()) {
            double best = 0;
            String bestName = entry.getName();
            for (String candidate : entry.allNames()) {
                double score = NameMatcher.similarity(name, candidate);
                if (score > best) {
                    best = score;
                    bestName = candidate;
                }
            }
            if (best >= minScore) {
                matches.add(new ScreeningMatchDto(entry.getId(), entry.getName(), bestName,
                        Math.round(best * 1000) / 1000.0, entry.getEntityType(), entry.getCountry(),
                        entry.getListSource(), entry.getProgram()));
            }
        }
        matches.sort(Comparator.comparingDouble(ScreeningMatchDto::score).reversed());
        return matches.size() > MAX_MATCHES ? matches.subList(0, MAX_MATCHES) : matches;
    }

    /** Ad-hoc screening for the UI: shows near-misses too, flags whether anything crossed the threshold. */
    public ScreeningResultDto screenForDisplay(String name) {
        List<ScreeningMatchDto> matches = screen(name, DISPLAY_FLOOR);
        boolean hit = !matches.isEmpty() && matches.get(0).score() >= threshold();
        return new ScreeningResultDto(name, threshold(), hit, matches);
    }

    /** Best match at or above the configured threshold, if any. */
    public ScreeningMatchDto bestHit(String name) {
        List<ScreeningMatchDto> matches = screen(name, threshold());
        return matches.isEmpty() ? null : matches.get(0);
    }

    // ---------------------------------------------------------------- list maintenance

    @Transactional(readOnly = true)
    public List<SanctionsEntryDto> list() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(SanctionsEntry::getName, String.CASE_INSENSITIVE_ORDER))
                .map(SanctionsScreeningService::toDto)
                .toList();
    }

    @Transactional
    public SanctionsEntryDto create(SanctionsEntryDto dto) {
        SanctionsEntry entry = new SanctionsEntry();
        apply(entry, dto);
        SanctionsEntryDto saved = toDto(repository.save(entry));
        invalidate();
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Sanctions entry", id);
        }
        repository.deleteById(id);
        invalidate();
    }

    /**
     * Imports a CSV with header {@code name,aliases,entityType,country,listSource,program}.
     * Aliases inside one cell are separated by {@code |}. Fields may be double-quoted.
     */
    @Transactional
    public ImportResultDto importCsv(InputStream in) throws IOException {
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null || !header.toLowerCase(Locale.ROOT).startsWith("name")) {
                throw new IllegalArgumentException(
                        "CSV must start with header: name,aliases,entityType,country,listSource,program");
            }
            String line;
            int lineNo = 1;
            List<SanctionsEntry> batch = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> cols = parseCsvLine(line);
                String name = col(cols, 0);
                if (name == null) {
                    skipped++;
                    errors.add("Line " + lineNo + ": missing name");
                    continue;
                }
                SanctionsEntry e = new SanctionsEntry();
                apply(e, new SanctionsEntryDto(null, name, col(cols, 1), col(cols, 2), col(cols, 3),
                        col(cols, 4), col(cols, 5)));
                batch.add(e);
                imported++;
            }
            repository.saveAll(batch);
        }
        invalidate();
        return new ImportResultDto(imported, skipped, errors.size() > 20 ? errors.subList(0, 20) : errors);
    }

    static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static String col(List<String> cols, int i) {
        if (i >= cols.size()) {
            return null;
        }
        String v = cols.get(i).trim();
        return v.isEmpty() ? null : v;
    }

    private static void apply(SanctionsEntry e, SanctionsEntryDto dto) {
        e.setName(dto.name().trim());
        e.setAliases(dto.aliases());
        e.setEntityType(dto.entityType() == null ? "INDIVIDUAL" : dto.entityType().toUpperCase(Locale.ROOT));
        e.setCountry(dto.country() == null ? null : dto.country().toUpperCase(Locale.ROOT));
        e.setListSource(dto.listSource() == null ? "INTERNAL" : dto.listSource());
        e.setProgram(dto.program());
    }

    static SanctionsEntryDto toDto(SanctionsEntry e) {
        return new SanctionsEntryDto(e.getId(), e.getName(), e.getAliases(), e.getEntityType(), e.getCountry(),
                e.getListSource(), e.getProgram());
    }

    private List<SanctionsEntry> entries() {
        List<SanctionsEntry> local = cache;
        if (local == null) {
            synchronized (this) {
                local = cache;
                if (local == null) {
                    local = List.copyOf(repository.findAll());
                    cache = local;
                }
            }
        }
        return local;
    }

    public void invalidate() {
        cache = null;
    }
}
