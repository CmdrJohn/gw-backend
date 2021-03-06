package com.faforever.gw.bpmn.message.planetary_assault;

import com.faforever.gw.bpmn.accessors.PlanetaryAssaultAccessor;
import com.faforever.gw.messaging.client.ClientMessagingService;
import com.faforever.gw.messaging.client.outbound.PlanetDefendedMessage;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

@Slf4j
@Component
public class PlanetDefendedNotification implements JavaDelegate {
    private final ClientMessagingService clientMessagingService;

    @Inject
    public PlanetDefendedNotification(ClientMessagingService clientMessagingService) {
        this.clientMessagingService = clientMessagingService;
    }

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        PlanetaryAssaultAccessor accessor = PlanetaryAssaultAccessor.of(execution);

        val planetId = accessor.getPlanetId();
        val battleId = accessor.getBattleId();
        val attackingFaction = accessor.getAttackingFaction();
        val defendingFaction = accessor.getDefendingFaction();

        log.debug("Sending PlanetDefendedMessage (planetId: {}, battleId: {}, attackingFaction: {}, defendingFaction: {})",
                planetId, battleId, attackingFaction, defendingFaction);
        clientMessagingService.sendToPublic(new PlanetDefendedMessage(planetId, battleId, attackingFaction, defendingFaction));

    }
}
