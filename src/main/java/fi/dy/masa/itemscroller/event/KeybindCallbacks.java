package fi.dy.masa.itemscroller.event;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.StonecuttingRecipe;
import net.minecraft.screen.LoomScreenHandler;
import net.minecraft.screen.StonecutterScreenHandler;
import net.minecraft.screen.slot.Slot;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.hotkeys.KeyCallbackToggleBooleanConfigWithMessage;
import fi.dy.masa.malilib.interfaces.IClientTickHandler;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.itemscroller.ItemScroller;
import fi.dy.masa.itemscroller.config.Configs;
import fi.dy.masa.itemscroller.config.Hotkeys;
import fi.dy.masa.itemscroller.gui.GuiConfigs;
import fi.dy.masa.itemscroller.recipes.CraftingHandler;
import fi.dy.masa.itemscroller.recipes.RecipePattern;
import fi.dy.masa.itemscroller.recipes.RecipeStorage;
import fi.dy.masa.itemscroller.util.AccessorUtils;
import fi.dy.masa.itemscroller.util.InputUtils;
import fi.dy.masa.itemscroller.util.InventoryUtils;
import fi.dy.masa.itemscroller.util.MoveAction;

import java.util.List;
import java.util.Objects;

import static fi.dy.masa.itemscroller.util.InventoryUtils.shiftClickSlot;
import static fi.dy.masa.itemscroller.util.InventoryUtils.tryMoveItemsToCraftingGridSlots;

public class KeybindCallbacks implements IHotkeyCallback, IClientTickHandler {
    private static final KeybindCallbacks INSTANCE = new KeybindCallbacks();

    protected int massCraftTicker;

    public static KeybindCallbacks getInstance() {
        return INSTANCE;
    }

    private KeybindCallbacks() {
    }

    public void setCallbacks() {
        for (ConfigHotkey hotkey : Hotkeys.HOTKEY_LIST) {
            hotkey.getKeybind().setCallback(this);
        }

        Hotkeys.MASS_CRAFT_TOGGLE.getKeybind()
                .setCallback(new KeyCallbackToggleBooleanConfigWithMessage(Configs.Generic.MASS_CRAFT_HOLD));
    }

    public boolean functionalityEnabled() {
        return Configs.Generic.MOD_MAIN_TOGGLE.getBooleanValue();
    }

    @Override
    public boolean onKeyAction(KeyAction action, IKeybind key) {
        boolean cancel = this.onKeyActionImpl(action, key);
        return cancel;
    }

