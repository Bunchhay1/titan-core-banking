package com.titan.titancorebanking.service;

// ✅ 1. Import ដ៏ត្រឹមត្រូវ (ត្រូវតែចេញពី com.titan.core.grpc)
import com.titan.core.grpc.RiskEngineGrpc;
import com.titan.core.grpc.RiskRequest;
import com.titan.core.grpc.RiskResponse;

import com.titan.titancorebanking.dto.response.RiskCheckResponse;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class RiskEngineGrpcService {

    private static final Logger logger = LoggerFactory.getLogger(RiskEngineGrpcService.class);

    // "riskEngineClient" គឺជាឈ្មោះដែលយើងដាក់ក្នុង application.properties
    @GrpcClient("riskEngineClient")
    private RiskEngineGrpc.RiskEngineBlockingStub riskEngineStub;

    /**
     * មុខងារ៖ ហៅទៅ Python តាមរយៈ gRPC
     * យើងប្តូរ parameter ពី double មក BigDecimal ឱ្យស្រួលប្រើជាមួយ TransactionService
     */
    public RiskCheckResponse analyzeTransaction(String username, BigDecimal amount) {
        // បំលែង BigDecimal ទៅ double ព្រោះ gRPC (Proto) ស្គាល់តែ double
        double amountAsDouble = amount.doubleValue();

        logger.info("🤖 gRPC Request: User={} | Amount=${}", username, amountAsDouble);

        try {
            // 1. បង្កើត Request (Protobuf Object)
            RiskRequest request = RiskRequest.newBuilder()
                    .setUsername(username)
                    .setAmount(amountAsDouble)
                    .build();

            // 2. ហៅទៅ Python (🚀 High Speed Call)
            RiskResponse response = riskEngineStub.checkRisk(request);

            // 3. ទទួលបានចម្លើយ
            logger.info("🤖 gRPC Response: Level={}, Action={}", response.getRiskLevel(), response.getAction());

            // 4. បំប្លែងទៅជា DTO ធម្មតាវិញ
            return new RiskCheckResponse(response.getRiskLevel(), response.getAction());

        } catch (Exception e) {
            logger.error("⚠️ gRPC Connection Failed: {}", e.getMessage());

            // Fail-Open: បើដាច់ gRPC ឱ្យចាត់ទុកថា ALLOW សិន
            return new RiskCheckResponse("UNKNOWN", "ALLOW");
        }
    }
}