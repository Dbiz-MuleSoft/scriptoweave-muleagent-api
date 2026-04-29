package com.mycompany;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * API Zip Builder - Dynamically generates Mule API projects from JSON design structures
 */
public class ApiZipBuilder {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Main method to build API zip file from JSON design
     * @param jsonDesign JSON string containing the API design structure
     * @return Raw byte array of the zip file content
     */
    public static byte[] buildApiZip(String jsonDesign) {
        try {
            JsonNode designRoot = objectMapper.readTree(jsonDesign);
            
            // Create temporary directory for project generation
            Path tempDir = Files.createTempDirectory("mule-api-gen");
            
            // Process each layer in the design
            JsonNode layers = designRoot.get("layers");
            if (layers != null && layers.isArray()) {
                for (JsonNode layer : layers) {
                    String layerName = layer.get("name").asText();
                    
                    // Generate dynamic project directory name based on content
                    String dynamicProjectDirName = generateDynamicProjectDirectoryName(layer, layerName);
                    Path layerDir = tempDir.resolve(dynamicProjectDirName);
                    
                    // Create Mule project structure for each layer
                    createMuleProjectStructure(layerDir, layerName, layer);
                    
                    // Generate flows for this layer
                    generateFlowsForLayer(layerDir, layer);
                }
            }
            
            // Create ZIP file from generated projects
            byte[] zipContent = createZipFromDirectory(tempDir);
            
            // Clean up temporary directory
            deleteDirectory(tempDir);
            
            // Return raw byte array
            return zipContent;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to build API zip: " + e.getMessage(), e);
        }
    }
    
    /**
     * Creates standard Mule project structure
     */
    private static void createMuleProjectStructure(Path projectDir, String layerName, JsonNode layer) throws IOException {
        // Create directory structure
        Files.createDirectories(projectDir.resolve("src/main/mule"));
        Files.createDirectories(projectDir.resolve("src/main/resources"));
        Files.createDirectories(projectDir.resolve("src/test/munit"));
        Files.createDirectories(projectDir.resolve("src/test/resources"));
        
        // Create pom.xml with dynamic naming and dependencies
        String pomContent = generatePomXml(layerName, layer);
        Files.write(projectDir.resolve("pom.xml"), pomContent.getBytes());
        
        // Create mule-artifact.json
        String muleArtifactContent = generateMuleArtifact();
        Files.write(projectDir.resolve("mule-artifact.json"), muleArtifactContent.getBytes());
        
        // Create global.xml
        String globalContent = generateGlobalConfiguration(layerName, layer);
        Files.write(projectDir.resolve("src/main/mule/global.xml"), globalContent.getBytes());
        
        // Create config.properties
        String configContent = generateConfigProperties(layerName, layer);
        Files.write(projectDir.resolve("src/main/resources/config.properties"), configContent.getBytes());
        
        // Create log4j2.xml
        String logContent = generateLogConfiguration();
        Files.write(projectDir.resolve("src/main/resources/log4j2.xml"), logContent.getBytes());
    }
    
    /**
     * Generates flows for a specific layer based on the JSON design
     */
    private static void generateFlowsForLayer(Path projectDir, JsonNode layer) throws IOException {
        String layerName = layer.get("name").asText();
        StringBuilder flowsXml = new StringBuilder();
        
        // Analyze required namespaces based on flow steps
        Set<String> requiredNamespaces = analyzeRequiredNamespaces(layer);
        
        // XML header with dynamically determined namespaces
        flowsXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<mule xmlns=\"http://www.mulesoft.org/schema/mule/core\"\n")
                .append("      xmlns:doc=\"http://www.mulesoft.org/schema/mule/documentation\"\n")
                .append("      xmlns:ee=\"http://www.mulesoft.org/schema/mule/ee/core\"\n")
                .append("      xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        
        // Add required connector namespaces
        for (String namespace : requiredNamespaces) {
            flowsXml.append("      xmlns:").append(namespace).append("=\"http://www.mulesoft.org/schema/mule/").append(namespace).append("\"\n");
        }
        
        // Schema locations
        flowsXml.append("      xsi:schemaLocation=\"http://www.mulesoft.org/schema/mule/core http://www.mulesoft.org/schema/mule/core/current/mule.xsd\n")
                .append("                          http://www.mulesoft.org/schema/mule/ee/core http://www.mulesoft.org/schema/mule/ee/core/current/mule-ee.xsd\n");
        
        // Add schema locations for required namespaces
        for (String namespace : requiredNamespaces) {
            flowsXml.append("                          http://www.mulesoft.org/schema/mule/").append(namespace)
                   .append(" http://www.mulesoft.org/schema/mule/").append(namespace).append("/current/mule-").append(namespace).append(".xsd\n");
        }
        
        flowsXml.append("\">\n\n");
        
        // Process flows in this layer
        JsonNode flows = layer.get("flows");
        if (flows != null && flows.isArray()) {
            for (JsonNode flow : flows) {
                String flowXml = generateFlowXml(flow, layerName);
                flowsXml.append(flowXml).append("\n");
            }
        }
        
        flowsXml.append("</mule>");
        
        // Write the flows XML file
        String fileName = layerName + "-flows.xml";
        Files.write(projectDir.resolve("src/main/mule/" + fileName), flowsXml.toString().getBytes());
    }
    
