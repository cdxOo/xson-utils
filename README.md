# xson-utils

JVM based command line utilities for BSON and JSON written in Kotlin.

* bsontojson
    
    Reads a BSON file and prints its contents as json. Available mongodb-ext-json modes are "extended", "relaxed" and "shell". Default is "relaxed".
    See the mongodb bson mode [documentation](https://mongodb.github.io/mongo-java-driver/4.0/bson/extended-json/).

* json-schema-inferrer
    
    Takes a JSON document or a collection of JSON documents from either STDIN or an input file and infers the json schema definition.
    The utility is mostly just a wrapper for [sassquatch/json-schema-inferrer](https://github.com/saasquatch/json-schema-inferrer) library functions.
   


## Installation
    
In addition to the  instuctions below, you can also download the utilities from the [release assets](https://github.com/cdxOo/xson-utils/releases).

### bsontojson
    
    curl -sSLO https://github.com/cdxOo/xson-utils/releases/download/0.1.0-alpha/bsontojson \
        && chmod a+x bsontojson

    # verify the executables sha512sum (optional but recommended)
    curl -sSLO https://github.com/cdxOo/xson-utils/releases/download/0.1.0-alpha/bsontojson.sha512 \
        && sha512sum bsontojson.sha512

### json-schema-inferrer

    # json-schema-inferrer
    curl -sSLO https://github.com/cdxOo/xson-utils/releases/download/0.1.0-alpha/json-schema-inferrer \
        && chmod a+x json-schema-inferrer

    # verify the executables sha512sum (optional but recommended)
    curl -sSLO https://github.com/cdxOo/xson-utils/releases/download/0.1.0-alpha/json-inferrer-schema.sha512 \
        && sha512sum json-inferrer-schema.sha512

## Usage
    
    # bsontojson
    ./bsontojson -m extended my-bson-file.bson

    # json-schema-inferrer
    json-schema-inferrer --help
    echo '[{ "foo": "bar"}, { "foo": "baz" }]' | ./json-schema-inferrer
    ./json-schema-inferrer -f my-json-file.json
