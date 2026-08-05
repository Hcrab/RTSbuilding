package com.rtsbuilding.rtsbuilding.network.pathfinding;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;import cpw.mods.fml.relauncher.Side;
public final class RtsPathfindingPackets{private RtsPathfindingPackets(){}public static void register(){RtsPayloadRegistrar.registerMessage(163,RtsPathfindingNetworkHandlers.Handler.class,C2SRtsPathfindingPayload.class,Side.SERVER);}}
