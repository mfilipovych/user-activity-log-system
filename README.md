# User Activity Log System

A high-throughput, distributed user activity logging system that records and queries high-volume user events with automatic
time-to-live (TTL) expiration, clustered data replication across multiple racks, and built-in background activity simulation.

---

## Tech Stack & Tools

* **Java**: 21
* **Framework**: Spring Boot 4.1.1 (`spring-boot-starter-webmvc`, `spring-boot-starter-data-cassandra`, `spring-boot-starter-actuator`, `spring-boot-starter-validation`)
* **Database**: Apache Cassandra 4.1 (3-node cluster, 1 datacenter)
* **Code Generation & Mapping**: Lombok, MapStruct (1.6.3), Lombok-MapStruct Binding (0.2.0)
* **Build Tool**: Maven 3.x (`maven-compiler-plugin` 3.11.0, `spring-boot-maven-plugin`)
* **Code Coverage**: JaCoCo Maven Plugin (0.8.11)
* **Testing**: Testcontainers Cassandra & JUnit Jupiter
* **Containerization**: Docker & Docker Compose

---

## System Architecture & Database Schema

### Cassandra Keyspace Configuration
- **Keyspace Name:** `log_system`
- **Replication Strategy:** `NetworkTopologyStrategy` (`dc1`: 3 nodes across `rack1`, `rack2`, and `rack3`)

### Data Schema _[schema.cql](src/main/resources/scripts/schema.cql)_

The system creates a keyspace named `log_system` configured with `NetworkTopologyStrategy` across 3 node replicas in datacenter `dc1`.

* **Keyspace**: `log_system` (Replication Factor: 3)
* **Table**: `user_activities`
* **Partition Key**: `user_id` (UUID) – balances logs across cluster nodes.
* **Clustering Keys**: `activity_timestamp` (DESC), `activity_id` (TimeUUID, DESC) – ensures fast retrieval of the most recent user logs and their uniqueness.
* **Default TTL**: 30 days (2,592,000 seconds).

```cassandraql
CREATE KEYSPACE IF NOT EXISTS log_system
WITH REPLICATION = {
    'class': 'NetworkTopologyStrategy',
    'dc1': 3
};

USE log_system;

CREATE TABLE IF NOT EXISTS user_activities (
    user_id uuid,
    activity_id timeuuid,
    activity_type text,
    activity_timestamp timestamp,
    details text,
    PRIMARY KEY ( (user_id), activity_timestamp, activity_id )
) WITH CLUSTERING ORDER BY (activity_timestamp DESC, activity_id DESC )
    AND DEFAULT_TIME_TO_LIVE = 2592000;
```

---

## Consistency Strategy

The microservice implements a hybrid consistency model designed to guarantee **strong consistency**
for time-critical writes and recent log queries, while offering **high performance** for bulk historical queries across the 3-node datacenter (`dc1`).


```
                           ┌────────────────────────────────────────────────────────┐
                           │           WRITE & RECENT READ OPERATIONS               │
                           │               Consistency: LOCAL_QUORUM                │
                           │      (Requires Acknowledgment from 2 of 3 Nodes)       │
                           └───────────────────────────┬────────────────────────────┘
                                                       │
                                                       ▼
                                   ┌───────────────────────────────────────┐
                                   │    Cassandra Cluster (dc1, RF = 3)    │
                                   │   [ Node 1 ]   [ Node 2 ]  [ Node 3 ] │
                                   └───────────────────┬───────────────────┘
                                                       │
                                                       ▼
                           ┌────────────────────────────────────────────────────────┐
                           │             HISTORICAL RANGE READ OPERATIONS           │
                           │                Consistency: LOCAL_ONE                  │
                           │         (Requires Response from 1 Node Only)           │
                           └────────────────────────────────────────────────────────┘

```


### 1. Write Operations: `LOCAL_QUORUM`
* **Configuration:** Set via `spring.cassandra.request.consistency: local_quorum` in `application.yml` and overridden via the `CASSANDRA_CONSISTENCY` environment variable in [_docker-compose.yaml_](./docker-compose.yaml).
* **Behavior:** Every write operation (`saveActivity` via Spring Data Cassandra / `CassandraTemplate` insert) must be acknowledged by a quorum of replica nodes in datacenter `dc1`.
* **Quorum Calculation:**
  $$\text{Quorum} = \lfloor \text{Replication Factor} / 2 \rfloor + 1 = \lfloor 3 / 2 \rfloor + 1 = 2 \text{ nodes}$$
