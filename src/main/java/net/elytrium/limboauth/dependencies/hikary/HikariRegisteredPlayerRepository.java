package net.elytrium.limboauth.dependencies.hikary;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.elytrium.limboauth.data.DataProvider;
import net.elytrium.limboauth.dependencies.IsolatedClassLoader;
import net.elytrium.limboauth.dependencies.IsolatedDriver;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.repository.RegisteredPlayerRepository;
import net.elytrium.limboauth.repository.exception.DataAccessException;

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.*;
import java.util.*;

public class HikariRegisteredPlayerRepository implements RegisteredPlayerRepository {

    private final HikariDataSource dataSource;

    public HikariRegisteredPlayerRepository(
            DataProvider dataProvider,
            String hostname,
            String database,
            String user,
            String password
    ) throws MalformedURLException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException, SQLException {
        String driverClassName = switch (dataProvider) {
            case MYSQL -> "com.mysql.cj.jdbc.NonRegisteringDriver";
            case MARIADB -> "org.mariadb.jdbc.Driver";
            case POSTGRESQL -> "org.postgresql.Driver";
        };
        String databaseUrl = switch (dataProvider) {
            case MYSQL -> "jdbc:mysql://%s/%s".formatted(hostname, database);
            case POSTGRESQL -> "jdbc:postgresql://%s/%s".formatted(hostname, database);
            case MARIADB -> "jdbc:mariadb://%s/%s".formatted(hostname, database);
        };

        IsolatedDriver driver = new IsolatedDriver("jdbc:limboauth_" + dataProvider.name().toLowerCase(Locale.ROOT) + ":");
        IsolatedClassLoader classLoader = new IsolatedClassLoader(new URL[] {dataProvider.getBaseLibrary().getClassLoaderURL()});
        Class<?> driverClass = classLoader.loadClass(driverClassName);

        driver.setOriginal((Driver) driverClass.getConstructor().newInstance());
        DriverManager.registerDriver(driver);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(driver.getInitializer() + databaseUrl);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(2);
        dataSource = new HikariDataSource(config);
    }

