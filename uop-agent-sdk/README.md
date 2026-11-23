# UOP Agent SDK

Core SDK for building monitoring agents that connect to the UOP platform via gRPC.

## Modules

- `uop-agent-sdk` - Core SDK (this module)
- `uop-agent-collectors` - Built-in collectors (disk, network, process, etc.)
- `uop-agent` - Launcher executable (fat JAR with install scripts)

## Features

- gRPC bidirectional streaming to platform
- Automatic registration & authentication
- Credential persistence (secure file storage)
- Exponential backoff reconnection
- Observation batching with retry logic
- Hot descriptor reload
- Pluggable collector interface

## Quick Start

```xml
<dependency>
    <groupId>com.platform.uop</groupId>
    <artifactId>uop-agent-sdk</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
AgentSdk agent = AgentSdk.builder()
    .platformHost("localhost")
    .platformPort(9090)
    .registrationToken("your-token")
    .collector(new DiskCollector())
    .build();

agent.start();
// ... runs until shutdown
agent.close();
```

## ⚠️ Fat JAR Requirement (Critical)

When bundling this SDK in a **fat/uber JAR** (e.g., for distribution), you **MUST** use `maven-shade-plugin` with `ServicesResourceTransformer` to properly merge gRPC SPI service files.

### Required Maven Configuration

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.1</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <createDependencyReducedPom>false</createDependencyReducedPom>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass>your.MainClass</mainClass>
                    </transformer>
                </transformers>
                <filters>
                    <filter>
                        <artifact>*:*</artifact>
                        <excludes>
                            <exclude>META-INF/*.SF</exclude>
                            <exclude>META-INF/*.DSA</exclude>
                            <exclude>META-INF/*.RSA</exclude>
                            <exclude>org/slf4j/**</exclude>
                            <exclude>ch/qos/logback/**</exclude>
                        </excludes>
                    </filter>
                </filters>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Why This Is Required

This SDK uses gRPC, which relies on Java SPI (`META-INF/services/io.grpc.LoadBalancerProvider`) for load balancer providers (`pick_first`, `round_robin`, etc.). 

When building a fat JAR, multiple gRPC dependencies have the same SPI files. **You must merge them** (concatenate contents), not overwrite. If not merged, the fat JAR only contains one provider → gRPC fails with:

```
Could not find policy 'pick_first'. Make sure its implementation is either registered to LoadBalancerRegistry or included in META-INF/services/io.grpc.LoadBalancerProvider from your jar files.
```

### What NOT to Use

- ❌ `maven-assembly-plugin` (does NOT merge SPI files by default)
- ❌ `maven-shade-plugin` version 3.6.0 (has a bug with `ManifestResourceTransformer`)

### Verification Test

Add this test to verify your fat JAR has merged SPI files:

```java
@Test
void fatJarHasMergedSPI() throws IOException {
    try (JarFile jar = new JarFile("target/your-fat-jar.jar")) {
        ZipEntry entry = jar.getEntry("META-INF/services/io.grpc.LoadBalancerProvider");
        assertNotNull(entry, "SPI file missing");
        String content = new String(jar.getInputStream(entry).readAllBytes());
        assertTrue(content.contains("PickFirstLoadBalancerProvider"));
        assertTrue(content.contains("RoundRobinLoadBalancerProvider"));
    }
}
```

## Build

```bash
./mvnw install -DskipTests
```

## Tests

```bash
./mvnw test
```

## License

Proprietary - UOP Platform
