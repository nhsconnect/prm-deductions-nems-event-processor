package uk.nhs.prm.deductions.nemseventprocessor.nemsevents;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.nhs.prm.deductions.nemseventprocessor.nemsevents.LocalStackAwsConfig.UNHANDLED_EVENTS_TEST_RECEIVING_QUEUE;

@SpringBootTest()
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LocalStackAwsConfig.class)
class NemsEventsIntegrationTest {

    @Autowired
    private AmazonSQSAsync amazonSQSAsync;

    @Value("${aws.nemsEventsQueueName}")
    private String nemsEventQueueName;

    @Test
    void shouldSendNonDeductionNemsEventMessageToUnhandledTopic() {
        String queueUrl = amazonSQSAsync.getQueueUrl(nemsEventQueueName).getQueueUrl();
        String nonDeductionMessageBody = "<Bundle xmlns=\"http://hl7.org/fhir\">\n" +
                "    <entry>\n" +
                "        <resource>\n" +
                "            <Patient>\n" +
                "                <generalPractitioner>\n" +
                "                    <reference value=\"urn:uuid:59a63170-b769-44f7-acb1-95cc3a0cb067\"/>\n" +
                "                    <display value=\"SHADWELL MEDICAL CENTRE\"/>\n" +
                "                </generalPractitioner>\n" +
                "            </Patient>\n" +
                "        </resource>\n" +
                "    </entry>\n" +
                "</Bundle>";
        amazonSQSAsync.sendMessage(queueUrl, nonDeductionMessageBody);

        String receiving = amazonSQSAsync.getQueueUrl(UNHANDLED_EVENTS_TEST_RECEIVING_QUEUE).getQueueUrl();

        Message[] receivedMessageHolder = new Message[1];
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            System.out.println("checking sqs queue: " + receiving);
            ReceiveMessageRequest requestForMessagesWithAttributesAsHavingToExplicitlyAskForThemIsApparentlyTheMostObviousBehaviour = new ReceiveMessageRequest().withQueueUrl(receiving).withMessageAttributeNames("traceId");
            List<Message> messages = amazonSQSAsync.receiveMessage(requestForMessagesWithAttributesAsHavingToExplicitlyAskForThemIsApparentlyTheMostObviousBehaviour).getMessages();
            System.out.println("messages: " + messages.size());
            assertThat(messages).hasSize(1);
            receivedMessageHolder[0] = messages.get(0);
            System.out.println("message: " + messages.get(0).getBody());
            System.out.println("message attributes: " + messages.get(0).getMessageAttributes());
            System.out.println("message attributes empty: " + messages.get(0).getMessageAttributes().isEmpty());
        });
        assertFalse(receivedMessageHolder[0].getMessageAttributes().isEmpty());
        assertTrue(receivedMessageHolder[0].getMessageAttributes().containsKey("traceId"));
        assertTrue(receivedMessageHolder[0].getBody().contains(nonDeductionMessageBody));
    }

}