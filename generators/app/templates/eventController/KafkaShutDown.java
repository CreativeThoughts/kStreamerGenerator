package au.com.cba.cep.adobe;

import org.apache.kafka.streams.KafkaStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaShutDown extends Thread implements Runnable {

	private static final Logger LOGGER = LoggerFactory.getLogger(KafkaShutDown.class.getName());

	KafkaStreams kafkaStreams;

	public KafkaShutDown(Object object) {
		this.kafkaStreams =(KafkaStreams)object;
	}

	public void run(){
		kafkaStreams.close();
		LOGGER.info("Kafka Streamer is shutting down !!!!");
	}
}
