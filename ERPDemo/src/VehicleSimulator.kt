import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.google.gson.Gson
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.KafkaJsonSerializer
import io.ktor.http.content.*
import khttp.responses.Response
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.*
import kotlin.concurrent.thread

data class Coordinate(val longitude: Double, val latitude: Double)
data class Vehicle(val regno: String) {
    var source: String = ""
    var destination: String = ""
    var route: List<Coordinate> = listOf()
    var location: Coordinate = Coordinate(0.0, 0.0)
    var timestamp: Long = 0
}

data class VehicleUnit(val regno: String, val location: Coordinate, val timestamp: Long)

fun main(args: Array<String>) {

    val server = embeddedServer(Netty, port = 8080) {
        routing {
            static("webapp") {
                staticRootFolder = File("webapp")

                file("css/bootstrap.min.css")
                file("css/leaflet.css")

                file("js/jquery.min.js")

                file("images/car_icon.png")
                file("images/confluent_logo_white.png")
                file("images/google_cloud_logo.png")
                file("images/javalin_logo.svg")
                file("images/kafka_logo.png")
                file("images/kotlin_logo.png")
                file("images/ktor_logo.svg")

                file("simulator.html")
            }

            get("/") {
                call.respondText("Hello World!", ContentType.Text.Plain)
            }

            get("/demo") {
                call.respondText("HELLO WORLD KTOR!")
            }

            get("/start-vehicle") {
                // replace with a more realistic simulator

                val origin: String? = call.request.queryParameters["origin"]
                val destination: String? = call.request.queryParameters["destination"]
                val format: String? = call.request.queryParameters["format"]

                var vehicle = getVehicle(generateRegNo(8), origin.toString(), destination.toString())

                var avroProducer: Producer<String, GenericData.Record> = createAvroProducer("<broker-list>")
                var jsonProducer: Producer<String, VehicleUnit> = createJsonProducer("<broker-list>")

                thread(start = true) {
                    println("Starting from home")
                    for(i in 0..(vehicle.route.size - 1)) {
                        var vehicleUnit = VehicleUnit(vehicle.regno, vehicle.route[i], System.currentTimeMillis())

                        var gson = Gson()
                        var jsonString = gson.toJson(vehicleUnit)

                        var key = vehicleUnit.regno;
                        var userSchema = """
                        {
                            "type":"record","name":"currentlocation",
                            "fields":[
                            {"name":"regno","type":"string"},
                            {"name":"longitude","type":"double"},
                            {"name":"latitude","type":"double"},
                            {"name":"timestamp","type":"long"}
                            ]
                        }
                        """

                        var parser = Schema.Parser()
                        var schema = parser.parse(userSchema)

                        var avroRecord = GenericData.Record(schema)
                        avroRecord.put("regno", vehicleUnit.regno)
                        avroRecord.put("longitude", vehicleUnit.location.longitude)
                        avroRecord.put("latitude", vehicleUnit.location.latitude)
                        avroRecord.put("timestamp", vehicleUnit.timestamp)

                        if(format.equals("avro")) {
                            var record = ProducerRecord("VEHICLEUNITAVRO", key, avroRecord)
                            avroProducer.send(record)
                        } else if(format.equals("json")) {
                            var record = ProducerRecord("VEHICLEUNITJSON", key, vehicleUnit)
                            jsonProducer.send(record)
                        }

                        Thread.sleep(1000)
                    }
                    println("Reached work")

                    Thread.sleep(10000)

                    println("Leaving from work")
                    for(i in (vehicle.route.size - 1) downTo 0) {
                        var vehicleUnit = VehicleUnit(vehicle.regno, vehicle.route[i], System.currentTimeMillis())

                        var gson = Gson()
                        var jsonString = gson.toJson(vehicleUnit)

                        var key = vehicleUnit.regno;
                        var userSchema = """
                        {
                            "type":"record","name":"currentlocation",
                            "fields":[
                            {"name":"regno","type":"string"},
                            {"name":"longitude","type":"double"},
                            {"name":"latitude","type":"double"},
                            {"name":"timestamp","type":"long"}
                            ]
                        }
                        """

                        var parser = Schema.Parser()
                        var schema = parser.parse(userSchema)

                        var avroRecord = GenericData.Record(schema)
                        avroRecord.put("regno", vehicleUnit.regno)
                        avroRecord.put("longitude", vehicleUnit.location.longitude)
                        avroRecord.put("latitude", vehicleUnit.location.latitude)
                        avroRecord.put("timestamp", vehicleUnit.timestamp)

                        if(format.equals("avro")) {
                            var record = ProducerRecord("VEHICLEUNITAVRO", key, avroRecord)
                            avroProducer.send(record)
                        } else if(format.equals("json")) {
                            var record = ProducerRecord("VEHICLEUNITJSON", key, vehicleUnit)
                            jsonProducer.send(record)
                        }

                        Thread.sleep(1000)
                    }
                    println("Back home")
                }

                call.respondText("VEHICLE STARTED")
            }
        }
    }
    server.start(wait = true)
}

