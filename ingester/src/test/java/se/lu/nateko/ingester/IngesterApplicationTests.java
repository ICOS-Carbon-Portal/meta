package se.lu.nateko.ingester;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.json.JSONException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import se.lu.nateko.ingester.repository.IngestRepository;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class IngesterApplicationTests {
	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@MockitoBean
	private IngestRepository ingestRepository;

	@ParameterizedTest
	@MethodSource("provideJsons")
	public void testThings(JsonNode jsonNode) throws JSONException, IOException, Exception {
		Mockito.doNothing().when(ingestRepository.save(Mockito.any()));
		List<String> values = new ArrayList<>();
		if (jsonNode.has("fileName")) {
			values.add(jsonNode.get("fileName").asText());
		}
		Assertions.assertFalse(values.get(0).isBlank());
	}

	private static Stream<JsonNode> provideJsons() throws IOException {
		List<String> resourceList = listResources();
		List<JsonNode> jsonNodes = new ArrayList<>();
		ObjectMapper mapper = new ObjectMapper();
		for (String resourceFileName : resourceList) {
			InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceFileName);
			JsonNode jsonNode = mapper.readValue(in, JsonNode.class);
			jsonNodes.add(jsonNode);
		}
		return jsonNodes.stream();
	}

	private static List<String> listResources() {
		URL resource = Thread.currentThread().getContextClassLoader().getResource(".");

		if (resource == null) {
			throw new IllegalArgumentException("Resource path not found");
		}

		File directory = new File(resource.getFile());

		if (!directory.isDirectory()) {
			throw new IllegalStateException("Resource path is not a directory " + directory.getAbsolutePath());
		}

		return Arrays.stream(Objects.requireNonNull(directory.listFiles()))
			.filter(file -> file.getName().endsWith(".json"))
			.map(File::getName)
			.toList();
	}
}
