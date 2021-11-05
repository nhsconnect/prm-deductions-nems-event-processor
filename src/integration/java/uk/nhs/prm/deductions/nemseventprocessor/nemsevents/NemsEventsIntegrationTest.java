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
import static uk.nhs.prm.deductions.nemseventprocessor.nemsevents.LocalStackAwsConfig.DEDUCTIONS_TEST_RECEIVING_QUEUE;
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

    @Test
    void shouldSendDeductionNemsEventMessageToDeductionsSnsTopic() {
        String queueUrl = amazonSQSAsync.getQueueUrl(nemsEventQueueName).getQueueUrl();
        String deductionMessageBody = "<Bundle xmlns=\"http://hl7.org/fhir\">\n" +
                "    <id value=\"236a1d4a-5d69-4fa9-9c7f-e72bf505aa5b\"/>\n" +
                "    <meta>\n" +
                "        <profile value=\"http://hl7.org/fhir/STU3/StructureDefinition/Bundle\"/>\n" +
                "    </meta>\n" +
                "    <type value=\"message\"/>\n" +
                "    <!--Entry for MessageHeader with id, timestamp, event type, source, responsible/publishing organization, communication-->\n" +
                "    <entry>\n" +
                "        <fullUrl value=\"3cfdf880-13e9-4f6b-8299-53e96ef5ec02\"/>\n" +
                "        <resource>\n" +
                "            <MessageHeader>\n" +
                "                <meta>\n" +
                "                    <lastUpdated value=\"2017-11-01T15:00:33+00:00\"/>\n" +
                "                </meta>\n" +
                "            </MessageHeader>\n" +
                "        </resource>\n" +
                "    </entry>\n" +
                "    <entry>\n" +
                "        <resource>\n" +
                "            <Patient>\n" +
                "                <identifier>\n" +
                "                    <value value=\"9912003888\"/>\n" +
                "                </identifier>\n" +
                "            </Patient>\n" +
                "        </resource>\n" +
                "    </entry>\n" +
                "    <entry>\n" +
                "        <resource>\n" +
                "            <EpisodeOfCare>\n" +
                "                <status value=\"finished\"/>\n" +
                "                <managingOrganization>\n" +
                "                    <reference value=\"urn:uuid:e84bfc04-2d79-451e-84ef-a50116506088\"/>\n" +
                "                    <display value=\"LIVERSEDGE MEDICAL CENTRE\"/>\n" +
                "                </managingOrganization>\n" +
                "            </EpisodeOfCare>\n" +
                "        </resource>\n" +
                "    </entry>\n" +
                "    <entry>\n" +
                "        <fullUrl value=\"urn:uuid:e84bfc04-2d79-451e-84ef-a50116506088\"/>\n" +
                "        <resource>\n" +
                "            <Organization>\n" +
                "                <id value=\"e84bfc04-2d79-451e-84ef-a50116506088\"/>\n" +
                "                <identifier>\n" +
                "                    <system value=\"https://fhir.nhs.uk/Id/ods-organization-code\"/>\n" +
                "                    <value value=\"B85612\"/>\n" +
                "                </identifier>\n" +
                "            </Organization>\n" +
                "        </resource>\n" +
                "    </entry>\n" +
                "</Bundle>";

        amazonSQSAsync.sendMessage(queueUrl, deductionMessageBody);

        String receiving = amazonSQSAsync.getQueueUrl(DEDUCTIONS_TEST_RECEIVING_QUEUE).getQueueUrl();

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
        assertTrue(receivedMessageHolder[0].getBody().contains("{\"lastUpdated\":\"2017-11-01T15:00:33+00:00\",\"previousOdsCode\":\"B85612\",\"eventType\":\"DEDUCTION\",\"nhsNumber\":\"9912003888\"}"));
    }
}
