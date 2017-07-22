package au.com.cba.cep.adobe;

import java.io.FileNotFoundException;
import java.io.FileReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import au.com.cba.cep.adobe.dto.AudienceManagerEventProperties;


public class KafkaStreamDriver implements CommandLineRunner {
	static Gson gson = new Gson();
	
	@Autowired
	ProcessorController processorController;



	private static final Logger LOGGER = LoggerFactory.getLogger(KafkaStreamDriver.class.getName());   
	
	@Override
	public void run(String... args) throws Exception {
	
		LOGGER.info("Started AudienceManager Event - Kafka Streamer");
		if (args.length != 1) {
			LOGGER.error("Configuration file not mentioned. Expecting AMEventProcessorConfig.json. Exiting..");
			throw new Exception("Expecting config file.");
		}
		
		String configFilePath = args[0];
		JsonReader reader;
		try {
			reader = new JsonReader(new FileReader(configFilePath));
			AudienceManagerEventProperties config = gson.fromJson(reader,AudienceManagerEventProperties.class);
			processorController.initializeKafkaStrems(config);
		} catch (FileNotFoundException e) {
			LOGGER.error("Failed in Kafka Stream Driver :"+e.toString() );
		}
	}
	
	
	public static void main(String[] args) throws Exception {
		LOGGER.debug("Started adobe application....with spring boot");
		SpringApplication.run("classpath:/appConfig.xml", args);
	}

}
