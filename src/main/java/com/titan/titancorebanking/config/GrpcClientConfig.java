package com.titan.titancorebanking.config;

import com.titan.riskengine.RiskEngineServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {

    // 1. បង្កើត "ខ្សែទូរស័ព្ទ" (Channel) ទៅកាន់ Python AI
    @Bean
    public ManagedChannel managedChannel() {
        return ManagedChannelBuilder.forAddress("localhost", 50051) // អាសយដ្ឋានរបស់ Python AI
                .usePlaintext() // ប្រើការតភ្ជាប់ធម្មតា (មិនមែន SSL) សម្រាប់ការ Test
                .build();
    }

    // 2. បង្កើត "ទូរស័ព្ទ" (Stub) ដើម្បីឱ្យ Service យកទៅប្រើ
    @Bean
    public RiskEngineServiceGrpc.RiskEngineServiceBlockingStub riskStub(ManagedChannel channel) {
        return RiskEngineServiceGrpc.newBlockingStub(channel);
    }
}