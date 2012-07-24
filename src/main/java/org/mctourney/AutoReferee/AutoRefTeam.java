package org.mctourney.AutoReferee;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import org.mctourney.AutoReferee.AutoReferee.eMatchStatus;
import org.mctourney.AutoReferee.AutoRefMatch.TranscriptEvent;
import org.mctourney.AutoReferee.util.*;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Maps;

public class AutoRefTeam implements Comparable<AutoRefTeam>
{
	// reference to the match
	private AutoRefMatch match = null;
	
	public AutoRefMatch getMatch()
	{ return match; }

	// player information
	private Set<AutoRefPlayer> players = Sets.newHashSet();

	public Set<AutoRefPlayer> getPlayers()
	{
		if (players != null) return players;
		return Sets.newHashSet();
	}

	public String getPlayerList()
	{
		Set<String> plist = Sets.newHashSet();
		for (AutoRefPlayer apl : getPlayers())
			plist.add(apl.getPlayerName());
		if (plist.size() == 0) return "{empty}";
		return StringUtils.join(plist, ", ");
	}
	
	private Set<OfflinePlayer> expectedPlayers = Sets.newHashSet();
	
	public void addExpectedPlayer(OfflinePlayer op)
	{ expectedPlayers.add(op); }
	
	public void addExpectedPlayer(String name)
	{ addExpectedPlayer(AutoReferee.getInstance().getServer().getOfflinePlayer(name)); }
	
	public Set<OfflinePlayer> getExpectedPlayers()
	{ return expectedPlayers; }
	
	// team's name, may or may not be color-related
	private String name = null;
	private String customName = null;

	// determine the name provided back from this team
	private String getRawName()
	{
		if (customName != null)
			return customName;
		return name;
	}

	public String getTag()
	{ return getRawName().toLowerCase().replaceAll("[^a-z0-9]+", ""); }
	
	public void setName(String name)
	{ customName = name; }

	public String getName()
	{ return color + getRawName() + ChatColor.RESET; }

	// color to use for members of this team
	private ChatColor color = null;

	public ChatColor getColor()
	{ return color; }

	public void setColor(ChatColor color)
	{ this.color = color; }

	// maximum size of a team (for manual mode only)
	public int maxSize = 0;
	
	// is this team ready to play?
	private boolean ready = false;

	public boolean isReady()
	{ return ready; }

	public void setReady(boolean ready)
	{ this.ready = ready; }

	// list of regions
	private Set<CuboidRegion> regions = null;
	
	public Set<CuboidRegion> getRegions()
	{ return regions; }

	// location of custom spawn
	private Location spawn;
	
	public Location getSpawnLocation()
	{ return spawn == null ? match.getWorld().getSpawnLocation() : spawn; }

	// win-conditions, locations mapped to expected block data
	public Map<Location, BlockData> winConditions;
	public Map<BlockData, SourceInventory> targetChests;
	
	// does a provided search string match this team?
	public boolean matches(String needle)
	{
		if (needle == null) return false;
		needle = needle.toLowerCase();

		String a = name, b = customName;
		if (a != null && -1 != needle.indexOf(a.toLowerCase())) return true;
		if (b != null && -1 != needle.indexOf(b.toLowerCase())) return true;
		return false;
	}