* **Fault Tolerance:** The cluster can sustain a complete outage of **1 node** in `dc1` without failing write requests.

---

### 2. Recent Activity Reads: `LOCAL_QUORUM`
* **Configuration:** Inherited from application-level defaults and explicitly specified on `findByKey_UserId(UUID keyUserId, Limit limit)` in `UserActivityRepository.java`.
* **Behavior:** Fetching a user's recent activities requires response acknowledgment from **2 local replica nodes**.
* **Rationale:** Guarantees **Read-Your-Own-Writes** consistency. When a user creates an activity log and immediately requests their recent activity list,
they are guaranteed to see the latest record without experiencing replication latency issues.

---

### 3. Historical Range Reads: `LOCAL_ONE`
* **Configuration:** Annotated explicitly using `@Consistency(value = DefaultConsistencyLevel.LOCAL_ONE)` on range queries in `UserActivityRepository.java`.
* **Behavior:** Time-range queries (`from` & `to` filters) complete as soon as **1 local replica** responds.
* **Performance:** Optimizes read latency and minimizes cluster overhead when retrieving larger historical log batches.

---

## Getting Started

### Prerequisites
* **Docker Desktop** (with Docker Compose enabled)
* **Java 21 SDK** (optional, if running Spring Boot outside Docker)
* **Apache Maven 3.8+** (optional, if building locally)

---

## Running with Docker Compose

The project includes a multi-node Cassandra cluster set up across three separate virtual racks (`rack1`, `rack2`, `rack3`), 
an initialization container (`cassandra-init`) that applies CQL schemas, and the main Spring Boot application service (`app`).

### 1. Start the Entire Stack
To start the 3-node Cassandra cluster, wait for healthy status, apply schema migrations, and run the app:

```bash
docker compose up -d --build
```

### 2. Check Node Status and Health
To view the status of all containers:
```bash
docker compose ps
```

To view real-time logs for the Spring Boot application:
```bash
docker compose logs -f app
```

To view logs for a specific Cassandra node:
```bash
docker compose logs -f cassandra-1
```

---

## Cassandra Cluster Operations & Maintenance

### Check Cluster Status (`nodetool status`)
To verify that all 3 Cassandra nodes are `UN` (Up / Normal) and properly load-balanced across racks:

```bash
docker compose exec cassandra-1 nodetool status
```

*Example Output:*
```text
Datacenter: dc1
================
Status=Up/Down
|/ State=Normal/Leaving/Joining/Moving
--  Address     Load       Tokens  Owns (effective)  Host ID                               Rack
UN  172.28.0.2  210.4 KB   256     100.0%            12345678-1111-2222-3333-1234567890ab  rack1
UN  172.28.0.3  195.2 KB   256     100.0%            23456789-2222-3333-4444-234567890abc  rack2
UN  172.28.0.4  188.1 KB   256     100.0%            34567890-3333-4444-5555-34567890abcd  rack3
```

### Access Interactive Shell (`cqlsh`)
Connect to the cluster directly using `cqlsh`:

```bash
docker compose exec cassandra-1 cqlsh -k log_system
```

Inside `cqlsh`, you can execute CQL queries:
```cassandraql
-- Query logs for a specific user
SELECT user_id, activity_type, activity_timestamp, TTL(details) FROM user_activities WHERE user_id = 3f2b8c1e-6a4d-4e7b-9c15-2d8a7f0b1e34;
```

### Execute Cluster Maintenance & Repair (`nodetool repair`)
To run a full nodetool repair across data partitions:

```bash
# Full repair on node 1
docker compose exec cassandra-1 nodetool repair log_system

# Primary range repair
docker compose exec cassandra-1 nodetool repair -pr log_system
```

### Node Control Commands

**Stop a specific node:**
```bash
docker compose stop cassandra-2
```

**Start a node back up:**
```bash
docker compose start cassandra-2
```

**Restart a node:**
```bash
docker compose restart cassandra-3
```

**Tear down the stack and remove data volumes:**
```bash
docker compose down -v
```

### Graceful shutdown
To guarantee zero data loss and prevent long startup replay times when bringing containers back up, 
run nodetool drain on each Cassandra node prior to stopping the containers. 
This writes all in-memory data (memtables) to disk (SSTables) and stops listening for client connections.

```bash
docker compose exec cassandra-3 nodetool drain
docker compose exec cassandra-2 nodetool drain
docker compose exec cassandra-1 nodetool drain
docker compose stop
```

### Restarting

```bash
docker compose up -d
```
---

## Running the Application Locally (Outside Docker)

