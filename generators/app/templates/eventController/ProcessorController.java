package au.com.cba.cep.adobe;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KStreamBuilder;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

//import au.com.cba.cep.avro.utils.SpecificAvroSerde;
import au.com.cba.cep.adobe.common.ApplicationConstants;
import au.com.cba.cep.adobe.common.Utilities;
import au.com.cba.cep.adobe.dto.AudienceManagerEventProperties;
import au.com.cba.cep.adobe.dto.TopicProcesorMapping;
import au.com.cba.cep.adobe.processor.MessageProcessor;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;

@Component
public class ProcessorController {

	@Value("${audiencemanager.applicationId}")
	private String applicationId;

	@Value("${audiencemanager.zookeeperServer}")
	private String zookeeperServer;

	@Value("${audiencemanager.bootstrapServer}")
	private String bootstrapServer;

	@Value("${audiencemanager.schemaRegistry}")
	private String schemaRegistry;

	@Value("${kafka.security.protocol:}")
	private String kafkaSecurityProtocol;

	@Value("${kafka.ssl.truststore.location:}")
	private String kafkaSSLTrustStoreLocation;

	@Value("${kafka.ssl.truststore.password:}")
	private String kafkaSSLTrustStorePassword;

	@Value("${kafka.ssl.keystore.location:}")
	private String kafkaSSLKeyStoreLocation;

	@Value("${kafka.ssl.keystore.password:}")
	private String kafkaSSLKeyStorePassword;

	@Value("${kafka.ssl.key.password:}")
	private String kafkaSSLKeyPassword;

	@Value("${audiencemanager.kafka.ssl.enabled:}")
	private String isProxyEnabled;

	KafkaStreams streams;
	private static final Integer SHUTDOWN_HOOK_TIME = 30;

	private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorController.class.getName());

	@Autowired
	private ApplicationContext appContext;

	void initializeKafkaStrems(AudienceManagerEventProperties config) {
		try{
			Properties streamsConfiguration;
			if(isProxyEnabled.equalsIgnoreCase(ApplicationConstants.TRUE))
			{
				streamsConfiguration = getKafkaSSLCommonConfig();

			}
			else
			{
				streamsConfiguration = new Properties();
				Utilities.disableSSLVerification();

			}
			/*
			 *Give the Streams application a unique name.  The name must be unique in the 
			 *Kafka cluster against which the application is run.
			 */
			streamsConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
			
			//TO DO: ADD ENVIRONMENT DETAILS TO STREAMER
			String streamerDetails = String.format("STREAMER=%s;",  applicationId);
			MDC.put( "STREAMER", streamerDetails );
			
			/*
			 * Where to find Kafka broker(s).
			 */
			streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
			/*
			 * Where to find the corresponding ZooKeeper ensemble.
			 */
			streamsConfiguration.put(StreamsConfig.ZOOKEEPER_CONNECT_CONFIG, zookeeperServer);

			streamsConfiguration.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistry);


			/*
			 * Specify default (de)serializers for record keys and for record values.
			 */
			streamsConfiguration.put(StreamsConfig.KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
			streamsConfiguration.put(StreamsConfig.VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
//			streamsConfiguration.put(StreamsConfig.VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);
			KStreamBuilder builder = new KStreamBuilder();
			for (TopicProcesorMapping topicMapping : config.getTopicProcessorMapping()) {
				//TODO: change the below value to an object once we have the avro schema sorted out
				//TODO: subsequently change the AMDecisionProcessor to have String, Object as the input parameters
				KStream<String, String> bizEvents = builder.stream(topicMapping.getTopicName());
				bizEvents.process(
						new ProcessorSupplier<String, String>() {
							public Processor<String, String> get() {
								MessageProcessor processor = null;
								try {
									processor = (MessageProcessor)appContext.getBean(Class.forName(topicMapping.getEventProcessor()));
									//processor = (MessageProcessor) Class.forName(topicMapping.getEventProcessor()).newInstance();
								} catch (BeansException | ClassNotFoundException e) {
									LOGGER.error("Error while loading custom processor : " +  e);
								}
								return processor;
							}
						});
			}

			streams = new KafkaStreams(builder, streamsConfiguration);
			streams.start();
			LOGGER.info("KafkaStreamerDriver - Started");
			
			/*
			 * Shutdown hook to gracefully close Kafka Streams
			 */
			Runtime.getRuntime().addShutdownHook(new KafkaShutDown(streams));		
		}
		catch(Exception e)
		{
			LOGGER.error("Exception occurred when starting the streamer : Error :"+ e.toString());
		}
	}



	private Properties getKafkaSSLCommonConfig() {
		// Common config and ssl configs
		Properties config = new Properties();
		config.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, kafkaSecurityProtocol);
		config.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, kafkaSSLTrustStoreLocation);
		config.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, kafkaSSLTrustStorePassword);
		config.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, kafkaSSLKeyStoreLocation);
		config.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, kafkaSSLKeyStorePassword);
		config.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, kafkaSSLKeyPassword);

		return config;
	}

	@SuppressWarnings("unused")
	public void shutDownStreamer() {
		ExecutorService executor = Executors.newCachedThreadPool();
		Callable<Object> task = new Callable<Object>() {
			public Object call() {
				streams.close();
				return null;
			}
		};
		Future<Object> future = executor.submit(task);
		try {
			try {
				Object result = future.get(SHUTDOWN_HOOK_TIME, TimeUnit.SECONDS);
			} catch (InterruptedException | ExecutionException e) {
				SpringApplication.exit(appContext);
			}
		} catch (TimeoutException ex) {
			SpringApplication.exit(appContext);
		} finally {
			future.cancel(true);
			executor.shutdown();
		}
	}
}
