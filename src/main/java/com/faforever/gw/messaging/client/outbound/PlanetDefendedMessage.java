package com.faforever.gw.messaging.client.outbound;

import com.faforever.gw.messaging.client.Audience;
import com.faforever.gw.model.Faction;
import lombok.Data;

import java.util.UUID;

@Data
public class PlanetDefendedMessage extends OutboundClientMessage {
    private UUID planetId;
    private UUID battleId;
    private Faction attackingFaction;
    private Faction defendingFaction;

    public PlanetDefendedMessage(UUID planetId, UUID battleId, Faction attackingFaction, Faction defendingFaction) {
        this.planetId = planetId;
        this.battleId = battleId;
        this.attackingFaction = attackingFaction;
        this.defendingFaction = defendingFaction;
    }

    @Override
    public Audience getAudience() {
        return Audience.PUBLIC;
    }
}