    public void updateSchema(DataProvider dataProvider) throws SQLException {
        try (Connection con = getConnection()) {
            PreparedStatement createStatement = con.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS AUTH (
                      NICKNAME varchar(255),
                      LOWERCASENICKNAME varchar(255) PRIMARY KEY,
                      HASH varchar(255),
                      IP varchar(255),
                      LOGINIP varchar(255),
                      TOTPTOKEN varchar(255),
                      REGDATE bigint,
                      LOGINDATE bigint,
                      UUID varchar(36),
                      PREMIUMUUID varchar(36),
                      ISSUEDTIME bigint
                    );
                    """);
            createStatement.execute();
        }
    }

    @Override
    public void deleteByLowercaseName(String name) throws DataAccessException {
        try (Connection con = getConnection()) {
            PreparedStatement st = con.prepareStatement("DELETE FROM AUTH where LOWERCASENICKNAME = ?");
            st.setString(1, name);
            st.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public Optional<RegisteredPlayer> getByLowercaseName(String name) throws DataAccessException {
        try (Connection con = getConnection()) {
            PreparedStatement st = con.prepareStatement(
                    """
                            SELECT
                            NICKNAME,
                            LOWERCASENICKNAME,
                            HASH,
                            IP,
                            LOGINIP,
                            TOTPTOKEN,
                            REGDATE,
                            LOGINDATE,
                            UUID,
                            PREMIUMUUID,
                            ISSUEDTIME
                            FROM AUTH
                            WHERE LOWERCASENICKNAME = ?
                            """
            );
            st.setString(1, name);
            st.execute();
            ResultSet resultSet = st.getResultSet();
            if (resultSet != null && resultSet.next()) {
                return Optional.of(parseRegisteredPlayer(resultSet));
            } else {
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public List<RegisteredPlayer> getByIp(String ip) throws DataAccessException {
        try (Connection con = getConnection()) {
            PreparedStatement st = con.prepareStatement(
                    """
                            SELECT
                            NICKNAME,
                            LOWERCASENICKNAME,
                            HASH,
                            IP,
                            LOGINIP,
                            TOTPTOKEN,
                            REGDATE,
                            LOGINDATE,
                            UUID,
                            PREMIUMUUID,
                            ISSUEDTIME
                            FROM AUTH
                            WHERE IP = ?
                            """
            );
            st.setString(1, ip);
            st.execute();
            ResultSet resultSet = st.getResultSet();
            List<RegisteredPlayer> players = new ArrayList<>();
            while (resultSet != null && resultSet.next()) {
                players.add(parseRegisteredPlayer(resultSet));
            }
            return Collections.unmodifiableList(players);
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public List<RegisteredPlayer> getByPremiumUUID(String uuid) throws DataAccessException {
        try (Connection con = getConnection()) {
            PreparedStatement st = con.prepareStatement(
                    """
                            SELECT
                            NICKNAME,
                            LOWERCASENICKNAME,
                            HASH,
                            IP,
                            LOGINIP,
                            TOTPTOKEN,
                            REGDATE,
                            LOGINDATE,
                            UUID,
                            PREMIUMUUID,
                            ISSUEDTIME
                            FROM AUTH
                            WHERE PREMIUMUUID = ?
                            """
            );
            st.setString(1, uuid);
            st.execute();
            ResultSet resultSet = st.getResultSet();
            List<RegisteredPlayer> players = new ArrayList<>();
            while (resultSet != null && resultSet.next()) {
                players.add(parseRegisteredPlayer(resultSet));
            }
            return Collections.unmodifiableList(players);
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public void createIfNotExists(RegisteredPlayer player) throws DataAccessException {
        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            PreparedStatement selectStatement = con.prepareStatement(
                    """
                            SELECT
                            NICKNAME,
                            LOWERCASENICKNAME,
                            HASH,
                            IP,
                            LOGINIP,
                            TOTPTOKEN,
                            REGDATE,
                            LOGINDATE,
                            UUID,
                            PREMIUMUUID,
                            ISSUEDTIME
                            FROM AUTH
                            WHERE nickname = ?
                            """
            );
            selectStatement.setString(1, player.getLowercaseNickname());
            ResultSet resultSet = selectStatement.getResultSet();
            if (resultSet != null && resultSet.next()) return;
            PreparedStatement insertStatement = con.prepareStatement(
                    """
                            INSERT INTO AUTH
                            (
                                NICKNAME,
                                LOWERCASENICKNAME,
                                HASH,
                                IP,
                                LOGINIP,
                                TOTPTOKEN,
                                REGDATE,
                                LOGINDATE,
                                UUID,
                                PREMIUMUUID,
                                ISSUEDTIME
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """
            );
            insertStatement.setString(1, player.getNickname());
            insertStatement.setString(2, player.getLowercaseNickname());
            insertStatement.setString(3, player.getHash());
            insertStatement.setString(4, player.getIP());
            insertStatement.setString(5, player.getLoginIp());
            insertStatement.setString(6, player.getTotpToken());
            insertStatement.setLong(7, player.getRegDate());
            insertStatement.setLong(8, player.getLoginDate());
            insertStatement.setString(9, player.getUuid());
            insertStatement.setString(10, player.getPremiumUuid());
            insertStatement.setLong(11, player.getTokenIssuedAt());
            insertStatement.executeUpdate();
            con.commit();
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public void update(RegisteredPlayer player)  throws DataAccessException {
        try (Connection con = getConnection()) {
            PreparedStatement updateStatement = con.prepareStatement(
                    """
                            UPDATE AUTH
                            SET NICKNAME = ? and
                                HASH = ? and
                                IP = ? and
                                LOGINIP = ? and
                                TOTPTOKEN = ? and
                                REGDATE = ? and
                                LOGINDATE = ? and
                                UUID = ? and
                                PREMIUMUUID = ? and
                                ISSUEDTIME = ? and
                            WHERE LOWERCASENICKNAME = ?
                            """
            );
            updateStatement.setString(1, player.getNickname());
            updateStatement.setString(2, player.getHash());
            updateStatement.setString(3, player.getIP());
            updateStatement.setString(4, player.getLoginIp());
            updateStatement.setString(5, player.getTotpToken());
            updateStatement.setLong(6, player.getRegDate());
            updateStatement.setLong(7, player.getLoginDate());
            updateStatement.setString(8, player.getUuid());
            updateStatement.setString(9, player.getPremiumUuid());
            updateStatement.setLong(10, player.getTokenIssuedAt());
            updateStatement.setString(11, player.getLowercaseNickname());
            updateStatement.executeUpdate();
            con.commit();
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public void updateHash(String lowercaseName, String hash) throws DataAccessException {
        try (Connection con = getConnection()) {
            PreparedStatement st = con.prepareStatement("UPDATE AUTH SET HASH = ? WHERE LOWERCASENICKNAME = ?");
            st.setString(1, hash);
            st.setString(2, lowercaseName);
            st.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public void updateTotpToken(String lowercaseName, String totpToken) throws DataAccessException {
        try (Connection con = getConnection()) {
            PreparedStatement st = con.prepareCall("UPDATE AUTH SET TOTPTOKEN = ? WHERE LOWERCASENICKNAME = ?");
            st.setString(1, totpToken);
            st.setString(2, lowercaseName);
            st.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public void updateLogin(String lowercase, String loginIp, Long loginDate) throws DataAccessException {
        try (Connection con = getConnection()) {
            PreparedStatement st = con.prepareStatement("UPDATE AUTH SET LOGINIP = ? AND LOGINDATE = ? WHERE LOWERCASENICKNAME = ?");
            st.setString(1, loginIp);
            st.setLong(1, loginDate);
            st.setString(3, lowercase);
            st.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public boolean isHashEmptyByPremiumUuid(String uuid) throws DataAccessException {
        try (Connection con = getConnection()) {
            PreparedStatement st = con.prepareStatement("SELECT HASH FROM AUTH WHERE PREMIUMUUID = ?");
            st.setString(1, uuid);
            ResultSet resultSet = st.getResultSet();
            return resultSet != null && resultSet.next() && "".equals(resultSet.getString("HASH"));
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public boolean isHashEmptyByLowercaseName(String name) throws DataAccessException {
        try (Connection con = getConnection()) {
            PreparedStatement st = con.prepareStatement("SELECT HASH FROM AUTH WHERE LOWERCASENICKNAME = ?");
            st.setString(1, name);
            ResultSet resultSet = st.getResultSet();
            return resultSet != null && resultSet.next() && "".equals(resultSet.getString("HASH"));
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    public int registeredPlayerCount() {
        try (Connection con = getConnection()) {
            PreparedStatement st = con.prepareStatement("SELECT COUNT(*) FROM AUTH");
            ResultSet set = st.getResultSet();
            if (set != null && set.next()) {
                return set.getInt(1);
            } else {
                return 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    private RegisteredPlayer parseRegisteredPlayer(ResultSet resultSet) throws SQLException {
        return RegisteredPlayer.builder()
                .nickname(resultSet.getString("NICKNAME"))
                .lowercaseNickname(resultSet.getString("LOWERCASENICKNAME"))
                .hash(resultSet.getString("HASH"))
                .ip(resultSet.getString("IP"))
                .totpToken(resultSet.getString("TOTPTOKEN"))
                .regDate(resultSet.getLong("REGDATE"))
                .uuid(resultSet.getString("UUID"))
                .premiumUuid(resultSet.getString("PREMIUMUUID"))
                .loginIp(resultSet.getString("LOGINIP"))
                .loginDate(resultSet.getLong("LOGINDATE"))
                .tokenIssuedAt(resultSet.getLong("ISSUEDTIME"))
                .build();
    }

    protected Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
