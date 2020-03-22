package com.github.cdxOo.JsonSchemaInferrerCli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.*

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

import de.undercouch.bson4jackson.BsonFactory
import java.io.ByteArrayInputStream
import java.io.BufferedInputStream

import org.bson.BSONDecoder
import org.bson.BSONObject
import org.bson.BasicBSONObject
import org.bson.BasicBSONDecoder
import org.bson.BasicBSONEncoder

import org.bson.BsonDocument

import java.nio.ByteBuffer
import org.bson.BsonBinaryReader
import org.bson.codecs.BsonDocumentCodec
import org.bson.codecs.DecoderContext


import org.bson.json.JsonWriterSettings
import org.bson.json.JsonMode

//import org.bson.Bits

import com.saasquatch.jsonschemainferrer.AdditionalPropertiesPolicies
import com.saasquatch.jsonschemainferrer.ArrayLengthFeature
import com.saasquatch.jsonschemainferrer.EnumExtractor
import com.saasquatch.jsonschemainferrer.EnumExtractors
import com.saasquatch.jsonschemainferrer.ExamplesPolicies
import com.saasquatch.jsonschemainferrer.FormatInferrerInput
import com.saasquatch.jsonschemainferrer.FormatInferrers
import com.saasquatch.jsonschemainferrer.IntegerTypeCriteria
import com.saasquatch.jsonschemainferrer.JsonSchemaInferrer
import com.saasquatch.jsonschemainferrer.MultipleOfPolicies
import com.saasquatch.jsonschemainferrer.NumberRangeFeature
import com.saasquatch.jsonschemainferrer.ObjectSizeFeature
import com.saasquatch.jsonschemainferrer.RequiredPolicies
import com.saasquatch.jsonschemainferrer.SpecVersion
import com.saasquatch.jsonschemainferrer.StringLengthFeature
import com.saasquatch.jsonschemainferrer.TitleDescriptionGenerators

// FIXME: can we get rid of those an use kotlin features instead?
import java.util.Collections
import java.util.stream.Collectors
import java.util.stream.StreamSupport

