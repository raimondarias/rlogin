package com.raimondarias.rlogin.common.db;

import com.raimondarias.rlogin.api.db.Storage;
import com.raimondarias.rlogin.common.config.RLoginConfig;

import java.nio.file.Path;

public final class StorageFactory {

    private StorageFactory() {
    }

    public static Storage create(RLoginConfig config, Path dataFolder) {
        if ("mysql".equalsIgnoreCase(config.databaseType())) {
            return new MysqlStorage(config);
        }
        return new SqliteStorage(dataFolder.resolve(config.sqliteFile()));
    }
}
