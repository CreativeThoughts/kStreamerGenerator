package au.com.cba.cep.adobe.processor;

import au.com.cba.cep.adobe.dto.AudienceManagerEventDto;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import au.com.cba.cep.adobe.ProcessorController;
import au.com.cba.cep.adobe.common.ProcessingStatus;


@Component
public abstract class MessageProcessor implements Processor<String, String> {

	private static final Logger LOGGER = LoggerFactory.getLogger(MessageProcessor.class.getName());   

	@Autowired
	ProcessorController processorController;
	
	protected ProcessorContext context;

	public final void init(ProcessorContext context) {
		this.context = context;
		String streamerDetails = String.format("STREAMER=%s;",  context.applicationId());
		MDC.put( "STREAMER", streamerDetails );
	}

	public final void punctuate(long timestamp) {
		/*
		 * Stays empty.  In this use case there would be no need for a 
		 * periodical action of this processor.
		 */
	}

	public final void close() {
		/*
		 * Any code for clean up would go here.This processor instance 
		 * will not be used again after this call.
		 */
	}
	
	@Override
	public final void process(String key, String value) {
		long startTime = System.currentTimeMillis();
		ProcessingStatus status;
		String sourceDetails=String.format(" SOURCE_TOPIC=%s; SOURCE_PARTITON=%s; SOURCE_OFFSET=%s;", context.topic(), 
				context.partition(),context.offset());
		MDC.put( "BEP_KAFKA_LOGS", sourceDetails );
		
		if (value != null) {
			try {
				SampleEventDto sampleEvent = buildMessage(value);

				//ADD MORE IDS IF NEEDED
				String eventIds=String.format("CORRELATIONID=%s;",  sampleEvent.getHeader().getCorrelationId());
				MDC.put( "BEP_EVENT_IDS", eventIds );
				status = processMessage(sampleEvent);
			} catch (Exception exception) {
				LOGGER.error("Exception in processing message. Reason:"+ exception.getMessage());
				status = ProcessingStatus.SKIPPED;
			}

		} else {
			LOGGER.error("Null message received");
			status = ProcessingStatus.SERIALIZATION_EXCEPTION;
		}
		if(status == ProcessingStatus.FATAL) {
			String eventStatus = String.format("STATUS=%s;", status);
			MDC.put( "BEP_EVENT_STATUS", eventStatus );
			LOGGER.error("Shutting down streamer");
			LOGGER.trace("Shutting down streamer");
			processorController.shutDownStreamer();
			LOGGER.error("Shut down process started");
		} else {
			long timeToProcess = (System.currentTimeMillis() - startTime);
			String eventStatus=String.format("STATUS=%s; PROCESSING_TIME=%s",  status,timeToProcess);
			MDC.put( "BEP_EVENT_STATUS", eventStatus );
			LOGGER.debug("Completed processing message");
			LOGGER.trace("Completed processing message");
		}
		
		MDC.remove("BEP_KAFKA_LOGS");
		MDC.remove("BEP_EVENT_IDS");
		MDC.remove("BEP_EVENT_STATUS");
	}
	
	abstract public ProcessingStatus processMessage(AudienceManagerEventDto value);
	
	abstract public AudienceManagerEventDto buildMessage(String value);
}