class JsonInferrer : CliktCommand(
    help = "Infer JSON-Schema from JSON-Datasets"
) {
    val jsonfile by argument().file(mustExist = true)
    
    val isSampleCollection by option(
        "-c",
        "--is-sample-collection",
        help="when json file contains array, treat this array as a collection of samples"
    ).flag()


    val isBson by option(
        "-b",
        "--bson",
        help="treat input file as bson"
    ).flag()
    
    val specVersionString by (
        option(
            "--spec-version",
            help="schema version e.g. draft-07"
        )
        .choice("draft-07", "draft-06", "draft-04")
        .default("draft-07")
        // FIXME: cannot inline shit
        //.convert { it -> SpecVersion.DRAFT_07 }
    )

    val examplesAmount by (
        option(
            "--examples"
        )
        .int()
        .default(3)
    )

    val examplesPolicy by (
        option(
            "--examples-policy"
        )
        .choice(
            "first"
        )
    )
    
    val requiredPropsPolicy by (
        option(
            "--required-props-policy"
        )
        .choice(
            "non-null-common",
            "common"
        )
    )
    
    val additionalPropsPolicy by (
        option(
            "--additional-props-policy"
        )
        .choice(
            "allowed",
            "not-allowed",
            "existing-types"
        )
    )

    val integerMultipleOfPolicy by (
        option(
            "--integer-multiple-of-policy"
        )
        .choice(
            "gcd"
        )
    )

    val defaultValuePolicy by (
        option(
            "--default-value-policy"
        )
        .choice(
            "first-sample",
            "last-sample"
        )
    )

    val formatInferrers by (
        option(
            "-i",
            "--format-inferrer"
        )
        .choice(
            "email",
            "date-time",
            "ip"
        )
        .multiple(required = false)
        .unique()
    )



    override fun run () {
   
        var selectedSpecVersion = when (specVersionString) {
            "draft-07" -> SpecVersion.DRAFT_07 
            "draft-06" -> SpecVersion.DRAFT_06
            "draft-04" -> SpecVersion.DRAFT_04
            else -> SpecVersion.DRAFT_07
        }

        val mapper = ObjectMapper();

        val builder = (
            JsonSchemaInferrer.newBuilder()
            .setSpecVersion(selectedSpecVersion)
            .setIntegerTypeCriterion(
                IntegerTypeCriteria.mathematicalInteger()
            )
        )

        if (examplesPolicy != null) {
            builder.setExamplesPolicy(
                when (examplesPolicy) {
                    "first" -> ExamplesPolicies.useFirstSamples(examplesAmount)
                    else -> throw IllegalArgumentException("Unknown Example Policy")
                }
            )
        }

        if (requiredPropsPolicy != null) {
            builder.setRequiredPolicy(
                when (requiredPropsPolicy) {
                    "non-null-common" -> RequiredPolicies.nonNullCommonFields()
                    "common" -> RequiredPolicies.commonFields()
                    else -> throw IllegalArgumentException("Unknown Required Properties Policy")
                }
            )
        }

        if (additionalPropsPolicy != null) {
            builder.setAdditionalPropertiesPolicy(
                when (requiredPropsPolicy) {
                    "allowed" -> AdditionalPropertiesPolicies.allowed()
                    "not-allowed" -> AdditionalPropertiesPolicies.notAllowed()
                    "existing-types" -> AdditionalPropertiesPolicies.existingTypes()
                    else -> throw IllegalArgumentException("Unknown Required Properties Policy")
                }
            )
        }

        if (formatInferrers != null) {
            formatInferrers.forEach {
                builder.addFormatInferrers(
                    when (it) {
                        "email" -> FormatInferrers.email()
                        "date-time" -> FormatInferrers.dateTime()
                        "ip" -> FormatInferrers.ip()
                        else -> throw IllegalArgumentException("Unknown Format Inferrer")
                    }
                )
            }
        }

        val inferrer = (
            builder
            /*.setTitleDescriptionGenerator(
                TitleDescriptionGenerators.useFieldNamesAsTitles()
            )*/
            .setMultipleOfPolicy(
                MultipleOfPolicies.gcd()
            )
            .addEnumExtractors(
                //EnumExtractors.validEnum(Month.class),
                //EnumExtractors.validEnum(DayOfWeek.class),
                EnumExtractor { input ->
                    val primitives = input.getSamples().stream()
                        .filter(JsonNode::isValueNode)
                        .collect(Collectors.toSet())
                    if (primitives.size <= 40 && primitives.size > 0) {
                        // FIXME: this wierd cast
                        Collections.singleton(primitives) as Set<Set<out JsonNode>>
                    }
                    else {
                        Collections.emptySet()
                    }
                }
            )
            /*.setArrayLengthFeatures(
                EnumSet.allOf(ArrayLengthFeature.class)
            )
            .setObjectSizeFeatures(
                EnumSet.allOf(ObjectSizeFeature.class)
            )
            .setStringLengthFeatures(
                EnumSet.allOf(StringLengthFeature.class)
            )
            .setNumberRangeFeatures(
                EnumSet.allOf(NumberRangeFeature.class)
            )*/
            .build()
        )
        
        val schema = (
            if (isBson) {
                val samples = ArrayList<JsonNode>()

                val decoder = BasicBSONDecoder()
                val encoder = BasicBSONEncoder()
                val codec = BsonDocumentCodec()

                val jsonSettings = (
                    JsonWriterSettings.builder()
                    .outputMode(JsonMode.EXTENDED)
                    .build()
                )

                val inputStream = jsonfile.inputStream()
                val bufferedInputStream = BufferedInputStream(inputStream)
                while (bufferedInputStream.available() > 0) {
                    val obj = decoder.readObject(bufferedInputStream)
                    val objBytes = encoder.encode(obj);
                    
                    val reader = BsonBinaryReader(ByteBuffer.wrap(objBytes))
                    val context = DecoderContext.builder().build()
                    val doc = codec.decode(reader, context)

                    samples.add(mapper.readTree(
                        doc.toJson(jsonSettings)
                    ))
                    //println(mapper.writeValueAsString(obj.toMap()))
                }

                /*val codec = BsonDocumentCodec()

                val inputStream = jsonfile.inputStream()
                val bufferedInputStream = BufferedInputStream(inputStream)
                
                while (bufferedInputStream.available() > 0) {
                    // https://github.com/mongodb/mongo-java-driver/blob/master/bson/src/main/org/bson/BasicBSONDecoder.java
                    val sizeBytes = ByteArray(4)
                    Bits.readFully(bufferedInputStream, sizeBytes)
                    val size = Bits.readInt(sizeBytes)

                    val byte_buffer = ByteArray(size)
                    System.arraycopy(sizeBytes, 0, byte_buffer, 0, 4)
                    Bits.readFully(bufferedInputStream, byte_buffer, 4, size - 4)
                }*/

                /*val codec = BsonDocumentCodec()
                // https://github.com/mongodb/mongo-java-driver/blob/master/bson/src/main/org/bson/BasicBSONDecoder.java
                val bytes = jsonfile.readBytes()

                val reader = BsonBinaryReader(ByteBuffer.wrap(bytes))
                val context = DecoderContext.builder().build()
                val doc = codec.decode(reader, context)
                println(doc.toString())
                val doc2 = codec.decode(reader, context)
                println(doc2.toString())*/
                

                /*val decoder = BasicBSONDecoder()
                
                val samples = ArrayList<BSONObject>()

                val inputStream = jsonfile.inputStream()
                val bufferedInputStream = BufferedInputStream(inputStream)
                while (bufferedInputStream.available() > 0) {
                    val obj = decoder.readObject(bufferedInputStream)
                    samples.add(obj);
                    println(obj.toString())
                    //println(mapper.readTree(obj.toString()))
                    println(mapper.writeValueAsString(obj.toMap()))
                }*/
                /*val bsonmapper = ObjectMapper(BsonFactory())
                val bytes : ByteArray = jsonfile.readBytes()
                val pojotree = bsonmapper.readTree(bytes)
                val str = mapper.writeValueAsString(pojotree)*/
                

                inferrer.inferForSamples(samples)
            }
            else {
                val tree = mapper.readTree(
                    jsonfile.readText()
                )
        
                if (isSampleCollection && tree.isArray()) {
                    inferrer.inferForSamples(
                        StreamSupport.stream(
                            tree.spliterator(), false
                        )
                        .collect(Collectors.toList())
                    )
                }
                else {
                    inferrer.inferForSample(tree);
                }
            }
        );

        println(
            mapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(schema)
        );
    }
}

fun main(args: Array<String>) = JsonInferrer().main(args)
