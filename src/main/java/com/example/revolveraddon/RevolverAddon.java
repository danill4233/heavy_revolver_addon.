package com.example.revolveraddon;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(RevolverAddon.MODID)
public class RevolverAddon {
    public static final String MODID = "revolver_addon";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(MODID);

    /** Сколько патронов сейчас в барабане. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ROUNDS =
            COMPONENTS.registerComponentType("rounds",
                    b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredItem<Item> HEAVY_REVOLVER = ITEMS.register("heavy_revolver",
            () -> new HeavyRevolverItem(new Item.Properties().stacksTo(1).durability(500)));

    public static final DeferredItem<Item> HEAVY_ROUND = ITEMS.register("heavy_round",
            () -> new Item(new Item.Properties()));

    public RevolverAddon(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(this::addToTabs);
    }

    private void addToTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.COMBAT) {
            e.accept(HEAVY_REVOLVER.get());
            e.accept(HEAVY_ROUND.get());
        }
    }
}
