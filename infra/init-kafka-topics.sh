#!/bin/sh
# Create LMR topics. Run after Kafka is up: docker exec -it <kafka-container> /bin/sh -c '...'
# Or from host: docker compose -f infra/docker-compose.yml exec kafka kafka-topics --bootstrap-server localhost:29092 --create --if-not-exists --topic lmr.approved.v1 --partitions 1 --replication-factor 1
# Eligibility topic MUST be log-compacted so LES can rebuild read-model from the topic.

set -e
BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"

for topic in lmr.approved.v1 lmr.withdraw.requested.v1 lmr.withdraw.completed.v1 lmr.withdraw.rejected.v1; do
  kafka-topics --bootstrap-server "$BOOTSTRAP" --create --if-not-exists --topic "$topic" --partitions 1 --replication-factor 1
done

# Log-compacted: key = planningYear:lmrId; latest value per key is the current eligibility state.
kafka-topics --bootstrap-server "$BOOTSTRAP" --create --if-not-exists --topic lmr.withdraw.eligibility.v1 \
  --partitions 1 --replication-factor 1 --config cleanup.policy=compact --config min.cleanable.dirty.ratio=0.01

echo "Topics created."
