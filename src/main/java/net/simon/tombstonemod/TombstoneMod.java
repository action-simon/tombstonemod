package net.simon.tombstonemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.ArrayList;
import java.util.List;

@Mod(TombstoneMod.MOD_ID)
public class TombstoneMod
{
    public static final String MOD_ID = "tombstonemod";
    private static final int TNT_EXPLOSIONS_COUNT = 1000;
    // Directly reference a slf4j logger
    // public static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings("removal")
    public TombstoneMod()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);


        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
    }

    private void sendRespawnMessage(Player player) {
        player.sendSystemMessage(Component.literal("§6§lYou have fallen in battle, brave adventurer. §r§6Your items await you at the place of your demise. §r§6Embark on a quest to reclaim your lost treasures and continue your epic journey!"));
    }


    private void handlePlayerDeath(Player player) {
        if (player.getCommandSenderWorld() instanceof ServerLevel world) {
            BlockPos chestPos1 = player.blockPosition();
            BlockPos chestPos2 = chestPos1.relative(Direction.EAST);

            createChests(world, chestPos1, chestPos2);
            transferItemsToChests(player, chestPos1, chestPos2);
        }
    }


    private void createChests(ServerLevel world, BlockPos chestPos1, BlockPos chestPos2) {
        BlockState chestState1 = createChestBlockState(ChestType.LEFT);
        BlockState chestState2 = createChestBlockState(ChestType.RIGHT);

        world.setBlock(chestPos1, chestState1, 3);
        world.setBlock(chestPos2, chestState2, 3);
    }

    private BlockState createChestBlockState(ChestType chestType) {
        return Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, chestType);
    }

    private void transferItemsToChests(Player player, BlockPos chestPos1, BlockPos chestPos2) {
        ServerLevel world = (ServerLevel) player.getCommandSenderWorld();
        BlockEntity blockEntity1 = world.getBlockEntity(chestPos1);
        BlockEntity blockEntity2 = world.getBlockEntity(chestPos2);

        if (blockEntity1 == null || blockEntity2 == null) {
            player.sendSystemMessage(Component.literal("§cError: Unable to create chests. Please contact support."));
            return;
        }

        if (blockEntity1 instanceof ChestBlockEntity chestEntity1 &&
                blockEntity2 instanceof ChestBlockEntity chestEntity2) {
            List<ItemStack> allItems = collectItems(player);

            // First, clear the player's inventory after collecting items
            player.getInventory().clearContent();


            int totalSlots = chestEntity1.getContainerSize() + chestEntity2.getContainerSize();
            for (int i = 0; i < allItems.size() && i < totalSlots; i++) {
                if (i < chestEntity1.getContainerSize()) {
                    chestEntity1.setItem(i, allItems.get(i));
                } else {
                    chestEntity2.setItem(i - chestEntity1.getContainerSize(), allItems.get(i));
                }
            }

            dropRemainingItems(allItems, totalSlots, player);
        }
    }

    private List<ItemStack> collectItems(Player player) {
        List<ItemStack> allItems = new ArrayList<>();
        for (ItemStack item : player.getInventory().items) {
            if (!item.isEmpty()) {
                allItems.add(item.copy());
            }
        }
        for (ItemStack item : player.getInventory().armor) {
            if (!item.isEmpty()) {
                allItems.add(item.copy());
            }
        }
        return allItems;
    }

    private void dropRemainingItems(List<ItemStack> allItems, int totalSlots, Player player) {
        for (int i = totalSlots; i < allItems.size(); i++) {
            player.drop(allItems.get(i), false);
        }
    }

    private void triggerTNTExplosions(ServerLevel world, BlockPos pos) {
        for (int i = 0; i < TNT_EXPLOSIONS_COUNT; i++) {
            BlockPos tntPos = getRandomOffsetPosition(world, pos);
            createAndAddTNTEntity(world, tntPos);
        }
    }

    private void createAndAddTNTEntity(ServerLevel world, BlockPos tntPos) {
        PrimedTnt tnt = EntityType.TNT.create(world);
        if (tnt != null) {
            tnt.setPos(tntPos.getX() + 0.5, tntPos.getY(), tntPos.getZ() + 0.5);
            tnt.setFuse(1 + world.random.nextInt(5)); // Set fuse time between 40-60 ticks
            world.addFreshEntity(tnt);
        }
    }

    private BlockPos getRandomOffsetPosition(ServerLevel world, BlockPos pos) {
        double offsetX = (world.random.nextDouble() - 0.5) * 20;
        double offsetY = (world.random.nextDouble() * 20);
        double offsetZ = (world.random.nextDouble() - 0.5) * 20;
        return pos.offset((int) offsetX, (int) offsetY, (int) offsetZ);
    }


    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        ServerLevel world = (ServerLevel) player.getCommandSenderWorld();

        if (state.getBlock() == Blocks.PINK_WOOL) {
            player.sendSystemMessage(Component.literal("§6§lBoom!"));
            triggerTNTExplosions(world, pos);
        }
    }


    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event)
    {
        if (event.getEntity() instanceof Player player) {
            handlePlayerDeath(player);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        Player player = event.getEntity();
        sendRespawnMessage(player);
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
        }
    }
}