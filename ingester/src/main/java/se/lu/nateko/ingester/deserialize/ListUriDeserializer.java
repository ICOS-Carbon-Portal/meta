package se.lu.nateko.ingester.deserialize;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

public class ListUriDeserializer extends JsonDeserializer<List<URI>> {
	@Override
	public List<URI> deserialize(JsonParser parser, DeserializationContext context)
		throws IOException, JsonProcessingException
	{
		JsonNode node = parser.getCodec().readTree(parser);
		List<URI> result = new ArrayList<>();

		if (node.isArray()) {
			for (JsonNode element : node) {
				result.add(parseUri(element.asText()));
			}
		} else if (node.isTextual()) {
			result.add(parseUri(node.asText()));
		} else if (node.isNull()) {
			return result;
		} else {
			throw new JsonProcessingException("Expected URI or list of URIs") {};
		}
		return result;
	}

	private URI parseUri(String value) throws JsonProcessingException {
		try {
			return new URI(value);
		} catch (URISyntaxException e) {
			throw new JsonProcessingException("Invalid URI: " + value, e) {
			};
		}
	}
}
