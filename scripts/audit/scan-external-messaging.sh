#!/usr/bin/env bash
set -euo pipefail

# Signatures are assembled from fragments so the scanner does not match itself.
kafka='kaf''ka'
rabbit='rabbit''mq'
broker='brok''er'
amqp='am''qp'
jms='J''M''S'
jms_lower='j''m''s'
activemq='active''mq'
artemis='arte''mis'
pulsar='pul''sar'
nats='na''ts'
qpid='qp''id'
hornetq='hornet''q'
rocketmq='rocket''mq'
servicebus='service''bus'
pubsub='pub''sub'
kinesis='kine''sis'
spring_kafka='spring''-kafka'
spring_rabbit='spring''-rabbit'
spring_amqp='spring''-amqp'
spring_cloud_stream='spring''-cloud-stream'
spring_integration_amqp='spring''-integration-amqp'
starter_amqp='spring''-boot-starter-amqp'
starter_jms='spring''-boot-starter-jms'
kafka_clients='kafka''-clients'
amqp_client='amqp''-client'
sqs='S''QS'
sns='S''NS'
sqs_lower='s''qs'
sns_lower='s''ns'
aws_sqs='aws-java-sdk-''sqs'
aws_sns='aws-java-sdk-''sns'
sqs_listener='@''SqsListener'
stream_listener='@''StreamListener'
kafka_listener='@''KafkaListener'
rabbit_listener='@''RabbitListener'
kafka_template='Kafka''Template'
rabbit_template='Rabbit''Template'
redis_stream='Redis''Stream'
pattern="(${kafka}|${rabbit}|${broker}|${amqp}|${jms}|${jms_lower}|${activemq}|${artemis}|${pulsar}|${nats}|${qpid}|${hornetq}|${rocketmq}|${servicebus}|${pubsub}|${kinesis}|${spring_kafka}|${spring_rabbit}|${spring_amqp}|${spring_cloud_stream}|${spring_integration_amqp}|${starter_amqp}|${starter_jms}|${kafka_clients}|${amqp_client}|${sqs}|${sns}|${sqs_lower}|${sns_lower}|${aws_sqs}|${aws_sns}|${sqs_listener}|${stream_listener}|${kafka_listener}|${rabbit_listener}|${kafka_template}|${rabbit_template}|${redis_stream})"

set +e
matches=$(grep -RInI -E "$pattern" . \
  --exclude=scan-external-messaging.sh \
  --exclude-dir=.git --exclude-dir=node_modules --exclude-dir=target \
  --exclude-dir=build --exclude-dir=dist --exclude-dir=.next)
status=$?
set -e
if [[ $status -eq 0 ]]; then
  printf '%s\n' "$matches"
  echo "External messaging technology references remain in repository files." >&2
  exit 1
fi
if [[ $status -ne 1 ]]; then
  echo "Repository scan failed with grep exit code $status" >&2
  exit "$status"
fi

if command -v mvn >/dev/null 2>&1 && [[ -f backend/pom.xml ]]; then
  echo "Resolving Maven dependency graph for external messaging libraries..."
  (cd backend && mvn --batch-mode --no-transfer-progress dependency:tree -Dscope=test -DoutputType=text) > /tmp/saumya-maven-dependency-tree.txt 2>&1
  set +e
  dependency_matches=$(grep -Ei "$pattern" /tmp/saumya-maven-dependency-tree.txt)
  dependency_status=$?
  set -e
  if [[ $dependency_status -eq 0 ]]; then
    printf '%s\n' "$dependency_matches"
    echo "External messaging dependencies remain in the resolved Maven graph." >&2
    exit 1
  fi
  if [[ $dependency_status -ne 1 ]]; then
    echo "Maven dependency scan failed with grep exit code $dependency_status" >&2
    exit "$dependency_status"
  fi
fi

echo "PASS: no external messaging technology references or resolved Maven dependencies found."
