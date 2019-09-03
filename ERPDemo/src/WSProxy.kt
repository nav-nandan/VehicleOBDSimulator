import java.util.concurrent.ConcurrentHashMap
import io.javalin.Javalin
import io.javalin.websocket.WsContext
import org.apache.avro.generic.GenericData
import org.apache.kafka.clients.consumer.*
import java.util.*

private val userUsernameMap = ConcurrentHashMap<WsContext, String>()
private var nextUserNumber = 1

fun main(args: Array<String>) {
    Javalin.create {
    }.apply {
        ws("/vehicle-location") { ws ->
            ws.onConnect { ctx ->
                val username = "User" + nextUserNumber++
                userUsernameMap.put(ctx, username)

                val props = Properties()

                props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = "<broker-list>"
                props[ConsumerConfig.GROUP_ID_CONFIG] = "group1"

                props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] =
                    "org.apache.kafka.common.serialization.StringDeserializer"
                props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] =
                    "io.confluent.kafka.serializers.KafkaAvroDeserializer"
                props["schema.registry.url"] = "<schema-registry-url>"

                props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"

                val consumer: Consumer<String, GenericData.Record> = KafkaConsumer<String, GenericData.Record>(props)
                consumer.subscribe(Arrays.asList("VEHICLEUNITAVRO"))

                while(true) {
                    val records: ConsumerRecords<String, GenericData.Record> = consumer.poll(100)
                    for (record: ConsumerRecord< String, GenericData.Record> in records) {
                        broadcastMessage(username, record.value().toString())
                    }
                }
            }
            ws.onClose { ctx ->

            }
            ws.onMessage { ctx ->

            }
        }
    }.start(7070)
}

fun broadcastMessage(sender: String, message: String) {
    userUsernameMap.keys.filter { it.session.isOpen }.forEach { session ->
        session.send(message)
    }
}