private fun createAvroProducer(brokers: String): Producer<String, GenericData.Record> {
    val props = Properties()
    props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = brokers
    props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName
    props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KafkaAvroSerializer::class.qualifiedName
    props["schema.registry.url"] = "<schema-registry-url>"
    return KafkaProducer<String, GenericData.Record>(props)
}

private fun createJsonProducer(brokers: String): Producer<String, VehicleUnit> {
    val props = Properties()
    props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = brokers
    props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName
    props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KafkaJsonSerializer::class.qualifiedName
    return KafkaProducer<String, VehicleUnit>(props)
}

private val charPool : List<Char> = ('A'..'Z') + ('0'..'9')

private fun generateRegNo(length: Int): String {
    return (1..length)
        .map { _ -> kotlin.random.Random.nextInt(0, charPool.size) }
        .map(charPool::get)
        .joinToString("")
}

private fun getVehicle(regno: String, source: String, destination: String): Vehicle {
    val route = getRoute(source, destination)
    val vehicle = Vehicle(regno)
    vehicle.source = source
    vehicle.destination = destination
    vehicle.route = route
    vehicle.location = route[0]
    vehicle.timestamp = System.currentTimeMillis()
    return vehicle
}

private fun getRoute(source: String, destination: String): List<Coordinate> {
    val response : Response = khttp.get(
        url = "https://maps.googleapis.com/maps/api/directions/json",
        params = mapOf(
            "origin" to source,
            "destination" to destination,
            "key" to "<your-google-maps-api-key>"
        )
    )

    val route = (response.jsonObject["routes"] as JSONArray)[0] as JSONObject
    val polyline = (route["overview_polyline"] as JSONObject)["points"] as String
    return decode(polyline)
}

/**
 * Decodes a polyline that has been encoded using Google's algorithm
 * (http://code.google.com/apis/maps/documentation/polylinealgorithm.html)
 *
 * code derived from : https://gist.github.com/signed0/2031157
 *
 * @param polyline-string
 * @return (long,lat)-Coordinates
 */
private fun decode(polyline: String): List<Coordinate> {
    val coordinateChunks: MutableList<MutableList<Int>> = mutableListOf()
    coordinateChunks.add(mutableListOf())

    for (char in polyline.toCharArray()) {
        // convert each character to decimal from ascii
        var value = char.toInt() - 63

        // values that have a chunk following have an extra 1 on the left
        val isLastOfChunk = (value and 0x20) == 0
        value = value and (0x1F)

        coordinateChunks.last().add(value)

        if (isLastOfChunk)
            coordinateChunks.add(mutableListOf())
    }

    coordinateChunks.removeAt(coordinateChunks.lastIndex)

    var coordinates: MutableList<Double> = mutableListOf()

    for (coordinateChunk in coordinateChunks) {
        var coordinate = coordinateChunk.mapIndexed { i, chunk -> chunk shl (i * 5) }.reduce { i, j -> i or j }

        // there is a 1 on the right if the coordinate is negative
        if (coordinate and 0x1 > 0)
            coordinate = (coordinate).inv()

        coordinate = coordinate shr 1
        coordinates.add((coordinate).toDouble() / 100000.0)
    }

    val points: MutableList<Coordinate> = mutableListOf()
    var previousX = 0.0
    var previousY = 0.0

    for(i in 0..coordinates.size-1 step 2) {
        if(coordinates[i] == 0.0 && coordinates[i+1] == 0.0)
            continue

        previousX += coordinates[i + 1]
        previousY += coordinates[i]

        points.add(Coordinate(round(previousX, 5), round(previousY, 5)))
    }
    return points
}

private fun round(value: Double, precision: Int) =
    (value * Math.pow(10.0,precision.toDouble())).toInt().toDouble() / Math.pow(10.0,precision.toDouble())
