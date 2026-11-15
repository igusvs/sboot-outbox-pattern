#!/bin/bash
set -euo pipefail

REGION="${AWS_DEFAULT_REGION:-us-east-1}"

echo "Criando filas SQS..."

# Dead-letter queue
DLQ_URL=$(awslocal sqs create-queue --queue-name outbox-events-dlq --region "$REGION" \
  --query 'QueueUrl' --output text)

DLQ_ARN=$(awslocal sqs get-queue-attributes --queue-url "$DLQ_URL" \
  --attribute-names QueueArn \
  --query 'Attributes.QueueArn' --output text --region "$REGION")

# Fila principal com redrive para a DLQ após 3 tentativas
awslocal sqs create-queue --queue-name outbox-events --region "$REGION" \
  --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}"

echo "Filas SQS criadas:"
awslocal sqs list-queues --region "$REGION"