If you wish to run the Cassandra cluster in Docker but run the Spring Boot app locally from your IDE or CLI:

1. **Start only the database cluster and initializer:**
   ```bash
   docker compose up -d cassandra-1 cassandra-2 cassandra-3 cassandra-init
   ```

2. **Run the Spring Boot application:**
   ```bash
   ./mvnw spring-boot:run
   ```

*(The application defaults to `127.0.0.1:9042` when running locally via Spring configuration).*

---

## Running Locally with Native Apache Cassandra (Without Docker)

When running Apache Cassandra directly on your host machine without Docker containers, 
you need to adjust your keyspace replication strategy, local datacenter settings, and application configuration.

---

### 1. Update Keyspace Replication Strategy

In a single-node local setup, you do not have 3 nodes in `dc1`. 
Using `NetworkTopologyStrategy` with `'dc1': 3` on a single native instance will cause query failures (`UnavailableException`) when requesting `LOCAL_QUORUM` consistency.

#### Step 1: Update _[schema.cql](src/main/resources/scripts/schema.cql)_
Change the replication strategy to `SimpleStrategy` with a replication factor of `1` for single-node local development:

```cassandraql
CREATE KEYSPACE IF NOT EXISTS log_system
WITH REPLICATION = {
    'class': 'SimpleStrategy',
    'replication_factor': 1
};

USE log_system;

CREATE TABLE IF NOT EXISTS user_activities (
    user_id uuid,
    activity_id timeuuid,
    activity_type text,
    activity_timestamp timestamp,
    details text,
    PRIMARY KEY ( (user_id), activity_timestamp, activity_id )
) WITH CLUSTERING ORDER BY (activity_timestamp DESC, activity_id DESC )
    AND DEFAULT_TIME_TO_LIVE = 2592000;

```

#### Step 2: Apply Schema via Native `cqlsh`

Execute the schema script using your locally installed Cassandra CLI:

```bash
cqlsh -f src/main/resources/scripts/schema.cql

```

---

### 2. Update Application Configuration (_[application.yaml](src/main/resources/application.yaml)_)

Update your local application properties or environment variables to point to your local Cassandra datacenter name (default for native installations is typically `datacenter1`) and consistency level:

```yaml
spring:
  cassandra:
    port: 9042
    keyspace-name: log_system
    local-datacenter: datacenter1  # Default for native Cassandra (verify with 'nodetool status')
    contact-points: 127.0.0.1:9042
    request:
      consistency: ONE             # Change to ONE for single-node local development

```

Or pass environment variables when starting the app:

```bash
CASSANDRA_LOCAL_DC=datacenter1 \
CASSANDRA_CONSISTENCY=ONE \
./mvnw spring-boot:run

```

---

## How to Add a Second Datacenter (`dc2`)

To expand your Cassandra cluster across multiple logical or geographical datacenters (e.g., `dc1` and `dc2`), follow these steps:

### Step 1: Configure Snitch in `cassandra.yaml`

On all Cassandra nodes across both datacenters, ensure the snitch supports rack and datacenter awareness in `cassandra.yaml`:

```yaml
endpoint_snitch: GossipingPropertyFileSnitch

```

### Step 2: Define Datacenter and Rack in `cassandra-rackdc.properties`

On each node, specify its corresponding datacenter and rack in the `conf/cassandra-rackdc.properties` file:

* **For nodes in DC1:**
```properties
dc=dc1
rack=rack1

```


* **For nodes in DC2:**
```properties
dc=dc2
rack=rack1

```

### Step 3: Set Seeds Across Both Datacenters

Update `cassandra.yaml` on **all nodes** so that the `seeds` list includes at least one seed node from `dc1` and one seed node from `dc2`:

```yaml
seed_provider:
    - class_name: org.apache.cassandra.locator.SimpleSeedProvider
      parameters:
          - seeds: "10.0.1.10,10.0.2.10" # IP of dc1-seed, IP of dc2-seed

```

### Step 4: Start Nodes in the New Datacenter (`dc2`)

Start the Cassandra service on the new nodes in `dc2` one by one. Verify node joining via:

```bash
nodetool status

```

---

### Step 5: Update Keyspace Replication Strategy

Update the `log_system` keyspace definition to replicate data to the new datacenter `dc2`:

```cassandraql
ALTER KEYSPACE log_system
WITH REPLICATION = {
    'class': 'NetworkTopologyStrategy',
    'dc1': 3,
    'dc2': 3
};

```

---

### Step 6: Stream Data to the New Datacenter (`nodetool rebuild`)

