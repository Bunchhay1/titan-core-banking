package com.titan.titancorebanking.load;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Load test for Titan Core Banking transfer endpoint.
 * Simulates 100 concurrent users performing transfers for 2 minutes.
 */
public class TransferLoadTest extends Simulation {

    HttpProtocolBuilder httpProtocol = http
        .baseUrl("http://localhost:8080")
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .header("Authorization", "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...");

    ScenarioBuilder transferScenario = scenario("Transfer Load Test")
        .exec(
            http("Transfer Request")
                .post("/api/v1/transactions/transfer")
                .header("Idempotency-Key", "#{idempotencyKey}")
                .body(StringBody("""
                    {
                        "fromAccountNumber": "1000000001",
                        "toAccountNumber": "1000000002",
                        "amount": 10.00,
                        "pin": "1234",
                        "note": "Load test transfer"
                    }
                    """))
                .check(status().is(200))
                .check(responseTimeInMillis().lt(500))
        )
        .pause(1, 3);

    ScenarioBuilder balanceCheckScenario = scenario("Balance Check")
        .exec(
            http("Get Balance")
                .get("/api/v1/accounts/1000000001/balance")
                .check(status().is(200))
                .check(responseTimeInMillis().lt(100))
        )
        .pause(2, 5);

    {
        setUp(
            transferScenario.injectOpen(
                rampUsers(50).during(30),  // Ramp up 50 users over 30s
                constantUsersPerSec(20).during(120) // 20 TPS for 2 minutes
            ),
            balanceCheckScenario.injectOpen(
                constantUsersPerSec(50).during(120) // 50 balance checks/sec
            )
        )
        .protocols(httpProtocol)
        .assertions(
            global().responseTime().percentile3().lt(500),
            global().successfulRequests().percent().gt(95.0),
            forAll().responseTime().max().lt(2000)
        );
    }
}
