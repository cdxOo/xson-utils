package com.github.cdxOo.XsonUtils.bsontojson

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.*

import java.io.BufferedInputStream
import java.nio.ByteBuffer

import org.bson.BasicBSONDecoder
import org.bson.BasicBSONEncoder
import org.bson.codecs.BsonDocumentCodec
import org.bson.codecs.DecoderContext

import org.bson.BsonBinaryReader
import org.bson.json.JsonWriterSettings
import org.bson.json.JsonMode

class BsonToJson : CliktCommand(
    help = "convert bson file contents to mongodb-ext-json"
) {
    val bsonfile by argument().file(mustExist = true)

    val selectedJsonMode by (
        option(
            "-m",
            "--json-mode",
            help=(
                """
                |mongodb-ext-json mode; affects fields with types 
                |that cannot directly be represented with json
                """.trimMargin()
            )
        )
        .choice(
            "extended",
            "relaxed",
            "shell"
        )
        .default("relaxed")
    )

    override fun run () {

        val decoder = BasicBSONDecoder()
        val encoder = BasicBSONEncoder()
        val codec = BsonDocumentCodec()

        val jsonSettings = (
            JsonWriterSettings.builder()
            .outputMode(
                when (selectedJsonMode) {
                    "extended" -> JsonMode.EXTENDED
                    "relaxed" -> JsonMode.RELAXED
                    "shell" -> JsonMode.SHELL
                    else -> JsonMode.RELAXED
                }
            )
            .build()
        )

        val inputStream = bsonfile.inputStream()
        val bufferedInputStream = BufferedInputStream(inputStream)
        print("[")
        while (bufferedInputStream.available() > 0) {
            val obj = decoder.readObject(bufferedInputStream)
            val objBytes = encoder.encode(obj);
            
            val reader = BsonBinaryReader(ByteBuffer.wrap(objBytes))
            val context = DecoderContext.builder().build()
            val doc = codec.decode(reader, context)
            
            print(doc.toJson(jsonSettings))
            if (bufferedInputStream.available() > 0) {
                print(",")
            }
        }
        println("]")
    }
}
