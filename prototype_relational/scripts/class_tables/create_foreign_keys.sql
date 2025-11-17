-- Generated SQL for class-based tables (FOREIGN KEYS)
-- Source: class_predicates_analysis.json
-- Run this after creating tables with class_tables/create_class_tables.sql

-- ======================================================================
-- FOREIGN KEY CONSTRAINTS
-- ======================================================================

-- Foreign keys for ct_collections
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_has_part FOREIGN KEY (has_part) REFERENCES ct_data_objects(id);
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_is_next_version_of FOREIGN KEY (is_next_version_of) REFERENCES ct_data_objects(id);
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_creator FOREIGN KEY (creator) REFERENCES ct_central_facilities(id);
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_see_also FOREIGN KEY (see_also) REFERENCES ct_document_objects(id);
ALTER TABLE ct_collections ADD CONSTRAINT fk_ct_collections_has_spatial_coverage FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id);

-- Foreign keys for ct_data_objects
ALTER TABLE ct_data_objects ADD CONSTRAINT fk_ct_data_objects_was_submitted_by FOREIGN KEY (was_submitted_by) REFERENCES ct_data_submissions(id);
ALTER TABLE ct_data_objects ADD CONSTRAINT fk_ct_data_objects_has_spatial_coverage FOREIGN KEY (has_spatial_coverage) REFERENCES ct_spatial_coverages(id);

-- Foreign keys for ct_data_submissions
ALTER TABLE ct_data_submissions ADD CONSTRAINT fk_ct_data_submissions_was_associated_with FOREIGN KEY (was_associated_with) REFERENCES ct_thematic_centers(id);

-- Foreign keys for ct_document_objects
ALTER TABLE ct_document_objects ADD CONSTRAINT fk_ct_document_objects_was_submitted_by FOREIGN KEY (was_submitted_by) REFERENCES ct_data_submissions(id);
ALTER TABLE ct_document_objects ADD CONSTRAINT fk_ct_document_objects_creator FOREIGN KEY (creator) REFERENCES ct_central_facilities(id);

-- Foreign keys for ct_plain_collections
ALTER TABLE ct_plain_collections ADD CONSTRAINT fk_ct_plain_collections_has_part FOREIGN KEY (has_part) REFERENCES ct_data_objects(id);
ALTER TABLE ct_plain_collections ADD CONSTRAINT fk_ct_plain_collections_is_next_version_of FOREIGN KEY (is_next_version_of) REFERENCES ct_data_objects(id);

-- Foreign keys for ct_thematic_centers
ALTER TABLE ct_thematic_centers ADD CONSTRAINT fk_ct_thematic_centers_has_data_theme FOREIGN KEY (has_data_theme) REFERENCES ct_data_themes(id);
