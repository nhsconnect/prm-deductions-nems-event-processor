package uk.nhs.prm.deductions.nemseventprocessor.nemsevents;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class NemsEventListener implements MessageListener {

    private final NemsEventService nemsEventService;

    @Override
    public void onMessage(Message message) {
        String traceIdUUID = UUID.randomUUID().toString();
        String traceIdHex = traceIdUUID.replaceAll("-", "");
        MDC.put("traceId", traceIdHex);

        log.info("RECEIVED: Nems Event Message");
        try {
            String payload = ((TextMessage) message).getText();
            nemsEventService.processNemsEvent(payload);
            message.acknowledge();
            log.info("ACKNOWLEDGED: Nems Event Message");
        } catch (JMSException e) {
            log.error("Error while processing message: {}", e.getMessage());
        }
    }
}
