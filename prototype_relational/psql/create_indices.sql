-- -- object_specs

-- CREATE INDEX idx_object_specs_triple ON object_specs(triple_id);
-- CREATE INDEX idx_object_specs_subject ON object_specs(subject);

-- -- projects

-- CREATE INDEX idx_projects_triple ON projects(triple_id);
-- CREATE INDEX idx_projects_subject ON projects(subject);
-- CREATE INDEX idx_object_spec_projects_spec ON object_spec_projects(object_spec_id);
-- CREATE INDEX idx_object_spec_projects_project ON object_spec_projects(project_id);

-- data_objects

CREATE INDEX idx_data_objects_acq_start ON data_objects(acquisition_start_time);
CREATE INDEX idx_data_objects_spec ON data_objects(hasObjectSpec);
CREATE INDEX idx_data_objects_acq_end ON data_objects(acquisition_end_time);
CREATE INDEX idx_data_objects_sub_start ON data_objects(submission_start_time);
CREATE INDEX idx_data_objects_sub_end ON data_objects(submission_end_time);
CREATE INDEX idx_data_objects_data_start ON data_objects(data_start_time);
CREATE INDEX idx_data_objects_data_end ON data_objects(data_end_time);
CREATE INDEX idx_data_objects_sha256 ON data_objects(hasSha256sum);
CREATE INDEX idx_data_objects_size ON data_objects(hasSizeInBytes);
CREATE INDEX idx_data_objects_rows ON data_objects(hasNumberOfRows);
CREATE INDEX idx_data_objects_submitted_by ON data_objects(wasSubmittedBy);
CREATE INDEX idx_data_objects_acquired_by ON data_objects(wasAcquiredBy);
CREATE INDEX idx_data_objects_acquisition_sampling_height ON data_objects(acquisition_hasSamplingHeight);

-- keywords

CREATE INDEX idx_triple_keywords_subject ON triple_keywords(subject);
CREATE INDEX idx_triple_keywords_keyword ON triple_keywords(keyword_id);
CREATE INDEX idx_project_keywords_project ON project_keywords(project_id);
CREATE INDEX idx_project_keywords_keyword ON project_keywords(keyword_id);
CREATE INDEX idx_keywords_keyword ON keywords(keyword);

-- data_object_all_keywords

CREATE INDEX idx_data_object_all_keywords_data_object ON data_object_all_keywords(data_object_id);
CREATE INDEX idx_data_object_all_keywords_keyword ON data_object_all_keywords(keyword_id);
