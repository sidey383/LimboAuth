package net.elytrium.limboauth.data;

import lombok.Getter;
import net.elytrium.limboauth.dependencies.BaseLibrary;
import net.elytrium.limboauth.dependencies.hikary.HikariRegisteredPlayerRepository;
import net.elytrium.limboauth.repository.RegisteredPlayerRepository;

import java.nio.file.Path;

@Getter
public enum DataProvider {
    MYSQL(BaseLibrary.MYSQL),
    MARIADB(BaseLibrary.MARIADB),
    POSTGRESQL(BaseLibrary.POSTGRESQL);

    private final BaseLibrary baseLibrary;

    DataProvider(BaseLibrary baseLibrary) {
        this.baseLibrary = baseLibrary;
    }

    public RegisteredPlayerRepository createRegisteredPlayerRepository(
            Path path,
            String host,
            String database,
            String user,
            String password
    ) throws Exception {
        HikariRegisteredPlayerRepository repo = new HikariRegisteredPlayerRepository(
                this,
                host,
                database,
                user,
                password
        );
        repo.updateSchema(this);
        return repo;
    }

}
