package com.platform.uop.agent;

import com.platform.uop.agent.sdk.AgentSdk;
import com.platform.uop.collectors.DiskCollector;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

@Command(name = "uop-agent", mixinStandardHelpOptions = true, version = "0.1.0",
    description = "UOP Universal Monitoring Agent")
public class AgentLauncher implements Callable<Integer> {

    @Option(names = {"-p", "--platform"}, description = "Platform gRPC address (host:port)", required = true)
    String platformUrl;

    @Option(names = {"-t", "--token"}, description = "Registration token (required for first run)")
    String registrationToken;

    @Option(names = {"-c", "--config"}, description = "Path to YAML config file")
    Path configFile;

    @Option(names = {"-v", "--verbose"}, description = "Enable debug logging")
    boolean verbose;

    @Option(names = {"--cred-path"}, description = "Path to credential store")
    Path credentialPath;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new AgentLauncher()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (verbose) {
            System.setProperty("log.level", "DEBUG");
        }

        String[] parts = platformUrl.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9090;

        Path credPath = credentialPath != null ? credentialPath 
            : Paths.get(System.getProperty("user.home"), ".uop-agent", "credentials");

        AgentSdk agent = AgentSdk.builder()
            .platformHost(host)
            .platformPort(port)
            .registrationToken(registrationToken)
            .credentialsPath(credPath)
            .collector(new DiskCollector())
            .build();

        Runtime.getRuntime().addShutdownHook(new Thread(agent::close));
        
        System.out.println("Starting UOP Agent...");
        System.out.println("Platform: " + platformUrl);
        System.out.println("Credential store: " + credPath);
        
        agent.start();
        agent.awaitTermination();
        
        return 0;
    }
}