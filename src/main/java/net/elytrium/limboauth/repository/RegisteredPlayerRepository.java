package net.elytrium.limboauth.repository;

import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.repository.exception.DataAccessException;

import java.util.List;
import java.util.Optional;

public interface RegisteredPlayerRepository {

    void deleteByLowercaseName(String name) throws DataAccessException;

    Optional<RegisteredPlayer> getByLowercaseName(String name) throws DataAccessException;

    List<RegisteredPlayer> getByIp(String ip) throws DataAccessException;

    List<RegisteredPlayer> getByPremiumUUID(String uuid) throws DataAccessException;

    void createIfNotExists(RegisteredPlayer player) throws DataAccessException;

    void update(RegisteredPlayer player) throws DataAccessException;

    void updateHash(String lowercaseName, String hash) throws DataAccessException;

    void updateTotpToken(String lowercaseName, String token) throws DataAccessException;

    void updateLogin(String lowercase, String loginIp, Long loginDate) throws DataAccessException;

    /**
     *
     QueryBuilder<RegisteredPlayer, String> premiumCountQuery = this.playerRepository.queryBuilder();
     premiumCountQuery.where()
     .eq(RegisteredPlayer.PREMIUM_UUID_FIELD, uuid.toString())
     .and()
     .eq(RegisteredPlayer.HASH_FIELD, "");
     premiumCountQuery.setCountOf(true);
     * */
    boolean isHashEmptyByPremiumUuid(String uuid) throws DataAccessException;

    /**
     *       QueryBuilder<RegisteredPlayer, String> crackedCountQuery = this.playerRepository.queryBuilder();
     *       crackedCountQuery.where()
     *           .eq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, nickname)
     *           .and()
     *           .ne(RegisteredPlayer.HASH_FIELD, "");
     *       crackedCountQuery.setCountOf(true);
     * */
    boolean isHashEmptyByLowercaseName(String name) throws DataAccessException;

    int registeredPlayerCount();

}
