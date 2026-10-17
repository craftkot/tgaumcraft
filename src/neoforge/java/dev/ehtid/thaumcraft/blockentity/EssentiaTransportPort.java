package dev.ehtid.thaumcraft.blockentity;

import net.minecraft.core.Direction;

/**
 * Loaded, server-owned BETA26 IEssentiaTransport surface. Transfer methods
 * debit/credit actual persisted block entities, never create free essentia.
 * A connection is valid only when BOTH endpoints expose opposite faces.
 */
public interface EssentiaTransportPort {
    boolean transportConnects(Direction face);
    boolean canTransportOutput(Direction face);
    boolean canTransportInput(Direction face);
    int transportSuction(Direction face);
    String transportSuctionAspect(Direction face);
    int transportMinimumSuction();
    int transportAmount(Direction face);
    String transportAspect(Direction face);
    int takeTransport(String aspect, int amount, Direction face);
    int addTransport(String aspect, int amount, Direction face);
}
