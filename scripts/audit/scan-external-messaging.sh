#!/usr/bin/env bash
set -euo pipefail

# Keep the signatures assembled from fragments so this scanner does not match itself.
kafka='kaf''ka'
rabbit='rabbit''mq'
amqp='am''qp'
jms='J''M''S'
activemq='active''mq'
pulsar='pul''sar'
nats='na''ts'
spring_kafka='spring''-kafka'
spring_rabbit='spring''-rabbit'
starter_amqp='spring''-boot-starter-amqp'
starter_jms='spring''-boot-starter-jms'
kafka_clients='kafka''-clients'
amqp_client='amqp''-client'
sqs='S''QS'
sns='S''NS'
aws_sqs='aws-java-sdk-''sqs'
aws_sns='aws-java-sdk-''sns'
sqs_listener='@''SqsListener'
kafka_listener='@''KafkaListener'
rabbit_listener='@''RabbitListener'
kafka_template='Kafka''Template'
rabbit_template='Rabbit''Template'
pattern="(${kafka}|${rabbit}|${amqp}|${jms}|${activemq}|${pulsar}|${nats}|${spring_kafka}|${spring_rabbit}|${starter_amqp}|${starter_jms}|${kafka_clients}|${amqp_client}|${sqs}|${sns}|${aws_sqs}|${aws_sns}|${sqs_listener}|${kafka_listener}|${rabbit_listener}|${kafka_template}|${rabbit_template})"

set +e
matches=$(grep -RInI -E "$pattern" . \
  --exclude=README.md \
  --exclude=scan-external-messaging.sh \
  --exclude-dir=.git --exclude-dir=node_modules --exclude-dir=target \
  --exclude-dir=build --exclude-dir=dist --exclude-dir=.next)
status=$?
set -e

if [[ $status -eq 0 ]]; then
  printf '%s\n' "$matches"
  echo "External messaging technology references remain; remove them or document why they are not a broker integration." >&2
  exit 1
fi
if [[ $status -ne 1 ]]; then
  echo "Repository scan failed with grep exit code $status" >&2
  exit "$status"
fi

echo "PASS: no external messaging technology references found (README.md excluded as instructed)."
