package mrriegel.treesofstages;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.tuple.Pair;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSapling;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.MapStorage;
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
	public static final String VERSION = "1.1.0";
	public static final String MODNAME = "Trees of Stages";

	@Instance(TreesOfStages.MODID)
	public static TreesOfStages instance;

	private static Configuration config;
	private static int growthSpeed;
	public static Set<String> blacklistMods;
	public static boolean sound;

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		config = new Configuration(event.getSuggestedConfigurationFile());
		growthSpeed = config.getInt("growthSpeed", Configuration.CATEGORY_GENERAL, 5, 1, 1000, "The lower the faster");
		blacklistMods = new HashSet<>(Arrays.asList(config.getStringList("blacklistMods", Configuration.CATEGORY_GENERAL, new String[0], "Mod IDs from saplings that will grow instantaneously")));
		sound = config.getBoolean("sound", Configuration.CATEGORY_GENERAL, true, "Enable sound when trees grow");
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
				boolean placed = false;
				while (!placed) {
					Entry<BlockPos, IBlockState> pair = tree.next();
					if (pair != null) {
						IBlockState current = event.world.getBlockState(pair.getKey());
						if (current.getBlock().isReplaceable(event.world, pair.getKey()) || //
								((current.getBlock() instanceof BlockSapling || event.world.getBlockState(tree.start).getBlock().getClass().isAssignableFrom(current.getBlock().getClass())) && Math.sqrt(pair.getKey().distanceSq(tree.start)) <= 2.1) || //
								current.getMaterial() == Material.LEAVES) {
							event.world.setBlockState(pair.getKey(), pair.getValue());
							TileEntity t = event.world.getTileEntity(pair.getKey());
							if (t != null)
								t.readFromNBT(pseudoWorldMap.get(event.world.provider.getDimension()).getTileEntity(pair.getKey()).writeToNBT(new NBTTagCompound()));
							if (sound) {
								SoundType soundtype = pair.getValue().getBlock().getSoundType(pair.getValue(), event.world, pair.getKey(), null);
								event.world.playSound(null, pair.getKey(), soundtype.getPlaceSound(), SoundCategory.BLOCKS, (soundtype.getVolume() + 1.0F) / 3.0F, soundtype.getPitch() * 0.8F);
							}
							placed = true;
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

	private Map<String, Class<?>> classMap = new HashMap<String, Class<?>>();

	private Class<?> getClass(String name) {
		try {
			if (!classMap.containsKey(name))
				classMap.put(name, Class.forName(name));
			return classMap.get(name);
		} catch (ClassNotFoundException e) {
			classMap.put(name, null);
			return null;
		}
	}

	private Runnable get(SaplingGrowTreeEvent event) {
		PseudoWorld ws = pseudoWorldMap.get(event.getWorld().provider.getDimension());
		IBlockState sapling = event.getWorld().getBlockState(event.getPos());
		if (sapling.getBlock() instanceof BlockSapling) {
			return () -> ((BlockSapling) sapling.getBlock()).generateTree(ws, event.getPos(), sapling, event.getWorld().rand);
		} else if (getClass("forestry.arboriculture.blocks.BlockSapling") == sapling.getBlock().getClass()) {
			return () -> {
				try {
					Method m = getClass("forestry.arboriculture.blocks.BlockSapling").getDeclaredMethod("grow", World.class, Random.class, BlockPos.class, IBlockState.class);
					m.invoke(sapling.getBlock(), ws, event.getWorld().rand, event.getPos(), sapling);
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
					e.printStackTrace();
				}
			};
		} else if (getClass("biomesoplenty.common.block.BlockBOPSapling") == sapling.getBlock().getClass()) {
			return () -> {
				try {
					Method m = getClass("biomesoplenty.common.block.BlockBOPSapling").getDeclaredMethod("generateTree", World.class, BlockPos.class, IBlockState.class, Random.class);
					m.invoke(sapling.getBlock(), ws, event.getPos(), sapling, event.getWorld().rand);
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
					e.printStackTrace();
				}
			};

		} else if (getClass("com.pam.harvestcraft.blocks.growables.BlockPamSapling") == sapling.getBlock().getClass()) {
			return () -> {
				try {
					Method m = getClass("com.pam.harvestcraft.blocks.growables.BlockPamSapling").getDeclaredMethod("grow", World.class, BlockPos.class, IBlockState.class, Random.class);
					m.setAccessible(true);
					m.invoke(sapling.getBlock(), ws, event.getPos(), sapling, event.getWorld().rand);
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
					e.printStackTrace();
				}
			};
		} else if (getClass("shetiphian.terraqueous.common.block.BlockTreeSapling") == sapling.getBlock().getClass()) {
			return () -> {
				try {
					Method m = getClass("shetiphian.terraqueous.common.block.BlockTreeSapling").getDeclaredMethod("grow", World.class, Random.class, BlockPos.class, IBlockState.class);
					m.invoke(sapling.getBlock(), ws, event.getWorld().rand, event.getPos(), sapling);
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
					e.printStackTrace();
				}
			};
		}

		return null;
	}

	@SubscribeEvent
	public void tryy(SaplingGrowTreeEvent event) {
		if (!(event.getWorld() instanceof PseudoWorld)) {
			Runnable run = get(event);
			if (run == null)
				return;
			PseudoWorld ws = pseudoWorldMap.get(event.getWorld().provider.getDimension());
			IBlockState sapling = event.getWorld().getBlockState(event.getPos());
			if (blacklistMods.contains(sapling.getBlock().getRegistryName().getResourcePath()))
				return;
			for (BlockPos p : BlockPos.getAllInBox(event.getPos().getX() - 9, 0, event.getPos().getZ() - 9, event.getPos().getX() + 9, event.getWorld().getActualHeight(), event.getPos().getZ() + 9)) {
				ws.setBlockState(p, event.getWorld().getBlockState(p));
				ws.setTileEntity(p, event.getWorld().getTileEntity(p));
			}
			/*TODO
			 * binnies extra tries
			*/
			ws.startTree(event.getPos());
			run.run();
			ws.endTree();
			event.setResult(Result.DENY);
		}
	}

	public static class PseudoWorld extends World {
		private Object2ObjectMap<BlockPos, IBlockState> blockMap = new Object2ObjectOpenHashMap<>();
		private Object2ObjectMap<BlockPos, TileEntity> tileMap = new Object2ObjectOpenHashMap<>();
		public World world;
		private boolean safeTree;
		BlockPos start;
		public ArrayDeque<Pair<BlockPos, IBlockState>> pairs = new ArrayDeque<>();
		public List<Tree> trees = new ArrayList<>();

		public PseudoWorld(World world) {
			super(null, world.getWorldInfo(), world.provider, world.getMinecraftServer().profiler, false);
			this.blockMap.defaultReturnValue(Blocks.AIR.getDefaultState());
			this.world = world;
			this.mapStorage = new MapStorage(null);
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
		public TileEntity getTileEntity(BlockPos pos) {
			return tileMap.get(pos);
		}

		@Override
		public void setTileEntity(BlockPos pos, TileEntity tileEntityIn) {
			if (tileEntityIn == null) {
				tileMap.put(pos, null);
				return;
			}
			TileEntity t = null;
			try {
				t = ConstructorUtils.invokeConstructor(tileEntityIn.getClass());
			} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
				e.printStackTrace();
			}
			if (t != null) {
				t.readFromNBT(tileEntityIn.writeToNBT(new NBTTagCompound()));
				t.setWorld(this);
				t.setPos(pos);
			}
			tileMap.put(pos, t);
		}

		@Override
		public IBlockState getBlockState(BlockPos pos) {
			return blockMap.get(pos);
		}

		@Override
		public boolean setBlockState(BlockPos pos, IBlockState newState, int flags) {
			final BlockPos pos2 = pos.toImmutable();
			blockMap.put(pos2, newState);
			setTileEntity(pos2, newState.getBlock().createTileEntity(this, newState));
			if (safeTree && newState.getBlock() != Blocks.AIR && !pairs.stream().anyMatch(p -> p.getKey().equals(pos2))) {
				pairs.add(Pair.of(pos2, newState));
			}
			return true;
		}

		@Override
		public void markChunkDirty(BlockPos pos, TileEntity unusedTileEntity) {
		}

		public void startTree(BlockPos sapling) {
			safeTree = true;
			start = sapling;
		}

		public void endTree() {
			safeTree = false;
			if (!pairs.isEmpty())
				trees.add(new Tree(this, start));
			pairs.clear();
			start = null;

		}

	}

	public static class Tree {
		List<Entry<BlockPos, IBlockState>> wood = new ArrayList<>();
		List<Entry<BlockPos, IBlockState>> leaves = new ArrayList<>();
		public final BlockPos start;
		public final int totalBlocks;
		public final Block sapling;

		public Tree(PseudoWorld world, BlockPos start) {
			this.start = start;
			this.sapling = world.getBlockState(start).getBlock();
			while (!world.pairs.isEmpty()) {
				Entry<BlockPos, IBlockState> pair = world.pairs.poll();
				if (pair.getValue().getMaterial() == Material.WOOD) {
					wood.add(pair);
				} else {
					leaves.add(pair);
				}
			}
			this.totalBlocks = wood.size() + leaves.size();
			if (wood.isEmpty())
				return;
			wood.sort((p1, p2) -> {
				int foo = Integer.compare(p1.getKey().getY(), p2.getKey().getY());
				if (foo == 0)
					foo = Double.compare(p1.getKey().distanceSq(start), p2.getKey().distanceSq(start));
				return foo;
			});
			BlockPos firstWood = wood.get(wood.size() - 1).getKey();
			BlockPos midWood = wood.get(MathHelper.clamp(wood.size() / 2, 0, wood.size() - 1)).getKey();
			BlockPos foo = midWood;
			if (midWood.getY() < this.start.getY())
				Collections.reverse(wood);
			leaves.sort((p1, p2) -> Double.compare(p1.getKey().getDistance(foo.getX(), foo.getY(), foo.getZ()), p2.getKey().getDistance(foo.getX(), foo.getY(), foo.getZ())));
		}

		public boolean isEmpty() {
			return wood.isEmpty() && leaves.isEmpty();
		}

		public Entry<BlockPos, IBlockState> next() {
			if (!wood.isEmpty())
				return wood.remove(0);
			else if (!leaves.isEmpty())
				return leaves.remove(0);
			else
				return null;
		}
	}

}