	// a factory for processing config maps
	@SuppressWarnings("unchecked")
	public static AutoRefTeam fromMap(Map<String, Object> conf, AutoRefMatch match)
	{
		AutoRefTeam newTeam = new AutoRefTeam();
		newTeam.color = ChatColor.RESET;
		newTeam.maxSize = 0;
		
		newTeam.match = match;
		World w = match.getWorld();

		// get name from map
		if (!conf.containsKey("name")) return null;
		newTeam.name = (String) conf.get("name");

		// get the color from the map
		if (conf.containsKey("color"))
		{
			String clr = ((String) conf.get("color")).toUpperCase();
			try { newTeam.color = ChatColor.valueOf(clr); }
			catch (IllegalArgumentException e) { }
		}

		// get the max size from the map
		if (conf.containsKey("maxsize"))
		{
			Integer msz = (Integer) conf.get("maxsize");
			if (msz != null) newTeam.maxSize = msz.intValue();
		}

		newTeam.regions = Sets.newHashSet();
		if (conf.containsKey("regions"))
		{
			List<String> coordstrings = (List<String>) conf.get("regions");
			if (coordstrings != null) for (String coords : coordstrings)
			{
				CuboidRegion creg = CuboidRegion.fromCoords(coords);
				if (creg != null) newTeam.regions.add(creg);
			}
		}

		newTeam.winConditions = Maps.newHashMap();
		if (conf.containsKey("win-condition"))
		{
			List<String> slist = (List<String>) conf.get("win-condition");
			if (slist != null) for (String s : slist)
			{
				String[] sp = s.split(":");
				
				BlockVector3 v = BlockVector3.fromCoords(sp[0]);
				newTeam.addWinCondition(w.getBlockAt(v.toLocation(w)), 
					BlockData.fromString(sp[1]));
			}
		}
		
		newTeam.targetChests = Maps.newHashMap();
		if (conf.containsKey("target-container"))
		{
			List<String> slist = (List<String>) conf.get("target-container");
			if (slist != null) for (String s : slist)
			{
				newTeam.addSourceInventory(
					w.getBlockAt(BlockVector3.fromCoords(s).toLocation(w)));
			}
		}

		newTeam.players = Sets.newHashSet();
		return newTeam;
	}

	public Map<String, Object> toMap()
	{
		Map<String, Object> map = Maps.newHashMap();

		// add name to the map
		map.put("name", name);

		// add string representation of the color
		map.put("color", color.name());

		// add the maximum team size
		map.put("maxsize", new Integer(maxSize));
		
		// convert the win conditions to strings
		List<String> wcond = Lists.newArrayList();
		for (Map.Entry<Location, BlockData> e : winConditions.entrySet())
			wcond.add(BlockVector3.fromLocation(e.getKey()).toCoords() 
				+ ":" + e.getValue());

		// add the win condition list
		map.put("win-condition", wcond);
		
		// convert the target containers to strings
		List<String> tcon = Lists.newArrayList();
		for (Map.Entry<BlockData, SourceInventory> e : targetChests.entrySet())
			tcon.add(BlockVector3.fromLocation(e.getValue().location).toCoords());

		// add the target container list
		map.put("target-container", tcon);

		// convert regions to strings
		List<String> regstr = Lists.newArrayList();
		for (CuboidRegion reg : regions) regstr.add(reg.toCoords());

		// add the region list
		map.put("regions", regstr);

		// return the map
		return map;
	}

	public AutoRefPlayer getPlayer(String name)
	{
		if (name != null) for (AutoRefPlayer apl : players)
			if (name.equalsIgnoreCase(apl.getPlayerName()))
				return apl;
		return null;
	}

	public AutoRefPlayer getPlayer(Player pl)
	{ return pl == null ? null : getPlayer(pl.getName()); }

	public void join(Player pl)
	{
		// create an APL object for this player.
		AutoRefPlayer apl = new AutoRefPlayer(pl, this);
		
		// null team not allowed, and quit if they are already on this team
		if (players.contains(apl)) return;
		players.add(apl);
		
		// prepare the player
		if (match != null && match.getCurrentState() != eMatchStatus.PLAYING)
			pl.teleport(getSpawnLocation());
		pl.setGameMode(GameMode.SURVIVAL);
		
		// if the match is in progress, no one may join
		if (match.getCurrentState().ordinal() >= eMatchStatus.PLAYING.ordinal()) return;
	
		String colorName = getPlayerName(pl);
		match.broadcast(colorName + " has joined " + getName());
		
		//FIXME if (pl.isOnline() && (pl instanceof Player))
		//	((Player) pl).setPlayerListName(StringUtils.substring(colorName, 0, 16));

		match.checkTeamsReady();
	}

