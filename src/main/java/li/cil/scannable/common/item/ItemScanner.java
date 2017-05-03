package li.cil.scannable.common.item;

import li.cil.scannable.client.ScanManager;
import li.cil.scannable.common.Scannable;
import li.cil.scannable.common.capabilities.CapabilityScanResultProvider;
import li.cil.scannable.common.config.Constants;
import li.cil.scannable.common.config.Settings;
import li.cil.scannable.common.gui.GuiId;
import li.cil.scannable.common.inventory.ItemScannerCapabilityProvider;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class ItemScanner extends Item {
    public ItemScanner() {
        setMaxStackSize(1);
    }

    // --------------------------------------------------------------------- //
    // Item

    @Override
    public ICapabilityProvider initCapabilities(final ItemStack stack, @Nullable final NBTTagCompound nbt) {
        return new ItemScannerCapabilityProvider(stack);
    }

    @Override
    public EnumAction getItemUseAction(final ItemStack stack) {
        return EnumAction.BLOCK;
    }

    @Override
    public void getSubItems(final Item item, final CreativeTabs tab, final NonNullList<ItemStack> subItems) {
        super.getSubItems(item, tab, subItems);
        if (Settings.useEnergy) {
            final ItemStack stack = new ItemStack(item);
            final IEnergyStorage energyStorage = stack.getCapability(CapabilityEnergy.ENERGY, null);
            if (energyStorage != null) {
                energyStorage.receiveEnergy(energyStorage.getMaxEnergyStored(), false);
                subItems.add(stack);
            }
        }
    }

    @Override
    public void addInformation(final ItemStack stack, final EntityPlayer playerIn, final List<String> tooltip, final boolean advanced) {
        super.addInformation(stack, playerIn, tooltip, advanced);
        tooltip.add(I18n.format(Constants.TOOLTIP_SCANNER));
        if (Settings.useEnergy) {
            final IEnergyStorage energyStorage = stack.getCapability(CapabilityEnergy.ENERGY, null);
            if (energyStorage != null) {
                tooltip.add(I18n.format(Constants.TOOLTIP_SCANNER_ENERGY, energyStorage.getEnergyStored(), energyStorage.getMaxEnergyStored()));
            }
        }
    }

    @Override
    public boolean showDurabilityBar(final ItemStack stack) {
        return Settings.useEnergy;
    }

    @Override
    public double getDurabilityForDisplay(final ItemStack stack) {
        if (!Settings.useEnergy) {
            return 0;
        }

        final IEnergyStorage energyStorage = stack.getCapability(CapabilityEnergy.ENERGY, null);
        if (energyStorage == null) {
            return 1;
        }

        return 1 - energyStorage.getEnergyStored() / (float) energyStorage.getMaxEnergyStored();
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(final World world, final EntityPlayer player, final EnumHand hand) {
        final ItemStack stack = player.getHeldItem(hand);
        if (player.isSneaking()) {
            player.openGui(Scannable.instance, GuiId.SCANNER.id, world, hand.ordinal(), 0, 0);
        } else {
            if (!tryConsumeEnergy(stack)) {
                return new ActionResult<>(EnumActionResult.FAIL, stack);
            }

            final List<ItemStack> modules = new ArrayList<>();
            if (!collectModules(stack, modules)) {
                return new ActionResult<>(EnumActionResult.FAIL, stack);
            }

            player.setActiveHand(hand);
            if (world.isRemote) {
                ScanManager.INSTANCE.beginScan(player, modules);
            }
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public boolean shouldCauseReequipAnimation(final ItemStack oldStack, final ItemStack newStack, final boolean slotChanged) {
        return oldStack.getItem() != newStack.getItem() || slotChanged;
    }

    @Override
    public int getMaxItemUseDuration(final ItemStack stack) {
        return Constants.SCAN_COMPUTE_DURATION;
    }

    @Override
    public void onUsingTick(final ItemStack stack, final EntityLivingBase entity, final int count) {
        if (entity.getEntityWorld().isRemote) {
            ScanManager.INSTANCE.updateScan(entity, stack, false);
        }
    }

    @Override
    public void onPlayerStoppedUsing(final ItemStack stack, final World world, final EntityLivingBase entity, final int timeLeft) {
        if (world.isRemote) {
            ScanManager.INSTANCE.cancelScan();
        }
        super.onPlayerStoppedUsing(stack, world, entity, timeLeft);
    }

    @Override
    public ItemStack onItemUseFinish(final ItemStack stack, final World world, final EntityLivingBase entity) {
        if (world.isRemote) {
            ScanManager.INSTANCE.updateScan(entity, stack, true);
        }
        if (entity instanceof EntityPlayer) {
            final EntityPlayer player = (EntityPlayer) entity;
            player.getCooldownTracker().setCooldown(this, 40);
        }
        return stack;
    }

    // --------------------------------------------------------------------- //

    private boolean tryConsumeEnergy(final ItemStack stack) {
        if (!Settings.useEnergy) {
            return true;
        }

        final IEnergyStorage energyStorage = stack.getCapability(CapabilityEnergy.ENERGY, null);
        if (energyStorage == null) {
            return false;
        }

        final int extracted = energyStorage.extractEnergy(Constants.SCANNER_ENERGY_COST, true);
        if (extracted < Constants.SCANNER_ENERGY_COST) {
            return false;
        }

        return true;
    }

    private boolean collectModules(final ItemStack stack, final List<ItemStack> modules) {
        boolean hasProvider = false;
        final IItemHandler scannerInventory = stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        assert scannerInventory != null;
        for (int slot = 0; slot < scannerInventory.getSlots(); slot++) {
            final ItemStack module = scannerInventory.getStackInSlot(slot);
            if (module.isEmpty()) {
                continue;
            }

            modules.add(module);
            if (module.hasCapability(CapabilityScanResultProvider.SCAN_RESULT_PROVIDER_CAPABILITY, null)) {
                hasProvider = true;
            }
        }
        return hasProvider;
    }
}
