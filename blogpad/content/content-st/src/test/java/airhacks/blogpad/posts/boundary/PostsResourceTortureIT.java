package airhacks.blogpad.posts.boundary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import airhacks.blogpad.Configuration;
import airhacks.blogpad.metrics.boundary.MetricsResourceClient;

public class PostsResourceTortureIT {
    private PostsResourceClient client;
	private String title;
    private ExecutorService threadPool;
    
    private MetricsResourceClient metricsClient;



    @BeforeEach
    public void init() {
        var uri = Configuration.getValue("resource.uri");
        this.client = RestClientBuilder.newBuilder().baseUri(uri).build(PostsResourceClient.class);
        this.title = "torture" + System.currentTimeMillis();
        JsonObject post = Json.createObjectBuilder().add("title", title).add("content", "for torture").build();
        Response response = this.client.createNew(post);
        int status = response.getStatus();
        assertEquals(201, status);
        this.threadPool = Executors.newFixedThreadPool(20);
        this.initMetricsEndpoint();
    }

    void initMetricsEndpoint() {
        var adminUri = Configuration.getValue("admin.uri");
        this.metricsClient = RestClientBuilder.
                newBuilder().
                baseUri(adminUri).
                build(MetricsResourceClient.class);


    }
    

    @Test
    public void startTorture() {
        assumeTrue(Configuration.getBooleanValue("torture"));
        List<CompletableFuture<Void>> tasks = Stream
                .generate(this::runScenario).limit(500)
                .collect(Collectors.toList());
        tasks.forEach(CompletableFuture::join);
        this.verifyPerformance();
    }

    CompletableFuture<Void> runScenario() {
        return CompletableFuture.runAsync(this::findPost, this.threadPool).
        thenRunAsync(this::findNonExistingPost,
                this.threadPool);
    }
    
    void findNonExistingPost() {
        var response = this.client.findPost("not-existing" + System.nanoTime());
        assertEquals(204,response.getStatus());
    
    }

    void findPost() {
        Response response = this.client.findPost(this.title);
        JsonObject post = response.readEntity(JsonObject.class);
        assertNotNull(post);
    }
    
    void verifyPerformance() {
        JsonObject findOperationResult = this.metricsClient.applicationMetrics()
                .getJsonObject("airhacks.blogpad.posts.boundary.PostsResource.find");
        double oneMinRate = findOperationResult.getJsonNumber("oneMinRate").doubleValue();
        System.out.println("####-------oneMinRate " + oneMinRate);
        assertTrue(oneMinRate > 5);
    }
    
}