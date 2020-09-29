package com.bgsoftware.superiorskyblock.utils.islands;

import com.bgsoftware.superiorskyblock.Locale;
import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.utils.chunks.ChunkPosition;
import com.bgsoftware.superiorskyblock.utils.chunks.ChunksProvider;
import com.bgsoftware.superiorskyblock.utils.chunks.ChunksTracker;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class IslandUtils {

    public static final String VISITORS_WARP_NAME = "visit";
    public static final int NO_LIMIT = -1;

    private static final SuperiorSkyblockPlugin plugin = SuperiorSkyblockPlugin.getPlugin();

    private IslandUtils(){

    }

    public static List<ChunkPosition> getChunkCoords(Island island, World world, boolean onlyProtected, boolean noEmptyChunks){
        List<ChunkPosition> chunkCoords = new ArrayList<>();

        Location min = onlyProtected ? island.getMinimumProtected() : island.getMinimum();
        Location max = onlyProtected ? island.getMaximumProtected() : island.getMaximum();

        for(int x = min.getBlockX() >> 4; x <= max.getBlockX() >> 4; x++){
            for(int z = min.getBlockZ() >> 4; z <= max.getBlockZ() >> 4; z++){
                if(!noEmptyChunks || ChunksTracker.isMarkedDirty(island, world, x, z)) {
                    chunkCoords.add(ChunkPosition.of(world, x, z));
                }
            }
        }

        return chunkCoords;
    }

    public static List<ChunkPosition> getChunkCoords(Island island, boolean onlyProtected, boolean noEmptyChunks){
        List<ChunkPosition> chunkCoords = new ArrayList<>();

        {
            World normalWorld = island.getCenter(World.Environment.NORMAL).getWorld();
            chunkCoords.addAll(getChunkCoords(island, normalWorld, onlyProtected, noEmptyChunks));
        }

        if(plugin.getProviders().isNetherEnabled() && island.wasSchematicGenerated(World.Environment.NETHER)){
            World netherWorld = island.getCenter(World.Environment.NETHER).getWorld();
            chunkCoords.addAll(getChunkCoords(island, netherWorld, onlyProtected, noEmptyChunks));
        }

        if(plugin.getProviders().isEndEnabled() && island.wasSchematicGenerated(World.Environment.THE_END)){
            World endWorld = island.getCenter(World.Environment.THE_END).getWorld();
            chunkCoords.addAll(getChunkCoords(island, endWorld, onlyProtected, noEmptyChunks));
        }

        for(World registeredWorld : plugin.getGrid().getRegisteredWorlds()){
            chunkCoords.addAll(getChunkCoords(island, registeredWorld, onlyProtected, noEmptyChunks));
        }

        return chunkCoords;
    }

    public static List<CompletableFuture<Chunk>> getAllChunksAsync(Island island, World world, boolean onlyProtected, boolean noEmptyChunks, BiConsumer<Chunk, Throwable> whenComplete){
        return IslandUtils.getChunkCoords(island, world, onlyProtected, noEmptyChunks).stream().map(chunkPosition -> {
            CompletableFuture<Chunk> completableFuture = ChunksProvider.loadChunk(chunkPosition, null);
            return whenComplete == null ? completableFuture : completableFuture.whenComplete(whenComplete);
        }).collect(Collectors.toList());
    }

    public static List<CompletableFuture<Chunk>> getAllChunksAsync(Island island, World world, boolean onlyProtected, boolean noEmptyChunks, Consumer<Chunk> onChunkLoad){
        return IslandUtils.getChunkCoords(island, world, onlyProtected, noEmptyChunks).stream()
                .map(chunkPosition -> ChunksProvider.loadChunk(chunkPosition, onChunkLoad))
                .collect(Collectors.toList());
    }

    public static List<CompletableFuture<Chunk>> getAllChunksAsync(Island island, boolean onlyProtected, boolean noEmptyChunks, Consumer<Chunk> onChunkLoad){
        List<CompletableFuture<Chunk>> chunkCoords = new ArrayList<>();

        {
            World normalWorld = island.getCenter(World.Environment.NORMAL).getWorld();
            chunkCoords.addAll(getAllChunksAsync(island, normalWorld, onlyProtected, noEmptyChunks, onChunkLoad));
        }

        if(plugin.getProviders().isNetherEnabled() && island.wasSchematicGenerated(World.Environment.NETHER)){
            World netherWorld = island.getCenter(World.Environment.NETHER).getWorld();
            chunkCoords.addAll(getAllChunksAsync(island, netherWorld, onlyProtected, noEmptyChunks, onChunkLoad));
        }

        if(plugin.getProviders().isEndEnabled() && island.wasSchematicGenerated(World.Environment.THE_END)){
            World endWorld = island.getCenter(World.Environment.THE_END).getWorld();
            chunkCoords.addAll(getAllChunksAsync(island, endWorld, onlyProtected, noEmptyChunks, onChunkLoad));
        }

        for(World registeredWorld : plugin.getGrid().getRegisteredWorlds()){
            chunkCoords.addAll(getAllChunksAsync(island, registeredWorld, onlyProtected, noEmptyChunks, onChunkLoad));
        }

        return chunkCoords;
    }

    public static void updateIslandFly(Island island, SuperiorPlayer superiorPlayer){
        Player player = superiorPlayer.asPlayer();
        if(!player.isFlying() && superiorPlayer.hasIslandFlyEnabled() && island.hasPermission(superiorPlayer, IslandPrivileges.FLY)){
            player.setAllowFlight(true);
            player.setFlying(true);
            Locale.ISLAND_FLY_ENABLED.send(player);
        }
        else if(player.isFlying() && !island.hasPermission(superiorPlayer, IslandPrivileges.FLY)){
            player.setAllowFlight(false);
            player.setFlying(false);
            Locale.ISLAND_FLY_DISABLED.send(player);
        }
    }

    public static void updateTradingMenus(Island island, SuperiorPlayer superiorPlayer){
        Player player = superiorPlayer.asPlayer();
        Inventory openInventory = player.getOpenInventory().getTopInventory();
        if(openInventory != null && openInventory.getType() == InventoryType.MERCHANT && !island.hasPermission(superiorPlayer, IslandPrivileges.VILLAGER_TRADING))
            player.closeInventory();
    }

    public static void resetChunksExcludedFromList(Island island, Collection<ChunkPosition> excludedChunkPositions) {
        List<ChunkPosition> chunksToDelete = IslandUtils.getChunkCoords(island, false, false);
        chunksToDelete.removeAll(excludedChunkPositions);
        chunksToDelete.forEach(chunkPosition -> plugin.getNMSBlocks().deleteChunk(island, chunkPosition, null));
    }

    public static void sendMessage(Island island, Locale message, List<UUID> ignoredMembers, Object... args){
        island.getIslandMembers(true).stream()
                .filter(superiorPlayer -> !ignoredMembers.contains(superiorPlayer.getUniqueId()) && superiorPlayer.isOnline())
                .forEach(superiorPlayer -> message.send(superiorPlayer, args));
    }

    public static double getGeneratorPercentageDecimal(Island island, com.bgsoftware.superiorskyblock.api.key.Key key){
        int totalAmount = island.getGeneratorTotalAmount();
        return totalAmount == 0 ? 0 : (island.getGeneratorAmount(key) * 100D) / totalAmount;
    }

}
