package com.faforever.gw.websocket;

import com.faforever.gw.messaging.client.ClientMessagingService;
import com.faforever.gw.messaging.client.outbound.ErrorMessage;
import com.faforever.gw.messaging.client.outbound.HelloMessage;
import com.faforever.gw.model.Battle;
import com.faforever.gw.model.GwCharacter;
import com.faforever.gw.security.GwUserRegistry;
import com.faforever.gw.security.User;
import com.faforever.gw.services.messaging.client.MessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class WebSocketInputHandler extends TextWebSocketHandler {
    private final EntityManager entityManager;
    private final WebSocketRegistry webSocketRegistry;
    private final GwUserRegistry gwUserRegistry;
    private final WebSocketController webSocketController;
    private final ClientMessagingService clientMessagingService;
    private final ObjectMapper jsonObjectMapper;
    private final Map<String, ActionFunc> actionMapping = new HashMap<>();

    @Inject
    public WebSocketInputHandler(EntityManager entityManager, WebSocketRegistry webSocketRegistry, GwUserRegistry gwUserRegistry, WebSocketController webSocketController, ClientMessagingService clientMessagingService, ObjectMapper jsonObjectMapper) {
        this.entityManager = entityManager;
        this.webSocketRegistry = webSocketRegistry;
        this.gwUserRegistry = gwUserRegistry;
        this.webSocketController = webSocketController;
        this.clientMessagingService = clientMessagingService;
        this.jsonObjectMapper = jsonObjectMapper;
    }

    @PostConstruct
    public void init() {
        Arrays.stream(webSocketController.getClass().getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(ActionMapping.class))
                .forEach(method -> {
                    val annotation = method.getAnnotation(ActionMapping.class);

                    actionMapping.put(annotation.value(), (WebSocketEnvelope envelope, User user) -> {
                        try {
                            val messageClass = MessageType.getByAction(envelope.getAction());
                            Object message = jsonObjectMapper.readValue(envelope.getData(), messageClass);
                            method.invoke(webSocketController, message, user);
                        } catch (IllegalAccessException | InvocationTargetException | IOException e) {
                            log.error("ActionMapping for `{}` failed", annotation.value(), e);
                        }
                    });
                });
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WebSocketEnvelope envelope;
        log.trace("Incoming websocket message: {}", message.getPayload());

        try {
            envelope = jsonObjectMapper.readValue(message.getPayload(), WebSocketEnvelope.class);

            if (envelope.getAction() == null)
                throw new IOException();
        } catch (Exception e) {
            log.error("Invalid message envelope. Ignoring message.");
            clientMessagingService.send(session, new ErrorMessage(null,
                    "E_INVALID",
                    "Invalid message envelope. Ignoring message.."));
            return;
        }

        if (actionMapping.containsKey(envelope.getAction())) {
            log.debug("Processing envelope: {}", envelope);
            actionMapping.get(envelope.getAction()).processMessage(envelope, webSocketRegistry.getUser(session));
        } else {
            log.error("Unknown action `{}`. Ignoring message.", envelope.getAction());
            clientMessagingService.send(session, new ErrorMessage(null,
                    "E_INVALID",
                    String.format("Unknown action `%s`. Ignoring message.", envelope.getAction())));
        }
    }

    @Override
    @Transactional
    public void afterConnectionEstablished(WebSocketSession session) {
        log.debug("User `{}` has connected via WebSocket", session.getPrincipal().getName());

        webSocketRegistry.add(session);
        val user = webSocketRegistry.getUser(session);

        UUID characterId = null;
        UUID currentBattleId = null;

        GwCharacter character = user.getActiveCharacter();

        if (character != null) {
            character = entityManager.merge(character);
            characterId = character.getId();
            currentBattleId = character.getCurrentBattle().map(Battle::getId).orElse(null);
        }

        gwUserRegistry.addConnection(user);
        log.debug("Sending HelloMessage (characterId: {}, currentBattleId: {})", characterId, currentBattleId);
        clientMessagingService.sendToUser(new HelloMessage(characterId, currentBattleId), user);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.debug("User `{}` has closed the WebSocket", session.getPrincipal().getName());
        gwUserRegistry.removeConnection(webSocketRegistry.getUser(session));
        webSocketRegistry.remove(session);
    }

    private interface ActionFunc {
        void processMessage(WebSocketEnvelope envelope, User user);
    }
}
