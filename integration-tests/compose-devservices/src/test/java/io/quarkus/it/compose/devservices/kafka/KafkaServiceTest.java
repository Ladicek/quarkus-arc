package io.quarkus.it.compose.devservices.kafka;

import static io.restassured.RestAssured.when;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(KafkaTestProfile.class)
public class KafkaServiceTest {

    @Test
    public void test() {
        when().get("/kafka/partitions/test").then().body(Matchers.is("2"));
        when().get("/kafka/partitions/test-consumer").then().body(Matchers.is("3"));
    }

}
