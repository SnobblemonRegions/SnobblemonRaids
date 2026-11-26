package com.cobbleraids.showdown;

import com.cobbleraids.RaidLog;
import com.cobbleraids.config.CobbleRaidsConfigManager;
import com.cobbleraids.encounter.EncounterService;
import com.cobbleraids.raid.RaidSession;
import com.cobbleraids.renown.RaidPoolAffix;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.dispatch.InstructionSet;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;

import java.util.UUID;

/** Applies shared raid damage, credits it to whoever dealt it, and ends the raid when the pool empties. */
public final class RaidDamageInstruction extends RaidPoolInstruction {

    public RaidDamageInstruction(InstructionSet instructionSet, BattleActor actor, BattleMessage publicMessage, BattleMessage privateMessage) {
        super(instructionSet, actor, publicMessage, privateMessage);
    }

    @Override
    protected void applyToPool(RaidSession raid, PokemonBattle battle, float amount) {
        UUID contributor = resolveContributor(battle);
        RaidPoolAffix poolAffix = CobbleRaidsConfigManager.get().renown().affixes().enabled()
                ? raid.getPoolAffix() : RaidPoolAffix.none();
        boolean lastStandBefore = poolAffix.lastStandFired();
        float requested = poolAffix.adjustDamage(raid.getCurrentHealth(), raid.getMaxHealth(), amount);
        float applied = raid.damage(contributor, requested);
        EncounterService.notifyDamage(raid, contributor, applied);
        if (poolAffix.affix() != null) applyPoolAffix(raid, battle, poolAffix, lastStandBefore);

        if (CobbleRaidsConfigManager.get().debugLogging()) {
            RaidLog.info("Applied raid damage: battle={}, requested={}, applied={}, hp={}/{}, contributor={}",
                    battle.getBattleId(), amount, applied, raid.getCurrentHealth(), raid.getMaxHealth(), contributor);
        }
    }

    /**
     * The boss's HP-pool affix, after the hit has landed. Runs in the same instruction as the damage, before the
     * health packets go out (see RaidPoolInstruction#invoke), so the bar players see already includes a
     * Second Wind heal. Unbroken is told to Showdown, which owns stat drops, through >cobbleaffixpool.
     */
    private static void applyPoolAffix(RaidSession raid, PokemonBattle battle, RaidPoolAffix poolAffix,
                                       boolean lastStandBefore) {
        String name = poolAffix.affix().displayName();
        if (!lastStandBefore && poolAffix.lastStandFired()) {
            battle.broadcastChatMessage(Component.literal("The boss's " + name + " held it on its feet!")
                    .withStyle(ChatFormatting.GOLD));
        }
        float heal = poolAffix.healAfterDamage(raid.getCurrentHealth(), raid.getMaxHealth());
        if (heal > 0f) {
            raid.heal(heal);
            battle.broadcastChatMessage(Component.literal("The boss's " + name + " restored some of its health!")
                    .withStyle(ChatFormatting.GOLD));
        }
        if (poolAffix.unbrokenEndsNow(raid.getCurrentHealth(), raid.getMaxHealth())) {
            battle.writeShowdownAction(">cobbleaffixpool unbroken_ended");
            battle.broadcastChatMessage(Component.literal("The boss is no longer " + name + ": its stats can be lowered now.")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /** A won raid is closed out here rather than on a tick, so the victory lands in the same batch. */
    @Override
    protected void afterPool(RaidSession raid, PokemonBattle battle) {
        if (raid.getStatus() == RaidSession.Status.COMPLETED) RaidCompletion.complete(battle);
    }

    /**
     * Who dealt the damage, or null if it came from nobody in particular (weather, a status, the
     * boss hurting itself).
     *
     * <p>Two ways of asking, because Showdown does not always give the same one. The structured
     * lookup is preferred; when it comes back empty the source argument still carries a raw actor
     * token like {@code "p3a"}, whose first two characters name the actor.
     */
    private UUID resolveContributor(PokemonBattle battle) {
        BattlePokemon source = publicMessage.battlePokemon(2, battle);
        if (source != null && source.getActor() != null) return source.getActor().getUuid();

        String sourceArgument = publicMessage.argumentAt(2);
        if (sourceArgument == null || sourceArgument.length() < 2) return null;
        BattleActor sourceActor = battle.getActor(sourceArgument.substring(0, 2));
        return sourceActor == null ? null : sourceActor.getUuid();
    }
}
