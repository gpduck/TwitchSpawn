package net.programmer.igoodie.twitchspawn.tslanguage.action;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.programmer.igoodie.twitchspawn.tslanguage.EventArguments;
import net.programmer.igoodie.twitchspawn.tslanguage.parser.TSLParser;
import net.programmer.igoodie.twitchspawn.tslanguage.parser.TSLSyntaxError;
import net.programmer.igoodie.twitchspawn.util.ItemParser;

import java.util.LinkedList;
import java.util.List;

public class ChangeAction extends ItemSelectiveAction {

    private String itemRaw;
    private int itemAmount;

    /*
     * Exemplar valid TSL:
     * CHANGE helmet INTO apple
     * CHANGE slot 21 FROM inventory INTO %stick{display:{Name:"\"Stick of Truth!\""}}%
     * CHANGE inventory INTO slime_ball{display:{Name:"\"Ewwww\""}}
     */
    public ChangeAction(List<String> words) throws TSLSyntaxError {
        this.message = TSLParser.parseMessage(words);
        List<String> actionWords = actionPart(words);

        List<String> slotSelectionPart = new LinkedList<>();
        List<String> itemPart = new LinkedList<>();
        splitParts(actionWords, slotSelectionPart, itemPart);

        // Parse slot selection part
        parseSlot(slotSelectionPart);

        // Parse item part
        parseItem(itemPart);
    }

    private void splitParts(List<String> actionWords, List<String> slotSelectionPart, List<String> itemPart) throws TSLSyntaxError {
        long delimiterCount = actionWords.stream()
                .filter(word -> word.equalsIgnoreCase("INTO")).count();

        if (delimiterCount != 1)
            throw new TSLSyntaxError("Expected exactly one INTO word.");

        List<String> filling = slotSelectionPart;

        for (String actionWord : actionWords) {
            if (actionWord.equalsIgnoreCase("INTO")) {
                filling = itemPart;
                continue;
            }
            filling.add(actionWord);
        }
    }

    private void parseSlot(List<String> slotSelectionPart) throws TSLSyntaxError {
        long countFrom = slotSelectionPart.stream()
                .filter(word -> word.equalsIgnoreCase("FROM")).count();

        if (countFrom == 0) parseSingleWord(slotSelectionPart);
        else if (countFrom == 1) parseFrom(slotSelectionPart);
        else throw new TSLSyntaxError("At most 1 FROM statement expected, found %d instead.", countFrom);
    }

    private void parseItem(List<String> itemPart) throws TSLSyntaxError {
        if (itemPart.size() != 1 && itemPart.size() != 2)
            throw new TSLSyntaxError("Invalid length of item words: " + itemPart);

        this.itemRaw = itemPart.get(0);

        try {
            this.itemAmount = itemPart.size() != 2 ? 1 : Integer.parseInt(itemPart.get(1));

        } catch (NumberFormatException e) {
            throw new TSLSyntaxError("Expected an integer, found instead -> %s", itemPart.get(1));
        }

        // Check if given item word is parse-able
        if (!new ItemParser(this.itemRaw).isValid())
            throw new TSLSyntaxError("Invalid item text");
    }

    @Override
    protected void performAction(EntityPlayerMP player, EventArguments args) {
        ItemStack itemStack = createItemStack(args);

        if (selectionType == SelectionType.WITH_INDEX) {
            getInventory(player, inventoryType).set(inventoryIndex, itemStack.copy());

        } else if (selectionType == SelectionType.EVERYTHING) {
            if (inventoryType == null) {
                setAll(player.inventory.mainInventory, itemStack);
                setAll(player.inventory.armorInventory, itemStack);
                setAll(player.inventory.offHandInventory, itemStack);

            } else {
                setAll(getInventory(player, inventoryType), itemStack);
            }

        } else if (selectionType == SelectionType.RANDOM) {
            InventorySlot randomSlot = inventoryType == null
                    ? randomInventorySlot(player, true)
                    : randomInventorySlot(getInventory(player, inventoryType), true);
            if (randomSlot != null) {
                randomSlot.inventory.set(randomSlot.index, itemStack.copy());
            }

        } else if (selectionType == SelectionType.ONLY_HELD_ITEM) {
            int selectedHotbarIndex = player.inventory.currentItem;
            player.inventory.mainInventory.set(selectedHotbarIndex, itemStack.copy());

        } else if (selectionType == SelectionType.HOTBAR) {
            for (int i = 0; i <= 8; i++) {
                player.inventory.mainInventory.set(i, itemStack.copy());
            }
        }

        ICommandSender commandSender = getCommandSender(player, true, true);

        player.getServer().getCommandManager().executeCommand(commandSender,
                "/playsound minecraft:item.armor.equip_leather master @s");
        player.getServer().getCommandManager().executeCommand(commandSender,
                "/particle enchantmenttable ~ ~ ~ 2 2 2 0.1 400");
    }

    private void setAll(NonNullList<ItemStack> inventory, ItemStack itemStack) {
        for (int i = 0; i < inventory.size(); i++) {
            inventory.set(i, itemStack.copy());
        }
    }

    private ItemStack createItemStack(EventArguments args) {
        String input = replaceExpressions(itemRaw, args);
        return new ItemParser(input).generateItemStack(itemAmount);
    }

}
