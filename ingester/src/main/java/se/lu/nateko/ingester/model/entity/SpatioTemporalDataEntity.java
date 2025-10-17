package se.lu.nateko.ingester.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class SpatioTemporalDataEntity {
	@Id
	private Integer id;

	public SpatioTemporalDataEntity(Integer id) {
		this.id = id;
	}
}
