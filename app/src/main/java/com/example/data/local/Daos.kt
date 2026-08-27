package com.example.data.local

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players ORDER BY expectedFantasyPoints DESC")
    fun getAllPlayersFlow(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players ORDER BY expectedFantasyPoints DESC")
    suspend fun getAllPlayers(): List<PlayerEntity>

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun getPlayerById(id: String): PlayerEntity?

    @Query("SELECT * FROM players WHERE role = :role ORDER BY expectedFantasyPoints DESC")
    fun getPlayersByRoleFlow(role: Role): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE isPurchased = 0 AND role = :role ORDER BY expectedFantasyPoints DESC")
    suspend fun getAvailablePlayersByRole(role: Role): List<PlayerEntity>

    @Query("SELECT * FROM players WHERE isPurchased = 0 ORDER BY expectedFantasyPoints DESC")
    suspend fun getAllAvailablePlayers(): List<PlayerEntity>

    @Query("SELECT * FROM players WHERE isPurchased = 1 AND purchasedByTeam = :teamName ORDER BY role, expectedFantasyPoints DESC")
    fun getPurchasedPlayersForTeamFlow(teamName: String): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE isPurchased = 1 AND purchasedByTeam = :teamName ORDER BY role, expectedFantasyPoints DESC")
    suspend fun getPurchasedPlayersForTeam(teamName: String): List<PlayerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayers(players: List<PlayerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayer(player: PlayerEntity)

    @Update
    suspend fun updatePlayer(player: PlayerEntity)

    @Query("UPDATE players SET isPurchased = :isPurchased, purchasedByTeam = :teamName, purchasePrice = :price WHERE id = :playerId")
    suspend fun updatePurchaseStatus(playerId: String, isPurchased: Boolean, teamName: String?, price: Int?)

    @Query("UPDATE players SET status = :status, injuryNotes = :notes, expectedReturnDate = :returnDate WHERE id = :playerId")
    suspend fun updateInjuryStatus(playerId: String, status: String, notes: String, returnDate: String)

    @Query("UPDATE players SET status = 'Disponibile', injuryNotes = '', expectedReturnDate = ''")
    suspend fun resetAllInjuries()

    @Query("UPDATE players SET purchasedByTeam = :newName WHERE purchasedByTeam = :oldName")
    suspend fun updateTeamNameInPlayers(oldName: String, newName: String)

    @Query("UPDATE players SET isPurchased = 0, purchasedByTeam = NULL, purchasePrice = NULL")
    suspend fun resetAllPurchases()

    @Query("DELETE FROM players")
    suspend fun clearAll()
}

@Dao
interface TeamDao {
    @Query("SELECT * FROM teams ORDER BY isUserTeam DESC, name ASC")
    fun getAllTeamsFlow(): Flow<List<TeamEntity>>

    @Query("SELECT * FROM teams ORDER BY isUserTeam DESC, name ASC")
    suspend fun getAllTeams(): List<TeamEntity>

    @Query("SELECT * FROM teams WHERE isUserTeam = 1 LIMIT 1")
    fun getUserTeamFlow(): Flow<TeamEntity?>

    @Query("SELECT * FROM teams WHERE isUserTeam = 1 LIMIT 1")
    suspend fun getUserTeam(): TeamEntity?

    @Query("SELECT * FROM teams WHERE name = :name")
    suspend fun getTeamByName(name: String): TeamEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeams(teams: List<TeamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeam(team: TeamEntity)

    @Update
    suspend fun updateTeam(team: TeamEntity)

    @Query("DELETE FROM teams WHERE name = :name")
    suspend fun deleteTeamByName(name: String)

    @Query("DELETE FROM teams")
    suspend fun clearAll()
}

@Dao
interface AuctionEventDao {
    @Query("SELECT * FROM auction_events ORDER BY id DESC")
    fun getAllEventsFlow(): Flow<List<AuctionEventEntity>>

    @Query("SELECT * FROM auction_events ORDER BY id DESC")
    suspend fun getAllEvents(): List<AuctionEventEntity>

    @Query("UPDATE auction_events SET buyerTeamName = :newName WHERE buyerTeamName = :oldName")
    suspend fun updateTeamNameInEvents(oldName: String, newName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: AuctionEventEntity): Long

    @Query("DELETE FROM auction_events WHERE id = :id")
    suspend fun deleteEvent(id: Long)

    @Query("DELETE FROM auction_events WHERE id = (SELECT MAX(id) FROM auction_events)")
    suspend fun deleteLastEvent()

    @Query("SELECT * FROM auction_events ORDER BY id DESC LIMIT 1")
    suspend fun getLastEvent(): AuctionEventEntity?

    @Query("DELETE FROM auction_events")
    suspend fun clearAll()
}

@Dao
interface LeagueDao {
    @Query("SELECT * FROM league_config WHERE id = 1")
    fun getLeagueConfigFlow(): Flow<LeagueConfig?>

    @Query("SELECT * FROM league_config WHERE id = 1")
    suspend fun getLeagueConfig(): LeagueConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLeagueConfig(config: LeagueConfig)
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist")
    fun getAllWatchlistFlow(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist WHERE playerId = :playerId")
    suspend fun getWatchlistForPlayer(playerId: String): WatchlistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setWatchlistTag(entry: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE playerId = :playerId")
    suspend fun removeWatchlistTag(playerId: String)
}