    /**
     * Generates XML for a single flow based on JSON definition
     */
    private static String generateFlowXml(JsonNode flowNode, String layerName) {
        String originalFlowName = flowNode.get("name").asText();
        String path = flowNode.get("path").asText();
        String method = flowNode.get("method").asText();
        
        // Generate dynamic flow name based on context and layer
        String dynamicFlowName = generateDynamicFlowName(originalFlowName, layerName, path, method);
        
        StringBuilder flowXml = new StringBuilder();
        flowXml.append("    <flow name=\"").append(dynamicFlowName).append("\" doc:id=\"").append(UUID.randomUUID().toString()).append("\">\n");
        
        // Add try scope for error handling
        flowXml.append("        <try doc:name=\"Try\" doc:id=\"").append(UUID.randomUUID().toString()).append("\">\n");
        
        JsonNode steps = flowNode.get("steps");
        if (steps != null && steps.isArray()) {
            for (JsonNode step : steps) {
                String stepXml = generateStepXml(step, path, method);
                flowXml.append("            ").append(stepXml).append("\n");
            }
        }
        
        // Add success response
        flowXml.append("            <ee:transform doc:name=\"Success Response\" doc:id=\"").append(UUID.randomUUID().toString()).append("\">\n");
        flowXml.append("                <ee:message>\n");
        flowXml.append("                    <ee:set-payload><![CDATA[%dw 2.0\n");
        flowXml.append("output application/json\n");
        flowXml.append("---\n");
        flowXml.append("{\n");
        flowXml.append("    \"status\": \"success\",\n");
        flowXml.append("    \"message\": \"Request processed successfully\",\n");
        flowXml.append("    \"data\": payload,\n");
        flowXml.append("    \"timestamp\": now()\n");
        flowXml.append("}]]></ee:set-payload>\n");
        flowXml.append("                </ee:message>\n");
        flowXml.append("                <ee:variables>\n");
        flowXml.append("                    <ee:set-variable variableName=\"httpStatus\">200</ee:set-variable>\n");
        flowXml.append("                </ee:variables>\n");
        flowXml.append("            </ee:transform>\n");
        
        // Add error handler
        flowXml.append("            <error-handler>\n");
        flowXml.append("                <on-error-propagate enableNotifications=\"true\" logException=\"true\" doc:name=\"Validation Error\" doc:id=\"").append(UUID.randomUUID().toString()).append("\" type=\"VALIDATION:INVALID_VALUE\">\n");
        flowXml.append("                    <ee:transform doc:name=\"Validation Error Response\" doc:id=\"").append(UUID.randomUUID().toString()).append("\">\n");
        flowXml.append("                        <ee:message>\n");
        flowXml.append("                            <ee:set-payload><![CDATA[%dw 2.0\n");
        flowXml.append("output application/json\n");
        flowXml.append("---\n");
        flowXml.append("{\n");
        flowXml.append("    \"status\": \"error\",\n");
        flowXml.append("    \"message\": \"Validation failed: \" ++ error.description,\n");
        flowXml.append("    \"errorType\": \"VALIDATION_ERROR\",\n");
        flowXml.append("    \"timestamp\": now()\n");
        flowXml.append("}]]></ee:set-payload>\n");
        flowXml.append("                        </ee:message>\n");
        flowXml.append("                        <ee:variables>\n");
        flowXml.append("                            <ee:set-variable variableName=\"httpStatus\">400</ee:set-variable>\n");
        flowXml.append("                        </ee:variables>\n");
        flowXml.append("                    </ee:transform>\n");
        flowXml.append("                </on-error-propagate>\n");
        flowXml.append("                <on-error-propagate enableNotifications=\"true\" logException=\"true\" doc:name=\"General Error\" doc:id=\"").append(UUID.randomUUID().toString()).append("\">\n");
        flowXml.append("                    <ee:transform doc:name=\"General Error Response\" doc:id=\"").append(UUID.randomUUID().toString()).append("\">\n");
        flowXml.append("                        <ee:message>\n");
        flowXml.append("                            <ee:set-payload><![CDATA[%dw 2.0\n");
        flowXml.append("output application/json\n");
        flowXml.append("---\n");
        flowXml.append("{\n");
        flowXml.append("    \"status\": \"error\",\n");
        flowXml.append("    \"message\": \"An unexpected error occurred: \" ++ error.description,\n");
        flowXml.append("    \"errorType\": error.errorType.identifier default \"UNKNOWN_ERROR\",\n");
        flowXml.append("    \"timestamp\": now()\n");
        flowXml.append("}]]></ee:set-payload>\n");
        flowXml.append("                        </ee:message>\n");
        flowXml.append("                        <ee:variables>\n");
        flowXml.append("                            <ee:set-variable variableName=\"httpStatus\">500</ee:set-variable>\n");
        flowXml.append("                        </ee:variables>\n");
        flowXml.append("                    </ee:transform>\n");
        flowXml.append("                </on-error-propagate>\n");
        flowXml.append("            </error-handler>\n");
        flowXml.append("        </try>\n");
        flowXml.append("    </flow>");
        
        return flowXml.toString();
    }
    