	public void leave(Player pl)
	{
		// create an APL object for this player.
		AutoRefPlayer apl = new AutoRefPlayer(pl);
		if (!players.remove(apl)) return;
		
		String colorName = getPlayerName(pl);
		match.broadcast(colorName + " has left " + getName());
		
		if (pl.isOnline() && (pl instanceof Player))
			((Player) pl).setPlayerListName(pl.getName());

		match.checkTeamsReady();
	}
	
	public String getPlayerName(Player pl)
	{
		AutoRefPlayer apl = new AutoRefPlayer(pl);
		if (!players.contains(apl)) return pl.getName();
		return color + pl.getName() + ChatColor.RESET;
	}

	// TRUE = this location *is* within this team's regions
	// FALSE = this location is *not* within team's regions
	public boolean checkPosition(Location loc)
	{
		// is the provided location owned by this team?
		return distanceToClosestRegion(loc) < ZoneListener.SNEAK_DISTANCE;
	}

	public double distanceToClosestRegion(Location loc)
	{
		double distance = match.getStartRegion().distanceToRegion(loc);
		for ( CuboidRegion reg : regions ) if (distance > 0)
			distance = Math.min(distance, reg.distanceToRegion(loc));
		return distance;
	}

	private static final long SEEN_COOLDOWN = 40 * 20;
	public class SourceInventory
	{
		public Location location;
		public Inventory inventory;
		public BlockData blockdata;
		
		// has this team seen this chest recently?
		public long lastSeen = 0;

		@Override public String toString()
		{ return BlockVector3.fromLocation(location).toCoords(); }

		public void hasSeen(AutoRefPlayer apl)
		{
			// if this team has seen this box before, nevermind
			long ctime = location.getWorld().getFullTime();
			if (lastSeen > 0 && ctime - lastSeen < SEEN_COOLDOWN) return;
			
			if (apl != null)
			{
				// generate a transcript event for seeing the box
				String message = String.format("%s is carrying %s", 
					apl.getPlayerName(), blockdata.getRawName());
				getMatch().addEvent(new TranscriptEvent(apl.getTeam().getMatch(),
					TranscriptEvent.EventType.OBJECTIVE_FOUND, message, location, apl, blockdata));
			}
			
			// mark this box as seen
			lastSeen = ctime;
		}
	}

	public void addSourceInventory(Block block)
	{
		if (block == null) return;

		if (!(block.getState() instanceof InventoryHolder)) return;
		InventoryHolder cblock = (InventoryHolder) block.getState();

		SourceInventory src = new SourceInventory();
		src.location = block.getLocation();
		src.inventory = cblock.getInventory();
		
		BlockData bd = BlockData.fromInventory(src.inventory);
		if (bd == null) return;
		
		targetChests.put(src.blockdata = bd, src);
		String nm = String.format("%s @ %s", block.getType().name(),
			BlockVector3.fromLocation(block.getLocation()).toCoords());
		match.broadcast(nm + " is a source for " + bd.getName());
	}
	
	public void addWinCondition(Block block)
	{
		// if the block is null, forget it
		if (block == null) return;
		
		// add the block data to the win-condition listing
		BlockData bd = BlockData.fromBlock(block);
		this.addWinCondition(block, bd);
	}
	
	public void addWinCondition(Block block, BlockData bd)
	{
		// if the block is null, forget it
		if (block == null || bd == null) return;
		winConditions.put(block.getLocation(), bd);
		
		// broadcast the update
		match.broadcast(bd.getName() + " is now a win condition for " + getName() + 
			" @ " + BlockVector3.fromLocation(block.getLocation()).toCoords());
	}

	public void updateCarrying(AutoRefPlayer apl, Set<BlockData> carrying, Set<BlockData> newCarrying)
	{
		// TODO: perhaps store team-level carrying information?
		match.updateCarrying(apl, carrying, newCarrying);
	}

	public void updateHealthArmor(AutoRefPlayer apl,
			int currentHealth, int currentArmor, int newHealth, int newArmor)
	{
		match.updateHealthArmor(apl, 
			currentHealth, currentArmor, newHealth, newArmor);
	}

	public int compareTo(AutoRefTeam team)
	{ return this.getRawName().compareTo(team.getRawName()); }
}
