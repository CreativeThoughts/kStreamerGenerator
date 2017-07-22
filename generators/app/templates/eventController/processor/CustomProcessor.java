package au.com.someorg.somedomain.processor;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
///replace someorg/somedomain with the inputs
import au.com.someorg.somedomain.common.ProcessingStatus;

@Component
@Scope("prototype")
public class CustomProcessor extends MessageProcessor {

		
	private static final Logger LOGGER = LoggerFactory.getLogger(CustomProcessor.class.getName());
	
	public ProcessingStatus processMessage(string sampleMessage) {
		
		try {
			LOGGER.debug("Message received in CustomProcessor: " + sampleMessage);
			audienceManagerAPIService.invokeAdobe(sampleMessage);
			LOGGER.debug("Leaving CustomProcessor after processing message: " + sampleMessage);
			return ProcessingStatus.DONE;
			
		}catch (ServiceException exception) {
			LOGGER.error("FATAL ERROR : Adobe API failed . " + exception.getMessage());
			return ProcessingStatus.FATAL;

		} catch (Exception exception) {
			LOGGER.error("Exception while processing message. Exception :" + exception.getMessage());
			return ProcessingStatus.SKIPPED;
			
		} 
	}

	public SampleEventDto buildMessage(String message) {
		///debug is turned off in staging and prod
		LOGGER.info("Processing Incoming Message:" + message);
		Gson gson = new Gson();
		return gson.fromJson(message, sampleEventDto.class);
	}

}