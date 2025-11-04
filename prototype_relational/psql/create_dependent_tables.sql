-- projects

DROP TABLE IF EXISTS projects CASCADE;

CREATE TABLE projects (
    id SERIAL PRIMARY KEY,
    subject TEXT UNIQUE NOT NULL,
    name TEXT
);

-- data_objects

DROP TABLE IF EXISTS data_objects CASCADE;
DROP TABLE IF EXISTS data_object_submissions CASCADE;

CREATE TABLE data_object_submissions (
    subject TEXT PRIMARY KEY,
    hasStartTime TIMESTAMP WITH TIME ZONE NOT NULL,
    hasEndTime TIMESTAMP WITH TIME ZONE
);

CREATE TABLE data_object_acquisitions (
    subject TEXT PRIMARY KEY,
    startedAtTime TIMESTAMP WITH TIME ZONE,,
    endedAtTime TIMESTAMP WITH TIME ZONE,
    wasAssociatedWith TEXT,
    hasSamplingHeight FLOAT
);

CREATE TABLE data_objects (
    subject TEXT PRIMARY KEY,
    name TEXT,
    hasObjectSpec TEXT NOT NULL,
    hasSha256sum TEXT NOT NULL,
    hasSizeInBytes BIGINT,
    hasNumberOfRows INTEGER,
    hasActualColumnNames TEXT,
    hasDoi TEXT,
    wasSubmittedBy TEXT,
    wasAcquiredBy TEXT,
    isNextVersionOf INTEGER,
    spec_containsDataset TEXT,
    spec_dataset_hasColumn TEXT,
    data_start_time TIMESTAMP WITH TIME ZONE,
    data_end_time TIMESTAMP WITH TIME ZONE,
    submission_start_time TIMESTAMP WITH TIME ZONE,
    submission_end_time TIMESTAMP WITH TIME ZONE,
    acquisition_start_time TIMESTAMP WITH TIME ZONE,
    acquisition_end_time TIMESTAMP WITH TIME ZONE,
    acquisition_wasAssociatedWith TEXT,
    acquisition_hasSamplingHeight FLOAT,
    FOREIGN KEY (wasSubmittedBy) REFERENCES data_object_submissions(subject),
    FOREIGN KEY (wasAcquiredBy) REFERENCES data_object_acquisitions(subject),
    FOREIGN KEY (isNextVersionOf) REFERENCES data_objects(subject)
);


-- keywords

DROP TABLE IF EXISTS project_keywords CASCADE;
DROP TABLE IF EXISTS triple_keywords CASCADE;
DROP TABLE IF EXISTS keywords CASCADE;


CREATE TABLE keywords (
    id SERIAL PRIMARY KEY,
    keyword TEXT UNIQUE NOT NULL
);

CREATE TABLE triple_keywords (
    subject TEXT NOT NULL,
    keyword_id INTEGER NOT NULL,
    PRIMARY KEY (subject, keyword_id),
    FOREIGN KEY (keyword_id) REFERENCES keywords(id)
);

CREATE TABLE project_keywords (
    project_id INTEGER NOT NULL,
    keyword_id INTEGER NOT NULL,
    PRIMARY KEY (project_id, keyword_id),
    FOREIGN KEY (project_id) REFERENCES projects(id),
    FOREIGN KEY (keyword_id) REFERENCES keywords(id)
);

-- aggregate_keywords

DROP TABLE IF EXISTS data_object_all_keywords CASCADE;
CREATE TABLE data_object_all_keywords (
    data_object_id INTEGER NOT NULL,
    keyword_id INTEGER NOT NULL,
    PRIMARY KEY (data_object_id, keyword_id),
    FOREIGN KEY (data_object_id) REFERENCES data_objects(id),
    FOREIGN KEY (keyword_id) REFERENCES keywords(id)
);


-- Initial indices

-- projects

CREATE INDEX idx_projects_subject ON projects(subject);