    /**
     * Generates XML for individual flow steps
     */
    private static String generateStepXml(JsonNode step, String path, String method) {
        String stepType = step.get("type").asText();
        String docId = UUID.randomUUID().toString();
        
        switch (stepType) {
            case "listener":
                return String.format("<http:listener doc:name=\"Listener\" doc:id=\"%s\" config-ref=\"HTTP_Listener_config\" path=\"%s\" allowedMethods=\"%s\"/>", 
                                   docId, path, method);
                
            case "logger":
                return String.format("<logger level=\"INFO\" doc:name=\"Logger\" doc:id=\"%s\" message=\"Processing %s %s\"/>", 
                                   docId, method, path);
                
            case "validation":
                return generateValidationXml(step, docId);
                
            case "transform":
                return generateTransformXml(step, docId);
                
            case "http-call":
                return generateHttpCallXml(step, docId);
                
            case "db":
                return generateDbXml(step, docId);
                
            default:
                return String.format("<logger level=\"INFO\" doc:name=\"%s\" doc:id=\"%s\" message=\"Step type: %s\"/>", 
                                   stepType, docId, stepType);
        }
    }
    
    private static String generateValidationXml(JsonNode step, String docId) {
        StringBuilder validation = new StringBuilder();
        validation.append("<validation:all doc:name=\"Validate Input\" doc:id=\"").append(docId).append("\">\n");
        
        JsonNode rules = step.get("rules");
        if (rules != null) {
            JsonNode mandatoryFields = rules.get("mandatoryFields");
            if (mandatoryFields != null && mandatoryFields.isArray()) {
                for (JsonNode field : mandatoryFields) {
                    String fieldName = field.asText();
                    validation.append("            <validation:is-not-null value=\"#[payload.").append(fieldName).append("]\" message=\"").append(fieldName).append(" is required\"/>\n");
                }
            }
        }
        
        validation.append("        </validation:all>");
        return validation.toString();
    }
    
    private static String generateTransformXml(JsonNode step, String docId) {
        StringBuilder transform = new StringBuilder();
        transform.append("<ee:transform doc:name=\"Transform Message\" doc:id=\"").append(docId).append("\">\n");
        transform.append("            <ee:message>\n");
        transform.append("                <ee:set-payload><![CDATA[%dw 2.0\n");
        transform.append("output application/json\n");
        transform.append("---\n");
        transform.append("{\n");
        
        JsonNode fieldMappings = step.get("fieldMappings");
        if (fieldMappings != null) {
            Iterator<Map.Entry<String, JsonNode>> fields = fieldMappings.fields();
            List<String> mappings = new ArrayList<>();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                mappings.add("    \"" + field.getKey() + "\": payload." + field.getValue().asText());
            }
            transform.append(String.join(",\n", mappings)).append("\n");
        }
        