    private boolean onKeyActionImpl(KeyAction action, IKeybind key) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.world == null) {
            return false;
        }

        if (key == Hotkeys.TOGGLE_MOD_ON_OFF.getKeybind()) {
            Configs.Generic.MOD_MAIN_TOGGLE.toggleBooleanValue();
            String msg = this.functionalityEnabled() ? "itemscroller.message.toggled_mod_on"
                    : "itemscroller.message.toggled_mod_off";
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO, msg);
            return true;
        } else if (key == Hotkeys.OPEN_CONFIG_GUI.getKeybind()) {
            GuiBase.openGui(new GuiConfigs());
            return true;
        }

        if (this.functionalityEnabled() == false ||
                (GuiUtils.getCurrentScreen() instanceof HandledScreen) == false ||
                Configs.GUI_BLACKLIST.contains(GuiUtils.getCurrentScreen().getClass().getName())) {
            return false;
        }

        HandledScreen<?> gui = (HandledScreen<?>) GuiUtils.getCurrentScreen();
        Slot slot = AccessorUtils.getSlotUnderMouse(gui);
        RecipeStorage recipes = RecipeStorage.getInstance();
        MoveAction moveAction = InputUtils.getDragMoveAction(key);

        if (slot != null) {
            if (moveAction != MoveAction.NONE) {
                final int mouseX = fi.dy.masa.malilib.util.InputUtils.getMouseX();
                final int mouseY = fi.dy.masa.malilib.util.InputUtils.getMouseY();
                return InventoryUtils.dragMoveItems(gui, moveAction, mouseX, mouseY, true);
            } else if (key == Hotkeys.KEY_MOVE_EVERYTHING.getKeybind()) {
                InventoryUtils.tryMoveStacks(slot, gui, false, true, false);
                return true;
            } else if (key == Hotkeys.DROP_ALL_MATCHING.getKeybind()) {
                if (Configs.Toggles.DROP_MATCHING.getBooleanValue() &&
                        Configs.GUI_BLACKLIST.contains(gui.getClass().getName()) == false &&
                        slot.hasStack()) {
                    InventoryUtils.dropStacks(gui, slot.getStack(), slot, true);
                    return true;
                }
            }
        }

        if (key == Hotkeys.CRAFT_EVERYTHING.getKeybind()) {
            InventoryUtils.craftEverythingPossibleWithCurrentRecipe(recipes.getSelectedRecipe(), gui);
            return true;
        } else if (key == Hotkeys.THROW_CRAFT_RESULTS.getKeybind()) {
            InventoryUtils.throwAllCraftingResultsToGround(recipes.getSelectedRecipe(), gui);
            return true;
        } else if (key == Hotkeys.MOVE_CRAFT_RESULTS.getKeybind()) {
            InventoryUtils.moveAllCraftingResultsToOtherInventory(recipes.getSelectedRecipe(), gui);
            return true;
        } else if (key == Hotkeys.STORE_RECIPE.getKeybind()) {
            if (InputUtils.isRecipeViewOpen() && InventoryUtils.isCraftingSlot(gui, slot)) {
                recipes.storeCraftingRecipeToCurrentSelection(slot, gui, true);
                return true;
            }
        } else if (key == Hotkeys.VILLAGER_TRADE_FAVORITES.getKeybind()) {
            return InventoryUtils.villagerTradeEverythingPossibleWithAllFavoritedTrades();
        } else if (key == Hotkeys.SLOT_DEBUG.getKeybind()) {
            if (slot != null) {
                InventoryUtils.debugPrintSlotInfo(gui, slot);
            } else {
                ItemScroller.logger.info("GUI class: {}", gui.getClass().getName());
            }

            return true;
        }

        return false;
    }

    @Override
    public void onClientTick(MinecraftClient mc) {
        if (this.functionalityEnabled() == false || mc.player == null) {
            return;
        }

        if (GuiUtils.getCurrentScreen() instanceof HandledScreen
                && (GuiUtils.getCurrentScreen() instanceof CreativeInventoryScreen) == false
                && Configs.GUI_BLACKLIST.contains(GuiUtils.getCurrentScreen().getClass().getName()) == false
                && (Hotkeys.MASS_CRAFT.getKeybind().isKeybindHeld()
                        || Configs.Generic.MASS_CRAFT_HOLD.getBooleanValue())) {

            if (++this.massCraftTicker < Configs.Generic.MASS_CRAFT_INTERVAL.getIntegerValue()) {
                return;
            }

            Screen guiScreen = GuiUtils.getCurrentScreen();
            HandledScreen<?> gui = (HandledScreen<?>) guiScreen;
            Slot outputSlot = CraftingHandler.getFirstCraftingOutputSlotForGui(gui);

            if (outputSlot != null) {
                RecipePattern recipe = RecipeStorage.getInstance().getSelectedRecipe();

                CraftingRecipe bookRecipe = InventoryUtils.getBookRecipeFromPattern(recipe);
                if (bookRecipe != null && !bookRecipe.isIgnoredInRecipeBook()) { // Use recipe book if possible
                    // System.out.println("recipe");
                    if (gui instanceof StonecutterScreen && recipe.getRecipeLength() == 1) {
                        Slot slot = CraftingHandler.getFirstCraftingOutputSlotForGui(gui);
                        tryMoveItemsToCraftingGridSlots(recipe, slot, gui, true);
                        List<StonecuttingRecipe> recipes = ((StonecutterScreenHandler) gui.getScreenHandler()).getAvailableRecipes();
                        for (int j = 0; j < recipes.size(); j++) {
                            if (recipe.getResult().getItem().toString().equals(recipes.get(j).getOutput(null).getItem().toString())) {
                                mc.interactionManager.clickButton(gui.getScreenHandler().syncId, j);
                                shiftClickSlot(gui, 1);
                                break;
                            }
                        }
                    } else if (recipe.getRecipeLength() != 1){
                        mc.interactionManager.clickRecipe(gui.getScreenHandler().syncId, bookRecipe, true);
                    }
                } else {
                    InventoryUtils.tryMoveItemsToFirstCraftingGrid(recipe, gui, true);
                    if (gui instanceof EnchantmentScreen) mc.interactionManager.clickButton(gui.getScreenHandler().syncId, 0);
                    for (int i = 0; i < recipe.getMaxCraftAmount(); i++) {
                        InventoryUtils.dropStack(gui, outputSlot.id);
                    }
                    if (gui instanceof EnchantmentScreen || gui instanceof GrindstoneScreen || gui instanceof CartographyTableScreen ||
                            gui instanceof SmithingScreen || gui instanceof AnvilScreen) {
                        this.massCraftTicker = 0;
                        return;
                    }
                }

                for (int i = 0; i < recipe.getMaxCraftAmount(); i++) {
                    InventoryUtils.dropStack(gui, outputSlot.id);
                }

                InventoryUtils.tryClearCursor(gui);
                InventoryUtils.throwAllCraftingResultsToGround(recipe, gui);
            }

            this.massCraftTicker = 0;
        }
    }
}
