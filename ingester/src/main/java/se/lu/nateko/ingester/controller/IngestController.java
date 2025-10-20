package se.lu.nateko.ingester.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import se.lu.nateko.ingester.model.dto.DataObjectDto;
import se.lu.nateko.ingester.service.IngestService;

@RestController
public class IngestController {
	private IngestService ingestService;

	public IngestController(IngestService ingestService) {
		this.ingestService = ingestService;
	}

	@PostMapping("/ingest/uploaded")
	public ResponseEntity<String> ingestUploadPayload(@RequestBody DataObjectDto dataObject) {
		ingestService.saveStationSpecificDataObject(dataObject);
		return ResponseEntity.ok("");
	}
}
