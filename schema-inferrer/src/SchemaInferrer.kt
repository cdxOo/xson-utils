package com.github.cdxOo.XsonUtils.schemainferrer

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.*

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode

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

import java.io.File

class SchemaInferrer : CliktCommand(
    help = "Infer JSON-Schema from JSON-Dataset"
) {
    val jsonfile by (
        option(
            "-f",
            "--input-file",
            help="optional input file. by default json is read from stdin"
        )
        .file(
            mustExist = true,
            mustBeReadable = true
        )
    )
    
    val isSampleCollection by option(
        "-c",
        "--is-sample-collection",
        help="when json input contains an array, treat this array as a collection of samples"
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
                        // FIXME: wow it also gives ma a waring that this is redundant
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
        
        val input = (
            if (jsonfile == null) {
                generateSequence(::readLine).joinToString("\n")
            }
            else {
                (jsonfile as File).readText()
            }
        );

        val tree = mapper.readTree(input);

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
        );

        println(
            mapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(schema)
        );
    }
}

