package com.stratio.streaming;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka.KafkaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.siddhi.core.SiddhiManager;
import org.wso2.siddhi.core.config.SiddhiConfiguration;
import org.wso2.siddhi.query.api.definition.Attribute;
import org.wso2.siddhi.query.api.definition.StreamDefinition;

import ca.zmatrix.cli.ParseCmd;

import com.stratio.streaming.common.StratioStreamingConstants;
import com.stratio.streaming.functions.FilterMessagesByOperationFunction;
import com.stratio.streaming.functions.KeepPayloadFromMessageFunction;
import com.stratio.streaming.functions.ddl.AddQueryToStreamFunction;
import com.stratio.streaming.functions.ddl.AlterStreamFunction;
import com.stratio.streaming.functions.ddl.CreateStreamFunction;
import com.stratio.streaming.functions.ddl.DropStreamFunction;
import com.stratio.streaming.functions.dml.InsertIntoStreamFunction;
import com.stratio.streaming.functions.dml.ListStreamsFunction;
import com.stratio.streaming.functions.dml.ListenStreamFunction;
import com.stratio.streaming.functions.persistence.SaveRequestsToAuditLogFunction;
import com.stratio.streaming.messages.BaseStreamingMessage;



/**
 * @author dmorales
 * 
 * =================
 * Stratio Streaming
 * =================
 * 
 * 1) Run a global Siddhi CEP engine
 * 2) Listen to the Kafka topic in order to receive stream commands (CREATE, ADDQUERY, LIST, DROP, INSERT, LISTEN, ALTER)
 * 3) Execute commands and send ACKs to Zookeeper
 * 4) Send back the events if there are listeners 
 *
 */
public class StreamingEngine {
	
	
	private static Logger logger = LoggerFactory.getLogger(StreamingEngine.class);
	private static SiddhiManager siddhiManager;


	public StreamingEngine() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * 
	 * usage: --sparkMaster local --zookeeper-cluster fqdn:port --kafka-cluster fqdn:port
	 * 
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		
//		DECODING ARGUMENTS FROM COMMAND LINE
		String usage = "usage: --sparkMaster local --zookeeper-cluster fqdn:port --kafka-cluster fqdn:port";
        ParseCmd cmd = new ParseCmd.Builder()
        							.help(usage)                          
        							.parm("--spark-master", "local").req()
//        							.parm("--sparkMaster", "node.stratio.com:9999" ).rex("^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9]):[0-9]{1,4}$").req()
        							.parm("--zookeeper-cluster", "node.stratio.com:2181").rex("^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9]):[0-9]{1,4}+(,(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9]):[0-9]{1,4}+)*$").req()
        							.parm("--kafka-cluster", "node.stratio.com:9092").rex("^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9]):[0-9]{1,4}+(,(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9]):[0-9]{1,4}+)*$").req()
        							.parm("--cassandra-cluster", "node.stratio.com:9160").rex("^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9]):[0-9]{1,4}+(,(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9]):[0-9]{1,4}+)*$")
        							.parm("--auditEnabled", "false")
        							.build();  
       
