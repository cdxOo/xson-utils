package com.github.cdxOo.JsonSchemaInferrerCli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

import de.undercouch.bson4jackson.BsonFactory;
import java.io.ByteArrayInputStream

import com.saasquatch.jsonschemainferrer.AdditionalPropertiesPolicies
import com.saasquatch.jsonschemainferrer.ArrayLengthFeature
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

    override fun run () {
        /*val mapper = (
            if (isBson) {
                ObjectMapper(BsonFactory())
            }
            else {
                ObjectMapper()
            }
        )*/
        val mapper = ObjectMapper();

        val inferrer = (
            JsonSchemaInferrer.newBuilder()
            .setSpecVersion(SpecVersion.DRAFT_06)
            .setIntegerTypeCriterion(
                IntegerTypeCriteria.mathematicalInteger()
            )
            .setExamplesPolicy(
                ExamplesPolicies.useFirstSamples(3)
            )
            .setAdditionalPropertiesPolicy(
                AdditionalPropertiesPolicies.existingTypes()
            )
            .setRequiredPolicy(
                RequiredPolicies.nonNullCommonFields()
            )
            .setTitleDescriptionGenerator(
                TitleDescriptionGenerators.useFieldNamesAsTitles()
            )
            .addFormatInferrers(
                FormatInferrers.email(),
                FormatInferrers.dateTime(),
                FormatInferrers.ip()
                //Example2::absoluteUriFormatInferrer
            )
            .setMultipleOfPolicy(
                MultipleOfPolicies.gcd()
            )
            /*.addEnumExtractors(
                EnumExtractors.validEnum(Month.class),
                EnumExtractors.validEnum(DayOfWeek.class),
                input -> {
                    final Set<? extends JsonNode> primitives = input.getSamples().stream()
                        .filter(JsonNode::isValueNode)
                        .collect(Collectors.toSet());
                    if (primitives.size() <= 100 && primitives.size() > 0) {
                        return Collections.singleton(primitives);
                    }
                    return Collections.emptySet();
                }
            )*/
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
        
        val tree : JsonNode = (
            if (isBson) {
                val bsonmapper = ObjectMapper(BsonFactory())
                val bytes : ByteArray = jsonfile.readBytes()
                val pojotree = bsonmapper.readTree(bytes)
                val str = mapper.writeValueAsString(pojotree)
                mapper.readTree(str)
            }
            else {
                mapper.readTree(
                    jsonfile.readText()
                )
            }
        );

        val schema = (
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
        )

        
        println(
            mapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(schema)
        );
    }
}

fun main(args: Array<String>) = JsonInferrer().main(args)
