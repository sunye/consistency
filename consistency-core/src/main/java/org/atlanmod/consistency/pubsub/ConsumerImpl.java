package org.atlanmod.consistency.pubsub;

import org.atlanmod.commons.log.Log;
import org.atlanmod.consistency.core.IntegerId;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ConsumerImpl extends PubSub implements Consumer {

    public static final int MAX_TRIES = 100;
    private BlockingQueue<Serializable> mailBox = new LinkedBlockingQueue<>(); // Just arrived from a topic/broker
    private BlockingQueue<Serializable> waitingMessages= new LinkedBlockingQueue<>(); // Waiting to be treated
    private Thread t;
    private List<Serializable> archivedMessages = new ArrayList<>(); // Treated messages (just an history)

    public ConsumerImpl(Broker broker) {
        super(broker);
        clientId = new IntegerId(nextId++);
        subscribe(groupTopic);
        t = new Thread(new FetchMessage());
        t.start();
    }

    public boolean subscribe(Topic topic) {
        return broker.newSubscriber(this, topic);
    }

    public boolean unsubscribe(Topic topic) {
        return broker.unsubscribe(this, topic);
    }

    public boolean receive(Serializable message) {
        return mailBox.offer(message);
    }

    @Override
    public Serializable receive(int timeout) {
        Serializable message = null;
        try {
            message = mailBox.poll(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            //e.printStackTrace();
            Log.trace(e);
        }
        return message;
    }


    @Override
    public BlockingQueue<Serializable> getReceived() {
        return waitingMessages;
    }

    @Override
    public Serializable archive() {
        Serializable message = null;
        try {
            message = waitingMessages.take();
            archivedMessages.add(message);
        } catch (InterruptedException e) {
            Log.trace(e);
        }
        return message;
    }

    public class FetchMessage implements Runnable {

        @Override
        public void run() {
            Serializable message;
            int tries = 0;
            while(tries < MAX_TRIES) { // Could be while(true) for infinite fetching, until program or thread manual shutdown
                message = receive(TIMEOUT_MS);
                if (message != null)
                    waitingMessages.offer(message);
                //Log.info("Thread " + Thread.currentThread().getName() + " of client " + clientId + " received " + waitingMessages.size() + " messages" + ((message != null) ? (" : " + message) : ". (TIMEOUT)"));
                ++tries;
            }
            //Log.info(Thread.currentThread().getName() + " of client " + clientId + " stopped.");
        }
    }
}
