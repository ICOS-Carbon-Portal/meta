package se.lu.nateko.ingester.model;

import java.net.URI;

public record SpecificInfo(
    URI station,
    AcquisitionInterval acquisitionInterval,
    URI instrument,
    Double samplingHeight,
    Production production
) {}