        transform.append("}]]></ee:set-payload>\n");
        transform.append("            </ee:message>\n");
        transform.append("        </ee:transform>");
        return transform.toString();
    }
    
    private static String generateHttpCallXml(JsonNode step, String docId) {
        JsonNode config = step.get("config");
        String url = config.get("url").asText();
        String method = config.get("method").asText();
        
        return String.format("<http:request method=\"%s\" doc:name=\"HTTP Call\" doc:id=\"%s\" config-ref=\"HTTP_Request_config\" path=\"%s\"/>", 
                           method, docId, url);
    }
    
    private static String generateDbXml(JsonNode step, String docId) {
        JsonNode config = step.get("config");
        String table = config.get("table").asText();
        
        return String.format("<db:insert doc:name=\"Insert to %s\" doc:id=\"%s\" config-ref=\"Database_Config\">\n" +
                           "            <db:sql>INSERT INTO %s (appointmentId, firstName, lastName, primaryEmail, primaryPhone, appointmentType) VALUES (:appointmentId, :firstName, :lastName, :primaryEmail, :primaryPhone, :appointmentType)</db:sql>\n" +
                           "            <db:input-parameters><![CDATA[#[payload]]]></db:input-parameters>\n" +
                           "        </db:insert>", table, docId, table);
    }
    
    /**
     * Generates dynamic flow name based on context and layer
     */
    private static String generateDynamicFlowName(String originalFlowName, String layerName, String path, String method) {
        // Extract the resource name from the path (e.g., "/appointments" -> "appointment")
        String resourceName = extractResourceName(path);
        
        // Generate abbreviated layer suffix
        String layerSuffix = generateLayerSuffix(layerName);
        
        // Generate method-based suffix if needed
        String methodSuffix = generateMethodSuffix(method, path);
        
        // Combine to create dynamic flow name
        String dynamicFlowName = resourceName + "-" + layerSuffix + methodSuffix;
        
        return dynamicFlowName.toLowerCase();
    }
    
    /**
     * Extracts resource name from path
     */
    private static String extractResourceName(String path) {
        // Remove leading slash and any path parameters
        String cleanPath = path.replaceAll("^/+", "").replaceAll("/.*", "");
        
        // Convert plural to singular for better naming (appointments -> appointment)
        if (cleanPath.endsWith("s") && cleanPath.length() > 1) {
            cleanPath = cleanPath.substring(0, cleanPath.length() - 1);
        }
        
        return cleanPath.isEmpty() ? "resource" : cleanPath;
    }
    
    /**
     * Generates layer-specific suffix
     */
    private static String generateLayerSuffix(String layerName) {
        switch (layerName.toLowerCase()) {
            case "experience":
                return "exp-api";
            case "process":
                return "process-api";
            case "system":
                return "sys-api";
            default:
                return layerName.toLowerCase() + "-api";
        }
    }
    
    /**
     * Generates method-specific suffix if needed
     */
    private static String generateMethodSuffix(String method, String path) {
        // For REST operations, we might want to include the method in complex scenarios
        if (path.contains("/{") || method.equalsIgnoreCase("DELETE") || method.equalsIgnoreCase("PATCH")) {
            return "-" + method.toLowerCase();
        }
        // For simple POST/GET operations on collections, method is implied
        return "";
    }
    
    /**
     * Generates dynamic project directory name based on layer content
     */
    private static String generateDynamicProjectDirectoryName(JsonNode layer, String layerName) {
        // Extract resource name from the first flow
        String resourceName = "api";
        if (layer.has("flows") && layer.get("flows").isArray() && layer.get("flows").size() > 0) {
            JsonNode firstFlow = layer.get("flows").get(0);
            if (firstFlow.has("path")) {
                resourceName = extractResourceName(firstFlow.get("path").asText());
            }
        }
        
        // Generate layer suffix
        String layerSuffix = generateLayerSuffix(layerName).replace("-api", "");
        
        // Create dynamic directory name: resource-layer-api (e.g., appointment-exp-api)
        return resourceName + "-" + layerSuffix + "-api";
    }
    
    // Helper methods for generating configuration files
    
    private static String generatePomXml(String layerName, JsonNode layer) {
        // Extract resource name from first flow to create dynamic project name
        String resourceName = "api";
        if (layer.has("flows") && layer.get("flows").isArray() && layer.get("flows").size() > 0) {
            JsonNode firstFlow = layer.get("flows").get(0);
            if (firstFlow.has("path")) {
                resourceName = extractResourceName(firstFlow.get("path").asText());
            }
        }
        
        // Create dynamic artifact ID and name
        String dynamicArtifactId = resourceName + "-" + generateLayerSuffix(layerName).replace("-api", "") + "-api";
        String dynamicName = resourceName.substring(0, 1).toUpperCase() + resourceName.substring(1) + " " + 
                            layerName.substring(0, 1).toUpperCase() + layerName.substring(1) + " API";
        
        // Analyze required dependencies based on flow steps
        Set<String> requiredDependencies = analyzeDependencies(layer);
        
        StringBuilder pom = new StringBuilder();
        pom.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
           .append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
           .append("    xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/maven-v4_0_0.xsd\">\n")
           .append("    <modelVersion>4.0.0</modelVersion>\n")
           .append("    <groupId>com.mycompany</groupId>\n")
           .append("    <artifactId>").append(dynamicArtifactId).append("</artifactId>\n")
           .append("    <version>1.0.0-SNAPSHOT</version>\n")
           .append("    <packaging>mule-application</packaging>\n")
           .append("    <name>").append(dynamicName).append("</name>\n")
           .append("    <properties>\n")
           .append("        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n")
           .append("        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>\n")
           .append("        <app.runtime>4.9.0</app.runtime>\n")
           .append("        <mule.maven.plugin.version>4.3.0</mule.maven.plugin.version>\n")
           .append("        <mule.http.connector.version>1.9.0</mule.http.connector.version>\n")
           .append("        <mule.db.connector.version>1.14.0</mule.db.connector.version>\n")
           .append("        <mule.validation.module.version>2.0.4</mule.validation.module.version>\n")
           .append("        <mule.salesforce.connector.version>10.18.4</mule.salesforce.connector.version>\n")
           .append("        <mule.file.connector.version>1.5.1</mule.file.connector.version>\n")
           .append("        <mule.ftp.connector.version>1.8.4</mule.ftp.connector.version>\n")
           .append("        <mule.jms.connector.version>1.8.5</mule.jms.connector.version>\n")
           .append("        <mule.vm.connector.version>2.0.1</mule.vm.connector.version>\n")
           .append("        <mule.email.connector.version>1.7.1</mule.email.connector.version>\n")
           .append("        <mule.amqp.connector.version>1.7.3</mule.amqp.connector.version>\n")
           .append("        <mule.kafka.connector.version>4.7.0</mule.kafka.connector.version>\n")
           .append("        <jackson.version>2.15.2</jackson.version>\n")
           .append("        <maven.compiler.source>17</maven.compiler.source>\n")
           .append("        <maven.compiler.target>17</maven.compiler.target>\n")
           .append("    </properties>\n")
           .append("    <build>\n")
           .append("        <plugins>\n")
           .append("            <plugin>\n")
           .append("                <groupId>org.apache.maven.plugins</groupId>\n")
           .append("                <artifactId>maven-clean-plugin</artifactId>\n")
           .append("                <version>3.1.0</version>\n")
           .append("            </plugin>\n")
           .append("            <plugin>\n")
           .append("                <groupId>org.mule.tools.maven</groupId>\n")
           .append("                <artifactId>mule-maven-plugin</artifactId>\n")
           .append("                <version>${mule.maven.plugin.version}</version>\n")
           .append("                <extensions>true</extensions>\n")
           .append("            </plugin>\n")
           .append("            <plugin>\n")
           .append("                <groupId>org.apache.maven.plugins</groupId>\n")
           .append("                <artifactId>maven-compiler-plugin</artifactId>\n")
           .append("                <version>3.11.0</version>\n")
           .append("                <configuration>\n")
           .append("                    <compilerArgs>\n")
           .append("                        <args>-parameters</args>\n")
           .append("                    </compilerArgs>\n")
           .append("                    <source>17</source>\n")
           .append("                    <target>17</target>\n")
           .append("                </configuration>\n")
           .append("            </plugin>\n")
           .append("        </plugins>\n")
           .append("    </build>\n")
           .append("    <dependencies>\n");
        
        // Add essential Mule runtime core dependencies first
        pom.append("        <!-- Essential Mule Runtime Core Dependencies -->\n")
           .append("        <dependency>\n")
           .append("            <groupId>org.mule.runtime</groupId>\n")
           .append("            <artifactId>mule-core</artifactId>\n")
           .append("            <version>${app.runtime}</version>\n")
           .append("            <scope>provided</scope>\n")
           .append("        </dependency>\n")
           .append("        <dependency>\n")
           .append("            <groupId>org.mule.runtime</groupId>\n")
           .append("            <artifactId>mule-module-extensions-support</artifactId>\n")
           .append("            <version>${app.runtime}</version>\n")
           .append("            <scope>provided</scope>\n")
           .append("        </dependency>\n")
           .append("        <dependency>\n")
           .append("            <groupId>org.mule.runtime</groupId>\n")
           .append("            <artifactId>mule-module-extensions-spring-support</artifactId>\n")
           .append("            <version>${app.runtime}</version>\n")
           .append("            <scope>provided</scope>\n")
           .append("        </dependency>\n");
        
        // Add dependencies dynamically based on flow analysis
        for (String dependency : requiredDependencies) {
            pom.append(dependency);
        }
        
        pom.append("    </dependencies>\n")
           .append("    <repositories>\n")
           .append("        <repository>\n")
           .append("            <id>anypoint-exchange-v3</id>\n")
           .append("            <name>Anypoint Exchange</name>\n")
           .append("            <url>https://maven.anypoint.mulesoft.com/api/v3/maven</url>\n")
           .append("            <layout>default</layout>\n")
           .append("        </repository>\n")
           .append("        <repository>\n")
           .append("            <id>mulesoft-releases</id>\n")
           .append("            <name>MuleSoft Releases Repository</name>\n")
           .append("            <url>https://repository.mulesoft.org/releases/</url>\n")
           .append("            <layout>default</layout>\n")
           .append("        </repository>\n")
           .append("    </repositories>\n")
           .append("    <pluginRepositories>\n")
           .append("        <pluginRepository>\n")
           .append("            <id>mulesoft-releases</id>\n")
           .append("            <name>mulesoft release repository</name>\n")
           .append("            <layout>default</layout>\n")
           .append("            <url>https://repository.mulesoft.org/releases/</url>\n")
           .append("            <snapshots>\n")
           .append("                <enabled>false</enabled>\n")
           .append("            </snapshots>\n")
           .append("        </pluginRepository>\n")
           .append("    </pluginRepositories>\n")
           .append("</project>");
        
        return pom.toString();
    }
    
    /**
     * Analyzes flows to determine required XML namespaces
     */
    private static Set<String> analyzeRequiredNamespaces(JsonNode layer) {
        Set<String> namespaces = new HashSet<>();
        
        // Always include HTTP for API layers
        namespaces.add("http");
        
        // Analyze all flows in the layer to detect required namespaces
        if (layer.has("flows")) {
            for (JsonNode flow : layer.get("flows")) {
                if (flow.has("steps")) {
                    for (JsonNode step : flow.get("steps")) {
                        String stepType = step.get("type").asText().toLowerCase();
                        
                        switch (stepType) {
                            case "validation":
                                namespaces.add("validation");
                                break;
                            case "db":
                            case "database":
                                namespaces.add("db");
                                break;
                            case "salesforce":
                            case "sfdc":
                                namespaces.add("salesforce");
                                break;
                            case "file":
                                namespaces.add("file");
                                break;
                            case "ftp":
                            case "sftp":
                                namespaces.add("ftp");
                                break;
                            case "jms":
                                namespaces.add("jms");
                                break;
                            case "vm":
                                namespaces.add("vm");
                                break;
                            case "email":
                            case "smtp":
                            case "pop3":
                            case "imap":
                                namespaces.add("email");
                                break;
                            case "amqp":
                            case "rabbitmq":
                                namespaces.add("amqp");
                                break;
                        }
                    }
                }
            }
        }
        
        return namespaces;
    }
    
    /**
     * Analyzes layer flows to determine required dependencies
     */
    private static Set<String> analyzeDependencies(JsonNode layer) {
        Set<String> dependencies = new HashSet<>();
        Set<String> requiredConnectors = new HashSet<>();
        
        // Always include HTTP connector for API layers
        requiredConnectors.add("http");
        
        // Analyze all flows in the layer to detect required connectors
        if (layer.has("flows")) {
            for (JsonNode flow : layer.get("flows")) {
                if (flow.has("steps")) {
                    for (JsonNode step : flow.get("steps")) {
                        String stepType = step.get("type").asText().toLowerCase();
                        
                        switch (stepType) {
                            case "validation":
                                requiredConnectors.add("validation");
                                break;
                            case "db":
                            case "database":
                                requiredConnectors.add("database");
                                break;
                            case "transform":
                            case "http-call":
                                requiredConnectors.add("json");
                                break;
                            case "salesforce":
                            case "sfdc":
                                requiredConnectors.add("salesforce");
                                break;
                            case "file":
                                requiredConnectors.add("file");
                                break;
                            case "ftp":
                            case "sftp":
                                requiredConnectors.add("ftp");
                                break;
                            case "jms":
                                requiredConnectors.add("jms");
                                break;
                            case "vm":
                                requiredConnectors.add("vm");
                                break;
                            case "email":
                            case "smtp":
                            case "pop3":
                            case "imap":
                                requiredConnectors.add("email");
                                break;
                            case "amqp":
                            case "rabbitmq":
                                requiredConnectors.add("amqp");
                                break;
                            case "kafka":
                                requiredConnectors.add("kafka");
                                break;
                            case "redis":
                                requiredConnectors.add("redis");
                                break;
                            case "mongodb":
                                requiredConnectors.add("mongodb");
                                break;
                            case "sap":
                                requiredConnectors.add("sap");
                                break;
                        }
                        
                        // Check for connector references in step configuration
                        if (step.has("connector")) {
                            String connectorName = step.get("connector").asText().toLowerCase();
                            requiredConnectors.add(connectorName);
                        }
                    }
                }
            }
        }
        
        // Map connectors to actual Maven dependencies
        for (String connector : requiredConnectors) {
            switch (connector) {
                case "http":
                    dependencies.add("        <dependency>\n" +
                                  "            <groupId>org.mule.connectors</groupId>\n" +
                                  "            <artifactId>mule-http-connector</artifactId>\n" +
                                  "            <version>${mule.http.connector.version}</version>\n" +
                                  "            <classifier>mule-plugin</classifier>\n" +
                                  "        </dependency>\n");
                    break;
                    
                case "validation":
                    dependencies.add("        <dependency>\n" +
                                  "            <groupId>org.mule.modules</groupId>\n" +
                                  "            <artifactId>mule-validation-module</artifactId>\n" +
                                  "            <version>${mule.validation.module.version}</version>\n" +
                                  "            <classifier>mule-plugin</classifier>\n" +
                                  "        </dependency>\n");
                    break;
                    
                case "database":
                    dependencies.add("        <dependency>\n" +
                                  "            <groupId>org.mule.connectors</groupId>\n" +
                                  "            <artifactId>mule-db-connector</artifactId>\n" +
                                  "            <version>${mule.db.connector.version}</version>\n" +
                                  "            <classifier>mule-plugin</classifier>\n" +
                                  "        </dependency>\n");
                    dependencies.add("        <dependency>\n" +
                                  "            <groupId>com.h2database</groupId>\n" +
                                  "            <artifactId>h2</artifactId>\n" +
                                  "            <version>2.2.220</version>\n" +
                                  "        </dependency>\n");
                    break;
                    
                case "json":
                    dependencies.add("        <dependency>\n" +
                                  "            <groupId>com.fasterxml.jackson.core</groupId>\n" +
                                  "            <artifactId>jackson-databind</artifactId>\n" +
                                  "            <version>${jackson.version}</version>\n" +
                                  "        </dependency>\n");
                    break;
                    
                case "salesforce":
                    dependencies.add("        <dependency>\n" +
                                  "            <groupId>org.mule.connectors</groupId>\n" +
                                  "            <artifactId>mule-salesforce-connector</artifactId>\n" +
                                  "            <version>${mule.salesforce.connector.version}</version>\n" +
                                  "            <classifier>mule-plugin</classifier>\n" +
                                  "        </dependency>\n");
                    break;
                    
                case "file":
                    dependencies.add("        <dependency>\n" +
                                  "            <groupId>org.mule.connectors</groupId>\n" +
                                  "            <artifactId>mule-file-connector</artifactId>\n" +
                                  "            <version>${mule.file.connector.version}</version>\n" +
                                  "            <classifier>mule-plugin</classifier>\n" +
                                  "        </dependency>\n");
                    break;
                    
                case "ftp":
                    dependencies.add("        <dependency>\n" +
                                  "            <groupId>org.mule.connectors</groupId>\n" +
                                  "            <artifactId>mule-ftp-connector</artifactId>\n" +
                                  "            <version>${mule.ftp.connector.version}</version>\n" +
                                  "            <classifier>mule-plugin</classifier>\n" +
                                  "        </dependency>\n");
                    break;
                    
                case "jms":
                    dependencies.add("        <dependency>\n" +
                                  "            <groupId>org.mule.connectors</groupId>\n" +
                                  "            <artifactId>mule-jms-connector</artifactId>\n" +
                                  "            <version>${mule.jms.connector.version}</version>\n" +
                                  "            <classifier>mule-plugin</classifier>\n" +
                                  "        </dependency>\n");
                    break;
                    
                case "vm":
                    dependencies.add("        <dependency>\n" +
                                  "            <groupId>org.mule.connectors</groupId>\n" +
                                  "            <artifactId>mule-vm-connector</artifactId>\n" +
                                  "            <version>${mule.vm.connector.version}</version>\n" +
                                  "            <classifier>mule-plugin</classifier>\n" +
                                  "        </dependency>\n");
                    break;
                    
                case "email":
                    dependencies.add("        <dependency>\n" +
                                  "            <groupId>org.mule.connectors</groupId>\n" +
                                  "            <artifactId>mule-email-connector</artifactId>\n" +
                                  "            <version>${mule.email.connector.version}</version>\n" +
                                  "            <classifier>mule-plugin</classifier>\n" +
                                  "        </dependency>\n");
                    break;
                    
                case "amqp":
                    dependencies.add("        <dependency>\n" +
                                  "            <groupId>org.mule.connectors</groupId>\n" +
                                  "            <artifactId>mule-amqp-connector</artifactId>\n" +
                                  "            <version>${mule.amqp.connector.version}</version>\n" +
                                  "            <classifier>mule-plugin</classifier>\n" +
                                  "        </dependency>\n");
                    break;
            }
        }
        
        return dependencies;
    }
    
    private static String generateMuleArtifact() {
        return "{\n" +
               "  \"minMuleVersion\": \"4.9.0\"\n" +
               "}";
    }
    
    private static String generateGlobalConfiguration(String layerName, JsonNode layer) {
        int basePort = 8081;
        if ("process".equals(layerName)) basePort = 8082;
        if ("system".equals(layerName)) basePort = 8083;
        
        Set<String> requiredNamespaces = analyzeRequiredNamespaces(layer);
        
        StringBuilder global = new StringBuilder();
        global.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
              .append("<mule xmlns=\"http://www.mulesoft.org/schema/mule/core\"\n")
              .append("      xmlns:doc=\"http://www.mulesoft.org/schema/mule/documentation\"\n")
              .append("      xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        
        // Add required connector namespaces
        for (String namespace : requiredNamespaces) {
            global.append("      xmlns:").append(namespace).append("=\"http://www.mulesoft.org/schema/mule/").append(namespace).append("\"\n");
        }
        
        // Schema locations
        global.append("      xsi:schemaLocation=\"http://www.mulesoft.org/schema/mule/core http://www.mulesoft.org/schema/mule/core/current/mule.xsd\n");
        for (String namespace : requiredNamespaces) {
            global.append("                          http://www.mulesoft.org/schema/mule/").append(namespace)
                  .append(" http://www.mulesoft.org/schema/mule/").append(namespace).append("/current/mule-").append(namespace).append(".xsd\n");
        }
        global.append("\">\n\n");
        
        // Configuration properties
        global.append("    <configuration-properties file=\"config.properties\" doc:name=\"Configuration properties\"/>\n\n");
        
        // HTTP configuration (always included for API layers)
        if (requiredNamespaces.contains("http")) {
            global.append("    <http:listener-config name=\"HTTP_Listener_config\" doc:name=\"HTTP Listener config\">\n")
                  .append("        <http:listener-connection host=\"${http.host}\" port=\"${http.port}\"/>\n")
                  .append("    </http:listener-config>\n\n")
                  .append("    <http:request-config name=\"HTTP_Request_config\" doc:name=\"HTTP Request config\">\n")
                  .append("        <http:request-connection host=\"${downstream.host}\" port=\"${downstream.port}\"/>\n")
                  .append("    </http:request-config>\n\n");
        }
        
        // Database configuration
        if (requiredNamespaces.contains("db")) {
            global.append("    <db:config name=\"Database_Config\" doc:name=\"Database Config\">\n")
                  .append("        <db:generic-connection url=\"${db.url}\" driverClassName=\"${db.driver}\" user=\"${db.username}\" password=\"${db.password}\"/>\n")
                  .append("    </db:config>\n\n");
        }
        
        // Salesforce configuration
        if (requiredNamespaces.contains("salesforce")) {
            global.append("    <salesforce:sfdc-config name=\"Salesforce_Config\" doc:name=\"Salesforce Config\">\n")
                  .append("        <salesforce:basic-connection username=\"${salesforce.username}\" password=\"${salesforce.password}\" securityToken=\"${salesforce.securityToken}\" url=\"${salesforce.url}\"/>\n")
                  .append("    </salesforce:sfdc-config>\n\n");
        }
        
        // File configuration
        if (requiredNamespaces.contains("file")) {
            global.append("    <file:config name=\"File_Config\" doc:name=\"File Config\">\n")
                  .append("        <file:connection workingDir=\"${file.workingDir}\"/>\n")
                  .append("    </file:config>\n\n");
        }
        
        // Email configuration
        if (requiredNamespaces.contains("email")) {
            global.append("    <email:smtp-config name=\"Email_SMTP_Config\" doc:name=\"Email SMTP Config\">\n")
                  .append("        <email:smtp-connection host=\"${email.smtp.host}\" port=\"${email.smtp.port}\" user=\"${email.smtp.user}\" password=\"${email.smtp.password}\"/>\n")
                  .append("    </email:smtp-config>\n\n");
        }
        
        global.append("</mule>");
        return global.toString();
    }
    
    private static String generateConfigProperties(String layerName, JsonNode layer) {
        int basePort = 8081;
        if ("process".equals(layerName)) basePort = 8082;
        if ("system".equals(layerName)) basePort = 8083;
        
        Set<String> requiredNamespaces = analyzeRequiredNamespaces(layer);
        StringBuilder config = new StringBuilder();
        
        // HTTP Configuration (always included for API layers)
        config.append("# HTTP Configuration\n")
              .append("http.host=0.0.0.0\n")
              .append("http.port=").append(basePort).append("\n")
              .append("\n")
              .append("# Downstream Service Configuration\n")
              .append("downstream.host=localhost\n")
              .append("downstream.port=").append(basePort + 1).append("\n")
              .append("\n");
        
        // Database Configuration
        if (requiredNamespaces.contains("db")) {
            config.append("# Database Configuration\n")
                  .append("db.url=jdbc:h2:mem:testdb\n")
                  .append("db.driver=org.h2.Driver\n")
                  .append("db.username=sa\n")
                  .append("db.password=\n")
                  .append("\n");
        }
        
        // Salesforce Configuration
        if (requiredNamespaces.contains("salesforce")) {
            config.append("# Salesforce Configuration\n")
                  .append("salesforce.username=your-username@salesforce.com\n")
                  .append("salesforce.password=your-password\n")
                  .append("salesforce.securityToken=your-security-token\n")
                  .append("salesforce.url=https://login.salesforce.com/services/Soap/u/58.0\n")
                  .append("\n");
        }
        
        // File Configuration
        if (requiredNamespaces.contains("file")) {
            config.append("# File Configuration\n")
                  .append("file.workingDir=/tmp\n")
                  .append("\n");
        }
        
        // Email Configuration
        if (requiredNamespaces.contains("email")) {
            config.append("# Email Configuration\n")
                  .append("email.smtp.host=smtp.gmail.com\n")
                  .append("email.smtp.port=587\n")
                  .append("email.smtp.user=your-email@gmail.com\n")
                  .append("email.smtp.password=your-password\n")
                  .append("\n");
        }
        
        return config.toString();
    }
    
    private static String generateLogConfiguration() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
               "<Configuration>\n" +
               "    <Appenders>\n" +
               "        <Console name=\"Console\" target=\"SYSTEM_OUT\">\n" +
               "            <PatternLayout pattern=\"%-5p %d [%t] %c: %m%n\"/>\n" +
               "        </Console>\n" +
               "    </Appenders>\n" +
               "    <Loggers>\n" +
               "        <Root level=\"INFO\">\n" +
               "            <AppenderRef ref=\"Console\"/>\n" +
               "        </Root>\n" +
               "    </Loggers>\n" +
               "</Configuration>";
    }
    
    // Utility methods
    
    private static byte[] createZipFromDirectory(Path sourceDir) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            Files.walk(sourceDir)
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    try {
                        String entryName = sourceDir.relativize(path).toString().replace("\\", "/");
                        ZipEntry zipEntry = new ZipEntry(entryName);
                        zos.putNextEntry(zipEntry);
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }
        return baos.toByteArray();
    }
    
    private static void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted((a, b) -> -a.compareTo(b)) // reverse order to delete files before directories
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        // Log but don't fail
                        System.err.println("Failed to delete: " + p + " - " + e.getMessage());
                    }
                });
        }
    }
}