Altering the keyspace replication does not automatically copy existing data to the new datacenter nodes. Run `nodetool rebuild` on **every node in `dc2**`:

```bash
nodetool rebuild -- dc1

```

*(This streams all existing partition keys for `log_system` from `dc1` to the `dc2` nodes).*

---

### Step 7: Update Application Configuration

Update the Spring Boot configuration to include contact points from both datacenters or point regional instances of your application to their respective local datacenters:

```yaml
spring:
  cassandra:
    local-datacenter: dc2 # Set to the local DC closest to this application instance
    contact-points: 10.0.1.10:9042,10.0.2.10:9042
```

---

## API Reference

### 1. Log a User Activity
- **HTTP Method:** `POST`
- **Endpoint:** `/user_activities/{userId}`
- **Headers:** `Content-Type: application/json`

#### Example Request:
```bash
curl -X POST "http://localhost:8080/user_activities/3f2b8c1e-6a4d-4e7b-9c15-2d8a7f0b1e34" \
     -H "Content-Type: application/json" \
     -d '{
           "activityType": "PURCHASE_COMPLETE",
           "details": "User completed order #98213",
           "ttlInSeconds": 86400
         }'
```

---

### 2. Fetch User Activities
- **HTTP Method:** `GET`
- **Endpoint:** `/user_activities/{userId}`
- **Query Parameters:**
    - `limit` *(optional)*: Integer (1 - 100)
    - `from` *(optional, ISO-8601 string)*: e.g. `2026-01-01T00:00:00Z`
    - `to` *(optional, ISO-8601 string)*: e.g. `2026-12-31T23:59:59Z`

#### Query Parameter Combinations & Behavior:
* **No `limit` and no time range (`from`/`to` omitted):** returns **all stored activities** for the given user, sorted from newest to oldest.
* **Only `limit` specified:** returns the **most recent activity logs up to the specified limit**.
* **Only time range specified (`from` & `to`):** returns **all activities within the given time range** without any count restriction.
* **Time range AND `limit` specified:** returns **activities within the given time range, capped at the requested limit*.

> **Note:** The parameters `from` and `to` must always be specified together. Providing only one of them will cause a validation error. Additionally `from` must come strictly after `to`, otherwise there will be an error.

#### Example Requests:
```bash
# Get all user activities
curl -X GET "http://localhost:8080/user_activities/3f2b8c1e-6a4d-4e7b-9c15-2d8a7f0b1e34"

# Get top 5 most recent activities
curl -X GET "http://localhost:8080/user_activities/3f2b8c1e-6a4d-4e7b-9c15-2d8a7f0b1e34?limit=5"

# Query activities within a time range without a limit
curl -X GET "http://localhost:8080/user_activities/3f2b8c1e-6a4d-4e7b-9c15-2d8a7f0b1e34?from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z"

# Query activities within a time range with a limit
curl -X GET "http://localhost:8080/user_activities/3f2b8c1e-6a4d-4e7b-9c15-2d8a7f0b1e34?from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z&limit=10"
```

---

## Background Activity Simulation

The application contains a built-in simulation service (`ActivitySimulationService`) enabled via Spring `@Scheduled`.
Every **5 seconds**, it automatically injects synthetic user activity records into the database. 
These **5 seconds** setting is configurable via envs (see [application.yaml](src/main/resources/application.yaml) for details).
You can inspect these simulated records in real-time through the `GET /user_activities/{userId}` endpoint or directly via `cqlsh`.

## `cassandra.yaml` editing

### Option 1: Install `nano` inside the Container (Quick & Temporary)

The official `cassandra:4.1` Docker image is minimal and does **not** include text editors like `nano` or `vim` by default. Below are two methods to edit your configuration using `nano`.

This approach is best for quick experimentation on a running container without modifying your [_docker-compose.yaml_](docker-compose.yaml) setup.

#### 1. Open a Root Shell in the Container
Run `docker compose exec` with the `-u root` flag to ensure you have permissions to install software:
```bash
docker compose exec -u root cassandra-1 bash

```

#### 2. Install `nano`

Update the `apt` package manager inside the container and install `nano`:

```bash
apt-get update && apt-get install -y nano

```

#### 3. Edit `cassandra.yaml`

Open the configuration file directly inside the container:

```bash
nano /etc/cassandra/cassandra.yaml

```

* **Save changes:** Press `Ctrl + O`, then press `Enter`.
* **Exit `nano`:** Press `Ctrl + X`.

#### 4. Exit the Container & Restart Service

