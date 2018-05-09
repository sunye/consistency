package org.consistency.core.tests.unit;

import org.atlanmod.consistency.pubsub.Broker;
import org.atlanmod.consistency.pubsub.Producer;
import org.atlanmod.consistency.pubsub.Subscriber;
import org.atlanmod.consistency.pubsub.Topic;
import org.eclipse.emf.common.util.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PubSubTest {

    private Broker broker;
    private Producer producer;
    private Topic topic1, topic2;
    private Subscriber sub1, sub2;

    @BeforeEach
    void setup() {
        broker = new Broker();
        producer = new Producer(broker);

        sub1 = new Subscriber(broker);
        sub2 = new Subscriber(broker);
        topic1 = new Topic(URI.createURI("topic1"));
        topic2 = new Topic(URI.createURI("topic2"));

        sub1.subscribe(topic1);
        sub2.subscribe(topic1);
        sub2.subscribe(topic2);
    }

    @Test
    void testInit() {
        assertThat(broker.getTopics().size()).isEqualTo(2);
        assertThat(broker.containsTopic(topic1));
    }
    @Test
    void testPublish() {
        assertThat(topic1.hasUnconsumedMessages()).isFalse();
        assertThat(topic2.hasUnconsumedMessages()).isFalse();

        producer.publish(topic1, "Hello");

        assertThat(topic1.hasUnconsumedMessages()).isTrue();
        assertThat(topic2.hasUnconsumedMessages()).isFalse();

        assertThat(producer.getMsgHistory().size()).isGreaterThan(0);
    }

    @Test
    void testTopicPublish() {
        producer.publish(topic1, "Hello");

        assertThat(topic1.hasUnconsumedMessages()).isTrue();
        assertThat(sub1.getMsgHistory().size()).isZero();
        assertThat(sub2.getMsgHistory().size()).isZero();

        broker.topicPublish(topic1);

        assertThat(topic1.hasUnconsumedMessages()).isFalse();
        assertThat(sub1.getMsgHistory().size()).isGreaterThan(0);
        assertThat(sub2.getMsgHistory().size()).isGreaterThan(0);
    }

    @Test
    void testTopicPublishAll() {
        producer.publish(topic1, "Hello topic 1");
        producer.publish(topic2, "Hello topic 2");

        assertThat(topic1.hasUnconsumedMessages()).isTrue();
        assertThat(topic2.hasUnconsumedMessages()).isTrue();

        broker.publishAll();

        assertThat(topic1.hasUnconsumedMessages()).isFalse();
        assertThat(topic2.hasUnconsumedMessages()).isFalse();
    }

    @Test
    void testUnsub() {
        sub2.unsubscribe(topic1);

        assertThat(topic1.subCount()).isEqualTo(1);
        assertThat(topic2.subCount()).isEqualTo(1);
    }
}