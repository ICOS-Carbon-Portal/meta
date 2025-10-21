package se.lu.nateko.ingester.deserialize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

public class ListStringDeserializer extends JsonDeserializer<List<String>> {
	@Override
	public List<String> deserialize(JsonParser parser, DeserializationContext context)
		throws IOException, JsonProcessingException
	{
		JsonNode node = parser.getCodec().readTree(parser);
		List<String> result = new ArrayList<>();

		if (node.isArray()) {
			for (JsonNode element : node) {
				result.add(element.asText());
			}
		} else if (node.isTextual()) {
			result.add(node.asText());
		} else if (node.isNull()) {
			return result;
		} else {
			throw new JsonProcessingException("Expected URI or list of URIs") {};
		}
		return result;
	}
}
