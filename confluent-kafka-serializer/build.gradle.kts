plugins {
    id("library-module-conventions")
    id("library-publish-conventions")
    kotlin("plugin.serialization")
}

description = "Avro4k's module to enable (de)serializing kotlin classes and other native types in avro, with a schema registry."

dependencies {
    implementation(project(":core"))
    implementation(kotlin("reflect"))

    api(libs.confluent.kafka.avro.serializer) {
        version {
            // AbstractKafkaAvroSerializer.serializeImpl has a breaking change starting from 8.3.0
            // AbstractKafkaAvroSerializer.getDatumReader has a breaking change starting from 8.3.2 where they added a writerSchemaId param
            strictly("[8.3.2,)")
            prefer(libs.confluent.kafka.avro.serializer.get().version!!)
        }
        // avro4k declares its own avro dependency
        exclude(group = "org.apache.avro")
        exclude(group = "org.apache.commons", module = "commons-compress")
    }
    testImplementation(libs.kotest.junit5)
    testImplementation(libs.kotest.core)
    testImplementation(libs.mockk)
}

repositories {
    maven("https://packages.confluent.io/maven")
}

// AbstractKafkaAvroDeserializer.getDatumReader gained a topic parameter in 8.3.3 and 8.4.1, so the tests also run against
// the latest Confluent release, besides the one avro4k compiles against.
val testRuntimeClasspathLatestConfluent: Configuration by configurations.creating {
    isCanBeConsumed = false
    extendsFrom(configurations.testImplementation.get(), configurations.testRuntimeOnly.get())
    val testRuntimeAttributes = configurations.testRuntimeClasspath.get().attributes
    attributes {
        testRuntimeAttributes.keySet().forEach {
            @Suppress("UNCHECKED_CAST")
            attribute(it as Attribute<Any>, testRuntimeAttributes.getAttribute(it)!!)
        }
    }
    resolutionStrategy.eachDependency {
        if (requested.group == "io.confluent" && requested.version.orEmpty().contains(libs.versions.confluent.schema.registry.get())) {
            useVersion(libs.confluent.latest.kafka.avro.serializer.get().version!!)
        }
    }
}

val testLatestConfluent by tasks.registering(Test::class) {
    description = "Runs the tests against the latest Confluent release."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().output + sourceSets.main.get().output + testRuntimeClasspathLatestConfluent
    useJUnitPlatform()
}

tasks.check {
    dependsOn(testLatestConfluent)
}