        HashMap<String, String> R = new HashMap<String,String>();
        String parseError    = cmd.validate(args);
        if( cmd.isValid(args) ) {
            R = (HashMap<String, String>) cmd.parse(args);            
            System.out.println("Settings received:" + cmd.displayMap(R));
        }
        else { 
        	System.out.println(parseError); 
        	System.exit(1); 
        }
        
        
        launchStratioStreamingEngine(R.get("--spark-master").toString().replace("_", "[") + "]",        		
										R.get("--zookeeper-cluster").toString(),
										R.get("--kafka-cluster").toString(),
										StratioStreamingConstants.BUS.TOPICS,
										R.get("--cassandra-cluster").toString(),
										Boolean.valueOf(R.get("--auditEnabled").toString()));

	}
	
	
	/**
	 * 
	 * - Launch the main process: spark context, kafkaInputDstream and siddhi CEP engine
	 * - Filter the incoming messages (kafka) by key in order to separate the different commands
	 * - Parse the request contained in the payload
	 * - execute related command for each request
	 *  
	 * 
	 * 
	 * @param sparkMaster
	 * @param zkCluster
	 * @param kafkaCluster
	 * @param topics
	 */
	private static void launchStratioStreamingEngine(String sparkMaster, String zkCluster, String kafkaCluster, String topics, String cassandraCluster, Boolean enableAuditing) {
		

		
//		Create the context with a x seconds batch size
		JavaStreamingContext jssc = new JavaStreamingContext(sparkMaster, StreamingEngine.class.getName(), 
																new Duration(StratioStreamingConstants.STREAMING.DURATION_MS), System.getenv("SPARK_HOME"), 
																JavaStreamingContext.jarOfClass(StreamingEngine.class));
		
		
		Map<String, Integer> topicMap = new HashMap<String, Integer>();
		String[] topic_list = topics.split(",");

		
//		building the topic map, by using the num of partitions of each topic
		for (String topic : topic_list) {
//			TODO use stratio bus API (int numberOfThreads = KafkaTopicUtils.getNumPartitionsForTopic(zkCluster, 9092, topic);)
			topicMap.put(topic, 2);
		}
				
		
//		Start the Kafka stream  
		JavaPairDStream<String, String> messages = KafkaUtils.createStream(jssc, zkCluster, StratioStreamingConstants.BUS.STREAMING_GROUP_ID, topicMap);
		
		
		
		
//		Create a DStream for each command, so we can treat all related requests in the same way and also apply functions by command  
		JavaDStream<BaseStreamingMessage> create_requests = messages.filter(new FilterMessagesByOperationFunction(StratioStreamingConstants.CEP_OPERATIONS.CREATE_OPERATION))
																  	.map(new KeepPayloadFromMessageFunction());
		
		JavaDStream<BaseStreamingMessage> alter_requests = messages.filter(new FilterMessagesByOperationFunction(StratioStreamingConstants.CEP_OPERATIONS.ALTER_OPERATION))
			  														.map(new KeepPayloadFromMessageFunction());
		
		JavaDStream<BaseStreamingMessage> insert_requests = messages.filter(new FilterMessagesByOperationFunction(StratioStreamingConstants.CEP_OPERATIONS.INSERT_OPERATION))
																  	.map(new KeepPayloadFromMessageFunction());
		
		JavaDStream<BaseStreamingMessage> add_query_requests = messages.filter(new FilterMessagesByOperationFunction(StratioStreamingConstants.CEP_OPERATIONS.ADD_QUERY_OPERATION))
				  												  	.map(new KeepPayloadFromMessageFunction());
		
		JavaDStream<BaseStreamingMessage> listen_requests = messages.filter(new FilterMessagesByOperationFunction(StratioStreamingConstants.CEP_OPERATIONS.LISTEN_OPERATION))
				  													.map(new KeepPayloadFromMessageFunction());
		
		JavaDStream<BaseStreamingMessage> list_requests = messages.filter(new FilterMessagesByOperationFunction(StratioStreamingConstants.CEP_OPERATIONS.LIST_OPERATION))
																	.map(new KeepPayloadFromMessageFunction());
		
		JavaDStream<BaseStreamingMessage> drop_requests = messages.filter(new FilterMessagesByOperationFunction(StratioStreamingConstants.CEP_OPERATIONS.DROP_OPERATION))
				  													.map(new KeepPayloadFromMessageFunction());
		
		
		
		create_requests.foreach(new CreateStreamFunction(getSiddhiManager(), zkCluster, kafkaCluster));
		
		alter_requests.foreach(new AlterStreamFunction(getSiddhiManager(), zkCluster, kafkaCluster));
		
		insert_requests.foreach(new InsertIntoStreamFunction(getSiddhiManager(), zkCluster, kafkaCluster));
		
		add_query_requests.foreach(new AddQueryToStreamFunction(getSiddhiManager(), zkCluster, kafkaCluster));
		
		listen_requests.foreach(new ListenStreamFunction(getSiddhiManager(), zkCluster, kafkaCluster));
		
		list_requests.foreach(new ListStreamsFunction(getSiddhiManager(), zkCluster, kafkaCluster));
		
		drop_requests.foreach(new DropStreamFunction(getSiddhiManager(), zkCluster, kafkaCluster));
		
//		TODO remove listeners requests when drop 
//		TODO take insert requests and saveToCassandra if persistence is enabled
		
		
		
		if (enableAuditing) {
			JavaDStream<BaseStreamingMessage> allRequests = create_requests.union(alter_requests).union(insert_requests).union(add_query_requests).union(listen_requests).union(list_requests).union(drop_requests);
					
//			persist the RDDs to cassandra using STRATIO DEEP
			allRequests.foreachRDD(new SaveRequestsToAuditLogFunction(getSiddhiManager(), zkCluster, kafkaCluster, cassandraCluster));
			
		}
		
		
		
		
		
//		DEBUG STRATIO STREAMING ENGINE //
		messages.count().foreach(new Function<JavaRDD<Long>, Void>() {

			@Override
			public Void call(JavaRDD<Long> arg0) throws Exception {
				logger.info("********************************************");						
				logger.info("**       SIDDHI STREAMS                   **");
				logger.info("** count:" + siddhiManager.getStreamDefinitions().size());				
				for(StreamDefinition streamMetaData : siddhiManager.getStreamDefinitions()) {
					
					String streamDefition = streamMetaData.getStreamId();
					
					for (Attribute column : streamMetaData.getAttributeList()) {
						streamDefition += " |" + column.getName() + "," + column.getType();
					}
					
					logger.info("** stream: " + streamDefition);
				}				
				logger.info("********************************************");
				
				return null;
			}
			
		});
		
		
		
		messages.print();
		jssc.start();
		jssc.awaitTermination();
		

	}
	
	
	/**
	 * 
	 * - Instantiates the Siddhi CEP engine
	 * - return the running CEP engine 
	 * 
	 * 
	 * @return SiddhiManager
	 */
	private static SiddhiManager getSiddhiManager() {
		
		if (siddhiManager == null) {
			SiddhiConfiguration conf = new SiddhiConfiguration();		
			conf.setInstanceIdentifier("StratioStreamingCEP-Instance-"+ UUID.randomUUID().toString());
			conf.setQueryPlanIdentifier("StratioStreamingCEP-Cluster");
			conf.setDistributedProcessing(false);
			
			
			// Create Siddhi Manager
			siddhiManager = new SiddhiManager(conf);
			
			
//			DEBUG STREAMS
//			siddhiManager.addCallback("testStream", new StreamCallback() {
//				@Override
//				public void receive(Event[] events) {
//					try {
//						List<Tuple2<String, Object>> alarms = Lists.newArrayList();
//						for (Event e : events) {
//							
//							logger.info(">>>>>>>>>>>>>>>>>> NEW event: time: " + e.getTimeStamp() + "/" + e.getData0() + "/" + e.getData1() + "/" + e.getData2());
//							
//						}					
//						
//					} catch (Exception e) {
//						logger.info(">>>>>>>>>>>>>>>>>> SIDDHI EXCEPTION: " + e.getMessage());
//					}
//					
//				}
//				
//			});
		}
		
		return siddhiManager;
		
							
	}

}
