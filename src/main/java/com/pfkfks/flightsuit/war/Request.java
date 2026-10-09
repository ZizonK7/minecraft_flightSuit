package com.pfkfks.flightsuit.war;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A kingdom asking the player for help (DESIGN.md 4-11 외교와 의뢰): defend our fortress on a given day, lend us
 * soldiers for a few days while we regroup, or march with us on another kingdom (Three Kingdoms only).
 */
public class Request {
    public enum Type {
        /** "N일 뒤 ○○군이 쳐들어온다, 도와달라": be at their fortress when the attack comes. */
        DEFEND,
        /** "재정비할 동안 며칠만 병력을 보내 달라": send soldier residents for a few days. */
        REINFORCE,
        /** "○○를 칠 건데 함께 가자": be at the target fortress on the day; their army attacks with you. */
        INVADE
    }

    public enum State { OFFERED, ACCEPTED, ACTIVE }

    public final int id;
    public final UUID player;
    public final Kingdom kingdom;
    public final Type type;
    /** The attacker (DEFEND) or the fortress to take (INVADE). */
    public final @Nullable Kingdom target;
    public final long offeredDay;
    /** The day it happens (DEFEND, INVADE) or the last day to send the soldiers (REINFORCE). */
    public long day;
    /** REINFORCE: how many soldiers, and for how many days. */
    public int needed;
    public int days;
    public State state = State.OFFERED;
    /** REINFORCE: the soldiers away (saved entities), the village they come back to, the day they return. */
    public ListTag away = new ListTag();
    public String villageKey = "";
    public long returnDay;
    /** The battle this request is fought in (-1 = none yet). */
    public int battle = -1;
    public boolean reminded;

    Request(int id, UUID player, Kingdom kingdom, Type type, @Nullable Kingdom target, long offeredDay) {
        this.id = id;
        this.player = player;
        this.kingdom = kingdom;
        this.type = type;
        this.target = target;
        this.offeredDay = offeredDay;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putUUID("Player", player);
        tag.putInt("Kingdom", kingdom.ordinal());
        tag.putInt("Type", type.ordinal());
        tag.putInt("Target", target == null ? -1 : target.ordinal());
        tag.putLong("Offered", offeredDay);
        tag.putLong("Day", day);
        tag.putInt("Needed", needed);
        tag.putInt("Days", days);
        tag.putInt("State", state.ordinal());
        tag.put("Away", away);
        tag.putString("Village", villageKey);
        tag.putLong("Return", returnDay);
        tag.putInt("Battle", battle);
        tag.putBoolean("Reminded", reminded);
        return tag;
    }

    static @Nullable Request load(CompoundTag tag) {
        if (!tag.hasUUID("Player")) {
            return null;
        }
        int type = tag.getInt("Type");
        int target = tag.getInt("Target");
        Request request = new Request(tag.getInt("Id"), tag.getUUID("Player"), Kingdom.byId(tag.getInt("Kingdom")),
                Type.values()[Math.max(0, Math.min(Type.values().length - 1, type))], target < 0 ? null : Kingdom.byId(target),
                tag.getLong("Offered"));
        request.day = tag.getLong("Day");
        request.needed = tag.getInt("Needed");
        request.days = tag.getInt("Days");
        int state = tag.getInt("State");
        request.state = State.values()[Math.max(0, Math.min(State.values().length - 1, state))];
        request.away = tag.getList("Away", Tag.TAG_COMPOUND);
        request.villageKey = tag.getString("Village");
        request.returnDay = tag.getLong("Return");
        request.battle = tag.contains("Battle") ? tag.getInt("Battle") : -1;
        request.reminded = tag.getBoolean("Reminded");
        return request;
    }
}
