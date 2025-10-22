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
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
class IngesterApplicationTests {
	@ParameterizedTest
	@MethodSource("provideJsons")
	public void testThings(JsonNode jsonNode) throws JSONException, IOException, Exception {
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
