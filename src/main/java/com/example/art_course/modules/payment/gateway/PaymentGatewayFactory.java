package com.example.art_course.modules.payment.gateway;

import com.example.art_course.common.AppError;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PaymentGatewayFactory {

    private final Map<String, PaymentGateway> gatewayMap;

    public PaymentGatewayFactory(List<PaymentGateway> gateways) {
        this.gatewayMap = gateways.stream()
                .collect(Collectors.toMap(
                        g -> g.getGatewayCode().toUpperCase(),
                        Function.identity()
                ));
    }

    public PaymentGateway getGateway(String gatewayCode) {
        if (gatewayCode == null) {
            throw new AppError(HttpStatus.BAD_REQUEST, "INVALID_GATEWAY", "Cổng thanh toán không được để trống");
        }
        PaymentGateway gateway = gatewayMap.get(gatewayCode.toUpperCase());
        if (gateway == null) {
            throw new AppError(HttpStatus.BAD_REQUEST, "UNSUPPORTED_GATEWAY", "Cổng thanh toán không được hỗ trợ: " + gatewayCode);
        }
        return gateway;
    }
}
