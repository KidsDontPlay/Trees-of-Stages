package mrriegel.treesofstages;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSapling;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.terraingen.SaplingGrowTreeEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent;

@Mod(modid = TreesOfStages.MODID, name = TreesOfStages.MODNAME, version = TreesOfStages.VERSION, acceptedMinecraftVersions = "[1.12,1.13)", acceptableRemoteVersions = "*")
public class TreesOfStages {
	public static final String MODID = "treesofstages";
	public static final String VERSION = "1.0.0";
	public static final String MODNAME = "Trees of Stages";

	@Instance(TreesOfStages.MODID)
	public static TreesOfStages instance;

	private static Configuration config;
	private static int growthSpeed;

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		config = new Configuration(event.getSuggestedConfigurationFile());
		growthSpeed = config.getInt("growthSpeed", Configuration.CATEGORY_GENERAL, 5, 1, 1000, "The lower the faster");
	}

	@EventHandler
	public void init(FMLInitializationEvent event) {
		MinecraftForge.TERRAIN_GEN_BUS.register(this);
		MinecraftForge.EVENT_BUS.register(this);
	}

	@EventHandler
	public void postInit(FMLPostInitializationEvent event) {
	}

	@SuppressWarnings("serial")
	public static Int2ObjectMap<PseudoWorld> pseudoWorldMap = new Int2ObjectOpenHashMap<PseudoWorld>() {
		@Override
		public PseudoWorld get(int k) {
			if (!containsKey(k))
				put(k, new PseudoWorld(FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(k)));
			return super.get(k);
		};

		@Override
		public PseudoWorld get(Integer ok) {
			return get(ok.intValue());
		};
	};

	@SubscribeEvent
	public void grow(WorldTickEvent event) {
		if (event.phase == Phase.END && !event.world.isRemote) {
			Iterator<Tree> it = pseudoWorldMap.get(event.world.provider.getDimension()).trees.iterator();
			while (it.hasNext()) {
				Tree tree = it.next();
				if (event.world.getTotalWorldTime() % growthSpeed != 0)
					continue;
				while (true) {
					Pair<BlockPos, IBlockState> pair = tree.next();
					if (pair != null) {
						if (event.world.getBlockState(pair.getKey()).getBlock().isReplaceable(event.world, pair.getKey()) || (event.world.getBlockState(pair.getKey()).getBlock() instanceof BlockSapling && Math.sqrt(pair.getKey().distanceSq(tree.start)) <= 2.1) || event.world.getBlockState(pair.getKey()).getMaterial() == Material.LEAVES) {
							event.world.setBlockState(pair.getKey(), pair.getValue());
							break;
						} else
							pair = tree.next();
					} else {
						it.remove();
						break;
					}
				}
			}
		}

	}

	@SubscribeEvent
	public void tryy(SaplingGrowTreeEvent event) {
		if (!(event.getWorld() instanceof PseudoWorld)) {
			PseudoWorld ws = pseudoWorldMap.get(event.getWorld().provider.getDimension());
			if (!true) {
				Chunk c = event.getWorld().getChunkFromBlockCoords(event.getPos());
				Iterable<BlockPos> i = BlockPos.getAllInBox(new ChunkPos(c.x - 1, c.z - 1).getXStart(), 0, new ChunkPos(c.x - 1, c.z - 1).getZStart(), new ChunkPos(c.x + 1, c.z + 1).getXEnd(), event.getWorld().getActualHeight(), new ChunkPos(c.x + 1, c.z + 1).getZEnd());
				for (BlockPos p : i)
					ws.setBlockState(p, event.getWorld().getBlockState(p));
			} else {
				Set<Chunk> chunks = new HashSet<Chunk>();
				chunks.add(event.getWorld().getChunkFromBlockCoords(event.getPos()));
				chunks.add(event.getWorld().getChunkFromBlockCoords(event.getPos().north(9)));
				chunks.add(event.getWorld().getChunkFromBlockCoords(event.getPos().east(9)));
				chunks.add(event.getWorld().getChunkFromBlockCoords(event.getPos().south(9)));
				chunks.add(event.getWorld().getChunkFromBlockCoords(event.getPos().west(9)));
				for (Chunk c : chunks) {
					ChunkPos cp = new ChunkPos(c.x, c.z);
					for (BlockPos p : BlockPos.getAllInBox(cp.getXStart(), 0, cp.getZStart(), cp.getXEnd(), event.getWorld().getActualHeight(), cp.getZEnd()))
						ws.setBlockState(p, event.getWorld().getBlockState(p));
				}
			}
			/*TODO
			 * forestry
			 * binnies extra tries
			 * bop
			 * natura
			 * pams
			 * plants
			 * terraqueos
			*/
			ws.startTree(event.getPos());
			if (event.getWorld().getBlockState(event.getPos()).getBlock() instanceof BlockSapling)
				((BlockSapling) event.getWorld().getBlockState(event.getPos()).getBlock()).generateTree(ws, event.getPos(), event.getWorld().getBlockState(event.getPos()), event.getWorld().rand);
			ws.endTree();
			event.setResult(Result.DENY);
		}
	}

	public static class PseudoWorld extends World {
		private Object2ObjectMap<BlockPos, IBlockState> map = new Object2ObjectOpenHashMap<BlockPos, IBlockState>();
		public World world;
		private boolean safeTree;
		BlockPos start;
		public ArrayDeque<Pair<BlockPos, IBlockState>> pairs = new ArrayDeque<>();
		public List<Tree> trees = new ArrayList<>();

		public PseudoWorld(World world) {
			super(null, world.getWorldInfo(), world.provider, world.getMinecraftServer().profiler, false);
			map.defaultReturnValue(Blocks.AIR.getDefaultState());
			this.world = world;
		}

		@Override
		protected IChunkProvider createChunkProvider() {
			return null;
		}

		@Override
		protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
			if (start == null)
				return false;
			int xx = start.getX() >> 4, zz = start.getZ() >> 4;
			return Math.abs(x - xx) + Math.abs(z - zz) <= 2;

		}

		@Override
		public IBlockState getBlockState(BlockPos pos) {
			return map.get(pos);
		}

		@Override
		public boolean setBlockState(BlockPos pos, IBlockState newState, int flags) {
			map.put(pos, newState);
			if (safeTree && newState.getBlock() != Blocks.AIR)
				pairs.add(Pair.of(pos, newState));
			return true;
		}

		public void startTree(BlockPos sapling) {
			safeTree = true;
			start = sapling;
		}

		public void endTree() {
			safeTree = false;
			trees.add(new Tree(this, new BlockPos(start)));
			start = null;

		}

	}

	public static class Tree {
		List<Pair<BlockPos, IBlockState>> wood = new ArrayList<>();
		List<Pair<BlockPos, IBlockState>> leaves = new ArrayList<>();
		public final BlockPos start;
		public final int totalBlocks;
		public final Block sapling;

		public Tree(PseudoWorld world, BlockPos start) {
			this.totalBlocks = wood.size() + leaves.size();
			this.start = start;
			this.sapling = world.getBlockState(start).getBlock();
			while (!world.pairs.isEmpty()) {
				Pair<BlockPos, IBlockState> pair = world.pairs.poll();
				if (pair.getValue().getMaterial() == Material.WOOD)
					wood.add(pair);
				else
					leaves.add(pair);
			}
			if (wood.isEmpty())
				return;
			wood.sort((p1, p2) -> Integer.compare(p1.getKey().getY(), p2.getKey().getY()));
			BlockPos mid = wood.get(MathHelper.clamp(wood.size() / 2, 0, wood.size() - 1)).getKey();
			BlockPos firstWood = /*wood.get(wood.size() - 1).getKey()*/mid;
			if (mid.getY() < this.start.getY())
				Collections.reverse(wood);
			leaves.sort((p1, p2) -> Double.compare(p1.getKey().getDistance(firstWood.getX(), firstWood.getY(), firstWood.getZ()), p2.getKey().getDistance(firstWood.getX(), firstWood.getY(), firstWood.getZ())));
			Comparator<Pair<BlockPos, IBlockState>> comp = (p1, p2) -> {
				int a = Integer.compare(p1.getKey().getY(), p2.getKey().getY());
				if (a != 0)
					return a;
				return Double.compare(p1.getKey().getDistance(start.getX(), start.getY(), start.getZ()), p2.getKey().getDistance(start.getX(), start.getY(), start.getZ()));
			};
			//			wood.addAll(leaves);
			//			leaves.clear();
			//			wood.sort(comp);
		}

		public boolean isEmpty() {
			return wood.isEmpty() && leaves.isEmpty();
		}

		public Pair<BlockPos, IBlockState> next() {
			if (!wood.isEmpty())
				return wood.remove(0);
			else if (!leaves.isEmpty())
				return leaves.remove(0);
			else
				return null;
		}
	}

}