Type `exit` to return to your host terminal, then restart the container to apply the changes:

```bash
docker compose restart cassandra-1

```

> **Warning:** Changes made inside the container's file system are temporary. If you tear down the container using `docker compose down`, your installed `nano` package and edits to `cassandra.yaml` will be lost.

---

### Option 2: Edit `cassandra.yaml` Locally on Host via Volume Mount (Persistent & Recommended)

This approach lets you edit the file using `nano` on your host machine while keeping all configuration changes version-controlled and persistent across container restarts.

#### 1. Copy `cassandra.yaml` from the Container to Host

Extract the default `cassandra.yaml` file to your local project directory:

```bash
docker cp useractivitylogsystem-cassandra-1-1:/etc/cassandra/cassandra.yaml ./cassandra.yaml

```

#### 2. Edit the Local File with `nano`

Use `nano` on your host terminal to modify the file:

```bash
nano ./cassandra.yaml

```

#### 3. Mount the Local File in _[docker-compose.yaml](./docker-compose.yaml)_

Add the file mount under the `volumes` section of your Cassandra service:

```yaml
services:
  cassandra-1:
    image: cassandra:4.1
    environment:
      <<: *cass-env
      CASSANDRA_RACK: rack1
    volumes:
      - cass1-data:/var/lib/cassandra
      # Mount custom cassandra.yaml read-only into the container
      - ./cassandra.yaml:/etc/cassandra/cassandra.yaml:ro
    healthcheck: *cass-health
    networks: [activity-net]

```

#### 4. Apply Changes

Restart the service with Docker Compose:

```bash
docker compose up -d cassandra-1

```

## Testing Strategy

The project employs a multi-tiered testing strategy covering unit tests, 
controller slice validation, exception handling, object mapping, background tasks, and 
full end-to-end integration testing with a real Cassandra container.

---

### 1. Test Suite Overview
| Test Class | Category / Scope | Key Responsibilities & Tools |
| :--- | :--- | :--- |
| **`UserActivityIntegrationTest`** | **Integration Tests** | Spawns a real **Apache Cassandra container** via **Testcontainers** (`CassandraContainer`) and `@ServiceConnection`. Verifies database persistence, TimeUUID key generation, TTL enforcement, and range query correctness. |
| **`UserActivityControllerTest`** | **REST API / Web Slice** | Uses `@WebMvcTest` and `MockMvc` to test HTTP endpoints. Verifies request parameter parsing, payload validation rules (`@Valid`), path variable conversion, HTTP status codes, and JSON response structures. |
| **`UserActivityServiceTest`** | **Business Logic** | Pure unit tests with **Mockito**. Verifies TTL fallback calculations, time range validation logic, repository routing, and TimeUUID key construction. |
| **`GlobalExceptionHandlerTest`** | **Error Handling** | Tests translation of application and Cassandra driver exceptions (`UnavailableException`, `DriverTimeoutException`, `ConstraintViolationException`) into RFC-7807 `ProblemDetail` responses. |
| **`ActivitySimulationServiceTest`** | **Scheduled Tasks** | Verifies random selection of mock users/activities and confirms that background simulation failures are gracefully caught without taking down the application. |
| **`UserActivityMapperTest`** | **Object Mapping** | Tests MapStruct entity-to-DTO mappings, including single entities, null checking, and collection mappings. |
---

### 2. Integration Testing with Testcontainers

Integration tests use **Testcontainers** to spin up an isolated Cassandra container using `scripts/schema.cql`:

* **Isolated Keyspace:** Uses a dedicated `activity_test` keyspace configured with `SimpleStrategy` ($RF=1$) for fast execution.
* **Real CQL Checks:** Validates actual data stored in Cassandra using native `CqlSession` queries (e.g., verifying `SELECT TTL(activity_type)`).
* **TimeUUID Verification:** Ensures time-based UUIDs match expected version 1 formatting.

---

### 3. Running Tests & Coverage Reports

#### Run All Unit and Integration Tests
```bash
./mvnw clean test

```

#### Run Only Integration Tests

```bash
./mvnw test -Dtest=UserActivityIntegrationTest

```

#### Run Only Web Slice / Controller Tests

```bash
./mvnw test -Dtest=UserActivityControllerTest

```

#### Generate JaCoCo Code Coverage Report

The project includes the **JaCoCo Maven Plugin** (configured in `pom.xml`). Run the following to execute tests and produce an HTML coverage report:

```bash
./mvnw clean test jacoco:report

```

> The HTML report will be generated at: `target/site/jacoco/index.html`