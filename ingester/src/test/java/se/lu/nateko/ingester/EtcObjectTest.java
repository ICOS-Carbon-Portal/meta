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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import se.lu.nateko.ingester.model.entity.EtcUploadMetadataEntity;
import se.lu.nateko.ingester.repository.etc.EtcUploadMetadataRepository;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class EtcObjectTest {
	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@MockitoBean
	private EtcUploadMetadataRepository etcUploadMetadataRepository;

	@ParameterizedTest
	@MethodSource("provideJsons")
	public void ingestEtcTest(JsonNode jsonNode) throws JSONException, IOException, Exception {
		Mockito.when(etcUploadMetadataRepository.save(Mockito.any()))
			.thenReturn(new EtcUploadMetadataEntity());

		String url = "http://localhost:" + port + "/ingest/uploaded";
		HttpEntity<JsonNode> request = new HttpEntity<>(jsonNode);

		Assertions.assertEquals(
			HttpStatusCode.valueOf(200),
			this.restTemplate.postForEntity(
			url, request, String.class
			).getStatusCode()
		);
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
		URL resource = Thread.currentThread().getContextClassLoader().getResource("./etc");

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
