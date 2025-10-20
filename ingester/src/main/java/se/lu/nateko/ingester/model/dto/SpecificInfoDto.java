package se.lu.nateko.ingester.model.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = StationTimeSeriesDto.class, name = "StationTimeSeriesDto"),
    @JsonSubTypes.Type(value = SpatioTemporalDto.class, name = "SpatioTemporalDto")
})
public interface SpecificInfoDto {}
