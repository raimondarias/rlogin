package com.raimondarias.rlogin.common.migrate;

import com.raimondarias.rlogin.api.importer.Importer;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ImporterRegistry {

    private final Map<String, Importer> importers = new LinkedHashMap<>();

    public ImporterRegistry() {
        register(new AuthMeImporter());
        register(new NLoginImporter());
        register(new JPremiumImporter());
    }

    public void register(Importer importer) {
        importers.put(importer.id().toLowerCase(Locale.ROOT), importer);
    }

    public Optional<Importer> get(String id) {
        return Optional.ofNullable(importers.get(id.toLowerCase(Locale.ROOT)));
    }

    public Map<String, Importer> all() {
        return importers;
    }
}
