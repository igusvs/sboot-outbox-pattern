package com.example.outboxpattern.queue;

import com.example.outboxpattern.PedidoDomain;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class PublishService {

    private final SqsTemplate sqsTemplate;

    public PublishService(SqsTemplate sqsTemplate) {
        this.sqsTemplate = sqsTemplate;
    }

    @Retryable(maxAttempts = 3)
    public void publish(PedidoDomain pedidoDomain) {
        sqsTemplate.send(to -> to.payload(pedidoDomain));
    }
}
